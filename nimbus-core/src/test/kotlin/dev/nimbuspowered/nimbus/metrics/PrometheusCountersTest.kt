package dev.nimbuspowered.nimbus.metrics

import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.event.NimbusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PrometheusCountersTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterEach
    fun teardown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /** Wait for subscribers to actually attach before emitting. */
    private suspend fun settleSubscribers() = delay(100)

    /** Wait for event delivery to run through. */
    private suspend fun quiesce() = delay(100)

    @Test
    fun `crashes are counted per group inferred from service name`() = runBlocking {
        val bus = EventBus(scope)
        val counters = PrometheusCounters(bus, scope)
        settleSubscribers()
        bus.emit(NimbusEvent.ServiceCrashed("Lobby-1", 1, 0))
        bus.emit(NimbusEvent.ServiceCrashed("Lobby-2", 1, 0))
        bus.emit(NimbusEvent.ServiceCrashed("BedWars-3", 1, 0))
        quiesce()
        val snap = counters.snapshot()
        assertEquals(2L, snap.crashesByGroup["Lobby"])
        assertEquals(1L, snap.crashesByGroup["BedWars"])
    }

    @Test
    fun `dedicated service names with no numeric suffix are used as-is`() = runBlocking {
        val bus = EventBus(scope)
        val counters = PrometheusCounters(bus, scope)
        settleSubscribers()
        bus.emit(NimbusEvent.ServiceCrashed("survival-vanilla", 1, 0))
        quiesce()
        val snap = counters.snapshot()
        assertEquals(1L, snap.crashesByGroup["survival-vanilla"])
    }

    @Test
    fun `scale events track group plus direction`() = runBlocking {
        val bus = EventBus(scope)
        val counters = PrometheusCounters(bus, scope)
        settleSubscribers()
        bus.emit(NimbusEvent.ScaleUp("Lobby", 1, 3, "load"))
        bus.emit(NimbusEvent.ScaleUp("Lobby", 3, 5, "load"))
        bus.emit(NimbusEvent.ScaleDown("Lobby", "Lobby-2", "idle"))
        quiesce()
        val snap = counters.snapshot()
        assertEquals(2L, snap.scaleEvents[PrometheusCounters.Key("Lobby", "up")])
        assertEquals(1L, snap.scaleEvents[PrometheusCounters.Key("Lobby", "down")])
    }

    @Test
    fun `placement blocked events are tallied per group`() = runBlocking {
        val bus = EventBus(scope)
        val counters = PrometheusCounters(bus, scope)
        settleSubscribers()
        bus.emit(NimbusEvent.PlacementBlocked("Lobby", "node offline"))
        bus.emit(NimbusEvent.PlacementBlocked("Lobby", "node offline"))
        quiesce()
        val snap = counters.snapshot()
        assertEquals(2L, snap.placementBlockedByGroup["Lobby"])
    }
}
