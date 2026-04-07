package dev.nimbuspowered.nimbus.database

import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.event.NimbusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Tracks Remote CLI sessions in the database.
 * Subscribes to [NimbusEvent.CliSessionConnected] and [NimbusEvent.CliSessionDisconnected].
 */
class CliSessionTracker(
    private val db: DatabaseManager,
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(CliSessionTracker::class.java)

    fun start(): List<Job> {
        val jobs = mutableListOf<Job>()

        jobs += eventBus.on<NimbusEvent.CliSessionConnected> { event ->
            try {
                db.query {
                    CliSessions.insert {
                        it[sessionId] = event.sessionId
                        it[remoteIp] = event.remoteIp
                        it[authenticatedAs] = "api-token"
                        it[clientUsername] = event.clientUsername
                        it[clientHostname] = event.clientHostname
                        it[clientOs] = event.clientOs
                        it[location] = event.location
                        it[connectedAt] = event.timestamp.toString()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to record CLI session connect: {}", e.message)
            }
        }

        jobs += eventBus.on<NimbusEvent.CliSessionDisconnected> { event ->
            try {
                db.query {
                    CliSessions.update({ CliSessions.sessionId eq event.sessionId }) {
                        it[disconnectedAt] = Instant.now().toString()
                        it[durationSeconds] = event.durationSeconds
                        it[commandCount] = event.commandCount
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to record CLI session disconnect: {}", e.message)
            }
        }

        logger.debug("CLI session tracking started")
        return jobs
    }

    data class SessionEntry(
        val sessionId: Int,
        val remoteIp: String,
        val clientUsername: String,
        val clientHostname: String,
        val clientOs: String,
        val location: String,
        val connectedAt: String,
        val disconnectedAt: String?,
        val durationSeconds: Long?,
        val commandCount: Int
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toSessionEntry() = SessionEntry(
        sessionId = this[CliSessions.sessionId],
        remoteIp = this[CliSessions.remoteIp],
        clientUsername = this[CliSessions.clientUsername],
        clientHostname = this[CliSessions.clientHostname],
        clientOs = this[CliSessions.clientOs],
        location = this[CliSessions.location],
        connectedAt = this[CliSessions.connectedAt],
        disconnectedAt = this[CliSessions.disconnectedAt],
        durationSeconds = this[CliSessions.durationSeconds],
        commandCount = this[CliSessions.commandCount]
    )

    suspend fun getRecentSessions(limit: Int = 20): List<SessionEntry> {
        return db.query {
            CliSessions.selectAll()
                .orderBy(CliSessions.connectedAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toSessionEntry() }
        }
    }

    suspend fun getActiveSessions(): List<SessionEntry> {
        return db.query {
            CliSessions.selectAll()
                .where { CliSessions.disconnectedAt.isNull() }
                .orderBy(CliSessions.connectedAt, SortOrder.DESC)
                .map { it.toSessionEntry() }
        }
    }
}
