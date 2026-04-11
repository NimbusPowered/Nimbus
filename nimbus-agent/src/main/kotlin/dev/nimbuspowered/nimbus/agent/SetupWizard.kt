package dev.nimbuspowered.nimbus.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path

class SetupWizard(private val baseDir: Path) {
    private val logger = LoggerFactory.getLogger(SetupWizard::class.java)

    fun run(): AgentConfig? {
        val reader = BufferedReader(InputStreamReader(System.`in`))

        println()
        println("  Nimbus Agent — First Run Setup")
        println("  ──────────────────────────────")
        println()

        // Step 1 + 2: REST URL and token
        val (restUrl, token) = promptRestAndToken(reader) ?: return null

        // Step 3 + 4: bootstrap fetch + trust confirmation
        val bootstrap = tryBootstrap(reader, restUrl, token)
        val controllerUrl: String
        val trustedFingerprint: String

        if (bootstrap != null) {
            println()
            println("  Controller certificate:")
            println("    Fingerprint: ${bootstrap.fingerprint}")
            println("    Valid until: ${bootstrap.validUntil}")
            if (bootstrap.sans.isNotEmpty()) {
                println("    SANs:        ${bootstrap.sans.joinToString(", ")}")
            }
            println()
            print("  Trust this controller? [Y/n]: ")
            val confirm = reader.readLine()?.trim()?.lowercase()
            if (confirm == "n" || confirm == "no") {
                println("  Setup cancelled.")
                return null
            }
            controllerUrl = bootstrap.wsUrl
            trustedFingerprint = bootstrap.fingerprint
            println("  ✓ Controller cert pinned.")
        } else {
            // Fallback: manual WebSocket URL entry
            println()
            println("  ⚠ Could not auto-fetch the controller cert.")
            println("    Falling back to manual WebSocket URL entry.")
            println("    You will need to configure trust manually in agent.toml:")
            println("      - Set trusted_fingerprint (preferred), OR")
            println("      - Set truststore_path/truststore_password (advanced), OR")
            println("      - Set tls_verify = false (DEV ONLY)")
            println()
            print("  Controller WebSocket URL [wss://127.0.0.1:8443/cluster]: ")
            val raw = reader.readLine()?.trim()?.ifEmpty { "wss://127.0.0.1:8443/cluster" }
                ?: return null
            controllerUrl = if (raw.startsWith("ws://")) "wss://" + raw.removePrefix("ws://") else raw
            trustedFingerprint = ""
        }

        // Remaining prompts
        val defaultName = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "worker-1" }
        print("  Node Name [$defaultName]: ")
        val nodeName = reader.readLine()?.trim()?.ifEmpty { defaultName } ?: defaultName

        val defaultMemory = autoDetectMemory()
        print("  Max Memory [$defaultMemory]: ")
        val maxMemory = reader.readLine()?.trim()?.ifEmpty { defaultMemory } ?: defaultMemory

        val defaultServices = autoDetectMaxServices()
        print("  Max Services [$defaultServices]: ")
        val maxServices = reader.readLine()?.trim()?.toIntOrNull() ?: defaultServices

        println()
        println("  Configuration:")
        println("    Controller:  $controllerUrl")
        println("    Node Name:   $nodeName")
        println("    Max Memory:  $maxMemory")
        println("    Max Services: $maxServices")
        if (trustedFingerprint.isNotBlank()) {
            println("    TLS Trust:   pinned fingerprint")
        } else {
            println("    TLS Trust:   unconfigured (edit agent.toml before starting!)")
        }
        println()

        return AgentConfig(
            agent = AgentDefinition(
                controller = controllerUrl,
                token = token,
                nodeName = nodeName,
                maxMemory = maxMemory,
                maxServices = maxServices,
                trustedFingerprint = trustedFingerprint
            )
        )
    }

    private fun promptRestAndToken(reader: BufferedReader): Pair<String, String>? {
        print("  Controller REST URL [http://127.0.0.1:8080]: ")
        val rest = reader.readLine()?.trim()?.ifEmpty { "http://127.0.0.1:8080" }?.trimEnd('/') ?: return null

        print("  Auth Token (cluster token from controller): ")
        val token = reader.readLine()?.trim() ?: return null
        if (token.isEmpty()) {
            println("  Error: Token is required.")
            return null
        }
        return rest to token
    }

    /**
     * Attempts GET /api/cluster/bootstrap on the controller with the cluster token.
     * Returns null on any failure (logged with an actionable hint).
     *
     * Transient errors (controller still booting) auto-retry silently with a 1s
     * backoff for the first [AUTO_RETRY_ATTEMPTS] tries, then fall back to an
     * interactive prompt. Keeps the wizard off the REST API rate limiter.
     */
    private fun tryBootstrap(reader: BufferedReader, restUrl: String, token: String): BootstrapInfo? {
        var attempts = 0
        while (true) {
            attempts++
            val result = runBlocking {
                val client = HttpClient(CIO) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                    engine { requestTimeout = 10_000 }
                }
                try {
                    val response: HttpResponse = client.get("$restUrl/api/cluster/bootstrap") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    when (response.status.value) {
                        200 -> BootstrapResult.Success(response.body<BootstrapInfo>())
                        401, 403 -> BootstrapResult.BadToken
                        404 -> BootstrapResult.NotSupported
                        503 -> BootstrapResult.ClusterDown(response.bodyAsText())
                        else -> BootstrapResult.OtherError("HTTP ${response.status.value}")
                    }
                } catch (e: java.net.ConnectException) {
                    BootstrapResult.Unreachable(e.message ?: "connection refused")
                } catch (e: Exception) {
                    BootstrapResult.OtherError(e.message ?: e::class.simpleName ?: "unknown")
                } finally {
                    client.close()
                }
            }

            when (result) {
                is BootstrapResult.Success -> return result.info
                BootstrapResult.BadToken -> {
                    println("  ✗ Wrong token. Check 'cluster token' on the controller.")
                    return null
                }
                BootstrapResult.NotSupported -> {
                    println("  ✗ Controller does not support /api/cluster/bootstrap (old version?).")
                    return null
                }
                is BootstrapResult.ClusterDown -> {
                    if (attempts <= AUTO_RETRY_ATTEMPTS) {
                        println("  … cluster server still booting, retrying in 1s (attempt $attempts/$AUTO_RETRY_ATTEMPTS)")
                        Thread.sleep(1000)
                        continue
                    }
                    println("  ✗ Controller reached, but cluster server is not ready:")
                    println("    ${result.body.take(200)}")
                    print("    Try again? [Y/n]: ")
                    val retry = reader.readLine()?.trim()?.lowercase()
                    if (retry == "n" || retry == "no") return null
                }
                is BootstrapResult.Unreachable -> {
                    if (attempts <= AUTO_RETRY_ATTEMPTS) {
                        println("  … controller not reachable, retrying in 1s (attempt $attempts/$AUTO_RETRY_ATTEMPTS)")
                        Thread.sleep(1000)
                        continue
                    }
                    println("  ✗ Could not reach controller at $restUrl: ${result.reason}")
                    print("    Try again? [Y/n]: ")
                    val retry = reader.readLine()?.trim()?.lowercase()
                    if (retry == "n" || retry == "no") return null
                }
                is BootstrapResult.OtherError -> {
                    println("  ✗ Bootstrap failed: ${result.reason}")
                    return null
                }
            }
        }
    }

    private companion object {
        // ~30s of silent retries covers both controller cold-start + cluster boot.
        const val AUTO_RETRY_ATTEMPTS = 30
    }

    @Serializable
    private data class BootstrapInfo(
        val fingerprint: String,
        val certPem: String = "",
        val wsUrl: String,
        val validUntil: String,
        val sans: List<String> = emptyList()
    )

    private sealed class BootstrapResult {
        data class Success(val info: BootstrapInfo) : BootstrapResult()
        object BadToken : BootstrapResult()
        object NotSupported : BootstrapResult()
        data class ClusterDown(val body: String) : BootstrapResult()
        data class Unreachable(val reason: String) : BootstrapResult()
        data class OtherError(val reason: String) : BootstrapResult()
    }
}
