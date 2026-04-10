package dev.nimbuspowered.nimbus.api.routes

import dev.nimbuspowered.nimbus.api.*
import dev.nimbuspowered.nimbus.api.ApiErrors
import dev.nimbuspowered.nimbus.api.apiError
import dev.nimbuspowered.nimbus.config.DedicatedDefinition
import dev.nimbuspowered.nimbus.config.DedicatedServiceConfig
import dev.nimbuspowered.nimbus.config.JvmConfig
import dev.nimbuspowered.nimbus.config.ServerSoftware
import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.event.NimbusEvent
import dev.nimbuspowered.nimbus.group.GroupManager
import dev.nimbuspowered.nimbus.service.DedicatedServiceManager
import dev.nimbuspowered.nimbus.service.ServiceManager
import dev.nimbuspowered.nimbus.service.ServiceRegistry
import dev.nimbuspowered.nimbus.service.ServiceState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

private val VALID_NAME = Regex("^[a-zA-Z0-9_-]{1,64}$")

fun Route.dedicatedRoutes(
    registry: ServiceRegistry,
    dedicatedServiceManager: DedicatedServiceManager,
    serviceManager: ServiceManager,
    groupManager: GroupManager,
    eventBus: EventBus,
    dedicatedDir: Path
) {
    route("/api/dedicated") {

        // GET /api/dedicated — List all dedicated configs with runtime status
        get {
            val responses = dedicatedServiceManager.getAllConfigs().map { config ->
                config.toResponse(registry, dedicatedServiceManager)
            }
            call.respond(DedicatedListResponse(responses, responses.size))
        }

        // GET /api/dedicated/{name} — Single dedicated service detail
        get("{name}") {
            val name = call.parameters["name"]!!
            val config = dedicatedServiceManager.getConfig(name)
                ?: return@get call.respond(HttpStatusCode.NotFound, apiError("Dedicated service '$name' not found", ApiErrors.DEDICATED_NOT_FOUND))
            call.respond(config.toResponse(registry, dedicatedServiceManager))
        }

        // POST /api/dedicated — Create a new dedicated service
        post {
            val request = call.receive<CreateDedicatedRequest>()

            val errors = validateDedicatedRequest(request)
            if (errors.isNotEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, apiError(errors.joinToString("; "), ApiErrors.VALIDATION_FAILED))
            }

            if (dedicatedServiceManager.getConfig(request.name) != null) {
                return@post call.respond(HttpStatusCode.Conflict, apiError("Dedicated service '${request.name}' already exists", ApiErrors.DEDICATED_ALREADY_EXISTS))
            }

            // Check if port is in use by another dedicated config
            val portConflict = dedicatedServiceManager.getAllConfigs().find { it.dedicated.port == request.port }
            if (portConflict != null) {
                return@post call.respond(HttpStatusCode.Conflict, apiError("Port ${request.port} is already used by dedicated service '${portConflict.dedicated.name}'", ApiErrors.DEDICATED_PORT_IN_USE))
            }

            val software = ServerSoftware.valueOf(request.software.uppercase())

            val config = DedicatedServiceConfig(
                dedicated = DedicatedDefinition(
                    name = request.name,
                    port = request.port,
                    software = software,
                    version = request.version,
                    jarName = request.jarName,
                    readyPattern = request.readyPattern,
                    javaPath = request.javaPath,
                    proxyEnabled = request.proxyEnabled,
                    memory = request.memory,
                    restartOnCrash = request.restartOnCrash,
                    maxRestarts = request.maxRestarts,
                    jvm = JvmConfig(request.jvmArgs, request.jvmOptimize)
                )
            )

            // Auto-create the managed service directory under paths.dedicated/<name>/
            dedicatedServiceManager.ensureServiceDirectory(request.name)
            dedicatedServiceManager.writeTOML(config)
            dedicatedServiceManager.addConfig(config)

            eventBus.emit(NimbusEvent.DedicatedCreated(request.name))
            call.respond(HttpStatusCode.Created, ApiMessage(true, "Dedicated service '${request.name}' created"))
        }

        // PUT /api/dedicated/{name} — Update config
        put("{name}") {
            val name = call.parameters["name"]!!
            dedicatedServiceManager.getConfig(name)
                ?: return@put call.respond(HttpStatusCode.NotFound, apiError("Dedicated service '$name' not found", ApiErrors.DEDICATED_NOT_FOUND))

            val request = call.receive<CreateDedicatedRequest>()

            val errors = validateDedicatedRequest(request)
            if (errors.isNotEmpty()) {
                return@put call.respond(HttpStatusCode.BadRequest, apiError(errors.joinToString("; "), ApiErrors.VALIDATION_FAILED))
            }

            // Stop if running
            val running = registry.get(name)
            if (running != null && running.state != ServiceState.STOPPED && running.state != ServiceState.CRASHED) {
                serviceManager.stopService(name)
            }

            val software = ServerSoftware.valueOf(request.software.uppercase())

            val config = DedicatedServiceConfig(
                dedicated = DedicatedDefinition(
                    name = request.name,
                    port = request.port,
                    software = software,
                    version = request.version,
                    jarName = request.jarName,
                    readyPattern = request.readyPattern,
                    javaPath = request.javaPath,
                    proxyEnabled = request.proxyEnabled,
                    memory = request.memory,
                    restartOnCrash = request.restartOnCrash,
                    maxRestarts = request.maxRestarts,
                    jvm = JvmConfig(request.jvmArgs, request.jvmOptimize)
                )
            )

            dedicatedServiceManager.writeTOML(config)
            dedicatedServiceManager.addConfig(config)

            call.respond(ApiMessage(true, "Dedicated service '$name' updated"))
        }

        // DELETE /api/dedicated/{name} — Delete
        delete("{name}") {
            val name = call.parameters["name"]!!
            dedicatedServiceManager.getConfig(name)
                ?: return@delete call.respond(HttpStatusCode.NotFound, apiError("Dedicated service '$name' not found", ApiErrors.DEDICATED_NOT_FOUND))

            // Stop if running
            val running = registry.get(name)
            if (running != null && running.state != ServiceState.STOPPED && running.state != ServiceState.CRASHED) {
                serviceManager.stopService(name)
            }

            dedicatedServiceManager.deleteTOML(name)
            dedicatedServiceManager.removeConfig(name)

            eventBus.emit(NimbusEvent.DedicatedDeleted(name))
            call.respond(ApiMessage(true, "Dedicated service '$name' deleted"))
        }

        // POST /api/dedicated/{name}/start — Start dedicated service
        post("{name}/start") {
            val name = call.parameters["name"]!!
            val config = dedicatedServiceManager.getConfig(name)
                ?: return@post call.respond(HttpStatusCode.NotFound, apiError("Dedicated service '$name' not found", ApiErrors.DEDICATED_NOT_FOUND))

            val existing = registry.get(name)
            if (existing != null && existing.state != ServiceState.STOPPED && existing.state != ServiceState.CRASHED) {
                return@post call.respond(HttpStatusCode.Conflict, apiError("Dedicated service '$name' is already running (state: ${existing.state})", ApiErrors.DEDICATED_ALREADY_RUNNING))
            }

            val service = serviceManager.startDedicatedService(config.dedicated)
            if (service != null) {
                call.respond(HttpStatusCode.Created, ApiMessage(true, "Dedicated service '${service.name}' starting on port ${service.port}"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, apiError("Failed to start dedicated service '$name'", ApiErrors.SERVICE_START_FAILED))
            }
        }

        // POST /api/dedicated/{name}/stop — Stop dedicated service
        post("{name}/stop") {
            val name = call.parameters["name"]!!
            dedicatedServiceManager.getConfig(name)
                ?: return@post call.respond(HttpStatusCode.NotFound, apiError("Dedicated service '$name' not found", ApiErrors.DEDICATED_NOT_FOUND))

            val existing = registry.get(name)
            if (existing == null || existing.state == ServiceState.STOPPED) {
                return@post call.respond(HttpStatusCode.Conflict, apiError("Dedicated service '$name' is not running", ApiErrors.SERVICE_NOT_READY))
            }

            val stopped = serviceManager.stopService(name)
            if (stopped) {
                call.respond(ApiMessage(true, "Dedicated service '$name' stopped"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, apiError("Failed to stop dedicated service '$name'", ApiErrors.SERVICE_STOP_FAILED))
            }
        }

        // POST /api/dedicated/{name}/restart — Restart dedicated service
        post("{name}/restart") {
            val name = call.parameters["name"]!!
            val config = dedicatedServiceManager.getConfig(name)
                ?: return@post call.respond(HttpStatusCode.NotFound, apiError("Dedicated service '$name' not found", ApiErrors.DEDICATED_NOT_FOUND))

            // Stop if running
            val existing = registry.get(name)
            if (existing != null && existing.state != ServiceState.STOPPED && existing.state != ServiceState.CRASHED) {
                serviceManager.stopService(name)
            }

            val service = serviceManager.startDedicatedService(config.dedicated)
            if (service != null) {
                call.respond(ApiMessage(true, "Dedicated service '${service.name}' restarted on port ${service.port}"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, apiError("Failed to restart dedicated service '$name'", ApiErrors.SERVICE_RESTART_FAILED))
            }
        }
    }
}

private fun validateDedicatedRequest(request: CreateDedicatedRequest): List<String> {
    val errors = mutableListOf<String>()

    if (!VALID_NAME.matches(request.name)) {
        errors += "Invalid name '${request.name}' — only alphanumeric, dash and underscore allowed (max 64 chars)"
    }

    if (request.port < 1 || request.port > 65535) {
        errors += "Port must be between 1 and 65535"
    }

    // Validate software enum
    try { ServerSoftware.valueOf(request.software.uppercase()) }
    catch (_: IllegalArgumentException) { errors += "Invalid software '${request.software}'. Valid: ${ServerSoftware.entries.joinToString()}" }

    // Validate memory format
    if (!request.memory.matches(Regex("^\\d+[MmGg]$"))) {
        errors += "Invalid memory format '${request.memory}' — expected e.g. '512M' or '2G'"
    }

    // Validate version format
    if (!request.version.matches(Regex("^\\d+\\.\\d+(\\.\\d+)?(-.*)?$"))) {
        errors += "Invalid version '${request.version}' — expected e.g. '1.21.4'"
    }

    if (request.maxRestarts < 0) errors += "max_restarts must be >= 0"

    return errors
}

private fun DedicatedServiceConfig.toResponse(
    registry: ServiceRegistry,
    dedicatedServiceManager: DedicatedServiceManager
): DedicatedServiceResponse {
    val def = dedicated
    val service = registry.get(def.name)

    val uptime = if (service?.startedAt != null) {
        val duration = Duration.between(service.startedAt, Instant.now())
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        "${hours}h ${minutes}m ${seconds}s"
    } else null

    return DedicatedServiceResponse(
        name = def.name,
        directory = dedicatedServiceManager.getServiceDirectory(def.name).toString(),
        port = def.port,
        software = def.software.name,
        version = def.version,
        memory = def.memory,
        proxyEnabled = def.proxyEnabled,
        restartOnCrash = def.restartOnCrash,
        maxRestarts = def.maxRestarts,
        jvmArgs = def.jvm.args,
        jvmOptimize = def.jvm.optimize,
        state = service?.state?.name,
        pid = service?.pid,
        playerCount = service?.playerCount,
        uptime = uptime
    )
}
