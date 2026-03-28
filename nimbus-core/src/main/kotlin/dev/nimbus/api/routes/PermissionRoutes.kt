package dev.nimbus.api.routes

import dev.nimbus.api.*
import dev.nimbus.event.EventBus
import dev.nimbus.event.NimbusEvent
import dev.nimbus.permissions.PermissionManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.permissionRoutes(
    permissionManager: PermissionManager,
    eventBus: EventBus
) {
    route("/api/permissions") {

        // ── Groups ──────────────────────────────────────────

        route("groups") {

            // GET /api/permissions/groups
            get {
                val groups = permissionManager.getAllGroups().map { it.toResponse() }
                call.respond(PermissionGroupListResponse(groups, groups.size))
            }

            // GET /api/permissions/groups/{name}
            get("{name}") {
                val name = call.parameters["name"]!!
                val group = permissionManager.getGroup(name)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiMessage(false, "Permission group '$name' not found"))
                call.respond(group.toResponse())
            }

            // POST /api/permissions/groups — Create group
            post {
                val request = call.receive<CreatePermissionGroupRequest>()
                if (request.name.isBlank() || !request.name.matches(Regex("^[a-zA-Z0-9_-]{1,64}$"))) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiMessage(false, "Invalid group name"))
                }
                if (permissionManager.getGroup(request.name) != null) {
                    return@post call.respond(HttpStatusCode.Conflict, ApiMessage(false, "Group '${request.name}' already exists"))
                }

                val group = permissionManager.createGroup(request.name, request.default)
                eventBus.emit(NimbusEvent.PermissionGroupCreated(group.name))
                call.respond(HttpStatusCode.Created, ApiMessage(true, "Permission group '${group.name}' created"))
            }

            // PUT /api/permissions/groups/{name} — Update group
            put("{name}") {
                val name = call.parameters["name"]!!
                val group = permissionManager.getGroup(name)
                    ?: return@put call.respond(HttpStatusCode.NotFound, ApiMessage(false, "Permission group '$name' not found"))

                val request = call.receive<UpdatePermissionGroupRequest>()
                if (request.default != null) {
                    permissionManager.setDefault(group.name, request.default)
                }
                if (request.permissions != null) {
                    // Replace all permissions
                    group.permissions.clear()
                    group.permissions.addAll(request.permissions)
                    // Trigger save by adding/removing (or just re-save via a reload)
                }
                if (request.parents != null) {
                    group.parents.clear()
                    group.parents.addAll(request.parents)
                }

                eventBus.emit(NimbusEvent.PermissionGroupUpdated(group.name))
                call.respond(ApiMessage(true, "Permission group '${group.name}' updated"))
            }

            // DELETE /api/permissions/groups/{name}
            delete("{name}") {
                val name = call.parameters["name"]!!
                if (permissionManager.getGroup(name) == null) {
                    return@delete call.respond(HttpStatusCode.NotFound, ApiMessage(false, "Permission group '$name' not found"))
                }

                permissionManager.deleteGroup(name)
                eventBus.emit(NimbusEvent.PermissionGroupDeleted(name))
                call.respond(ApiMessage(true, "Permission group '$name' deleted"))
            }

            // POST /api/permissions/groups/{name}/permissions — Add permission
            post("{name}/permissions") {
                val name = call.parameters["name"]!!
                if (permissionManager.getGroup(name) == null) {
                    return@post call.respond(HttpStatusCode.NotFound, ApiMessage(false, "Permission group '$name' not found"))
                }
                val request = call.receive<PermissionModifyRequest>()
                permissionManager.addPermission(name, request.permission)
                eventBus.emit(NimbusEvent.PermissionGroupUpdated(name))
                call.respond(ApiMessage(true, "Permission '${request.permission}' added to '$name'"))
            }

            // DELETE /api/permissions/groups/{name}/permissions — Remove permission
            delete("{name}/permissions") {
                val name = call.parameters["name"]!!
                if (permissionManager.getGroup(name) == null) {
                    return@delete call.respond(HttpStatusCode.NotFound, ApiMessage(false, "Permission group '$name' not found"))
                }
                val request = call.receive<PermissionModifyRequest>()
                permissionManager.removePermission(name, request.permission)
                eventBus.emit(NimbusEvent.PermissionGroupUpdated(name))
                call.respond(ApiMessage(true, "Permission '${request.permission}' removed from '$name'"))
            }
        }

        // ── Players ─────────────────────────────────────────

        route("players") {

            // GET /api/permissions/players/{uuid}
            get("{uuid}") {
                val uuid = call.parameters["uuid"]!!
                val entry = permissionManager.getPlayer(uuid)
                val effective = permissionManager.getEffectivePermissions(uuid)
                call.respond(PlayerPermissionResponse(
                    uuid = uuid,
                    name = entry?.name ?: "unknown",
                    groups = entry?.groups ?: emptyList(),
                    effectivePermissions = effective.sorted()
                ))
            }

            // PUT /api/permissions/players/{uuid} — Register/update player (called on join)
            put("{uuid}") {
                val uuid = call.parameters["uuid"]!!
                val request = call.receive<PlayerRegisterRequest>()
                permissionManager.registerPlayer(uuid, request.name)
                val effective = permissionManager.getEffectivePermissions(uuid)
                call.respond(PlayerPermissionResponse(
                    uuid = uuid,
                    name = request.name,
                    groups = permissionManager.getPlayer(uuid)?.groups ?: emptyList(),
                    effectivePermissions = effective.sorted()
                ))
            }

            // POST /api/permissions/players/{uuid}/groups — Add group to player
            post("{uuid}/groups") {
                val uuid = call.parameters["uuid"]!!
                val request = call.receive<PlayerGroupRequest>()
                val playerName = request.name ?: permissionManager.getPlayer(uuid)?.name ?: "unknown"

                try {
                    permissionManager.setPlayerGroup(uuid, playerName, request.group)
                    eventBus.emit(NimbusEvent.PlayerPermissionsUpdated(uuid, playerName))
                    call.respond(ApiMessage(true, "Group '${request.group}' added to player '$playerName'"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage(false, e.message ?: "Invalid request"))
                }
            }

            // DELETE /api/permissions/players/{uuid}/groups — Remove group from player
            delete("{uuid}/groups") {
                val uuid = call.parameters["uuid"]!!
                val request = call.receive<PlayerGroupRequest>()
                val playerName = permissionManager.getPlayer(uuid)?.name ?: "unknown"

                try {
                    permissionManager.removePlayerGroup(uuid, request.group)
                    eventBus.emit(NimbusEvent.PlayerPermissionsUpdated(uuid, playerName))
                    call.respond(ApiMessage(true, "Group '${request.group}' removed from player '$playerName'"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage(false, e.message ?: "Invalid request"))
                }
            }
        }

        // ── Permission Check ────────────────────────────────

        // GET /api/permissions/check/{uuid}/{permission}
        get("check/{uuid}/{permission...}") {
            val uuid = call.parameters["uuid"]!!
            val permission = call.parameters["permission"]!!
            val allowed = permissionManager.hasPermission(uuid, permission)
            call.respond(PermissionCheckResponse(uuid, permission, allowed))
        }
    }
}

private fun dev.nimbus.permissions.PermissionGroup.toResponse() = PermissionGroupResponse(
    name = name,
    default = default,
    permissions = permissions.toList(),
    parents = parents.toList()
)
