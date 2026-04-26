package dev.nimbuspowered.nimbus.module.storage

import dev.nimbuspowered.nimbus.api.ApiError
import dev.nimbuspowered.nimbus.api.apiError
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.storageRoutes(config: StorageConfig, syncManager: TemplateSyncManager?) {

    route("/api/storage") {

        // GET /api/storage/status — overall status + per-template sync state
        get("status") {
            if (syncManager == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    apiError("Storage module is disabled", ApiError.SERVICE_UNAVAILABLE)
                )
                return@get
            }
            val statuses = syncManager.status()
            call.respond(StorageStatusResponse(
                enabled = config.enabled,
                bucket = config.bucket,
                endpoint = config.endpoint.ifBlank { "AWS S3 (${config.region})" },
                templates = statuses.map { it.toDto() }
            ))
        }

        // GET /api/storage/templates — list local and remote template names
        get("templates") {
            if (syncManager == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    apiError("Storage module is disabled", ApiError.SERVICE_UNAVAILABLE)
                )
                return@get
            }
            val local = syncManager.listLocal()
            val remote = syncManager.listRemote()
            call.respond(StorageTemplateListResponse(local = local, remote = remote))
        }

        // POST /api/storage/templates/{name}/push — push one template to S3
        post("templates/{name}/push") {
            if (syncManager == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    apiError("Storage module is disabled", ApiError.SERVICE_UNAVAILABLE)
                )
                return@post
            }
            val name = call.parameters["name"]!!
            val result = syncManager.push(name)
            call.respond(result.toDto())
        }

        // POST /api/storage/templates/{name}/pull — pull one template from S3
        post("templates/{name}/pull") {
            if (syncManager == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    apiError("Storage module is disabled", ApiError.SERVICE_UNAVAILABLE)
                )
                return@post
            }
            val name = call.parameters["name"]!!
            val result = syncManager.pull(name)
            call.respond(result.toDto())
        }

        // POST /api/storage/sync — push then pull for all local templates
        post("sync") {
            if (syncManager == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    apiError("Storage module is disabled", ApiError.SERVICE_UNAVAILABLE)
                )
                return@post
            }
            val local = syncManager.listLocal()
            val results = local.flatMap { name ->
                listOf(syncManager.push(name), syncManager.pull(name))
            }
            call.respond(results.map { it.toDto() })
        }
    }
}
