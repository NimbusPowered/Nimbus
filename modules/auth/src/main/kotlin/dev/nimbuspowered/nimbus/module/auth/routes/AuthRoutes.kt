package dev.nimbuspowered.nimbus.module.auth.routes

import dev.nimbuspowered.nimbus.api.ApiErrors
import dev.nimbuspowered.nimbus.api.ApiMessage
import dev.nimbuspowered.nimbus.api.apiError
import dev.nimbuspowered.nimbus.module.PermissionSet
import dev.nimbuspowered.nimbus.module.auth.AuthConfig
import dev.nimbuspowered.nimbus.module.auth.service.ChallengeKind
import dev.nimbuspowered.nimbus.module.auth.service.LoginChallengeService
import dev.nimbuspowered.nimbus.module.auth.service.SessionService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Error code strings local to the auth module. Added here rather than in
 * ApiErrors.kt (core) to keep the module self-contained in Phase 1.
 */
object AuthErrors {
    const val AUTH_CHALLENGE_INVALID = "AUTH_CHALLENGE_INVALID"
    const val AUTH_RATE_LIMITED = "AUTH_RATE_LIMITED"
    const val AUTH_DISABLED = "AUTH_DISABLED"
    const val AUTH_SESSION_INVALID = "AUTH_SESSION_INVALID"
}

@Serializable
data class GenerateCodeRequest(val uuid: String, val name: String)

@Serializable
data class GenerateCodeResponse(
    val code: String,
    val expiresAt: Long,
    val ttlSeconds: Long
)

@Serializable
data class RequestMagicLinkRequest(val uuid: String, val name: String)

@Serializable
data class RequestMagicLinkResponse(
    val url: String,
    val token: String,
    val expiresAt: Long,
    val ttlSeconds: Long
)

@Serializable
data class ConsumeChallengeRequest(val challenge: String)

@Serializable
data class UserInfo(
    val uuid: String,
    val name: String,
    val permissions: List<String>,
    val isAdmin: Boolean,
    val totpEnabled: Boolean
)

@Serializable
data class ConsumeChallengeResponse(
    val token: String,
    val expiresAt: Long,
    val user: UserInfo,
    val totpRequired: Boolean = false
)

/**
 * Registers the /api/auth routes.
 *
 * Phase 1 scope: generate-code, request-magic-link, consume-challenge, logout, me.
 * TOTP flow (`totp_required`) and the in-game magic-link delivery live in later phases.
 */
fun Route.authRoutes(
    challengeService: LoginChallengeService,
    sessionService: SessionService,
    configSupplier: () -> AuthConfig
) {
    route("/api/auth") {

        // Used by the Bridge/SDK (Phase 3) to hand a freshly generated code back
        // to the in-game player. Cluster-token-authenticated in production — the
        // core wires this into the service-token route block for now.
        post("generate-code") {
            val req = runCatching { call.receive<GenerateCodeRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    apiError("uuid + name required", ApiErrors.VALIDATION_FAILED))
            val uuid = runCatching { UUID.fromString(req.uuid) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    apiError("Invalid uuid", ApiErrors.VALIDATION_FAILED))
            val cfg = configSupplier().loginChallenge
            try {
                val issued = challengeService.issueCode(uuid.toString(), req.name.take(16))
                call.respond(GenerateCodeResponse(
                    code = issued.raw,
                    expiresAt = issued.expiresAt,
                    ttlSeconds = cfg.codeTtlSeconds
                ))
            } catch (e: LoginChallengeService.RateLimitedException) {
                call.respond(HttpStatusCode.TooManyRequests,
                    apiError(e.message ?: "Rate limited", AuthErrors.AUTH_RATE_LIMITED))
            }
        }

        post("request-magic-link") {
            val req = runCatching { call.receive<RequestMagicLinkRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    apiError("uuid + name required", ApiErrors.VALIDATION_FAILED))
            if (!configSupplier().loginChallenge.magicLinkEnabled) {
                return@post call.respond(HttpStatusCode.Forbidden,
                    apiError("Magic link login is disabled", AuthErrors.AUTH_DISABLED))
            }
            val uuid = runCatching { UUID.fromString(req.uuid) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    apiError("Invalid uuid", ApiErrors.VALIDATION_FAILED))
            val ip = call.request.local.remoteAddress
            try {
                val issued = challengeService.issueMagicLink(uuid.toString(), req.name.take(16), ip)
                val base = configSupplier().dashboard.publicUrl.trimEnd('/')
                val url = "$base/login?link=${issued.raw}"
                call.respond(HttpStatusCode.Accepted, RequestMagicLinkResponse(
                    url = url,
                    token = issued.raw,
                    expiresAt = issued.expiresAt,
                    ttlSeconds = configSupplier().loginChallenge.magicLinkTtlSeconds
                ))
            } catch (e: LoginChallengeService.RateLimitedException) {
                call.respond(HttpStatusCode.TooManyRequests,
                    apiError(e.message ?: "Rate limited", AuthErrors.AUTH_RATE_LIMITED))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Forbidden,
                    apiError(e.message ?: "Disabled", AuthErrors.AUTH_DISABLED))
            }
        }

        /**
         * Unified consume endpoint: accepts either a 6-digit code or a
         * magic-link token. Returns a real session + user profile.
         *
         * Phase 1: no TOTP branch. `totpRequired` stays false until Phase 4.
         */
        post("consume-challenge") {
            val req = runCatching { call.receive<ConsumeChallengeRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    apiError("challenge required", ApiErrors.VALIDATION_FAILED))

            val consumed = challengeService.consume(req.challenge)
                ?: return@post call.respond(HttpStatusCode.Unauthorized,
                    apiError("Invalid, expired, or already-used challenge", AuthErrors.AUTH_CHALLENGE_INVALID))

            val uuid = runCatching { UUID.fromString(consumed.uuid) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.InternalServerError,
                    apiError("Stored challenge has invalid uuid", ApiErrors.INTERNAL_ERROR))

            // Phase 2 will resolve real permissions from the Perms module here.
            val permissions = PermissionSet.EMPTY
            val loginMethod = when (consumed.kind) {
                ChallengeKind.CODE -> "code"
                ChallengeKind.MAGIC_LINK -> "magic_link"
            }

            val session = sessionService.issue(
                uuid = uuid,
                name = consumed.name,
                permissions = permissions,
                ip = call.request.local.remoteAddress,
                userAgent = call.request.headers["User-Agent"],
                loginMethod = loginMethod
            )

            call.respond(ConsumeChallengeResponse(
                token = session.rawToken,
                expiresAt = session.principal.expiresAt,
                user = UserInfo(
                    uuid = uuid.toString(),
                    name = consumed.name,
                    permissions = permissions.asSet().toList(),
                    isAdmin = permissions.has(PermissionSet.ADMIN_NODE),
                    totpEnabled = false  // Phase 4
                ),
                totpRequired = false
            ))
        }

        post("logout") {
            val raw = extractBearerToken(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized,
                    apiError("Missing session token", ApiErrors.UNAUTHORIZED))
            val ok = sessionService.revoke(raw)
            call.respond(ApiMessage(success = ok, message = if (ok) "Session revoked" else "No such session"))
        }

        get("me") {
            val raw = extractBearerToken(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized,
                    apiError("Missing session token", ApiErrors.UNAUTHORIZED))
            val principal = sessionService.validate(raw)
                ?: return@get call.respond(HttpStatusCode.Unauthorized,
                    apiError("Invalid or expired session", AuthErrors.AUTH_SESSION_INVALID))

            call.respond(UserInfo(
                uuid = principal.uuid.toString(),
                name = principal.name,
                permissions = principal.permissions.asSet().toList(),
                isAdmin = principal.permissions.has(PermissionSet.ADMIN_NODE),
                totpEnabled = false  // Phase 4
            ))
        }
    }
}

private fun extractBearerToken(call: io.ktor.server.application.ApplicationCall): String? {
    val header = call.request.headers["Authorization"] ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    return header.substring(7).trim().takeIf { it.isNotEmpty() }
}
