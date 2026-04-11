package dev.nimbuspowered.nimbus.api.routes

import dev.nimbuspowered.nimbus.api.ApiErrors
import dev.nimbuspowered.nimbus.api.NimbusApi
import dev.nimbuspowered.nimbus.api.apiError
import dev.nimbuspowered.nimbus.cluster.ClusterServer
import dev.nimbuspowered.nimbus.config.ClusterConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.net.InetAddress

private val logger = LoggerFactory.getLogger("ClusterBootstrapRoutes")

/**
 * Agent-facing bootstrap endpoint. Returns the controller's TLS cert material so the
 * agent can pin it before the first wss:// connection. Gated on the cluster token
 * (shared secret distributed to agents), not the REST API token.
 *
 * Must be reachable over plain HTTP on the REST API port — otherwise we have a
 * chicken-and-egg problem where the agent can't trust the TLS cert until it has
 * fetched it. Cert material is public-key, not secret.
 */
fun Route.clusterBootstrapRoutes(
    clusterConfig: ClusterConfig,
    clusterServer: ClusterServer?
) {
    get("/api/cluster/bootstrap") {
        if (!clusterConfig.enabled) {
            return@get call.respond(
                HttpStatusCode.ServiceUnavailable,
                apiError("Cluster mode is not enabled", ApiErrors.NOT_FOUND)
            )
        }
        if (clusterConfig.token.isBlank()) {
            return@get call.respond(
                HttpStatusCode.ServiceUnavailable,
                apiError("No cluster token configured", ApiErrors.NOT_FOUND)
            )
        }

        val header = call.request.header(HttpHeaders.Authorization) ?: ""
        val presented = if (header.startsWith("Bearer ", ignoreCase = true)) {
            header.substring(7).trim()
        } else ""
        if (presented.isEmpty() || !NimbusApi.timingSafeEquals(presented, clusterConfig.token)) {
            return@get call.respond(
                HttpStatusCode.Unauthorized,
                apiError("Invalid or missing cluster token", ApiErrors.FORBIDDEN)
            )
        }

        val info = clusterServer?.certInfo
        if (info == null) {
            return@get call.respond(
                HttpStatusCode.ServiceUnavailable,
                apiError(
                    "Cluster server not running or TLS not enabled",
                    ApiErrors.NOT_FOUND
                )
            )
        }

        val host = resolvePublicHost(clusterConfig)
        val wsUrl = "wss://$host:${clusterConfig.agentPort}/cluster"

        logger.info("Bootstrap request served (host={}, fingerprint={})", host, info.fingerprint.take(23))

        call.respond(
            BootstrapResponse(
                fingerprint = info.fingerprint,
                certPem = info.pemEncoded,
                wsUrl = wsUrl,
                validUntil = info.validUntil,
                sans = info.sans
            )
        )
    }
}

private fun resolvePublicHost(cfg: ClusterConfig): String {
    if (cfg.publicHost.isNotBlank()) return cfg.publicHost
    if (cfg.bind.isNotBlank() && cfg.bind != "0.0.0.0") return cfg.bind
    return try {
        InetAddress.getLocalHost().hostName
    } catch (_: Exception) {
        "127.0.0.1"
    }
}

@Serializable
data class BootstrapResponse(
    val fingerprint: String,
    val certPem: String,
    val wsUrl: String,
    val validUntil: String,
    val sans: List<String>
)
