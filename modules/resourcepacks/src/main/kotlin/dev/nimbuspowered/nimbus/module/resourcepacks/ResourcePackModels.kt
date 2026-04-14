package dev.nimbuspowered.nimbus.module.resourcepacks

import kotlinx.serialization.Serializable

/** Internal record for a resource pack row. */
data class ResourcePackRecord(
    val id: Int,
    val packUuid: String,
    val name: String,
    val source: String,           // "URL" | "LOCAL"
    val url: String,              // absolute URL for URL source, relative path for LOCAL
    val sha1Hash: String,
    val promptMessage: String,
    val force: Boolean,
    val fileSize: Long,
    val uploadedAt: String,
    val uploadedBy: String
)

data class AssignmentRecord(
    val id: Int,
    val packId: Int,
    val scope: String,            // "GLOBAL" | "GROUP" | "SERVICE"
    val target: String,
    val priority: Int
)

/** One resolved pack entry as returned to the backend plugin on player join. */
data class ResolvedPack(
    val packUuid: String,
    val name: String,
    val url: String,              // absolute URL resolved against publicBaseUrl
    val sha1Hash: String,
    val promptMessage: String,
    val force: Boolean,
    val priority: Int
)

// ── REST DTOs ───────────────────────────────────────────────

@Serializable
data class ResourcePackResponse(
    val id: Int,
    val packUuid: String,
    val name: String,
    val source: String,
    val url: String,
    val sha1Hash: String,
    val promptMessage: String,
    val force: Boolean,
    val fileSize: Long,
    val uploadedAt: String,
    val uploadedBy: String
)

@Serializable
data class ResourcePackListResponse(
    val packs: List<ResourcePackResponse>,
    val total: Int
)

@Serializable
data class CreateResourcePackRequest(
    val name: String,
    val url: String,
    val sha1Hash: String? = null,
    val promptMessage: String = "",
    val force: Boolean = false
)

@Serializable
data class AssignmentRequest(
    val scope: String,            // "GLOBAL" | "GROUP" | "SERVICE"
    val target: String = "",
    val priority: Int = 0
)

@Serializable
data class AssignmentResponse(
    val id: Int,
    val packId: Int,
    val scope: String,
    val target: String,
    val priority: Int
)

@Serializable
data class ResolvedPackResponse(
    val packUuid: String,
    val name: String,
    val url: String,
    val sha1Hash: String,
    val promptMessage: String,
    val force: Boolean,
    val priority: Int
)

@Serializable
data class ResolvedPackListResponse(
    val packs: List<ResolvedPackResponse>
)

@Serializable
data class StatusReportRequest(
    val playerUuid: String,
    val packUuid: String,
    val status: String            // ACCEPTED | DECLINED | FAILED_DOWNLOAD | SUCCESSFULLY_LOADED
)

fun ResourcePackRecord.toResponse() = ResourcePackResponse(
    id, packUuid, name, source, url, sha1Hash, promptMessage, force, fileSize, uploadedAt, uploadedBy
)

fun AssignmentRecord.toResponse() = AssignmentResponse(id, packId, scope, target, priority)

fun ResolvedPack.toResponse() = ResolvedPackResponse(
    packUuid, name, url, sha1Hash, promptMessage, force, priority
)
