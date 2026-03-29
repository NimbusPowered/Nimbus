package dev.nimbus.database

import dev.nimbus.event.EventBus
import dev.nimbus.event.NimbusEvent
import kotlinx.coroutines.Job
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

class MetricsCollector(
    private val db: DatabaseManager,
    private val eventBus: EventBus
) {
    private val logger = LoggerFactory.getLogger(MetricsCollector::class.java)

    fun start(): List<Job> {
        val jobs = mutableListOf<Job>()

        // Service lifecycle events
        jobs += eventBus.on<NimbusEvent.ServiceStarting> { event ->
            db.query {
                ServiceEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "STARTING"
                    it[serviceName] = event.serviceName
                    it[groupName] = event.groupName
                    it[port] = event.port
                }
            }
        }

        jobs += eventBus.on<NimbusEvent.ServiceReady> { event ->
            db.query {
                ServiceEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "READY"
                    it[serviceName] = event.serviceName
                    it[groupName] = event.groupName
                }
            }
        }

        jobs += eventBus.on<NimbusEvent.ServiceStopping> { event ->
            db.query {
                ServiceEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "STOPPING"
                    it[serviceName] = event.serviceName
                }
            }
        }

        jobs += eventBus.on<NimbusEvent.ServiceStopped> { event ->
            db.query {
                ServiceEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "STOPPED"
                    it[serviceName] = event.serviceName
                }
            }
        }

        jobs += eventBus.on<NimbusEvent.ServiceCrashed> { event ->
            db.query {
                ServiceEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "CRASHED"
                    it[serviceName] = event.serviceName
                    it[exitCode] = event.exitCode
                    it[restartAttempt] = event.restartAttempt
                }
            }
        }

        // Scaling events
        jobs += eventBus.on<NimbusEvent.ScaleUp> { event ->
            db.query {
                ScalingEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "SCALE_UP"
                    it[groupName] = event.groupName
                    it[currentInstances] = event.currentInstances
                    it[targetInstances] = event.targetInstances
                    it[reason] = event.reason
                }
            }
        }

        jobs += eventBus.on<NimbusEvent.ScaleDown> { event ->
            db.query {
                ScalingEvents.insert {
                    it[timestamp] = event.timestamp.toString()
                    it[eventType] = "SCALE_DOWN"
                    it[groupName] = event.groupName
                    it[serviceName] = event.serviceName
                    it[reason] = event.reason
                }
            }
        }

        // Player sessions
        jobs += eventBus.on<NimbusEvent.PlayerConnected> { event ->
            db.query {
                PlayerSessions.insert {
                    it[playerName] = event.playerName
                    it[serviceName] = event.serviceName
                    it[connectedAt] = event.timestamp.toString()
                }
            }
        }

        jobs += eventBus.on<NimbusEvent.PlayerDisconnected> { event ->
            db.query {
                PlayerSessions.update(
                    where = {
                        (PlayerSessions.playerName eq event.playerName) and
                        (PlayerSessions.serviceName eq event.serviceName) and
                        (PlayerSessions.disconnectedAt.isNull())
                    }
                ) {
                    it[disconnectedAt] = event.timestamp.toString()
                }
            }
        }

        logger.info("Metrics collector started ({} event subscriptions)", jobs.size)
        return jobs
    }
}
