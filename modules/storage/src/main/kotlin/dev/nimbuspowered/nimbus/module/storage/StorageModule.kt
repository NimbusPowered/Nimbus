package dev.nimbuspowered.nimbus.module.storage

import dev.nimbuspowered.nimbus.module.api.ModuleContext
import dev.nimbuspowered.nimbus.module.api.NimbusModule
import dev.nimbuspowered.nimbus.NimbusVersion

class StorageModule : NimbusModule {
    override val id = "storage"
    override val name = "Storage"
    override val version: String get() = NimbusVersion.version
    override val description = "S3-compatible template sync for multi-node clusters"

    override suspend fun init(context: ModuleContext) {}

    override suspend fun enable() {}

    override fun disable() {}
}
