package dev.nimbuspowered.nimbus.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Machine-readable API error codes returned in the `error` field of [ApiMessage].
 * Each entry carries its canonical HTTP status so route handlers can stay terse:
 *   `call.respond(err.defaultStatus, apiError("msg", err))`
 * or via the [fail] extension:
 *   `call.fail(err, "msg")`.
 *
 * The `code` string is the stable wire format — tests lock it against accidental rename.
 */
enum class ApiError(val code: String, val defaultStatus: HttpStatusCode) {

    // ── Auth (core) ─────────────────────────────────────────────
    AUTH_FAILED("AUTH_FAILED", HttpStatusCode.Unauthorized),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatusCode.Unauthorized),
    FORBIDDEN("FORBIDDEN", HttpStatusCode.Forbidden),
    READ_ONLY("READ_ONLY", HttpStatusCode.Forbidden),

    // ── Auth (module — folded in from AuthErrors) ───────────────
    AUTH_CHALLENGE_INVALID("AUTH_CHALLENGE_INVALID", HttpStatusCode.Unauthorized),
    AUTH_RATE_LIMITED("AUTH_RATE_LIMITED", HttpStatusCode.TooManyRequests),
    AUTH_DISABLED("AUTH_DISABLED", HttpStatusCode.Forbidden),
    AUTH_SESSION_INVALID("AUTH_SESSION_INVALID", HttpStatusCode.Unauthorized),
    AUTH_SESSION_EXPIRED("AUTH_SESSION_EXPIRED", HttpStatusCode.Unauthorized),
    AUTH_PLAYER_OFFLINE("AUTH_PLAYER_OFFLINE", HttpStatusCode.Conflict),
    AUTH_TOTP_REQUIRED("AUTH_TOTP_REQUIRED", HttpStatusCode.Unauthorized),
    AUTH_TOTP_INVALID("AUTH_TOTP_INVALID", HttpStatusCode.Unauthorized),
    AUTH_TOTP_ALREADY_ENABLED("AUTH_TOTP_ALREADY_ENABLED", HttpStatusCode.Conflict),
    AUTH_MAGIC_LINK_INVALID("AUTH_MAGIC_LINK_INVALID", HttpStatusCode.Unauthorized),
    AUTH_LOGIN_CHALLENGE_EXPIRED("AUTH_LOGIN_CHALLENGE_EXPIRED", HttpStatusCode.Gone),

    // ── Generic ─────────────────────────────────────────────────
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatusCode.BadRequest),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatusCode.InternalServerError),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", HttpStatusCode.PayloadTooLarge),
    NO_FIELDS_TO_UPDATE("NO_FIELDS_TO_UPDATE", HttpStatusCode.BadRequest),

    // ── Service ─────────────────────────────────────────────────
    SERVICE_NOT_FOUND("SERVICE_NOT_FOUND", HttpStatusCode.NotFound),
    SERVICE_NOT_READY("SERVICE_NOT_READY", HttpStatusCode.ServiceUnavailable),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatusCode.ServiceUnavailable),
    SERVICE_START_FAILED("SERVICE_START_FAILED", HttpStatusCode.InternalServerError),
    SERVICE_STOP_FAILED("SERVICE_STOP_FAILED", HttpStatusCode.InternalServerError),
    SERVICE_RESTART_FAILED("SERVICE_RESTART_FAILED", HttpStatusCode.InternalServerError),

    // ── Group ───────────────────────────────────────────────────
    GROUP_NOT_FOUND("GROUP_NOT_FOUND", HttpStatusCode.NotFound),
    GROUP_ALREADY_EXISTS("GROUP_ALREADY_EXISTS", HttpStatusCode.Conflict),
    GROUP_HAS_RUNNING_INSTANCES("GROUP_HAS_RUNNING_INSTANCES", HttpStatusCode.Conflict),

    // ── Dedicated ───────────────────────────────────────────────
    DEDICATED_NOT_FOUND("DEDICATED_NOT_FOUND", HttpStatusCode.NotFound),
    DEDICATED_ALREADY_EXISTS("DEDICATED_ALREADY_EXISTS", HttpStatusCode.Conflict),
    DEDICATED_ALREADY_RUNNING("DEDICATED_ALREADY_RUNNING", HttpStatusCode.Conflict),
    DEDICATED_DIRECTORY_NOT_FOUND("DEDICATED_DIRECTORY_NOT_FOUND", HttpStatusCode.NotFound),
    DEDICATED_PORT_IN_USE("DEDICATED_PORT_IN_USE", HttpStatusCode.Conflict),

    // ── Command ─────────────────────────────────────────────────
    COMMAND_NOT_FOUND("COMMAND_NOT_FOUND", HttpStatusCode.NotFound),
    COMMAND_NOT_REMOTE("COMMAND_NOT_REMOTE", HttpStatusCode.Forbidden),
    COMMAND_EXECUTION_FAILED("COMMAND_EXECUTION_FAILED", HttpStatusCode.InternalServerError),

    // ── Stress ──────────────────────────────────────────────────
    STRESS_ALREADY_RUNNING("STRESS_ALREADY_RUNNING", HttpStatusCode.Conflict),
    STRESS_NOT_RUNNING("STRESS_NOT_RUNNING", HttpStatusCode.Conflict),

    // ── Cluster / LB ────────────────────────────────────────────
    CLUSTER_NOT_ENABLED("CLUSTER_NOT_ENABLED", HttpStatusCode.NotFound),
    CLUSTER_TOKEN_MISSING("CLUSTER_TOKEN_MISSING", HttpStatusCode.NotFound),
    LOAD_BALANCER_NOT_ENABLED("LOAD_BALANCER_NOT_ENABLED", HttpStatusCode.NotFound),
    NODE_NOT_FOUND("NODE_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Files ───────────────────────────────────────────────────
    INVALID_SCOPE("INVALID_SCOPE", HttpStatusCode.BadRequest),
    PATH_NOT_FOUND("PATH_NOT_FOUND", HttpStatusCode.NotFound),
    PATH_TRAVERSAL("PATH_TRAVERSAL", HttpStatusCode.BadRequest),

    // ── Proxy ───────────────────────────────────────────────────
    PROXY_NOT_AVAILABLE("PROXY_NOT_AVAILABLE", HttpStatusCode.ServiceUnavailable),

    // ── Template ────────────────────────────────────────────────
    TEMPLATE_NOT_FOUND("TEMPLATE_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Modpack ─────────────────────────────────────────────────
    MODPACK_NOT_FOUND("MODPACK_NOT_FOUND", HttpStatusCode.NotFound),
    MODPACK_INVALID("MODPACK_INVALID", HttpStatusCode.BadRequest),
    MODPACK_UPLOAD_FAILED("MODPACK_UPLOAD_FAILED", HttpStatusCode.InternalServerError),
    CHUNKED_UPLOAD_NOT_FOUND("CHUNKED_UPLOAD_NOT_FOUND", HttpStatusCode.NotFound),
    CHUNKED_UPLOAD_INVALID("CHUNKED_UPLOAD_INVALID", HttpStatusCode.BadRequest),
    CURSEFORGE_API_KEY_MISSING("CURSEFORGE_API_KEY_MISSING", HttpStatusCode.FailedDependency),

    // ── Plugin ──────────────────────────────────────────────────
    PLUGIN_VERSION_NOT_FOUND("PLUGIN_VERSION_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Software ────────────────────────────────────────────────
    SOFTWARE_UNKNOWN("SOFTWARE_UNKNOWN", HttpStatusCode.NotFound),

    // ── Scaling ─────────────────────────────────────────────────
    SCALING_CONFIG_NOT_FOUND("SCALING_CONFIG_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Players ─────────────────────────────────────────────────
    PLAYER_NOT_FOUND("PLAYER_NOT_FOUND", HttpStatusCode.NotFound),
    PLAYER_NOT_ONLINE("PLAYER_NOT_ONLINE", HttpStatusCode.NotFound),

    // ── Perms ───────────────────────────────────────────────────
    PERMISSION_GROUP_NOT_FOUND("PERMISSION_GROUP_NOT_FOUND", HttpStatusCode.NotFound),
    PERMISSION_TRACK_NOT_FOUND("PERMISSION_TRACK_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Display ─────────────────────────────────────────────────
    DISPLAY_CONFIG_NOT_FOUND("DISPLAY_CONFIG_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Punishments ─────────────────────────────────────────────
    PUNISHMENT_NOT_FOUND("PUNISHMENT_NOT_FOUND", HttpStatusCode.NotFound),
    PUNISHMENT_ALREADY_REVOKED("PUNISHMENT_ALREADY_REVOKED", HttpStatusCode.Conflict),
    PUNISHMENT_TARGET_INVALID("PUNISHMENT_TARGET_INVALID", HttpStatusCode.BadRequest),
    PUNISHMENT_DURATION_INVALID("PUNISHMENT_DURATION_INVALID", HttpStatusCode.BadRequest),

    // ── Resource Packs ──────────────────────────────────────────
    RESOURCE_PACK_NOT_FOUND("RESOURCE_PACK_NOT_FOUND", HttpStatusCode.NotFound),
    RESOURCE_PACK_ALREADY_EXISTS("RESOURCE_PACK_ALREADY_EXISTS", HttpStatusCode.Conflict),
    RESOURCE_PACK_INVALID_URL("RESOURCE_PACK_INVALID_URL", HttpStatusCode.BadRequest),
    RESOURCE_PACK_UPLOAD_FAILED("RESOURCE_PACK_UPLOAD_FAILED", HttpStatusCode.InternalServerError),
    RESOURCE_PACK_ASSIGNMENT_NOT_FOUND("RESOURCE_PACK_ASSIGNMENT_NOT_FOUND", HttpStatusCode.NotFound),

    // ── Backup ──────────────────────────────────────────────────
    BACKUP_NOT_FOUND("BACKUP_NOT_FOUND", HttpStatusCode.NotFound),
    BACKUP_ARCHIVE_MISSING("BACKUP_ARCHIVE_MISSING", HttpStatusCode.NotFound),
    BACKUP_MANIFEST_MISSING("BACKUP_MANIFEST_MISSING", HttpStatusCode.NotFound),
    BACKUP_IN_PROGRESS("BACKUP_IN_PROGRESS", HttpStatusCode.Conflict),
    BACKUP_RESTORE_FAILED("BACKUP_RESTORE_FAILED", HttpStatusCode.Conflict),
    BACKUP_VERIFICATION_FAILED("BACKUP_VERIFICATION_FAILED", HttpStatusCode.UnprocessableEntity),
    BACKUP_CONFIG_INVALID("BACKUP_CONFIG_INVALID", HttpStatusCode.BadRequest);

    override fun toString(): String = code
}

/** Create a failed [ApiMessage] with a machine-readable error code. */
fun apiError(message: String, error: ApiError) =
    ApiMessage(success = false, message = message, error = error.code)

/** Legacy overload — used by the deprecated [ApiErrors] compat object and any raw callers. */
fun apiError(message: String, error: String) =
    ApiMessage(success = false, message = message, error = error)

/** Respond with [error]'s default HTTP status and a JSON body carrying the error code. */
suspend fun ApplicationCall.fail(error: ApiError, message: String) =
    respond(error.defaultStatus, apiError(message, error))

/**
 * Backwards-compatibility facade. Existing `ApiErrors.X` call-sites keep
 * compiling against the same String codes; new code should use [ApiError].
 * Remove in 0.14.0 after all call-sites migrate.
 */
@Deprecated("Use ApiError enum directly", ReplaceWith("ApiError"))
object ApiErrors {
    const val AUTH_FAILED = "AUTH_FAILED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val READ_ONLY = "READ_ONLY"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val NO_FIELDS_TO_UPDATE = "NO_FIELDS_TO_UPDATE"
    const val SERVICE_NOT_FOUND = "SERVICE_NOT_FOUND"
    const val SERVICE_NOT_READY = "SERVICE_NOT_READY"
    const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
    const val SERVICE_START_FAILED = "SERVICE_START_FAILED"
    const val SERVICE_STOP_FAILED = "SERVICE_STOP_FAILED"
    const val SERVICE_RESTART_FAILED = "SERVICE_RESTART_FAILED"
    const val GROUP_NOT_FOUND = "GROUP_NOT_FOUND"
    const val GROUP_ALREADY_EXISTS = "GROUP_ALREADY_EXISTS"
    const val GROUP_HAS_RUNNING_INSTANCES = "GROUP_HAS_RUNNING_INSTANCES"
    const val DEDICATED_NOT_FOUND = "DEDICATED_NOT_FOUND"
    const val DEDICATED_ALREADY_EXISTS = "DEDICATED_ALREADY_EXISTS"
    const val DEDICATED_ALREADY_RUNNING = "DEDICATED_ALREADY_RUNNING"
    const val DEDICATED_DIRECTORY_NOT_FOUND = "DEDICATED_DIRECTORY_NOT_FOUND"
    const val DEDICATED_PORT_IN_USE = "DEDICATED_PORT_IN_USE"
    const val COMMAND_NOT_FOUND = "COMMAND_NOT_FOUND"
    const val COMMAND_NOT_REMOTE = "COMMAND_NOT_REMOTE"
    const val COMMAND_EXECUTION_FAILED = "COMMAND_EXECUTION_FAILED"
    const val STRESS_ALREADY_RUNNING = "STRESS_ALREADY_RUNNING"
    const val STRESS_NOT_RUNNING = "STRESS_NOT_RUNNING"
    const val CLUSTER_NOT_ENABLED = "CLUSTER_NOT_ENABLED"
    const val LOAD_BALANCER_NOT_ENABLED = "LOAD_BALANCER_NOT_ENABLED"
    const val NODE_NOT_FOUND = "NODE_NOT_FOUND"
    const val INVALID_SCOPE = "INVALID_SCOPE"
    const val PATH_NOT_FOUND = "PATH_NOT_FOUND"
    const val PATH_TRAVERSAL = "PATH_TRAVERSAL"
    const val PROXY_NOT_AVAILABLE = "PROXY_NOT_AVAILABLE"
    const val MODPACK_NOT_FOUND = "MODPACK_NOT_FOUND"
    const val MODPACK_INVALID = "MODPACK_INVALID"
    const val MODPACK_UPLOAD_FAILED = "MODPACK_UPLOAD_FAILED"
    const val CHUNKED_UPLOAD_NOT_FOUND = "CHUNKED_UPLOAD_NOT_FOUND"
    const val CHUNKED_UPLOAD_INVALID = "CHUNKED_UPLOAD_INVALID"
    const val CURSEFORGE_API_KEY_MISSING = "CURSEFORGE_API_KEY_MISSING"
    const val PUNISHMENT_NOT_FOUND = "PUNISHMENT_NOT_FOUND"
    const val PUNISHMENT_ALREADY_REVOKED = "PUNISHMENT_ALREADY_REVOKED"
    const val PUNISHMENT_TARGET_INVALID = "PUNISHMENT_TARGET_INVALID"
    const val PUNISHMENT_DURATION_INVALID = "PUNISHMENT_DURATION_INVALID"
    const val RESOURCE_PACK_NOT_FOUND = "RESOURCE_PACK_NOT_FOUND"
    const val RESOURCE_PACK_ALREADY_EXISTS = "RESOURCE_PACK_ALREADY_EXISTS"
    const val RESOURCE_PACK_INVALID_URL = "RESOURCE_PACK_INVALID_URL"
    const val RESOURCE_PACK_UPLOAD_FAILED = "RESOURCE_PACK_UPLOAD_FAILED"
    const val RESOURCE_PACK_ASSIGNMENT_NOT_FOUND = "RESOURCE_PACK_ASSIGNMENT_NOT_FOUND"

    // --- Deprecated ---
    // Wire string kept at "INVALID_INPUT" during the 0.13.x deprecation window
    // so any lingering external caller relying on it does not break. All
    // in-tree call-sites were migrated explicitly to ApiError.VALIDATION_FAILED.
    @Deprecated("Use ApiError.VALIDATION_FAILED", ReplaceWith("ApiError.VALIDATION_FAILED.code"))
    const val INVALID_INPUT = "INVALID_INPUT"
    @Deprecated("Use a domain-specific *_NOT_FOUND code")
    const val NOT_FOUND = "NOT_FOUND"
    @Deprecated("No call-sites — removed in 0.14.0")
    const val INSUFFICIENT_SCOPE = "INSUFFICIENT_SCOPE"
}
