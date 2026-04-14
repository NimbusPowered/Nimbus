package dev.nimbuspowered.nimbus.module.punishments

import dev.nimbuspowered.nimbus.database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistence + business logic for punishments.
 *
 * Hot path (login check) is served from an in-memory cache that mirrors
 * active bans keyed by uuid & ip. The cache is invalidated on every mutation
 * and reloaded from DB.
 */
class PunishmentManager(private val db: DatabaseManager) {

    private val logger = LoggerFactory.getLogger(PunishmentManager::class.java)

    // uuid -> most severe active login-blocking punishment
    private val activeBansByUuid = ConcurrentHashMap<String, PunishmentRecord>()
    // ip -> most severe active login-blocking ipban
    private val activeBansByIp = ConcurrentHashMap<String, PunishmentRecord>()
    // uuid -> most recent active mute
    private val activeMutesByUuid = ConcurrentHashMap<String, PunishmentRecord>()

    /** Load active punishments into the in-memory cache. Call after migrations have run. */
    suspend fun init() {
        newSuspendedTransaction(Dispatchers.IO, db.database) {
            activeBansByUuid.clear()
            activeBansByIp.clear()
            activeMutesByUuid.clear()
            Punishments.selectAll().where { Punishments.active eq true }
                .forEach { row ->
                    val record = row.toRecord()
                    when {
                        record.type == PunishmentType.IPBAN && record.targetIp != null ->
                            activeBansByIp[record.targetIp] = record
                        record.type.blocksLogin() ->
                            activeBansByUuid[record.targetUuid] = record
                        record.type.blocksChat() ->
                            activeMutesByUuid[record.targetUuid] = record
                        else -> {}
                    }
                }
            logger.info(
                "Loaded {} active bans, {} ipbans, {} mutes",
                activeBansByUuid.size, activeBansByIp.size, activeMutesByUuid.size
            )
        }
    }

    // ── Read ─────────────────────────────────────────────────────

    /** Fast in-memory check — never hits DB. */
    fun checkLoginCached(uuid: String, ip: String?): PunishmentRecord? {
        val byUuid = activeBansByUuid[uuid]
        if (byUuid != null && !byUuid.isExpired()) return byUuid
        if (ip != null) {
            val byIp = activeBansByIp[ip]
            if (byIp != null && !byIp.isExpired()) return byIp
        }
        return null
    }

    /** Fast in-memory mute check — never hits DB. */
    fun checkMuteCached(uuid: String): PunishmentRecord? {
        val record = activeMutesByUuid[uuid]
        return if (record != null && !record.isExpired()) record else null
    }

    suspend fun getById(id: Int): PunishmentRecord? = newSuspendedTransaction(Dispatchers.IO, db.database) {
        Punishments.selectAll().where { Punishments.id eq id }.firstOrNull()?.toRecord()
    }

    suspend fun getHistory(uuid: String, limit: Int = 100): List<PunishmentRecord> =
        newSuspendedTransaction(Dispatchers.IO, db.database) {
            Punishments.selectAll().where { Punishments.targetUuid eq uuid }
                .orderBy(Punishments.issuedAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toRecord() }
        }

    suspend fun list(activeOnly: Boolean, type: PunishmentType?, limit: Int, offset: Int): List<PunishmentRecord> =
        newSuspendedTransaction(Dispatchers.IO, db.database) {
            val query = Punishments.selectAll()
            if (activeOnly && type != null) {
                query.andWhere { (Punishments.active eq true) and (Punishments.type eq type.name) }
            } else if (activeOnly) {
                query.andWhere { Punishments.active eq true }
            } else if (type != null) {
                query.andWhere { Punishments.type eq type.name }
            }
            query.orderBy(Punishments.issuedAt, SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { it.toRecord() }
        }

    // ── Write ────────────────────────────────────────────────────

    /**
     * Issue a new punishment. Returns the persisted record.
     *
     * For [PunishmentType.BAN] / [PunishmentType.MUTE] / [PunishmentType.IPBAN] any
     * currently-active punishment of the same type against the same target is
     * automatically revoked (superseded) to keep only one active record per type+target.
     */
    suspend fun issue(
        type: PunishmentType,
        targetUuid: String,
        targetName: String,
        targetIp: String?,
        duration: Duration?,
        reason: String,
        issuer: String,
        issuerName: String
    ): PunishmentRecord {
        val now = Instant.now()
        val expiresAt = duration?.let { now.plus(it) }

        val record = newSuspendedTransaction(Dispatchers.IO, db.database) {
            // Supersede existing active records of overlapping type
            if (type.blocksLogin()) {
                Punishments.update({
                    (Punishments.targetUuid eq targetUuid) and
                    (Punishments.active eq true) and
                    (Punishments.type.inList(listOf(PunishmentType.BAN.name, PunishmentType.TEMPBAN.name, PunishmentType.IPBAN.name)))
                }) {
                    it[active] = false
                    it[revokedBy] = issuer
                    it[revokedAt] = now.toString()
                    it[revokeReason] = "Superseded by new punishment"
                }
            } else if (type.blocksChat()) {
                Punishments.update({
                    (Punishments.targetUuid eq targetUuid) and
                    (Punishments.active eq true) and
                    (Punishments.type.inList(listOf(PunishmentType.MUTE.name, PunishmentType.TEMPMUTE.name)))
                }) {
                    it[active] = false
                    it[revokedBy] = issuer
                    it[revokedAt] = now.toString()
                    it[revokeReason] = "Superseded by new punishment"
                }
            }

            // Kicks and warns are historical-only: always active = false after record
            val storeActive = type.isRevocable()

            val id = Punishments.insertAndGetId {
                it[Punishments.type] = type.name
                it[Punishments.targetUuid] = targetUuid
                it[Punishments.targetName] = targetName
                it[Punishments.targetIp] = targetIp
                it[Punishments.reason] = reason
                it[Punishments.issuer] = issuer
                it[Punishments.issuerName] = issuerName
                it[Punishments.issuedAt] = now.toString()
                it[Punishments.expiresAt] = expiresAt?.toString()
                it[Punishments.active] = storeActive
            }

            PunishmentRecord(
                id = id.value,
                type = type,
                targetUuid = targetUuid,
                targetName = targetName,
                targetIp = targetIp,
                reason = reason,
                issuer = issuer,
                issuerName = issuerName,
                issuedAt = now.toString(),
                expiresAt = expiresAt?.toString(),
                active = storeActive,
                revokedBy = null,
                revokedAt = null,
                revokeReason = null
            )
        }

        // Update cache after commit
        if (record.active) {
            when {
                record.type == PunishmentType.IPBAN && record.targetIp != null ->
                    activeBansByIp[record.targetIp] = record
                record.type.blocksLogin() ->
                    activeBansByUuid[record.targetUuid] = record
                record.type.blocksChat() ->
                    activeMutesByUuid[record.targetUuid] = record
            }
        }

        return record
    }

    /**
     * Revoke an active punishment (unban/unmute). Returns true if something was changed.
     */
    suspend fun revoke(id: Int, revokedBy: String, reason: String?): PunishmentRecord? {
        val updated = newSuspendedTransaction(Dispatchers.IO, db.database) {
            val row = Punishments.selectAll().where { Punishments.id eq id }.firstOrNull()
                ?: return@newSuspendedTransaction null
            if (!row[Punishments.active]) return@newSuspendedTransaction null
            val now = Instant.now().toString()
            Punishments.update({ Punishments.id eq id }) {
                it[active] = false
                it[Punishments.revokedBy] = revokedBy
                it[Punishments.revokedAt] = now
                it[Punishments.revokeReason] = reason
            }
            Punishments.selectAll().where { Punishments.id eq id }.first().toRecord()
        } ?: return null

        // Invalidate cache
        when {
            updated.type == PunishmentType.IPBAN && updated.targetIp != null ->
                activeBansByIp.remove(updated.targetIp)
            updated.type.blocksLogin() ->
                activeBansByUuid.remove(updated.targetUuid)
            updated.type.blocksChat() ->
                activeMutesByUuid.remove(updated.targetUuid)
        }
        return updated
    }

    /**
     * Find the active login-blocking punishment for a target (uuid, optionally by name fallback).
     * Used by console `unban <player>` when the admin has no punishment id.
     */
    suspend fun findActiveBan(targetUuidOrName: String): PunishmentRecord? =
        newSuspendedTransaction(Dispatchers.IO, db.database) {
            Punishments.selectAll().where {
                (Punishments.active eq true) and
                ((Punishments.targetUuid eq targetUuidOrName) or (Punishments.targetName eq targetUuidOrName)) and
                (Punishments.type.inList(listOf(PunishmentType.BAN.name, PunishmentType.TEMPBAN.name, PunishmentType.IPBAN.name)))
            }
                .orderBy(Punishments.issuedAt, SortOrder.DESC)
                .firstOrNull()?.toRecord()
        }

    suspend fun findActiveMute(targetUuidOrName: String): PunishmentRecord? =
        newSuspendedTransaction(Dispatchers.IO, db.database) {
            Punishments.selectAll().where {
                (Punishments.active eq true) and
                ((Punishments.targetUuid eq targetUuidOrName) or (Punishments.targetName eq targetUuidOrName)) and
                (Punishments.type.inList(listOf(PunishmentType.MUTE.name, PunishmentType.TEMPMUTE.name)))
            }
                .orderBy(Punishments.issuedAt, SortOrder.DESC)
                .firstOrNull()?.toRecord()
        }

    /**
     * Deactivate tempbans/tempmutes whose `expires_at` has passed.
     * Returns the records that were expired. Meant to run periodically.
     */
    suspend fun expireOverdue(): List<PunishmentRecord> {
        val nowIso = Instant.now().toString()
        val expired = newSuspendedTransaction(Dispatchers.IO, db.database) {
            val candidates = Punishments.selectAll().where {
                (Punishments.active eq true) and
                Punishments.expiresAt.isNotNull() and
                (Punishments.expiresAt less nowIso)
            }.map { it.toRecord() }

            if (candidates.isNotEmpty()) {
                Punishments.update({
                    (Punishments.active eq true) and
                    Punishments.expiresAt.isNotNull() and
                    (Punishments.expiresAt less nowIso)
                }) {
                    it[active] = false
                }
            }
            candidates
        }

        // Invalidate cache entries
        expired.forEach { r ->
            when {
                r.type == PunishmentType.IPBAN && r.targetIp != null -> activeBansByIp.remove(r.targetIp)
                r.type.blocksLogin() -> activeBansByUuid.remove(r.targetUuid)
                r.type.blocksChat() -> activeMutesByUuid.remove(r.targetUuid)
            }
        }
        return expired
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun ResultRow.toRecord() = PunishmentRecord(
        id = this[Punishments.id].value,
        type = PunishmentType.valueOf(this[Punishments.type]),
        targetUuid = this[Punishments.targetUuid],
        targetName = this[Punishments.targetName],
        targetIp = this[Punishments.targetIp],
        reason = this[Punishments.reason],
        issuer = this[Punishments.issuer],
        issuerName = this[Punishments.issuerName],
        issuedAt = this[Punishments.issuedAt],
        expiresAt = this[Punishments.expiresAt],
        active = this[Punishments.active],
        revokedBy = this[Punishments.revokedBy],
        revokedAt = this[Punishments.revokedAt],
        revokeReason = this[Punishments.revokeReason]
    )

    private fun PunishmentRecord.isExpired(): Boolean {
        val iso = expiresAt ?: return false
        return try {
            Instant.parse(iso).isBefore(Instant.now())
        } catch (_: Exception) { false }
    }
}
