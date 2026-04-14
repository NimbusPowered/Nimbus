package dev.nimbuspowered.nimbus.module.resourcepacks

import dev.nimbuspowered.nimbus.event.NimbusEvent

object ResourcePacksEvents {
    private const val MODULE_ID = "resourcepacks"

    fun created(record: ResourcePackRecord) = NimbusEvent.ModuleEvent(
        MODULE_ID, "RESOURCE_PACK_CREATED", mapOf(
            "id" to record.id.toString(),
            "name" to record.name,
            "source" to record.source
        )
    )

    fun deleted(id: Int, name: String) = NimbusEvent.ModuleEvent(
        MODULE_ID, "RESOURCE_PACK_DELETED", mapOf(
            "id" to id.toString(),
            "name" to name
        )
    )

    fun assigned(packId: Int, scope: String, target: String) = NimbusEvent.ModuleEvent(
        MODULE_ID, "RESOURCE_PACK_ASSIGNED", mapOf(
            "packId" to packId.toString(),
            "scope" to scope,
            "target" to target
        )
    )

    fun unassigned(packId: Int, scope: String, target: String) = NimbusEvent.ModuleEvent(
        MODULE_ID, "RESOURCE_PACK_UNASSIGNED", mapOf(
            "packId" to packId.toString(),
            "scope" to scope,
            "target" to target
        )
    )

    fun statusReport(playerUuid: String, packUuid: String, status: String) = NimbusEvent.ModuleEvent(
        MODULE_ID, "RESOURCE_PACK_STATUS", mapOf(
            "player" to playerUuid,
            "pack" to packUuid,
            "status" to status
        )
    )
}
