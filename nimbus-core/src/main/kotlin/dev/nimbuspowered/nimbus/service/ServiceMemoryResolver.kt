package dev.nimbuspowered.nimbus.service

import dev.nimbuspowered.nimbus.cluster.NodeConnection
import dev.nimbuspowered.nimbus.group.GroupManager

/**
 * Single source of truth for per-service memory stats. Used by REST routes
 * and console commands so every surface shows identical numbers.
 *
 *  - `usedMb` = resident set size from `/proc/<pid>/status` via [ProcessMemoryReader]
 *    (Linux only; returns 0 elsewhere).
 *  - `maxMb` = configured `-Xmx` parsed from the group / dedicated TOML config.
 */
data class ResolvedMemory(val usedMb: Long, val maxMb: Long)

object ServiceMemoryResolver {

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
        val max = configured?.let { NodeConnection.parseMemoryMb(it) } ?: 0L
        return ResolvedMemory(used, max)
    }
}
