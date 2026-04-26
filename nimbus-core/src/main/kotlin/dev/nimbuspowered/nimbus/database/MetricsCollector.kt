package dev.nimbuspowered.nimbus.database

import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.event.NimbusEvent
import dev.nimbuspowered.nimbus.group.GroupManager
import dev.nimbuspowered.nimbus.service.DedicatedServiceManager
import dev.nimbuspowered.nimbus.service.ServiceMemoryResolver
import dev.nimbuspowered.nimbus.service.ServiceRegistry
import dev.nimbuspowered.nimbus.service.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class MetricsCollector(
    private val db: DatabaseManager,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val retentionDays: Long = 30L
) {
    private val logger = LoggerFactory.getLogger(MetricsCollector::class.java)

    /** Flush interval for batched writes (3 seconds). */
    private val flushIntervalMs = 3000L

    // Queues for batched writes
    private val maxQueueSize = 10_000
    private val serviceEventQueue = ConcurrentLinkedQueue<ServiceEventEntry>()
    private val scalingEventQueue = ConcurrentLinkedQueue<ScalingEventEntry>()
    private var flushJob: Job? = null

    // Startup time tracking: service name → instant when STARTING was observed
    private val startingTimestamps = ConcurrentHashMap<String, Instant>()

    // Ring buffer of recent (groupName?, elapsedSeconds) startup samples — capped at 200
    private val startupTimeSamples: MutableList<Pair<String?, Long>> = Collections.synchronizedList(mutableListOf())

    private data class ServiceEventEntry(
        val timestamp: String, val eventType: String, val serviceName: String,
        val groupName: String? = null, val port: Int? = null,
        val exitCode: Int? = null, val restartAttempt: Int? = null
    )

    private data class ScalingEventEntry(
        val timestamp: String, val eventType: String, val groupName: String,
        val serviceName: String? = null, val currentInstances: Int? = null,
        val targetInstances: Int? = null, val reason: String
    )

    private fun <T> enqueueIfNotFull(queue: ConcurrentLinkedQueue<T>, entry: T, queueName: String) {
        if (queue.size >= maxQueueSize) {
            logger.warn("Metrics {} queue full ({} entries), dropping event", queueName, maxQueueSize)
            return
        }
        queue.add(entry)
    }

    fun start(): List<Job> {
        val jobs = mutableListOf<Job>()

        // Service lifecycle events — enqueue instead of direct insert
        jobs += eventBus.on<NimbusEvent.ServiceStarting> { event ->
            startingTimestamps[event.serviceName] = Instant.now()
            enqueueIfNotFull(serviceEventQueue, ServiceEventEntry(
                timestamp = event.timestamp.toString(), eventType = "STARTING",
                serviceName = event.serviceName, groupName = event.groupName, port = event.port
            ), "service")
        }

        jobs += eventBus.on<NimbusEvent.ServiceReady> { event ->
            val startedAt = startingTimestamps.remove(event.serviceName)
            if (startedAt != null) {
                val elapsedSeconds = Duration.between(startedAt, Instant.now()).seconds
                synchronized(startupTimeSamples) {
                    startupTimeSamples.add(event.groupName to elapsedSeconds)
                    if (startupTimeSamples.size > 200) startupTimeSamples.removeAt(0)
                }
            }
            enqueueIfNotFull(serviceEventQueue, ServiceEventEntry(
                timestamp = event.timestamp.toString(), eventType = "READY",
                serviceName = event.serviceName, groupName = event.groupName
            ), "service")
        }

        jobs += eventBus.on<NimbusEvent.ServiceDraining> { event ->
            enqueueIfNotFull(serviceEventQueue, ServiceEventEntry(
                timestamp = event.timestamp.toString(), eventType = "DRAINING",
                serviceName = event.serviceName, groupName = event.groupName
            ), "service")
        }

        jobs += eventBus.on<NimbusEvent.ServiceStopping> { event ->
            enqueueIfNotFull(serviceEventQueue, ServiceEventEntry(
                timestamp = event.timestamp.toString(), eventType = "STOPPING",
                serviceName = event.serviceName
            ), "service")
        }

        jobs += eventBus.on<NimbusEvent.ServiceStopped> { event ->
            enqueueIfNotFull(serviceEventQueue, ServiceEventEntry(
                timestamp = event.timestamp.toString(), eventType = "STOPPED",
                serviceName = event.serviceName
            ), "service")
        }

        jobs += eventBus.on<NimbusEvent.ServiceCrashed> { event ->
            enqueueIfNotFull(serviceEventQueue, ServiceEventEntry(
                timestamp = event.timestamp.toString(), eventType = "CRASHED",
                serviceName = event.serviceName, exitCode = event.exitCode,
                restartAttempt = event.restartAttempt
            ), "service")
        }

        // Scaling events
        jobs += eventBus.on<NimbusEvent.ScaleUp> { event ->
            enqueueIfNotFull(scalingEventQueue, ScalingEventEntry(
                timestamp = event.timestamp.toString(), eventType = "SCALE_UP",
                groupName = event.groupName, currentInstances = event.currentInstances,
                targetInstances = event.targetInstances, reason = event.reason
            ), "scaling")
        }

        jobs += eventBus.on<NimbusEvent.ScaleDown> { event ->
            enqueueIfNotFull(scalingEventQueue, ScalingEventEntry(
                timestamp = event.timestamp.toString(), eventType = "SCALE_DOWN",
                groupName = event.groupName, serviceName = event.serviceName,
                reason = event.reason
            ), "scaling")
        }

        // Start periodic flush loop
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
        jobs += flushJob!!

        logger.info("Metrics collector started ({} event subscriptions, batched flush every {}s)",
            jobs.size - 1, flushIntervalMs / 1000)
        return jobs
    }

    private suspend fun flush() {
        val serviceEvents = drainQueue(serviceEventQueue)
        val scalingEvents = drainQueue(scalingEventQueue)
        if (serviceEvents.isEmpty() && scalingEvents.isEmpty()) return

        try {
            db.query {
                if (serviceEvents.isNotEmpty()) {
                    ServiceEvents.batchInsert(serviceEvents) { entry ->
                        this[ServiceEvents.timestamp] = entry.timestamp
                        this[ServiceEvents.eventType] = entry.eventType
                        this[ServiceEvents.serviceName] = entry.serviceName
                        this[ServiceEvents.groupName] = entry.groupName
                        this[ServiceEvents.port] = entry.port
                        this[ServiceEvents.exitCode] = entry.exitCode
                        this[ServiceEvents.restartAttempt] = entry.restartAttempt
                    }
                }

                if (scalingEvents.isNotEmpty()) {
                    ScalingEvents.batchInsert(scalingEvents) { entry ->
                        this[ScalingEvents.timestamp] = entry.timestamp
                        this[ScalingEvents.eventType] = entry.eventType
                        this[ScalingEvents.groupName] = entry.groupName
                        this[ScalingEvents.serviceName] = entry.serviceName
                        this[ScalingEvents.currentInstances] = entry.currentInstances
                        this[ScalingEvents.targetInstances] = entry.targetInstances
                        this[ScalingEvents.reason] = entry.reason
                    }
                }

            }
        } catch (e: Exception) {
            val total = serviceEvents.size + scalingEvents.size
            logger.warn("Failed to flush metrics batch ({} entries): {}", total, e.message)
        }
    }

    /**
     * Average startup seconds from the in-memory sample deque.
     * If [groupName] is non-null, only samples for that group are considered.
     */
    fun getAverageStartupSeconds(groupName: String? = null): Double {
        val samples = synchronized(startupTimeSamples) { startupTimeSamples.toList() }
        val filtered = if (groupName != null) samples.filter { it.first == groupName } else samples
        return if (filtered.isEmpty()) 0.0 else filtered.sumOf { it.second }.toDouble() / filtered.size
    }

    /**
     * Count of CRASHED events for each group in the last [hours] hours.
     * Returns a map of groupName (or "" for ungrouped) → crash count.
     */
    suspend fun getCrashCountsByGroup(hours: Int = 24): Map<String, Int> {
        val since = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS).toString()
        return db.query {
            ServiceEvents.selectAll()
                .where { (ServiceEvents.eventType eq "CRASHED") and (ServiceEvents.timestamp greaterEq since) }
                .groupBy { it[ServiceEvents.groupName] ?: "" }
                .mapValues { (_, rows) -> rows.size }
        }
    }

    data class NetworkPlayerSample(
        val timestamp: Instant,
        val totalPlayers: Int,
        val byGroup: Map<String, Int>,
    )

    /**
     * Queries [ServiceMetricSamples] for the last [minutes] minutes, buckets rows by
     * truncated minute, and sums playerCount per bucket across all services.
     */
    suspend fun getNetworkPlayerHistory(minutes: Int = 60): List<NetworkPlayerSample> {
        val since = Instant.now().minus(minutes.toLong(), ChronoUnit.MINUTES).toString()
        val rows = db.query {
            ServiceMetricSamples.selectAll()
                .where { ServiceMetricSamples.timestamp greaterEq since }
                .orderBy(ServiceMetricSamples.timestamp, org.jetbrains.exposed.sql.SortOrder.ASC)
                .limit(10_000)
                .map { row ->
                    Triple(
                        row[ServiceMetricSamples.timestamp],
                        row[ServiceMetricSamples.groupName] ?: "",
                        row[ServiceMetricSamples.playerCount],
                    )
                }
        }
        // Bucket by truncated-to-minute timestamp prefix (first 16 chars = "YYYY-MM-DDTHH:MM")
        val buckets = linkedMapOf<String, MutableMap<String, Int>>()
        for ((ts, group, players) in rows) {
            val bucket = ts.take(16) // "YYYY-MM-DDTHH:MM"
            buckets.getOrPut(bucket) { mutableMapOf() }.merge(group, players, Int::plus)
        }
        return buckets.map { (bucket, byGroup) ->
            NetworkPlayerSample(
                timestamp = Instant.parse("${bucket}:00Z"),
                totalPlayers = byGroup.values.sum(),
                byGroup = byGroup,
            )
        }
    }

    data class GroupSampleStats(
        val averageMemoryPercent: Double,
        val averageTps: Double,
        val playerCount: Int,
    )

    /**
     * Aggregates the last hour of [ServiceMetricSamples] rows per group.
     * Returns averageMemoryPercent and live playerCount per groupName.
     */
    suspend fun getGroupSampleStats(): Map<String, GroupSampleStats> {
        val since = Instant.now().minus(1L, ChronoUnit.HOURS).toString()
        val rows = db.query {
            ServiceMetricSamples.selectAll()
                .where { ServiceMetricSamples.timestamp greaterEq since }
                .map { row ->
                    val used = row[ServiceMetricSamples.memoryUsedMb]
                    val max = row[ServiceMetricSamples.memoryMaxMb]
                    val pct = if (max > 0) used.toDouble() / max * 100.0 else 0.0
                    Triple(
                        row[ServiceMetricSamples.groupName] ?: "",
                        pct,
                        row[ServiceMetricSamples.playerCount],
                    )
                }
        }
        return rows.groupBy { it.first }.mapValues { (_, groupRows) ->
            GroupSampleStats(
                averageMemoryPercent = groupRows.map { it.second }.average(),
                averageTps = 20.0, // TPS not stored in samples — callers fill from live registry
                playerCount = groupRows.lastOrNull()?.third ?: 0,
            )
        }
    }

    /** Flush remaining events on shutdown. */
    suspend fun shutdown() {
        flushJob?.cancel()
        flush()
        logger.info("Metrics collector shut down (final flush complete)")
    }

    // ── Periodic memory sampling ──────────────────────────────
    //
    // Writes a row per running service every [samplingIntervalMs] into
    // [ServiceMetricSamples]. The dashboard queries this table to render
    // "memory over the last hour" charts so users don't start from blank
    // every time a service detail page is opened.

    private val samplingIntervalMs = 30_000L

    fun startMemorySampling(
        registry: ServiceRegistry,
        groupManager: GroupManager,
        dedicatedServiceManager: DedicatedServiceManager?,
    ): Job = scope.launch {
        while (isActive) {
            delay(samplingIntervalMs)
            val running = registry.getAll().filter {
                it.state == ServiceState.READY || it.state == ServiceState.STARTING
            }
            if (running.isEmpty()) continue
            val ts = Instant.now().toString()
            val rows = running.map { svc ->
                val mem = ServiceMemoryResolver.resolve(svc, groupManager, dedicatedServiceManager)
                SampleEntry(
                    timestamp = ts,
                    serviceName = svc.name,
                    groupName = svc.groupName,
                    memoryUsedMb = mem.usedMb.toInt(),
                    memoryMaxMb = mem.maxMb.toInt(),
                    playerCount = svc.playerCount,
                )
            }
            try {
                db.query {
                    ServiceMetricSamples.batchInsert(rows) { entry ->
                        this[ServiceMetricSamples.timestamp] = entry.timestamp
                        this[ServiceMetricSamples.serviceName] = entry.serviceName
                        this[ServiceMetricSamples.groupName] = entry.groupName
                        this[ServiceMetricSamples.memoryUsedMb] = entry.memoryUsedMb
                        this[ServiceMetricSamples.memoryMaxMb] = entry.memoryMaxMb
                        this[ServiceMetricSamples.playerCount] = entry.playerCount
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to write {} service metric samples: {}", rows.size, e.message)
            }
        }
    }

    private data class SampleEntry(
        val timestamp: String,
        val serviceName: String,
        val groupName: String?,
        val memoryUsedMb: Int,
        val memoryMaxMb: Int,
        val playerCount: Int,
    )

    private fun <T> drainQueue(queue: ConcurrentLinkedQueue<T>): List<T> {
        val list = mutableListOf<T>()
        while (true) {
            list.add(queue.poll() ?: break)
        }
        return list
    }

    /**
     * Starts a periodic job that prunes metrics older than [retentionDays].
     * Should be called once during bootstrap with the application's CoroutineScope.
     */
    fun startRetentionCleanup(scope: CoroutineScope): Job = scope.launch {
        // Run daily
        while (isActive) {
            delay(24 * 60 * 60 * 1000L) // 24 hours
            pruneOldMetrics()
        }
    }

    private val pruneBatchSize = 5000

    private suspend fun pruneOldMetrics() {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toString()
        try {
            val deletedServices = pruneTableInBatches(ServiceEvents, ServiceEvents.id, ServiceEvents.timestamp, cutoff)
            val deletedScaling = pruneTableInBatches(ScalingEvents, ScalingEvents.id, ScalingEvents.timestamp, cutoff)
            val deletedSamples = pruneTableInBatches(ServiceMetricSamples, ServiceMetricSamples.id, ServiceMetricSamples.timestamp, cutoff)
            if (deletedServices + deletedScaling + deletedSamples > 0) {
                logger.info(
                    "Metrics retention cleanup: pruned {} service events, {} scaling events, {} metric samples older than {} days",
                    deletedServices, deletedScaling, deletedSamples, retentionDays
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to prune old metrics: {}", e.message)
        }
    }

    private suspend fun pruneTableInBatches(
        table: org.jetbrains.exposed.sql.Table,
        idColumn: org.jetbrains.exposed.sql.Column<Long>,
        timestampColumn: org.jetbrains.exposed.sql.Column<String>,
        cutoff: String
    ): Int {
        var totalDeleted = 0
        while (true) {
            val deleted = db.query {
                val ids = table.selectAll()
                    .where { timestampColumn less cutoff }
                    .limit(pruneBatchSize)
                    .map { it[idColumn] }
                if (ids.isEmpty()) return@query 0
                table.deleteWhere { idColumn inList ids }
            }
            totalDeleted += deleted
            if (deleted < pruneBatchSize) break
            delay(100) // yield to other writers between batches
        }
        return totalDeleted
    }
}
