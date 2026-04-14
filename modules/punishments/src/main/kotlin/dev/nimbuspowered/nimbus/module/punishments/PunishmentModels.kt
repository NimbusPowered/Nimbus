package dev.nimbuspowered.nimbus.module.punishments

import kotlinx.serialization.Serializable

/**
 * A punishment record. Shared between manager, routes, and console command.
 */
data class PunishmentRecord(
    val id: Int,
    val type: PunishmentType,
    val targetUuid: String,
    val targetName: String,
    val targetIp: String?,
    val reason: String,
    val issuer: String,
    val issuerName: String,
    val issuedAt: String,           // ISO-8601
    val expiresAt: String?,         // ISO-8601, null = permanent
    val active: Boolean,
    val revokedBy: String?,
    val revokedAt: String?,
    val revokeReason: String?
)

// ── REST API DTOs ───────────────────────────────────────────

@Serializable
data class PunishmentResponse(
    val id: Int,
    val type: String,
    val targetUuid: String,
    val targetName: String,
    val targetIp: String?,
    val reason: String,
    val issuer: String,
    val issuerName: String,
    val issuedAt: String,
    val expiresAt: String?,
    val active: Boolean,
    val revokedBy: String?,
    val revokedAt: String?,
    val revokeReason: String?
)

@Serializable
data class PunishmentListResponse(
    val punishments: List<PunishmentResponse>,
    val total: Int
)

@Serializable
data class IssuePunishmentRequest(
    val type: String,               // "ban" | "tempban" | "ipban" | "mute" | "tempmute" | "kick" | "warn"
    val targetUuid: String? = null,
    val targetName: String,
    val targetIp: String? = null,
    val duration: String? = null,   // "30m", "7d", "perm", null
    val reason: String = "",
    val issuer: String = "api",
    val issuerName: String = "API"
)

@Serializable
data class RevokePunishmentRequest(
    val revokedBy: String = "api",
    val reason: String = ""
)

/**
 * Compact response used by Bridge on PreLogin.
 * Only includes fields the kick screen needs.
 */
@Serializable
data class PunishmentCheckResponse(
    val punished: Boolean,
    val type: String? = null,
    val reason: String? = null,
    val issuerName: String? = null,
    val issuedAt: String? = null,
    val expiresAt: String? = null,
    val remainingSeconds: Long? = null
)

fun PunishmentRecord.toResponse() = PunishmentResponse(
    id = id,
    type = type.name,
    targetUuid = targetUuid,
    targetName = targetName,
    targetIp = targetIp,
    reason = reason,
    issuer = issuer,
    issuerName = issuerName,
    issuedAt = issuedAt,
    expiresAt = expiresAt,
    active = active,
    revokedBy = revokedBy,
    revokedAt = revokedAt,
    revokeReason = revokeReason
)
