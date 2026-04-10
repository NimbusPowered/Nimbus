package dev.nimbuspowered.nimbus.service

import dev.nimbuspowered.nimbus.cluster.NodeConnection
import dev.nimbuspowered.nimbus.group.GroupManager

/**
 * Single source of truth for per-service memory stats. Used by REST routes
 * and console commands so every surface shows identical numbers.
 *
 *  - [usedMb] = resident set size (RSS) from `/proc/<pid>/status` on Linux or
 *    `tasklist` on Windows, via [ProcessMemoryReader]. Includes heap, native,
 *    metaspace, thread stacks, and code cache — the full process footprint.
 *  - [maxMb] = configured `-Xmx` from TOML PLUS a realistic JVM overhead
 *    budget (30% of heap, minimum 256 MB), so it reflects the whole process
 *    budget rather than just the heap. Without this, RSS would always exceed
 *    the displayed max since JVM processes always use more than -Xmx.
 *  - [xmxMb] = the raw configured -Xmx value, for displays that want to show
 *    the heap budget explicitly.
 */
data class ResolvedMemory(val usedMb: Long, val maxMb: Long, val xmxMb: Long)

object ServiceMemoryResolver {

    /** JVM process overhead above the configured heap, as a fraction of -Xmx. */
    private const val OVERHEAD_FRACTION = 0.30

    /** Minimum JVM process overhead in MB (regardless of heap size). */
    private const val MIN_OVERHEAD_MB = 256L

    fun resolve(
        service: Service,
        groupManager: GroupManager,
        dedicatedServiceManager: DedicatedServiceManager?
    ): ResolvedMemory {
        val used = if (service.nodeId == "local") {
            // Local service — read /proc directly and refresh the cache
            val rss = service.pid?.let { ProcessMemoryReader.readRssMb(it) } ?: 0L
            if (rss > 0) service.memoryUsedMb = rss
            rss
        } else {
            // Remote service — use value pushed by the agent heartbeat
            service.memoryUsedMb
        }
        val configured = if (service.isDedicated) {
            dedicatedServiceManager?.getConfig(service.name)?.dedicated?.memory
        } else {
            groupManager.getGroup(service.groupName)?.config?.group?.resources?.memory
        }
        val xmx = configured?.let { NodeConnection.parseMemoryMb(it) } ?: 0L
        val max = if (xmx > 0) xmx + maxOf(MIN_OVERHEAD_MB, (xmx * OVERHEAD_FRACTION).toLong()) else 0L
        return ResolvedMemory(usedMb = used, maxMb = max, xmxMb = xmx)
    }
}
