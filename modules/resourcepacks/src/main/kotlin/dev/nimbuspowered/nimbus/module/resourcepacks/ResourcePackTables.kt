package dev.nimbuspowered.nimbus.module.resourcepacks

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Registry of resource packs (URL-referenced or locally hosted).
 *
 * `source` is either "URL" (external) or "LOCAL" (file stored by controller).
 * For LOCAL packs, `url` holds a relative path like `/api/resourcepacks/files/<id>.zip`
 * that clients resolve against the public controller base URL.
 */
object ResourcePacks : IntIdTable("resource_packs") {
    val packUuid = varchar("pack_uuid", 36).uniqueIndex() // used for 1.20.3+ multi-pack stacks
    val name = varchar("name", 64)
    val packSource = varchar("source", 16)                // "URL" | "LOCAL" (col-name kept stable)
    val url = varchar("url", 1024)
    val sha1Hash = varchar("sha1_hash", 40)
    val promptMessage = varchar("prompt_message", 256).default("")
    val force = bool("force").default(false)
    val fileSize = long("file_size").default(0)
    val uploadedAt = varchar("uploaded_at", 30)
    val uploadedBy = varchar("uploaded_by", 128).default("system")
}

/**
 * Assignments link packs to a scope (global / group / service) with a priority.
 * Multiple packs per scope are allowed on 1.20.3+ — stacked by priority ascending.
 */
object ResourcePackAssignments : IntIdTable("resource_pack_assignments") {
    val packId = reference("pack_id", ResourcePacks)
    val scope = varchar("scope", 16)                      // "GLOBAL" | "GROUP" | "SERVICE"
    val target = varchar("target", 128).default("")       // group or service name; empty for GLOBAL
    val priority = integer("priority").default(0)

    init {
        uniqueIndex(packId, scope, target)
        index(false, scope, target)
    }
}
