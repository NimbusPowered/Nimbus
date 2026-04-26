package dev.nimbuspowered.nimbus.module.storage

import kotlinx.serialization.Serializable

@Serializable
data class StorageStatusResponse(
    val enabled: Boolean,
    val bucket: String,
    val endpoint: String,
    val templates: List<TemplateSyncStatusDto>
)

@Serializable
data class TemplateSyncStatusDto(
    val templateName: String,
    val localExists: Boolean,
    val remoteExists: Boolean,
    val inSync: Boolean,
    val localFileCount: Int,
    val remoteFileCount: Int
)

@Serializable
data class StorageTemplateListResponse(
    val local: List<String>,
    val remote: List<String>
)

@Serializable
data class SyncResultDto(
    val templateName: String,
    val uploaded: Int,
    val downloaded: Int,
    val skipped: Int,
    val errors: List<String>,
    val success: Boolean
)

fun TemplateSyncStatus.toDto() = TemplateSyncStatusDto(
    templateName = templateName,
    localExists = localExists,
    remoteExists = remoteExists,
    inSync = inSync,
    localFileCount = localFileCount,
    remoteFileCount = remoteFileCount
)

fun SyncResult.toDto() = SyncResultDto(
    templateName = templateName,
    uploaded = uploaded,
    downloaded = downloaded,
    skipped = skipped,
    errors = errors,
    success = success
)
