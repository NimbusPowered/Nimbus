package dev.nimbuspowered.nimbus.module.punishments

import dev.nimbuspowered.nimbus.event.NimbusEvent

/**
 * Factory for punishment module events. Emitted as [NimbusEvent.ModuleEvent]
 * with moduleId "punishments", picked up by AuditCollector + console formatters.
 */
object PunishmentsEvents {
    private const val MODULE_ID = "punishments"

    /**
     * @param rendered optional pre-rendered kick text (§-prefixed legacy format) —
     *   when present, the Velocity plugin's LiveKickHandler forwards it verbatim to
     *   the player's disconnect screen instead of rebuilding its own message.
     */
    fun issued(record: PunishmentRecord, rendered: String? = null) = NimbusEvent.ModuleEvent(
        MODULE_ID, "PUNISHMENT_ISSUED", buildMap {
            put("id", record.id.toString())
            put("type", record.type.name)
            put("target", record.targetName)
            put("targetUuid", record.targetUuid)
            put("issuer", record.issuerName)
            put("reason", record.reason)
            put("expiresAt", record.expiresAt ?: "")
            put("scope", record.scope.name)
            if (record.scopeTarget != null) put("scopeTarget", record.scopeTarget)
            if (rendered != null) put("kickMessage", rendered)
        }
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
