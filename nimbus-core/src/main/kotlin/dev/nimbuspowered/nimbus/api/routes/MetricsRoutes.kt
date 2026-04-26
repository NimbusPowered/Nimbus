package dev.nimbuspowered.nimbus.api.routes

import dev.nimbuspowered.nimbus.api.GroupPerformanceSummaryResponse
import dev.nimbuspowered.nimbus.api.NetworkPlayerHistoryResponse
import dev.nimbuspowered.nimbus.api.NetworkPlayerSampleResponse
import dev.nimbuspowered.nimbus.api.NimbusApi
import dev.nimbuspowered.nimbus.api.PerformanceSummaryResponse
import dev.nimbuspowered.nimbus.api.requirePermission
import dev.nimbuspowered.nimbus.cluster.NodeConnection
import dev.nimbuspowered.nimbus.cluster.NodeManager
import dev.nimbuspowered.nimbus.database.MetricsCollector
import dev.nimbuspowered.nimbus.group.GroupManager
import dev.nimbuspowered.nimbus.loadbalancer.TcpLoadBalancer
import dev.nimbuspowered.nimbus.metrics.PrometheusCounters
import dev.nimbuspowered.nimbus.proxy.ProxySyncManager
import dev.nimbuspowered.nimbus.service.ServiceMemoryResolver
import dev.nimbuspowered.nimbus.service.ServiceRegistry
import dev.nimbuspowered.nimbus.service.ServiceState
import dev.nimbuspowered.nimbus.service.WarmPoolManager
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Duration
import java.time.Instant

/**
 * Prometheus text exposition format endpoint.
 * Public (no auth) — designed for scraping by Prometheus.
 */
fun Route.metricsRoutes(
    registry: ServiceRegistry,
    groupManager: GroupManager,
    nodeManager: NodeManager?,
    loadBalancer: TcpLoadBalancer?,
    proxySyncManager: ProxySyncManager,
    startedAt: Instant,
    stateSyncManager: dev.nimbuspowered.nimbus.service.StateSyncManager? = null,
    warmPoolManager: WarmPoolManager? = null,
    counters: PrometheusCounters? = null,
    metricsCollector: MetricsCollector? = null,
    dedicatedServiceManager: dev.nimbuspowered.nimbus.service.DedicatedServiceManager? = null,
) {
    get("/api/metrics") {
        val sb = StringBuilder()
        val services = registry.getAll()
        val groups = groupManager.getAllGroups()

        // ── Nimbus info ──
        sb.metric("nimbus_info", "gauge", "Nimbus instance information",
            1, "version" to NimbusApi.VERSION)
        sb.metric("nimbus_uptime_seconds", "gauge", "Seconds since Nimbus started",
            Duration.between(startedAt, Instant.now()).seconds)

        // ── Service counts ──
        sb.metric("nimbus_services_total", "gauge", "Total number of services",
            services.size)

        val byState = services.groupBy { it.state.name }
        sb.help("nimbus_services_by_state", "gauge", "Services by state")
        for ((state, list) in byState) {
            sb.value("nimbus_services_by_state", list.size, "state" to state)
        }

        val byGroup = services.groupBy { it.groupName }
        sb.help("nimbus_services_by_group", "gauge", "Services by group")
        for ((group, list) in byGroup) {
            sb.value("nimbus_services_by_group", list.size, "group" to group)
        }

        // ── Player counts ──
        val totalPlayers = services.sumOf { it.playerCount }
        sb.metric("nimbus_players_total", "gauge", "Total players across all services",
            totalPlayers)

        sb.help("nimbus_players_by_group", "gauge", "Players by group")
        for ((group, list) in byGroup) {
            sb.value("nimbus_players_by_group", list.sumOf { it.playerCount }, "group" to group)
        }

        sb.help("nimbus_players_by_service", "gauge", "Players per service")
        for (service in services) {
            sb.value("nimbus_players_by_service", service.playerCount,
                "service" to service.name, "group" to service.groupName)
        }

        // ── Group configuration ──
        sb.help("nimbus_group_min_instances", "gauge", "Minimum instances configured per group")
        sb.help("nimbus_group_max_instances", "gauge", "Maximum instances configured per group")
        sb.help("nimbus_group_max_players", "gauge", "Max players per instance per group")
        for (group in groups) {
            sb.value("nimbus_group_min_instances", group.minInstances, "group" to group.name)
            sb.value("nimbus_group_max_instances", group.maxInstances, "group" to group.name)
            sb.value("nimbus_group_max_players", group.config.group.resources.maxPlayers,
                "group" to group.name)
        }

        // ── Cluster nodes ──
        if (nodeManager != null) {
            val nodes = nodeManager.getAllNodes()
            sb.metric("nimbus_nodes_total", "gauge", "Total registered nodes",
                nodes.size)
            sb.metric("nimbus_nodes_online", "gauge", "Online nodes",
                nodeManager.getOnlineNodeCount())

            sb.help("nimbus_node_cpu_usage", "gauge", "Node CPU usage (0.0–1.0)")
            sb.help("nimbus_node_memory_used_bytes", "gauge", "Node memory used in bytes")
            sb.help("nimbus_node_memory_total_bytes", "gauge", "Node total memory in bytes")
            sb.help("nimbus_node_services", "gauge", "Services running on node")
            for (node in nodes) {
                val labels = arrayOf("node" to node.nodeId)
                sb.value("nimbus_node_cpu_usage", node.cpuUsage, *labels)
                sb.value("nimbus_node_memory_used_bytes", node.memoryUsedMb * 1024 * 1024, *labels)
                sb.value("nimbus_node_memory_total_bytes", node.memoryTotalMb * 1024 * 1024, *labels)
                sb.value("nimbus_node_services", node.currentServices, *labels)
            }
        }

        // ── Load balancer ──
        if (loadBalancer != null) {
            sb.metric("nimbus_loadbalancer_connections_total", "counter",
                "Total connections handled by load balancer", loadBalancer.totalConnections)
            sb.metric("nimbus_loadbalancer_connections_active", "gauge",
                "Active load balancer connections", loadBalancer.activeConnections)
            sb.metric("nimbus_loadbalancer_connections_rejected", "counter",
                "Connections rejected due to limit", loadBalancer.rejectedConnections)
            sb.metric("nimbus_loadbalancer_connections_failed", "counter",
                "Connections failed to reach backend", loadBalancer.failedConnections)

            val backendStates = loadBalancer.healthManager.getAll()
            if (backendStates.isNotEmpty()) {
                sb.help("nimbus_loadbalancer_backend_connections_active", "gauge",
                    "Active connections per backend")
                sb.help("nimbus_loadbalancer_backend_health", "gauge",
                    "Backend health status (1=healthy, 0=unhealthy)")
                for (backend in backendStates) {
                    val service = registry.getAll().firstOrNull { it.host == backend.host && it.port == backend.port }
                    val name = service?.name ?: "${backend.host}:${backend.port}"
                    sb.value("nimbus_loadbalancer_backend_connections_active",
                        backend.activeConnections.get(),
                        "backend" to name, "host" to backend.host, "port" to backend.port.toString())
                    sb.value("nimbus_loadbalancer_backend_health",
                        if (backend.status.get() == dev.nimbuspowered.nimbus.loadbalancer.BackendHealthManager.HealthStatus.HEALTHY) 1 else 0,
                        "backend" to name, "host" to backend.host, "port" to backend.port.toString())
                }
            }
        }

        // ── State sync ──
        if (stateSyncManager != null) {
            sb.help("nimbus_sync_canonical_size_bytes", "gauge",
                "Canonical state size on controller disk per service")
            sb.help("nimbus_sync_last_push_bytes", "gauge",
                "Bytes in the last successful push per service")
            sb.help("nimbus_sync_last_push_files", "gauge",
                "File count in the last successful push per service")
            sb.help("nimbus_sync_last_push_timestamp", "gauge",
                "Unix timestamp of the last successful push per service")
            sb.help("nimbus_sync_in_flight", "gauge",
                "1 if a sync is currently running for this service, else 0")
            // Drive enumeration from StateSyncManager, not the service registry,
            // so a service that crashed or was removed still shows its canonical
            // on disk (important for ops dashboards during failover).
            val serviceByName = services.associateBy { it.name }
            for (name in stateSyncManager.listSyncServices()) {
                val size = stateSyncManager.canonicalSizeBytes(name)
                val stats = stateSyncManager.getStats(name)
                val inFlight = if (stateSyncManager.isSyncInFlight(name)) 1 else 0
                if (size == 0L && stats == null && inFlight == 0) continue
                val groupLabel = serviceByName[name]?.groupName ?: ""
                val labels = arrayOf("service" to name, "group" to groupLabel)
                sb.value("nimbus_sync_canonical_size_bytes", size, *labels)
                sb.value("nimbus_sync_in_flight", inFlight, *labels)
                if (stats != null) {
                    sb.value("nimbus_sync_last_push_bytes", stats.lastPushBytes, *labels)
                    sb.value("nimbus_sync_last_push_files", stats.lastPushFiles, *labels)
                    sb.value("nimbus_sync_last_push_timestamp", stats.lastPushAtEpochMs / 1000, *labels)
                }
            }
        }

        // ── Warm pool ──
        if (warmPoolManager != null) {
            sb.help("nimbus_warmpool_size", "gauge", "Warm pool size per group")
            sb.help("nimbus_warmpool_target", "gauge", "Configured warm pool target per group")
            for (group in groups) {
                val target = group.config.group.scaling.warmPoolSize
                if (target <= 0) continue
                sb.value("nimbus_warmpool_size", warmPoolManager.poolSize(group.name), "group" to group.name)
                sb.value("nimbus_warmpool_target", target, "group" to group.name)
            }
        }

        // ── Event counters (reset on controller restart; use rate() in Prometheus) ──
        if (counters != null) {
            val snap = counters.snapshot()

            if (snap.crashesByGroup.isNotEmpty()) {
                sb.help("nimbus_service_crashes_total", "counter",
                    "Service crashes since controller start, keyed by group")
                for ((group, count) in snap.crashesByGroup) {
                    sb.value("nimbus_service_crashes_total", count, "group" to group)
                }
            }

            if (snap.scaleEvents.isNotEmpty()) {
                sb.help("nimbus_scaling_events_total", "counter",
                    "Scaling events since controller start, by group + direction")
                for ((key, count) in snap.scaleEvents) {
                    sb.value("nimbus_scaling_events_total", count,
                        "group" to key.group, "direction" to key.direction)
                }
            }

            if (snap.placementBlockedByGroup.isNotEmpty()) {
                sb.help("nimbus_placement_blocked_total", "counter",
                    "Scaling starts blocked by placement constraints, per group")
                for ((group, count) in snap.placementBlockedByGroup) {
                    sb.value("nimbus_placement_blocked_total", count, "group" to group)
                }
            }
        }

        // ── Maintenance ──
        sb.metric("nimbus_maintenance_enabled", "gauge",
            "Whether global maintenance mode is enabled (1=on, 0=off)",
            if (proxySyncManager.globalMaintenanceEnabled) 1 else 0)

        call.respondText(sb.toString(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
    }

    // GET /api/metrics/players/history?minutes=60 — network-wide player count over time
    get("/api/metrics/players/history") {
        if (!call.requirePermission("nimbus.dashboard.metrics.view")) return@get
        val collector = metricsCollector
            ?: return@get call.respond(NetworkPlayerHistoryResponse(emptyList()))
        val minutes = (call.queryParameters["minutes"]?.toIntOrNull() ?: 60).coerceIn(5, 24 * 60)
        val rawSamples = collector.getNetworkPlayerHistory(minutes)
        call.respond(NetworkPlayerHistoryResponse(
            samples = rawSamples.map { s ->
                NetworkPlayerSampleResponse(
                    timestamp = s.timestamp.toString(),
                    totalPlayers = s.totalPlayers,
                    byGroup = s.byGroup,
                )
            }
        ))
    }

    // GET /api/metrics/performance — aggregated performance summary
    get("/api/metrics/performance") {
        if (!call.requirePermission("nimbus.dashboard.metrics.view")) return@get
        val services = registry.getAll()
        val groups = groupManager.getAllGroups()
        val collector = metricsCollector

        val crashesByGroup24h: Map<String, Int> = if (collector != null) collector.getCrashCountsByGroup(24) else emptyMap()
        val crashesByGroup7d: Map<String, Int> = if (collector != null) collector.getCrashCountsByGroup(168) else emptyMap()
        val groupSampleStats: Map<String, MetricsCollector.GroupSampleStats> =
            if (collector != null) collector.getGroupSampleStats() else emptyMap()

        val readyServices = services.filter { it.state == ServiceState.READY }
        val networkPlayers = services.sumOf { it.playerCount }

        val memoryData = services.filter { it.state == ServiceState.READY || it.state == ServiceState.STARTING }
            .map { svc ->
                val mem = ServiceMemoryResolver.resolve(svc, groupManager, dedicatedServiceManager)
                mem.usedMb to mem.maxMb
            }
        val totalMemUsed = memoryData.sumOf { it.first }
        val totalMemMax = memoryData.sumOf { it.second }

        val averageStartup = collector?.getAverageStartupSeconds() ?: 0.0
        val crashesLast24h = crashesByGroup24h.values.sum()
        val crashesLast7d = crashesByGroup7d.values.sum()
        val uptimePercent = (100.0 - crashesLast24h * 2.0).coerceAtLeast(80.0)

        val groupSummaries = groups.map { group ->
            val groupServices = services.filter { it.groupName == group.name }
            val groupReady = groupServices.filter { it.state == ServiceState.READY }
            val stats = groupSampleStats[group.name]
            val avgTps = if (groupReady.isNotEmpty()) groupReady.map { it.tps }.average() else 20.0
            GroupPerformanceSummaryResponse(
                groupName = group.name,
                crashesLast24h = crashesByGroup24h[group.name] ?: 0,
                averageStartupSeconds = collector?.getAverageStartupSeconds(group.name) ?: 0.0,
                averageMemoryPercent = stats?.averageMemoryPercent ?: 0.0,
                averageTps = avgTps,
                playerCount = groupServices.sumOf { it.playerCount },
                maxPlayers = group.config.group.resources.maxPlayers * groupReady.size.coerceAtLeast(1),
                serviceCount = groupServices.size,
                readyServiceCount = groupReady.size,
            )
        }

        call.respond(PerformanceSummaryResponse(
            crashesLast24h = crashesLast24h,
            crashesLast7d = crashesLast7d,
            averageStartupSeconds = averageStartup,
            uptimePercent = uptimePercent,
            totalMemoryUsedMb = totalMemUsed,
            totalMemoryMaxMb = totalMemMax,
            networkPlayers = networkPlayers,
            serviceCount = services.size,
            readyServiceCount = readyServices.size,
            groups = groupSummaries,
        ))
    }
}

// ── Prometheus format helpers ──

private fun StringBuilder.metric(name: String, type: String, help: String, value: Number, vararg labels: Pair<String, String>) {
    help(name, type, help)
    value(name, value, *labels)
}

private fun StringBuilder.help(name: String, type: String, help: String) {
    append("# HELP ").append(name).append(' ').append(help).append('\n')
    append("# TYPE ").append(name).append(' ').append(type).append('\n')
}

private fun StringBuilder.value(name: String, value: Number, vararg labels: Pair<String, String>) {
    append(name)
    if (labels.isNotEmpty()) {
        append('{')
        labels.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append(k).append("=\"").append(prometheusEscape(v)).append('"')
        }
        append('}')
    }
    append(' ').append(value).append('\n')
}

private fun prometheusEscape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
