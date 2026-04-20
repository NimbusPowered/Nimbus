package dev.nimbuspowered.nimbus.metrics

import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.event.NimbusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-memory counter registry fed by the [EventBus]. Exposed to
 * the Prometheus scrape endpoint via [snapshot]. Values reset on controller
 * restart — that's intentional. Prometheus itself handles counter resets via
 * `rate()`/`increase()`, and persisting these to disk would just duplicate
 * what [MetricsCollector] already stores in the DB for historical charts.
 *
 * Subscribes to:
 *   - [NimbusEvent.ServiceCrashed]   → nimbus_service_crashes_total{group}
 *   - [NimbusEvent.ScaleUp]          → nimbus_scaling_events_total{group, direction="up"}
 *   - [NimbusEvent.ScaleDown]        → nimbus_scaling_events_total{group, direction="down"}
 *   - [NimbusEvent.PlacementBlocked] → nimbus_placement_blocked_total{group}
 */
class PrometheusCounters(
    eventBus: EventBus,
    scope: CoroutineScope
) {
    private val crashesByGroup = ConcurrentHashMap<String, AtomicLong>()
    private val scaleEvents = ConcurrentHashMap<Key, AtomicLong>()
    private val placementBlockedByGroup = ConcurrentHashMap<String, AtomicLong>()

    init {
        scope.launch {
            eventBus.on<NimbusEvent.ServiceCrashed> { ev ->
                // ServiceCrashed carries the service name; the group is implied via the
                // service registry. We store under the best label we can derive cheaply
                // — the service name is a safe fallback when the group isn't on the event.
                val key = inferGroupFromServiceName(ev.serviceName)
                crashesByGroup.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
            }
        }
        scope.launch {
            eventBus.on<NimbusEvent.ScaleUp> { ev ->
                scaleEvents.computeIfAbsent(Key(ev.groupName, "up")) { AtomicLong() }.incrementAndGet()
            }
        }
        scope.launch {
            eventBus.on<NimbusEvent.ScaleDown> { ev ->
                scaleEvents.computeIfAbsent(Key(ev.groupName, "down")) { AtomicLong() }.incrementAndGet()
            }
        }
        scope.launch {
            eventBus.on<NimbusEvent.PlacementBlocked> { ev ->
                placementBlockedByGroup.computeIfAbsent(ev.groupName) { AtomicLong() }.incrementAndGet()
            }
        }
    }

    data class Key(val group: String, val direction: String)

    data class Snapshot(
        val crashesByGroup: Map<String, Long>,
        val scaleEvents: Map<Key, Long>,
        val placementBlockedByGroup: Map<String, Long>
    )

    fun snapshot(): Snapshot = Snapshot(
        crashesByGroup = crashesByGroup.mapValues { it.value.get() },
        scaleEvents = scaleEvents.mapValues { it.value.get() },
        placementBlockedByGroup = placementBlockedByGroup.mapValues { it.value.get() }
    )

    /**
     * Services are named `<Group>-<N>`. Extract the prefix as a best-effort
     * group label. Dedicated services (no "-N" suffix) fall under their own
     * name, which is still a useful label.
     */
    private fun inferGroupFromServiceName(serviceName: String): String {
        val idx = serviceName.lastIndexOf('-')
        if (idx <= 0) return serviceName
        val suffix = serviceName.substring(idx + 1)
        return if (suffix.toIntOrNull() != null) serviceName.substring(0, idx) else serviceName
    }
}
