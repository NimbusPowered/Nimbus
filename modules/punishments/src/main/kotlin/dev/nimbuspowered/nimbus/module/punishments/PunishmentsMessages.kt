package dev.nimbuspowered.nimbus.module.punishments

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Message templates for punishment-related output shown to players.
 * Loaded from `config/modules/punishments/messages.toml`.
 *
 * Placeholders are simple `{name}` tokens:
 *   {type}, {target}, {issuer}, {reason}, {duration}, {expires}, {remaining}
 */
@Serializable
data class PunishmentsMessages(
    val ban: String =
        "&c&lYou are banned from the network\n" +
        "&7Reason: &f{reason}\n" +
        "&7Banned by: &f{issuer}\n" +
        "&7Appeal at our support portal.",
    val tempban: String =
        "&c&lYou are temporarily banned\n" +
        "&7Reason: &f{reason}\n" +
        "&7Banned by: &f{issuer}\n" +
        "&7Expires in: &f{remaining}",
    val ipban: String =
        "&c&lYour IP is banned from the network\n" +
        "&7Reason: &f{reason}",
    val mute: String =
        "&cYou are muted: &f{reason}",
    val tempmute: String =
        "&cYou are muted for &f{remaining}&c: &f{reason}",
    val kick: String =
        "&cKicked: &f{reason}",

    // Broadcast templates shown in console / audit
    @kotlinx.serialization.SerialName("broadcast_issued")
    val broadcastIssued: String =
        "&c[PUNISH] &f{target} &7was &c{type} &7by &f{issuer} &7({reason})",
    @kotlinx.serialization.SerialName("broadcast_revoked")
    val broadcastRevoked: String =
        "&a[PUNISH] &f{target} &7was un-{type} by &f{issuer}"
)

/**
 * Render a message template with placeholder substitution.
 *
 * @param template the raw template (with &-color codes)
 * @param record   the punishment record to derive placeholders from
 */
fun renderPunishmentMessage(template: String, record: PunishmentRecord): String {
    val remaining = record.expiresAt?.let {
        try {
            val exp = Instant.parse(it)
            val secs = java.time.Duration.between(Instant.now(), exp).seconds.coerceAtLeast(0)
            DurationParser.format(java.time.Duration.ofSeconds(secs))
        } catch (_: Exception) { "unknown" }
    } ?: "permanent"

    return template
        .replace("{type}", record.type.name.lowercase())
        .replace("{target}", record.targetName)
        .replace("{issuer}", record.issuerName)
        .replace("{reason}", record.reason.ifBlank { "No reason given" })
        .replace("{duration}", remaining)
        .replace("{expires}", record.expiresAt ?: "never")
        .replace("{remaining}", remaining)
        .replace("&", "\u00a7")
}

/**
 * Load or create `messages.toml` in the module's config dir.
 * If the file is missing or malformed, defaults are written back so the operator can edit them.
 */
object PunishmentsMessagesLoader {
    private val logger = LoggerFactory.getLogger(PunishmentsMessagesLoader::class.java)
    private const val FILE_NAME = "messages.toml"

    fun loadOrCreate(configDir: Path): PunishmentsMessages {
        val file = configDir.resolve(FILE_NAME)
        if (!Files.exists(file)) {
            writeDefault(file)
            return PunishmentsMessages()
        }
        return try {
            val text = Files.readString(file)
            Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true))
                .decodeFromString(PunishmentsMessages.serializer(), text)
        } catch (e: Exception) {
            logger.warn("Failed to parse {} — using defaults ({})", file, e.message)
            PunishmentsMessages()
        }
    }

    private fun writeDefault(file: Path) {
        Files.createDirectories(file.parent)
        val defaults = PunishmentsMessages()
        val content = buildString {
            appendLine("# Punishment messages — shown to players on kick / chat mute.")
            appendLine("# Use &-color codes (e.g. &c for red). Placeholders: {target} {issuer} {reason} {remaining} {expires}")
            appendLine()
            appendLine("ban = ${defaults.ban.toTomlString()}")
            appendLine("tempban = ${defaults.tempban.toTomlString()}")
            appendLine("ipban = ${defaults.ipban.toTomlString()}")
            appendLine("mute = ${defaults.mute.toTomlString()}")
            appendLine("tempmute = ${defaults.tempmute.toTomlString()}")
            appendLine("kick = ${defaults.kick.toTomlString()}")
            appendLine("broadcast_issued = ${defaults.broadcastIssued.toTomlString()}")
            appendLine("broadcast_revoked = ${defaults.broadcastRevoked.toTomlString()}")
        }
        Files.writeString(file, content)
    }

    private fun String.toTomlString(): String =
        "\"\"\"" + this.replace("\\", "\\\\").replace("\"\"\"", "\\\"\"\"") + "\"\"\""
}
