package dev.nimbuspowered.nimbus.module.punishments

import dev.nimbuspowered.nimbus.event.NimbusEvent

/**
 * Factory for punishment module events. Emitted as [NimbusEvent.ModuleEvent]
 * with moduleId "punishments", picked up by AuditCollector + console formatters.
 */
object PunishmentsEvents {
    private const val MODULE_ID = "punishments"

    fun issued(record: PunishmentRecord) = NimbusEvent.ModuleEvent(
        MODULE_ID, "PUNISHMENT_ISSUED", mapOf(
            "id" to record.id.toString(),
            "type" to record.type.name,
            "target" to record.targetName,
            "targetUuid" to record.targetUuid,
            "issuer" to record.issuerName,
            "reason" to record.reason,
            "expiresAt" to (record.expiresAt ?: "")
        )
    )

    fun revoked(record: PunishmentRecord) = NimbusEvent.ModuleEvent(
        MODULE_ID, "PUNISHMENT_REVOKED", mapOf(
            "id" to record.id.toString(),
            "type" to record.type.name,
            "target" to record.targetName,
            "revokedBy" to (record.revokedBy ?: "unknown"),
            "reason" to (record.revokeReason ?: "")
        )
    )

    fun expired(record: PunishmentRecord) = NimbusEvent.ModuleEvent(
        MODULE_ID, "PUNISHMENT_EXPIRED", mapOf(
            "id" to record.id.toString(),
            "type" to record.type.name,
            "target" to record.targetName
        )
    )
}
