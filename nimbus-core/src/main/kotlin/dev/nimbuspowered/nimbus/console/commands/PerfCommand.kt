package dev.nimbuspowered.nimbus.console.commands

import dev.nimbuspowered.nimbus.console.Command
import dev.nimbuspowered.nimbus.console.ConsoleFormatter
import dev.nimbuspowered.nimbus.console.ConsoleOutput
import dev.nimbuspowered.nimbus.database.MetricsCollector
import dev.nimbuspowered.nimbus.group.GroupManager
import dev.nimbuspowered.nimbus.module.api.CommandOutput
import dev.nimbuspowered.nimbus.service.DedicatedServiceManager
import dev.nimbuspowered.nimbus.service.ServiceMemoryResolver
import dev.nimbuspowered.nimbus.service.ServiceRegistry
import dev.nimbuspowered.nimbus.service.ServiceState

class PerfCommand(
    private val metricsCollector: MetricsCollector,
    private val registry: ServiceRegistry,
    private val groupManager: GroupManager,
    private val dedicatedServiceManager: DedicatedServiceManager? = null
) : Command {

    override val name = "perf"
    override val description = "Show performance metrics summary"
    override val usage = "perf"
    override val permission = "nimbus.cloud.status"

    override suspend fun execute(args: List<String>, output: CommandOutput): Boolean {
        val allServices = registry.getAll()
        val groups = groupManager.getAllGroups()

        val crashes24h = metricsCollector.getCrashCountsByGroup(24).values.sum()
        val crashes7d = metricsCollector.getCrashCountsByGroup(168).values.sum()
        val crashesByGroup24h = metricsCollector.getCrashCountsByGroup(24)
        val avgStartup = metricsCollector.getAverageStartupSeconds()

        val readyCount = allServices.count { it.state == ServiceState.READY }
        val totalPlayers = allServices.sumOf { it.playerCount }

        val memPerService = allServices.associateWith {
            ServiceMemoryResolver.resolve(it, groupManager, dedicatedServiceManager)
        }
        val totalMemUsedMb = memPerService.values.sumOf { it.usedMb }
        val totalMemMaxMb = memPerService.values.sumOf { it.maxMb }
        val memUsedGb = totalMemUsedMb / 1024.0
        val memMaxGb = totalMemMaxMb / 1024.0
        val memPct = if (totalMemMaxMb > 0) (totalMemUsedMb * 100 / totalMemMaxMb).toInt() else 0

        output.header("Performance Summary (last 24h)")
        output.text("")

        output.text(
            "  ${ConsoleFormatter.hint("Network:")}  " +
            "${ConsoleFormatter.colorize("$totalPlayers", ConsoleFormatter.BOLD)} players   |   " +
            "${ConsoleFormatter.colorize("${allServices.size}", ConsoleFormatter.BOLD)} services " +
            "(${ConsoleFormatter.success("$readyCount ready")})   |   " +
            "${ConsoleFormatter.hint("Uptime:")} ${ConsoleFormatter.success("online")}"
        )

        val memLabel = "%.1f GB / %.1f GB (%d%%)".format(memUsedGb, memMaxGb, memPct)
        output.text("  ${ConsoleFormatter.hint("Memory:")}   $memLabel")

        val crashInfo = (
            "${ConsoleFormatter.colorize("$crashes24h", if (crashes24h > 0) ConsoleFormatter.RED else ConsoleFormatter.GREEN)} (24h)  /  " +
            "${ConsoleFormatter.colorize("$crashes7d", if (crashes7d > 0) ConsoleFormatter.YELLOW else ConsoleFormatter.GREEN)} (7d)"
        )
        output.text("  ${ConsoleFormatter.hint("Crashes:")}  $crashInfo")

        val startupLabel = if (avgStartup > 0.0) "avg ${"%.1f".format(avgStartup)}s" else ConsoleFormatter.placeholder()
        output.text("  ${ConsoleFormatter.hint("Startup:")}  $startupLabel")

        if (groups.isNotEmpty()) {
            output.text("")
            output.text("  ${ConsoleFormatter.section("Group Performance")}")

            val headers = listOf("Group", "Services", "Players", "Memory", "TPS", "Crashes")
            val rows = groups.sortedBy { it.name }.map { group ->
                val services = registry.getByGroup(group.name)
                val ready = services.count { it.state == ServiceState.READY }
                val players = services.sumOf { it.playerCount }
                val maxPlayers = group.config.group.resources.maxPlayers * services.size.coerceAtLeast(1)

                val groupUsedMb = services.sumOf { memPerService[it]?.usedMb ?: 0L }
                val groupMaxMb = services.sumOf { memPerService[it]?.maxMb ?: 0L }
                val groupMemPct = if (groupMaxMb > 0) (groupUsedMb * 100 / groupMaxMb).toInt() else 0

                val readyServices = services.filter { it.state == ServiceState.READY }
                val tps = if (readyServices.isNotEmpty()) readyServices.sumOf { it.tps } / readyServices.size else null
                val tpsLabel = if (tps != null) "%.1f".format(tps) else ConsoleFormatter.placeholder()

                val groupCrashes = crashesByGroup24h[group.name] ?: 0

                listOf(
                    ConsoleFormatter.colorize(group.name, ConsoleFormatter.BOLD),
                    "$ready/${services.size}",
                    "$players/$maxPlayers",
                    "$groupMemPct%",
                    tpsLabel,
                    if (groupCrashes > 0) ConsoleFormatter.error("$groupCrashes") else ConsoleFormatter.success("0")
                )
            }

            output.text(ConsoleFormatter.formatTable(headers, rows))
        }

        return true
    }

    override suspend fun execute(args: List<String>) {
        execute(args, ConsoleOutput())
    }
}
