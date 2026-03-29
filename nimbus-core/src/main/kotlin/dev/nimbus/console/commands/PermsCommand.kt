package dev.nimbus.console.commands

import dev.nimbus.console.Command
import dev.nimbus.console.ConsoleFormatter
import dev.nimbus.event.EventBus
import dev.nimbus.event.NimbusEvent
import dev.nimbus.permissions.PermissionManager

class PermsCommand(
    private val permissionManager: PermissionManager,
    private val eventBus: EventBus
) : Command {

    override val name = "perms"
    override val description = "Manage permission groups and player assignments"
    override val usage = "perms <group|user|reload> [subcommand] [args]"

    override suspend fun execute(args: List<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        when (args[0].lowercase()) {
            "group" -> handleGroup(args.drop(1))
            "user" -> handleUser(args.drop(1))
            "reload" -> handleReload()
            else -> printUsage()
        }
    }

    private suspend fun handleGroup(args: List<String>) {
        if (args.isEmpty()) {
            printGroupUsage()
            return
        }

        when (args[0].lowercase()) {
            "list" -> groupList()
            "info" -> {
                if (args.size < 2) return println(ConsoleFormatter.error("Usage: perms group info <name>"))
                groupInfo(args[1])
            }
            "create" -> {
                if (args.size < 2) return println(ConsoleFormatter.error("Usage: perms group create <name>"))
                groupCreate(args[1])
            }
            "delete" -> {
                if (args.size < 2) return println(ConsoleFormatter.error("Usage: perms group delete <name>"))
                groupDelete(args[1])
            }
            "addperm" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group addperm <group> <permission>"))
                groupAddPerm(args[1], args[2])
            }
            "removeperm" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group removeperm <group> <permission>"))
                groupRemovePerm(args[1], args[2])
            }
            "setdefault" -> {
                if (args.size < 2) return println(ConsoleFormatter.error("Usage: perms group setdefault <group> [true/false]"))
                val value = args.getOrNull(2)?.toBooleanStrictOrNull() ?: true
                groupSetDefault(args[1], value)
            }
            "addparent" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group addparent <group> <parent>"))
                groupAddParent(args[1], args[2])
            }
            "removeparent" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group removeparent <group> <parent>"))
                groupRemoveParent(args[1], args[2])
            }
            "setprefix" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group setprefix <group> <prefix...>"))
                groupSetPrefix(args[1], args.drop(2).joinToString(" "))
            }
            "setsuffix" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group setsuffix <group> <suffix...>"))
                groupSetSuffix(args[1], args.drop(2).joinToString(" "))
            }
            "setpriority" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms group setpriority <group> <number>"))
                val priority = args[2].toIntOrNull()
                if (priority == null) return println(ConsoleFormatter.error("Priority must be a number."))
                groupSetPriority(args[1], priority)
            }
            else -> printGroupUsage()
        }
    }

    private suspend fun handleUser(args: List<String>) {
        if (args.isEmpty()) {
            printUserUsage()
            return
        }

        when (args[0].lowercase()) {
            "info" -> {
                if (args.size < 2) return println(ConsoleFormatter.error("Usage: perms user info <name|uuid>"))
                userInfo(args[1])
            }
            "addgroup" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms user addgroup <name|uuid> <group>"))
                userAddGroup(args[1], args[2])
            }
            "removegroup" -> {
                if (args.size < 3) return println(ConsoleFormatter.error("Usage: perms user removegroup <name|uuid> <group>"))
                userRemoveGroup(args[1], args[2])
            }
            "list" -> userList()
            else -> printUserUsage()
        }
    }

    // ── Group subcommands ───────────────────────────────────

    private fun groupList() {
        val groups = permissionManager.getAllGroups()
        if (groups.isEmpty()) {
            println(ConsoleFormatter.emptyState("No permission groups configured."))
            return
        }

        val headers = listOf("NAME", "DEFAULT", "PRIORITY", "PREFIX", "PERMISSIONS", "PARENTS")
        val rows = groups.sortedByDescending { it.priority }.map { group ->
            listOf(
                ConsoleFormatter.colorize(group.name, ConsoleFormatter.BOLD),
                if (group.default) ConsoleFormatter.colorize("yes", ConsoleFormatter.GREEN) else "no",
                group.priority.toString(),
                if (group.prefix.isEmpty()) ConsoleFormatter.placeholder()
                else group.prefix,
                group.permissions.size.toString(),
                if (group.parents.isEmpty()) ConsoleFormatter.placeholder()
                else group.parents.joinToString(", ")
            )
        }

        println(ConsoleFormatter.header("Permission Groups"))
        println(ConsoleFormatter.formatTable(headers, rows))
        println(ConsoleFormatter.count(groups.size, "group"))
    }

    private fun groupInfo(name: String) {
        val group = permissionManager.getGroup(name)
        if (group == null) {
            println(ConsoleFormatter.error("Permission group '$name' not found."))
            return
        }

        println(ConsoleFormatter.header("Permission Group: ${group.name}"))
        println(ConsoleFormatter.field("Default", if (group.default) ConsoleFormatter.success("yes") else "no"))
        println(ConsoleFormatter.field("Priority", group.priority.toString()))
        println(ConsoleFormatter.field("Prefix", if (group.prefix.isEmpty()) ConsoleFormatter.placeholder() else group.prefix))
        println(ConsoleFormatter.field("Suffix", if (group.suffix.isEmpty()) ConsoleFormatter.placeholder() else group.suffix))
        println(ConsoleFormatter.field("Parents", if (group.parents.isEmpty()) ConsoleFormatter.placeholder() else group.parents.joinToString(", ")))
        println()
        println(ConsoleFormatter.section("Permissions (${group.permissions.size})"))

        if (group.permissions.isEmpty()) {
            println("  ${ConsoleFormatter.emptyState("No permissions set.")}")
        } else {
            for (perm in group.permissions.sorted()) {
                val color = if (perm.startsWith("-")) ConsoleFormatter.RED else ConsoleFormatter.GREEN
                println("  $color$perm${ConsoleFormatter.RESET}")
            }
        }
    }

    private suspend fun groupCreate(name: String) {
        try {
            permissionManager.createGroup(name)
            eventBus.emit(NimbusEvent.PermissionGroupCreated(name))
            println(ConsoleFormatter.success("Permission group '$name' created."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed to create group"))
        }
    }

    private suspend fun groupDelete(name: String) {
        try {
            permissionManager.deleteGroup(name)
            eventBus.emit(NimbusEvent.PermissionGroupDeleted(name))
            println(ConsoleFormatter.success("Permission group '$name' deleted."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed to delete group"))
        }
    }

    private suspend fun groupAddPerm(groupName: String, permission: String) {
        try {
            permissionManager.addPermission(groupName, permission)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Added '$permission' to group '$groupName'."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupRemovePerm(groupName: String, permission: String) {
        try {
            permissionManager.removePermission(groupName, permission)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Removed '$permission' from group '$groupName'."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupSetDefault(groupName: String, value: Boolean) {
        try {
            permissionManager.setDefault(groupName, value)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Group '$groupName' default set to $value."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupAddParent(groupName: String, parentName: String) {
        try {
            permissionManager.addParent(groupName, parentName)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Added parent '$parentName' to group '$groupName'."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupRemoveParent(groupName: String, parentName: String) {
        try {
            permissionManager.removeParent(groupName, parentName)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Removed parent '$parentName' from group '$groupName'."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupSetPrefix(groupName: String, prefix: String) {
        try {
            permissionManager.updateGroupDisplay(groupName, prefix = prefix, suffix = null, priority = null)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Prefix for '$groupName' set to: $prefix"))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupSetSuffix(groupName: String, suffix: String) {
        try {
            permissionManager.updateGroupDisplay(groupName, prefix = null, suffix = suffix, priority = null)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Suffix for '$groupName' set to: $suffix"))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun groupSetPriority(groupName: String, priority: Int) {
        try {
            permissionManager.updateGroupDisplay(groupName, prefix = null, suffix = null, priority = priority)
            eventBus.emit(NimbusEvent.PermissionGroupUpdated(groupName))
            println(ConsoleFormatter.success("Priority for '$groupName' set to $priority."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    // ── User subcommands ────────────────────────────────────

    private fun userInfo(identifier: String) {
        // Try as UUID first, then by name
        val uuid: String
        val playerName: String
        val groups: List<String>

        if (identifier.contains("-")) {
            uuid = identifier
            val entry = permissionManager.getPlayer(identifier)
            playerName = entry?.name ?: identifier
            groups = entry?.groups ?: emptyList()
        } else {
            val result = permissionManager.getPlayerByName(identifier)
            if (result != null) {
                uuid = result.first
                playerName = result.second.name
                groups = result.second.groups
            } else {
                println(ConsoleFormatter.error("Player '$identifier' not found. Use UUID for new players."))
                return
            }
        }

        val effective = permissionManager.getEffectivePermissions(uuid)
        val defaultGroup = permissionManager.getDefaultGroup()
        val display = permissionManager.getPlayerDisplay(uuid)

        println(ConsoleFormatter.header("Player: $playerName"))
        println(ConsoleFormatter.field("UUID", uuid))
        println(ConsoleFormatter.field("Groups", if (groups.isEmpty()) ConsoleFormatter.placeholder() else groups.joinToString(", ")))
        if (defaultGroup != null) {
            println(ConsoleFormatter.field("Default", defaultGroup.name))
        }
        println(ConsoleFormatter.field("Display", "${display.prefix}$playerName${display.suffix} ${ConsoleFormatter.hint("(group: ${display.groupName}, priority: ${display.priority})")}"))
        println()
        println(ConsoleFormatter.section("Effective Permissions (${effective.size})"))

        if (effective.isEmpty()) {
            println("  ${ConsoleFormatter.emptyState("No permissions.")}")
        } else {
            for (perm in effective.sorted()) {
                println("  ${ConsoleFormatter.GREEN}$perm${ConsoleFormatter.RESET}")
            }
        }
    }

    private suspend fun userAddGroup(identifier: String, groupName: String) {
        val (uuid, playerName) = resolvePlayer(identifier) ?: return
        try {
            permissionManager.setPlayerGroup(uuid, playerName, groupName)
            eventBus.emit(NimbusEvent.PlayerPermissionsUpdated(uuid, playerName))
            println(ConsoleFormatter.success("Added group '$groupName' to player '$playerName'."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private suspend fun userRemoveGroup(identifier: String, groupName: String) {
        val (uuid, playerName) = resolvePlayer(identifier) ?: return
        try {
            permissionManager.removePlayerGroup(uuid, groupName)
            eventBus.emit(NimbusEvent.PlayerPermissionsUpdated(uuid, playerName))
            println(ConsoleFormatter.success("Removed group '$groupName' from player '$playerName'."))
        } catch (e: IllegalArgumentException) {
            println(ConsoleFormatter.error(e.message ?: "Failed"))
        }
    }

    private fun userList() {
        val players = permissionManager.getAllPlayers()
        if (players.isEmpty()) {
            println(ConsoleFormatter.emptyState("No player assignments."))
            return
        }

        val headers = listOf("NAME", "UUID", "GROUPS")
        val rows = players.entries.sortedBy { it.value.name }.map { (uuid, entry) ->
            listOf(
                ConsoleFormatter.colorize(entry.name, ConsoleFormatter.BOLD),
                ConsoleFormatter.colorize(uuid, ConsoleFormatter.DIM),
                if (entry.groups.isEmpty()) "-" else entry.groups.joinToString(", ")
            )
        }

        println(ConsoleFormatter.header("Player Assignments"))
        println(ConsoleFormatter.formatTable(headers, rows))
        println(ConsoleFormatter.count(players.size, "player"))
    }

    private fun resolvePlayer(identifier: String): Pair<String, String>? {
        if (identifier.contains("-")) {
            val entry = permissionManager.getPlayer(identifier)
            return if (entry != null) identifier to entry.name
            else identifier to identifier // UUID not seen before
        } else {
            val result = permissionManager.getPlayerByName(identifier)
            if (result != null) return result.first to result.second.name
            println(ConsoleFormatter.error("Player '$identifier' not found. Use UUID for first-time assignment."))
            return null
        }
    }

    // ── Reload ──────────────────────────────────────────────

    private suspend fun handleReload() {
        permissionManager.reload()
        println(ConsoleFormatter.success("Permissions reloaded."))
    }

    // ── Help ────────────────────────────────────────────────

    private fun printUsage() {
        val pad = 44
        println(ConsoleFormatter.header("Permissions"))
        println(ConsoleFormatter.commandEntry("perms group list", "List all groups", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group info <group>", "Show group details", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group create <name>", "Create a group", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group delete <name>", "Delete a group", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group addperm <group> <perm>", "Add permission", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group removeperm <group> <perm>", "Remove permission", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group setdefault <group>", "Set as default group", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group addparent <group> <parent>", "Add inheritance", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group removeparent <group> <parent>", "Remove inheritance", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group setprefix <group> <prefix...>", "Set display prefix (MiniMessage)", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group setsuffix <group> <suffix...>", "Set display suffix (MiniMessage)", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms group setpriority <group> <number>", "Set display priority", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms user list", "List all players", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms user info <name|uuid>", "Show player perms", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms user addgroup <name|uuid> <group>", "Assign group", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms user removegroup <name|uuid> <group>", "Remove group", padWidth = pad))
        println(ConsoleFormatter.commandEntry("perms reload", "Reload from database", padWidth = pad))
    }

    private fun printGroupUsage() {
        println(ConsoleFormatter.error("Usage: perms group <list|info|create|delete|addperm|removeperm|setdefault|addparent|removeparent|setprefix|setsuffix|setpriority>"))
    }

    private fun printUserUsage() {
        println(ConsoleFormatter.error("Usage: perms user <list|info|addgroup|removegroup>"))
    }
}
