package dev.nimbus.update

import dev.nimbus.NimbusVersion
import dev.nimbus.console.ConsoleFormatter
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Checks GitHub Releases for new Nimbus versions on startup.
 *
 * - Patch/Minor updates: auto-downloads the new JAR, stages it for swap on next start
 * - Major updates: prompts the user with y/N before downloading
 */
class UpdateChecker(
    private val baseDir: Path,
    private val repoOwner: String = "jonax1337",
    private val repoName: String = "Nimbus"
) {
    private val logger = LoggerFactory.getLogger(UpdateChecker::class.java)
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    data class VersionInfo(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : Comparable<VersionInfo> {
        override fun compareTo(other: VersionInfo): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }

        override fun toString(): String = "$major.$minor.$patch"

        companion object {
            fun parse(version: String): VersionInfo? {
                val cleaned = version.removePrefix("v").trim()
                val parts = cleaned.split(".")
                if (parts.size < 3) return null
                return try {
                    VersionInfo(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                } catch (_: NumberFormatException) {
                    null
                }
            }
        }
    }

    enum class UpdateType { PATCH, MINOR, MAJOR }

    data class UpdateResult(
        val currentVersion: VersionInfo,
        val latestVersion: VersionInfo,
        val type: UpdateType,
        val downloadUrl: String,
        val releaseUrl: String,
        val changelog: String
    )

    /**
     * Check for updates and handle them.
     * Returns true if the application should restart (JAR was updated).
     */
    suspend fun checkAndApply(): Boolean {
        val currentVersionStr = NimbusVersion.version
        if (currentVersionStr == "dev") {
            logger.debug("Running dev build, skipping update check")
            return false
        }

        val current = VersionInfo.parse(currentVersionStr)
        if (current == null) {
            logger.warn("Cannot parse current version '{}', skipping update check", currentVersionStr)
            return false
        }

        val update = checkForUpdate(current) ?: return false

        return when (update.type) {
            UpdateType.PATCH, UpdateType.MINOR -> {
                println()
                println(ConsoleFormatter.info("Update available: v${update.currentVersion} -> v${update.latestVersion} (${update.type.name.lowercase()})"))
                println(ConsoleFormatter.hint("  Downloading automatically..."))
                applyUpdate(update)
            }
            UpdateType.MAJOR -> {
                println()
                println(ConsoleFormatter.warn("Major update available: v${update.currentVersion} -> v${update.latestVersion}"))
                println(ConsoleFormatter.hint("  Release: ${update.releaseUrl}"))
                if (update.changelog.isNotEmpty()) {
                    println(ConsoleFormatter.hint("  Changelog:"))
                    update.changelog.lines().take(10).forEach { line ->
                        println(ConsoleFormatter.hint("    $line"))
                    }
                    if (update.changelog.lines().size > 10) {
                        println(ConsoleFormatter.hint("    ... (see release page for full changelog)"))
                    }
                }
                println()

                if (promptUpgrade()) {
                    applyUpdate(update)
                } else {
                    println(ConsoleFormatter.hint("  Update skipped. You can update later by restarting Nimbus."))
                    false
                }
            }
        }
    }

    private suspend fun checkForUpdate(current: VersionInfo): UpdateResult? {
        return try {
            val response = client.get("https://api.github.com/repos/$repoOwner/$repoName/releases/latest") {
                header("Accept", "application/vnd.github.v3+json")
                header("User-Agent", "Nimbus-Cloud/${NimbusVersion.version}")
            }

            if (response.status != HttpStatusCode.OK) {
                logger.debug("GitHub API returned {}, skipping update check", response.status)
                return null
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tagName = body["tag_name"]?.jsonPrimitive?.content ?: return null
            val latest = VersionInfo.parse(tagName) ?: return null

            if (latest <= current) {
                logger.debug("Nimbus is up to date (v{})", current)
                return null
            }

            // Find the JAR asset (nimbus-core-*-all.jar or nimbus-*.jar)
            val assets = body["assets"]?.jsonArray ?: return null
            val jarAsset = assets.firstOrNull { asset ->
                val name = asset.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                name.endsWith("-all.jar") || (name.startsWith("nimbus") && name.endsWith(".jar"))
            }

            val downloadUrl = jarAsset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
            if (downloadUrl == null) {
                logger.warn("No JAR asset found in release {}", tagName)
                return null
            }

            val releaseUrl = body["html_url"]?.jsonPrimitive?.content ?: ""
            val changelog = body["body"]?.jsonPrimitive?.content ?: ""

            val type = when {
                latest.major != current.major -> UpdateType.MAJOR
                latest.minor != current.minor -> UpdateType.MINOR
                else -> UpdateType.PATCH
            }

            UpdateResult(current, latest, type, downloadUrl, releaseUrl, changelog)
        } catch (e: Exception) {
            logger.debug("Update check failed: {}", e.message)
            null
        }
    }

    private suspend fun applyUpdate(update: UpdateResult): Boolean {
        return try {
            // Determine current JAR path
            val currentJar = resolveCurrentJar() ?: run {
                logger.warn("Cannot determine current JAR path, skipping auto-update")
                return false
            }

            val updateJar = currentJar.resolveSibling("nimbus-update.jar")
            val backupJar = currentJar.resolveSibling("nimbus-backup.jar")

            // Download new JAR
            print(ConsoleFormatter.hint("  Downloading v${update.latestVersion}... "))
            val response = client.get(update.downloadUrl) {
                header("User-Agent", "Nimbus-Cloud/${NimbusVersion.version}")
            }

            if (response.status != HttpStatusCode.OK) {
                println(ConsoleFormatter.error("failed (HTTP ${response.status})"))
                return false
            }

            withContext(Dispatchers.IO) {
                val bytes = response.readRawBytes()
                Files.write(updateJar, bytes)
            }
            println(ConsoleFormatter.success("done"))

            // Backup current JAR
            withContext(Dispatchers.IO) {
                if (Files.exists(currentJar)) {
                    Files.copy(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            // Swap: update -> current
            withContext(Dispatchers.IO) {
                Files.move(updateJar, currentJar, StandardCopyOption.REPLACE_EXISTING)
            }

            println(ConsoleFormatter.successLine("Updated to v${update.latestVersion} (backup: ${backupJar.fileName})"))
            println(ConsoleFormatter.warn("  Restart Nimbus to apply the update."))
            println()

            true
        } catch (e: Exception) {
            logger.error("Auto-update failed: {}", e.message)
            println(ConsoleFormatter.error("  Update failed: ${e.message}"))
            false
        }
    }

    private fun resolveCurrentJar(): Path? {
        return try {
            val uri = UpdateChecker::class.java.protectionDomain.codeSource.location.toURI()
            val path = Path.of(uri)
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) path else null
        } catch (_: Exception) {
            null
        }
    }

    private fun promptUpgrade(): Boolean {
        return try {
            val terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .build()
            val reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build()

            val hint = ConsoleFormatter.hint("[y/N]")
            val prompt = "  Upgrade now? $hint${ConsoleFormatter.hint(":")} "
            val answer = reader.readLine(prompt).trim().lowercase()
            terminal.close()

            answer == "y" || answer == "yes"
        } catch (_: Exception) {
            false
        }
    }

    fun close() {
        client.close()
    }
}
