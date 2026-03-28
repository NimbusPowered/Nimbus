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
            println(ConsoleFormatter.warn("No permission groups configured."))
            return
        }

        val headers = listOf("NAME", "DEFAULT", "PERMISSIONS", "PARENTS")
        val rows = groups.sortedBy { it.name }.map { group ->
            listOf(
                ConsoleFormatter.colorize(group.name, ConsoleFormatter.BOLD),
                if (group.default) ConsoleFormatter.colorize("yes", ConsoleFormatter.GREEN) else "no",
                group.permissions.size.toString(),
                if (group.parents.isEmpty()) ConsoleFormatter.colorize("-", ConsoleFormatter.DIM)
                else group.parents.joinToString(", ")
            )
        }

        println(ConsoleFormatter.header("Permission Groups"))
        println(ConsoleFormatter.formatTable(headers, rows))
        println(ConsoleFormatter.colorize("${groups.size} group(s)", ConsoleFormatter.DIM))
    }

    private fun groupInfo(name: String) {
        val group = permissionManager.getGroup(name)
        if (group == null) {
            println(ConsoleFormatter.error("Permission group '$name' not found."))
            return
        }

        println(ConsoleFormatter.header("Permission Group: ${group.name}"))
        println("  ${ConsoleFormatter.DIM}Default:${ConsoleFormatter.RESET}  ${if (group.default) ConsoleFormatter.success("yes") else "no"}")
        println("  ${ConsoleFormatter.DIM}Parents:${ConsoleFormatter.RESET}  ${if (group.parents.isEmpty()) "-" else group.parents.joinToString(", ")}")
        println()
        println(ConsoleFormatter.section("Permissions (${group.permissions.size})"))

        if (group.permissions.isEmpty()) {
            println("  ${ConsoleFormatter.colorize("No permissions set.", ConsoleFormatter.DIM)}")
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

        println(ConsoleFormatter.header("Player: $playerName"))
        println("  ${ConsoleFormatter.DIM}UUID:${ConsoleFormatter.RESET}    $uuid")
        println("  ${ConsoleFormatter.DIM}Groups:${ConsoleFormatter.RESET}  ${if (groups.isEmpty()) "-" else groups.joinToString(", ")}")
        if (defaultGroup != null) {
            println("  ${ConsoleFormatter.DIM}Default:${ConsoleFormatter.RESET} ${defaultGroup.name}")
        }
        println()
        println(ConsoleFormatter.section("Effective Permissions (${effective.size})"))

        if (effective.isEmpty()) {
            println("  ${ConsoleFormatter.colorize("No permissions.", ConsoleFormatter.DIM)}")
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
            println(ConsoleFormatter.warn("No player assignments."))
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
        println(ConsoleFormatter.colorize("${players.size} player(s)", ConsoleFormatter.DIM))
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

    private fun handleReload() {
        permissionManager.reload()
        println(ConsoleFormatter.success("Permissions reloaded."))
    }

    // ── Help ────────────────────────────────────────────────

    private fun printUsage() {
        println(ConsoleFormatter.header("Permissions"))
        println("  ${ConsoleFormatter.CYAN}perms group list${ConsoleFormatter.RESET}                         List all groups")
        println("  ${ConsoleFormatter.CYAN}perms group info <group>${ConsoleFormatter.RESET}                 Show group details")
        println("  ${ConsoleFormatter.CYAN}perms group create <name>${ConsoleFormatter.RESET}                Create a group")
        println("  ${ConsoleFormatter.CYAN}perms group delete <name>${ConsoleFormatter.RESET}                Delete a group")
        println("  ${ConsoleFormatter.CYAN}perms group addperm <group> <perm>${ConsoleFormatter.RESET}      Add permission")
        println("  ${ConsoleFormatter.CYAN}perms group removeperm <group> <perm>${ConsoleFormatter.RESET}   Remove permission")
        println("  ${ConsoleFormatter.CYAN}perms group setdefault <group>${ConsoleFormatter.RESET}           Set as default group")
        println("  ${ConsoleFormatter.CYAN}perms group addparent <group> <parent>${ConsoleFormatter.RESET}  Add inheritance")
        println("  ${ConsoleFormatter.CYAN}perms group removeparent <group> <parent>${ConsoleFormatter.RESET} Remove inheritance")
        println("  ${ConsoleFormatter.CYAN}perms user list${ConsoleFormatter.RESET}                          List all players")
        println("  ${ConsoleFormatter.CYAN}perms user info <name|uuid>${ConsoleFormatter.RESET}              Show player perms")
        println("  ${ConsoleFormatter.CYAN}perms user addgroup <name|uuid> <group>${ConsoleFormatter.RESET} Assign group")
        println("  ${ConsoleFormatter.CYAN}perms user removegroup <name|uuid> <group>${ConsoleFormatter.RESET} Remove group")
        println("  ${ConsoleFormatter.CYAN}perms reload${ConsoleFormatter.RESET}                             Reload from files")
    }

    private fun printGroupUsage() {
        println(ConsoleFormatter.error("Usage: perms group <list|info|create|delete|addperm|removeperm|setdefault|addparent|removeparent>"))
    }

    private fun printUserUsage() {
        println(ConsoleFormatter.error("Usage: perms user <list|info|addgroup|removegroup>"))
    }
}
