package dev.nimbus.permissions

import dev.nimbus.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory

/**
 * Manages permission groups and player-to-group assignments.
 * Data is stored in SQLite via Exposed, with in-memory caches for fast reads.
 */
class PermissionManager(private val db: DatabaseManager) {

    private val logger = LoggerFactory.getLogger(PermissionManager::class.java)

    private val groups = mutableMapOf<String, PermissionGroup>()
    private val players = mutableMapOf<String, PlayerEntry>() // UUID -> PlayerEntry

    suspend fun init() {
        reload()
        ensureDefaultGroup()
    }

    private suspend fun ensureDefaultGroup() {
        if (groups.isNotEmpty()) return
        logger.info("No permission groups found — creating default group")
        createGroup("Default", default = true)
    }

    suspend fun reload() {
        groups.clear()
        players.clear()
        loadGroups()
        loadPlayers()
        logger.info("Loaded {} permission group(s), {} player assignment(s)", groups.size, players.size)
    }

    // ── Group CRUD ──────────────────────────────────────────────

    fun getAllGroups(): List<PermissionGroup> = groups.values.toList()

    fun getGroup(name: String): PermissionGroup? =
        groups.values.find { it.name.equals(name, ignoreCase = true) }

    fun getDefaultGroup(): PermissionGroup? =
        groups.values.find { it.default }

    suspend fun createGroup(name: String, default: Boolean = false): PermissionGroup {
        require(getGroup(name) == null) { "Group '$name' already exists" }
        val group = PermissionGroup(name = name, default = default)
        groups[name.lowercase()] = group
        saveGroup(group)
        return group
    }

    suspend fun deleteGroup(name: String) {
        val group = getGroup(name) ?: throw IllegalArgumentException("Group '$name' not found")
        groups.remove(group.name.lowercase())

        db.query {
            // CASCADE deletes group_permissions and group_parents
            PermissionGroups.deleteWhere { PermissionGroups.name.lowerCase() eq group.name.lowercase() }
            // Clean up player_groups references
            PlayerGroups.deleteWhere { PlayerGroups.groupName.lowerCase() eq group.name.lowercase() }
        }

        // Remove group from in-memory player cache
        players.values.forEach { it.groups.removeAll { g -> g.equals(name, ignoreCase = true) } }
    }

    suspend fun addPermission(groupName: String, permission: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (permission !in group.permissions) {
            group.permissions.add(permission)
            saveGroup(group)
        }
    }

    suspend fun removePermission(groupName: String, permission: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (group.permissions.remove(permission)) {
            saveGroup(group)
        }
    }

    suspend fun setDefault(groupName: String, default: Boolean) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (default) {
            groups.values.filter { it.default && it.name != group.name }.forEach {
                groups[it.name.lowercase()] = it.copy(default = false)
                saveGroup(groups[it.name.lowercase()]!!)
            }
        }
        groups[group.name.lowercase()] = group.copy(default = default)
        saveGroup(groups[group.name.lowercase()]!!)
    }

    suspend fun addParent(groupName: String, parentName: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        getGroup(parentName) ?: throw IllegalArgumentException("Parent group '$parentName' not found")
        if (!group.parents.any { it.equals(parentName, ignoreCase = true) }) {
            group.parents.add(parentName)
            saveGroup(group)
        }
    }

    suspend fun removeParent(groupName: String, parentName: String) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        if (group.parents.removeAll { it.equals(parentName, ignoreCase = true) }) {
            saveGroup(group)
        }
    }

    // ── Player CRUD ─────────────────────────────────────────────

    fun getAllPlayers(): Map<String, PlayerEntry> = players.toMap()

    fun getPlayer(uuid: String): PlayerEntry? = players[uuid]

    fun getPlayerByName(name: String): Pair<String, PlayerEntry>? =
        players.entries.find { it.value.name.equals(name, ignoreCase = true) }?.let { it.key to it.value }

    suspend fun registerPlayer(uuid: String, playerName: String): Boolean {
        val existing = players[uuid]
        if (existing != null && existing.name == playerName) return false

        players[uuid] = existing?.copy(name = playerName) ?: PlayerEntry(name = playerName)

        db.query {
            val exists = Players.selectAll().where { Players.uuid eq uuid }.count() > 0
            if (exists) {
                Players.update({ Players.uuid eq uuid }) { it[name] = playerName }
            } else {
                Players.insert {
                    it[Players.uuid] = uuid
                    it[name] = playerName
                }
            }
        }
        return true
    }

    suspend fun setPlayerGroup(uuid: String, playerName: String, groupName: String) {
        getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        val entry = players.getOrPut(uuid) { PlayerEntry(name = playerName) }
        if (!entry.groups.any { it.equals(groupName, ignoreCase = true) }) {
            entry.groups.add(groupName)
        }
        players[uuid] = entry.copy(name = playerName)

        db.query {
            // Ensure player exists
            val exists = Players.selectAll().where { Players.uuid eq uuid }.count() > 0
            if (exists) {
                Players.update({ Players.uuid eq uuid }) { it[name] = playerName }
            } else {
                Players.insert {
                    it[Players.uuid] = uuid
                    it[name] = playerName
                }
            }
            // Add group assignment (ignore if duplicate)
            val alreadyAssigned = PlayerGroups.selectAll().where {
                (PlayerGroups.playerUuid eq uuid) and (PlayerGroups.groupName.lowerCase() eq groupName.lowercase())
            }.count() > 0
            if (!alreadyAssigned) {
                PlayerGroups.insert {
                    it[playerUuid] = uuid
                    it[PlayerGroups.groupName] = groupName
                }
            }
        }
    }

    suspend fun removePlayerGroup(uuid: String, groupName: String) {
        val entry = players[uuid] ?: throw IllegalArgumentException("Player not found")
        if (entry.groups.removeAll { it.equals(groupName, ignoreCase = true) }) {
            db.query {
                PlayerGroups.deleteWhere {
                    (PlayerGroups.playerUuid eq uuid) and (PlayerGroups.groupName.lowerCase() eq groupName.lowercase())
                }
            }
        }
    }

    // ── Permission Resolution ───────────────────────────────────

    fun getEffectivePermissions(uuid: String): Set<String> {
        val result = mutableSetOf<String>()
        val negated = mutableSetOf<String>()

        getDefaultGroup()?.let { collectPermissions(it, result, negated, mutableSetOf()) }

        val entry = players[uuid]
        if (entry != null) {
            for (groupName in entry.groups) {
                val group = getGroup(groupName) ?: continue
                collectPermissions(group, result, negated, mutableSetOf())
            }
        }

        result.removeAll(negated)
        return result
    }

    fun hasPermission(uuid: String, permission: String): Boolean {
        val effective = getEffectivePermissions(uuid)
        return matchesPermission(effective, permission)
    }

    // ── Display (Prefix/Suffix) ──────────────────────────────────

    fun getPlayerDisplay(uuid: String): PlayerDisplay {
        val entry = players[uuid]
        val playerGroups = entry?.groups?.mapNotNull { getGroup(it) } ?: emptyList()
        val defaultGroup = getDefaultGroup()

        val allGroups = if (playerGroups.isEmpty() && defaultGroup != null) {
            listOf(defaultGroup)
        } else {
            playerGroups
        }

        val bestGroup = allGroups.maxByOrNull { it.priority } ?: defaultGroup

        return PlayerDisplay(
            prefix = bestGroup?.prefix ?: "",
            suffix = bestGroup?.suffix ?: "",
            groupName = bestGroup?.name ?: "",
            priority = bestGroup?.priority ?: 0
        )
    }

    data class PlayerDisplay(
        val prefix: String,
        val suffix: String,
        val groupName: String,
        val priority: Int
    )

    suspend fun updateGroupDisplay(groupName: String, prefix: String?, suffix: String?, priority: Int?) {
        val group = getGroup(groupName) ?: throw IllegalArgumentException("Group '$groupName' not found")
        groups[group.name.lowercase()] = group.copy(
            prefix = prefix ?: group.prefix,
            suffix = suffix ?: group.suffix,
            priority = priority ?: group.priority
        )
        saveGroup(groups[group.name.lowercase()]!!)
    }

    // ── Internal ────────────────────────────────────────────────

    private fun collectPermissions(
        group: PermissionGroup,
        granted: MutableSet<String>,
        negated: MutableSet<String>,
        visited: MutableSet<String>
    ) {
        if (group.name.lowercase() in visited) return
        visited.add(group.name.lowercase())

        for (parentName in group.parents) {
            val parent = getGroup(parentName) ?: continue
            collectPermissions(parent, granted, negated, visited)
        }

        for (perm in group.permissions) {
            if (perm.startsWith("-")) {
                negated.add(perm.removePrefix("-"))
            } else {
                granted.add(perm)
            }
        }
    }

    companion object {
        fun matchesPermission(effective: Set<String>, permission: String): Boolean {
            if (permission in effective) return true
            if ("*" in effective) return true

            val parts = permission.split(".")
            for (i in parts.indices) {
                val wildcard = parts.subList(0, i + 1).joinToString(".").removeSuffix(".${parts[i]}") + ".*"
                if (i > 0 && wildcard in effective) return true
            }

            for (i in 1..parts.size) {
                val prefix = parts.subList(0, i - 1).joinToString(".")
                val wildcard = if (prefix.isEmpty()) "*" else "$prefix.*"
                if (wildcard in effective) return true
            }

            return false
        }
    }

    // ── Database I/O ────────────────────────────────────────────

    private suspend fun loadGroups() {
        db.query {
            PermissionGroups.selectAll().forEach { row ->
                val groupId = row[PermissionGroups.id]
                val name = row[PermissionGroups.name]

                val permissions = GroupPermissions.selectAll()
                    .where { GroupPermissions.groupId eq groupId }
                    .map { it[GroupPermissions.permission] }
                    .toMutableList()

                val parents = GroupParents.selectAll()
                    .where { GroupParents.groupId eq groupId }
                    .map { it[GroupParents.parentName] }
                    .toMutableList()

                groups[name.lowercase()] = PermissionGroup(
                    name = name,
                    default = row[PermissionGroups.isDefault],
                    prefix = row[PermissionGroups.prefix],
                    suffix = row[PermissionGroups.suffix],
                    priority = row[PermissionGroups.priority],
                    permissions = permissions,
                    parents = parents
                )
            }
        }
    }

    private suspend fun loadPlayers() {
        db.query {
            Players.selectAll().forEach { row ->
                val uuid = row[Players.uuid]
                val name = row[Players.name]

                val playerGroupNames = PlayerGroups.selectAll()
                    .where { PlayerGroups.playerUuid eq uuid }
                    .map { it[PlayerGroups.groupName] }
                    .toMutableList()

                players[uuid] = PlayerEntry(name = name, groups = playerGroupNames)
            }
        }
    }

    private suspend fun saveGroup(group: PermissionGroup) {
        db.query {
            val existing = PermissionGroups.selectAll()
                .where { PermissionGroups.name.lowerCase() eq group.name.lowercase() }
                .firstOrNull()

            val groupId = if (existing != null) {
                PermissionGroups.update({ PermissionGroups.name.lowerCase() eq group.name.lowercase() }) {
                    it[name] = group.name
                    it[isDefault] = group.default
                    it[prefix] = group.prefix
                    it[suffix] = group.suffix
                    it[priority] = group.priority
                }
                existing[PermissionGroups.id]
            } else {
                PermissionGroups.insertAndGetId {
                    it[name] = group.name
                    it[isDefault] = group.default
                    it[prefix] = group.prefix
                    it[suffix] = group.suffix
                    it[priority] = group.priority
                }
            }

            // Replace permissions
            GroupPermissions.deleteWhere { GroupPermissions.groupId eq groupId }
            for (perm in group.permissions) {
                GroupPermissions.insert {
                    it[GroupPermissions.groupId] = groupId
                    it[permission] = perm
                }
            }

            // Replace parents
            GroupParents.deleteWhere { GroupParents.groupId eq groupId }
            for (parent in group.parents) {
                GroupParents.insert {
                    it[GroupParents.groupId] = groupId
                    it[parentName] = parent
                }
            }
        }
    }
}
