package dev.nimbuspowered.nimbus.module.storage

import dev.nimbuspowered.nimbus.console.ConsoleFormatter
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.BOLD
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.CYAN
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.DIM
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.GREEN
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.RED
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.RESET
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.YELLOW
import dev.nimbuspowered.nimbus.module.api.CommandOutput
import dev.nimbuspowered.nimbus.module.api.ModuleCommand

class StorageCommand(private val syncManager: TemplateSyncManager?) : ModuleCommand {
    override val name = "storage"
    override val description = "S3 template sync management"
    override val usage = "storage [status|list|push|pull|sync] [<name>|--all]"
    override val permission = "nimbus.cloud.storage"

    override suspend fun execute(args: List<String>, output: CommandOutput): Boolean {
        if (syncManager == null) {
            output.error("Storage module is disabled — set enabled = true in storage.toml to activate")
            return true
        }
        val sub = args.firstOrNull()?.lowercase() ?: "status"
        when (sub) {
            "status" -> {
                val statuses = syncManager.status()
                if (statuses.isEmpty()) {
                    output.info("No templates found (local or remote)")
                    return true
                }
                output.header("Template Sync Status")
                val nameW = (statuses.maxOfOrNull { it.templateName.length } ?: 8).coerceAtLeast(8)
                output.item("${"Template".padEnd(nameW)}  ${"Local".padEnd(7)}  ${"Remote".padEnd(7)}  In Sync")
                output.item("${"─".repeat(nameW)}  ${"─".repeat(7)}  ${"─".repeat(7)}  ${"─".repeat(7)}")
                for (s in statuses) {
                    val local = if (s.localExists) "${GREEN}yes (${s.localFileCount})$RESET" else "${DIM}no$RESET"
                    val remote = if (s.remoteExists) "${GREEN}yes (${s.remoteFileCount})$RESET" else "${DIM}no$RESET"
                    val sync = when {
                        s.inSync -> "${GREEN}✓$RESET"
                        s.localExists && s.remoteExists -> "${YELLOW}≠$RESET"
                        else -> "${DIM}–$RESET"
                    }
                    output.item("${BOLD}${s.templateName.padEnd(nameW)}$RESET  $local  $remote  $sync")
                }
            }

            "list" -> {
                val remote = syncManager.listRemote()
                val local = syncManager.listLocal()
                output.header("Templates")
                if (local.isNotEmpty()) {
                    output.item("${BOLD}Local:$RESET")
                    for (name in local) output.item("  $CYAN$name$RESET")
                } else {
                    output.item("${DIM}No local templates$RESET")
                }
                if (remote.isNotEmpty()) {
                    output.item("${BOLD}Remote:$RESET")
                    for (name in remote) output.item("  $CYAN$name$RESET")
                } else {
                    output.item("${DIM}No remote templates$RESET")
                }
            }

            "push" -> {
                val target = args.getOrNull(1)
                if (target == null) {
                    output.error("Usage: storage push <name>|--all")
                    return true
                }
                if (target == "--all") {
                    val local = syncManager.listLocal()
                    if (local.isEmpty()) { output.info("No local templates to push"); return true }
                    output.header("Pushing ${local.size} template(s)")
                    for (name in local) {
                        val result = syncManager.push(name) { output.item("  $DIM$it$RESET") }
                        printSyncResult(output, result, "push")
                    }
                } else {
                    output.header("Pushing $target")
                    val result = syncManager.push(target) { output.item("  $DIM$it$RESET") }
                    printSyncResult(output, result, "push")
                }
            }

            "pull" -> {
                val target = args.getOrNull(1)
                if (target == null) {
                    output.error("Usage: storage pull <name>|--all")
                    return true
                }
                if (target == "--all") {
                    val remote = syncManager.listRemote()
                    if (remote.isEmpty()) { output.info("No remote templates to pull"); return true }
                    output.header("Pulling ${remote.size} template(s)")
                    for (name in remote) {
                        val result = syncManager.pull(name) { output.item("  $DIM$it$RESET") }
                        printSyncResult(output, result, "pull")
                    }
                } else {
                    output.header("Pulling $target")
                    val result = syncManager.pull(target) { output.item("  $DIM$it$RESET") }
                    printSyncResult(output, result, "pull")
                }
            }

            "sync" -> {
                val target = args.getOrNull(1)
                if (target == null) {
                    output.error("Usage: storage sync <name>|--all")
                    return true
                }
                if (target == "--all") {
                    val local = syncManager.listLocal()
                    if (local.isEmpty()) { output.info("No local templates to sync"); return true }
                    output.header("Syncing ${local.size} template(s)")
                    for (name in local) {
                        output.item("${BOLD}$name$RESET: pushing…")
                        val pushResult = syncManager.push(name) { output.item("  $DIM$it$RESET") }
                        printSyncResult(output, pushResult, "push")
                        output.item("${BOLD}$name$RESET: pulling…")
                        val pullResult = syncManager.pull(name) { output.item("  $DIM$it$RESET") }
                        printSyncResult(output, pullResult, "pull")
                    }
                } else {
                    output.header("Syncing $target")
                    output.item("Pushing…")
                    val pushResult = syncManager.push(target) { output.item("  $DIM$it$RESET") }
                    printSyncResult(output, pushResult, "push")
                    output.item("Pulling…")
                    val pullResult = syncManager.pull(target) { output.item("  $DIM$it$RESET") }
                    printSyncResult(output, pullResult, "pull")
                }
            }

            else -> {
                output.error("Unknown subcommand: $sub")
                output.info("Usage: $usage")
            }
        }
        return true
    }

    override suspend fun execute(args: List<String>) {
        if (syncManager == null) {
            println(ConsoleFormatter.error("Storage module is disabled — set enabled = true in storage.toml to activate"))
            return
        }
        val sub = args.firstOrNull()?.lowercase() ?: "status"
        when (sub) {
            "status" -> {
                val statuses = syncManager.status()
                if (statuses.isEmpty()) {
                    println(ConsoleFormatter.info("No templates found (local or remote)"))
                    return
                }
                println(ConsoleFormatter.header("Template Sync Status"))
                val nameW = (statuses.maxOfOrNull { it.templateName.length } ?: 8).coerceAtLeast(8)
                println("  ${"Template".padEnd(nameW)}  ${"Local".padEnd(10)}  ${"Remote".padEnd(10)}  In Sync")
                println("  ${"─".repeat(nameW)}  ${"─".repeat(10)}  ${"─".repeat(10)}  ${"─".repeat(7)}")
                for (s in statuses) {
                    val local = if (s.localExists) "${GREEN}yes (${s.localFileCount})$RESET" else "${DIM}no$RESET"
                    val remote = if (s.remoteExists) "${GREEN}yes (${s.remoteFileCount})$RESET" else "${DIM}no$RESET"
                    val sync = when {
                        s.inSync -> "${GREEN}✓$RESET"
                        s.localExists && s.remoteExists -> "${YELLOW}≠$RESET"
                        else -> "${DIM}–$RESET"
                    }
                    println("  $BOLD${s.templateName.padEnd(nameW)}$RESET  $local  $remote  $sync")
                }
            }

            "list" -> {
                val remote = syncManager.listRemote()
                val local = syncManager.listLocal()
                println(ConsoleFormatter.header("Templates"))
                if (local.isNotEmpty()) {
                    println("  ${BOLD}Local:$RESET")
                    for (name in local) println("    $CYAN$name$RESET")
                } else {
                    println("  ${DIM}No local templates$RESET")
                }
                if (remote.isNotEmpty()) {
                    println("  ${BOLD}Remote:$RESET")
                    for (name in remote) println("    $CYAN$name$RESET")
                } else {
                    println("  ${DIM}No remote templates$RESET")
                }
            }

            "push" -> {
                val target = args.getOrNull(1)
                if (target == null) {
                    println(ConsoleFormatter.error("Usage: storage push <name>|--all"))
                    return
                }
                if (target == "--all") {
                    val local = syncManager.listLocal()
                    if (local.isEmpty()) { println(ConsoleFormatter.info("No local templates to push")); return }
                    println(ConsoleFormatter.header("Pushing ${local.size} template(s)"))
                    for (name in local) {
                        val result = syncManager.push(name) { msg -> println("    $DIM$msg$RESET") }
                        printSyncResultConsole(result, "push")
                    }
                } else {
                    println(ConsoleFormatter.header("Pushing $target"))
                    val result = syncManager.push(target) { msg -> println("    $DIM$msg$RESET") }
                    printSyncResultConsole(result, "push")
                }
            }

            "pull" -> {
                val target = args.getOrNull(1)
                if (target == null) {
                    println(ConsoleFormatter.error("Usage: storage pull <name>|--all"))
                    return
                }
                if (target == "--all") {
                    val remote = syncManager.listRemote()
                    if (remote.isEmpty()) { println(ConsoleFormatter.info("No remote templates to pull")); return }
                    println(ConsoleFormatter.header("Pulling ${remote.size} template(s)"))
                    for (name in remote) {
                        val result = syncManager.pull(name) { msg -> println("    $DIM$msg$RESET") }
                        printSyncResultConsole(result, "pull")
                    }
                } else {
                    println(ConsoleFormatter.header("Pulling $target"))
                    val result = syncManager.pull(target) { msg -> println("    $DIM$msg$RESET") }
                    printSyncResultConsole(result, "pull")
                }
            }

            "sync" -> {
                val target = args.getOrNull(1)
                if (target == null) {
                    println(ConsoleFormatter.error("Usage: storage sync <name>|--all"))
                    return
                }
                if (target == "--all") {
                    val local = syncManager.listLocal()
                    if (local.isEmpty()) { println(ConsoleFormatter.info("No local templates to sync")); return }
                    println(ConsoleFormatter.header("Syncing ${local.size} template(s)"))
                    for (name in local) {
                        println("  ${BOLD}$name$RESET: pushing…")
                        val pushResult = syncManager.push(name) { msg -> println("    $DIM$msg$RESET") }
                        printSyncResultConsole(pushResult, "push")
                        println("  ${BOLD}$name$RESET: pulling…")
                        val pullResult = syncManager.pull(name) { msg -> println("    $DIM$msg$RESET") }
                        printSyncResultConsole(pullResult, "pull")
                    }
                } else {
                    println(ConsoleFormatter.header("Syncing $target"))
                    println("  Pushing…")
                    val pushResult = syncManager.push(target) { msg -> println("    $DIM$msg$RESET") }
                    printSyncResultConsole(pushResult, "push")
                    println("  Pulling…")
                    val pullResult = syncManager.pull(target) { msg -> println("    $DIM$msg$RESET") }
                    printSyncResultConsole(pullResult, "pull")
                }
            }

            else -> {
                println(ConsoleFormatter.error("Unknown subcommand: $sub"))
                println(ConsoleFormatter.info("Usage: $usage"))
            }
        }
    }

    private fun printSyncResult(output: CommandOutput, result: SyncResult, op: String) {
        if (result.success) {
            val counts = buildList {
                if (op == "push" && result.uploaded > 0) add("${result.uploaded} uploaded")
                if (op == "pull" && result.downloaded > 0) add("${result.downloaded} downloaded")
                if (result.skipped > 0) add("${result.skipped} skipped")
            }.joinToString(", ").ifBlank { "nothing to do" }
            output.success("${result.templateName}: $counts")
        } else {
            output.error("${result.templateName}: ${result.errors.size} error(s)")
            for (err in result.errors) output.item("  $RED$err$RESET")
        }
    }

    private fun printSyncResultConsole(result: SyncResult, op: String) {
        if (result.success) {
            val counts = buildList {
                if (op == "push" && result.uploaded > 0) add("${result.uploaded} uploaded")
                if (op == "pull" && result.downloaded > 0) add("${result.downloaded} downloaded")
                if (result.skipped > 0) add("${result.skipped} skipped")
            }.joinToString(", ").ifBlank { "nothing to do" }
            println(ConsoleFormatter.success("${result.templateName}: $counts"))
        } else {
            println(ConsoleFormatter.error("${result.templateName}: ${result.errors.size} error(s)"))
            for (err in result.errors) println("    $RED$err$RESET")
        }
    }
}
