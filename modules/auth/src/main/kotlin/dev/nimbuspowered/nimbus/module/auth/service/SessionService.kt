package dev.nimbuspowered.nimbus.module.auth.service

import dev.nimbuspowered.nimbus.database.DatabaseManager
import dev.nimbuspowered.nimbus.module.AuthPrincipal
import dev.nimbuspowered.nimbus.module.PermissionSet
import dev.nimbuspowered.nimbus.module.auth.AuthConfig
import dev.nimbuspowered.nimbus.module.auth.db.DashboardSessions
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class IssuedSession(
    val rawToken: String,
    val principal: AuthPrincipal.UserSession
)

/**
 * Creates, validates, refreshes, and revokes dashboard sessions.
 *
 * Tokens are 256-bit URL-safe random strings. Only `sha256(token)` is persisted.
 */
class SessionService(
    private val db: DatabaseManager,
    private val config: () -> AuthConfig
) {
    private val secureRandom = SecureRandom()

    /**
     * Issue a new session for the given user. Enforces `max_per_user` by revoking
     * the oldest sessions if the limit would be exceeded.
     *
     * Permissions Phase 1: [permissions] is typically [PermissionSet.EMPTY] or a
     * stub — real resolution lands in Phase 2 (Perms module integration).
     */
    suspend fun issue(
        uuid: UUID,
        name: String,
        permissions: PermissionSet,
        ip: String?,
        userAgent: String?,
        loginMethod: String
    ): IssuedSession {
        val cfg = config().sessions
        val now = System.currentTimeMillis()
        val expiresAt = now + cfg.ttlSeconds * 1000L
        val raw = generateToken()
        val hash = LoginChallengeService.sha256Hex(raw)
        val sessionId = hash.take(16)

        val snapshot = permissions.asSet().joinToString(",")

        newSuspendedTransaction(Dispatchers.IO, db.database) {
            // Evict oldest sessions if we're over max_per_user (non-revoked, non-expired).
            val existing = DashboardSessions
                .selectAll()
                .where {
                    (DashboardSessions.uuid eq uuid.toString()) and
                        (DashboardSessions.revoked eq false) and
                        (DashboardSessions.expiresAt greaterEq now)
                }
                .orderBy(DashboardSessions.createdAt, SortOrder.ASC)
                .toList()
            val toEvict = (existing.size - (cfg.maxPerUser - 1)).coerceAtLeast(0)
            existing.take(toEvict).forEach { row ->
                DashboardSessions.update({ DashboardSessions.tokenHash eq row[DashboardSessions.tokenHash] }) {
                    it[revoked] = true
                }
            }

            DashboardSessions.insert {
                it[tokenHash] = hash
                it[DashboardSessions.uuid] = uuid.toString()
                it[DashboardSessions.name] = name.take(16)
                it[createdAt] = now
                it[DashboardSessions.expiresAt] = expiresAt
                it[lastUsedAt] = now
                it[DashboardSessions.ip] = ip?.take(45)
                it[DashboardSessions.userAgent] = userAgent?.take(255)
                it[revoked] = false
                it[DashboardSessions.loginMethod] = loginMethod.take(16)
                it[permissionsSnapshot] = snapshot
            }
        }

        return IssuedSession(
            rawToken = raw,
            principal = AuthPrincipal.UserSession(
                uuid = uuid,
                name = name,
                permissions = permissions,
                sessionId = sessionId,
                expiresAt = expiresAt
            )
        )
    }

    /**
     * Validate a raw session token. Returns the user session principal if the
     * token is active, non-revoked, and not expired. If rolling refresh is
     * enabled, also extends `expires_at` and updates `last_used_at`.
     */
    suspend fun validate(rawToken: String): AuthPrincipal.UserSession? {
        val hash = LoginChallengeService.sha256Hex(rawToken.trim())
        val cfg = config().sessions
        val now = System.currentTimeMillis()

        return newSuspendedTransaction(Dispatchers.IO, db.database) {
            // Lazy pruning of expired/revoked sessions
            DashboardSessions.deleteWhere {
                (DashboardSessions.expiresAt less (now - 86_400_000L)) or (DashboardSessions.revoked eq true)
            }

            val row = DashboardSessions.selectAll()
                .where { DashboardSessions.tokenHash eq hash }
                .firstOrNull() ?: return@newSuspendedTransaction null
            if (row[DashboardSessions.revoked]) return@newSuspendedTransaction null
            if (row[DashboardSessions.expiresAt] < now) return@newSuspendedTransaction null

            val newExpires = if (cfg.rollingRefresh) now + cfg.ttlSeconds * 1000L else row[DashboardSessions.expiresAt]
            DashboardSessions.update({ DashboardSessions.tokenHash eq hash }) {
                it[lastUsedAt] = now
                if (cfg.rollingRefresh) it[expiresAt] = newExpires
            }

            val permSet = row[DashboardSessions.permissionsSnapshot]
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            AuthPrincipal.UserSession(
                uuid = runCatching { UUID.fromString(row[DashboardSessions.uuid]) }.getOrNull()
                    ?: return@newSuspendedTransaction null,
                name = row[DashboardSessions.name],
                permissions = PermissionSet(permSet),
                sessionId = hash.take(16),
                expiresAt = newExpires
            )
        }
    }

    /**
     * Refresh the cached `permissions_snapshot` for every active session of a
     * given UUID. Called from the `PermissionsChanged` event subscription so
     * group/permission mutations take effect without waiting for re-login.
     *
     * Returns the number of sessions updated.
     */
    suspend fun refreshPermissionsFor(uuid: String, permissions: PermissionSet): Int {
        val snapshot = permissions.asSet().joinToString(",")
        return newSuspendedTransaction(Dispatchers.IO, db.database) {
            DashboardSessions.update({
                (DashboardSessions.uuid eq uuid) and
                    (DashboardSessions.revoked eq false)
            }) {
                it[permissionsSnapshot] = snapshot
            }
        }
    }

    /**
     * Refresh permissions for every active session by re-resolving each
     * unique UUID via [resolve]. Used on group-scope perms mutations (group
     * create/update/delete, track create/delete) where any group member may
     * be affected.
     */
    suspend fun refreshAllActive(resolve: (String) -> PermissionSet): Int {
        val now = System.currentTimeMillis()
        val uuids = newSuspendedTransaction(Dispatchers.IO, db.database) {
            DashboardSessions
                .selectAll()
                .where {
                    (DashboardSessions.revoked eq false) and
                        (DashboardSessions.expiresAt greaterEq now)
                }
                .map { it[DashboardSessions.uuid] }
                .toSet()
        }
        var total = 0
        for (uuid in uuids) {
            total += refreshPermissionsFor(uuid, resolve(uuid))
        }
        return total
    }

    suspend fun revoke(rawToken: String): Boolean {
        val hash = LoginChallengeService.sha256Hex(rawToken.trim())
        return newSuspendedTransaction(Dispatchers.IO, db.database) {
            DashboardSessions.update({ DashboardSessions.tokenHash eq hash }) {
                it[revoked] = true
            } > 0
        }
    }

    suspend fun revokeAll(uuid: UUID): Int {
        return newSuspendedTransaction(Dispatchers.IO, db.database) {
            DashboardSessions.update({ DashboardSessions.uuid eq uuid.toString() }) {
                it[revoked] = true
            }
        }
    }

    private fun generateToken(): String {
        val buf = ByteArray(32)
        secureRandom.nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }
}

