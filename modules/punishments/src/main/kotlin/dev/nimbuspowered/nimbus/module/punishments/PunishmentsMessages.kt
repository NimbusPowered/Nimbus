package dev.nimbuspowered.nimbus.module.punishments

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Message templates for punishment-related output shown to players.
 *
 * Persisted at `config/modules/punishments/messages.toml` and live-editable
 * through `PUT /api/punishments/messages` (→ Dashboard editor). A singleton
 * [PunishmentsMessagesStore] holds the active copy and handles atomic file
 * write-through so operators don't need to restart the controller.
 *
 * Placeholders (replaced in [renderPunishmentMessage]):
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
 * Outputs §-prefixed legacy color codes so Adventure / legacy chat both work.
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
 * Pick the right template for a given punishment type.
 * WARN falls through to the generic "ban" line since we don't normally display
 * warnings at the proxy — a client-side toast is enough.
 */
fun PunishmentsMessages.templateFor(type: PunishmentType): String = when (type) {
    PunishmentType.BAN -> ban
    PunishmentType.TEMPBAN -> tempban
    PunishmentType.IPBAN -> ipban
    PunishmentType.MUTE -> mute
    PunishmentType.TEMPMUTE -> tempmute
    PunishmentType.KICK -> kick
    PunishmentType.WARN -> kick
}

/**
 * Thread-safe holder for the active message set + file write-through.
 * One instance per module — exposed through the [PunishmentsModule] so routes
 * can read + update it without touching the filesystem directly.
 */
class PunishmentsMessagesStore(private val file: Path) {

    private val logger = LoggerFactory.getLogger(PunishmentsMessagesStore::class.java)
    private val ref = AtomicReference(PunishmentsMessages())

    fun current(): PunishmentsMessages = ref.get()

    /**
     * Replace the current message set and atomically rewrite messages.toml.
     * Returns the freshly stored value so callers can echo it back.
     */
    fun update(next: PunishmentsMessages): PunishmentsMessages {
        ref.set(next)
        persist(next)
        return next
    }

    fun loadOrCreate() {
        if (!Files.exists(file)) {
            persist(ref.get())
            return
        }
        try {
            val text = Files.readString(file)
            ref.set(Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true))
                .decodeFromString(PunishmentsMessages.serializer(), text))
        } catch (e: Exception) {
            logger.warn("Failed to parse {} — keeping defaults ({})", file, e.message)
        }
    }

    private fun persist(messages: PunishmentsMessages) {
        Files.createDirectories(file.parent)
        val content = buildString {
            appendLine("# Punishment messages — shown to players on kick / chat mute.")
            appendLine("# Use &-color codes (e.g. &c for red). Placeholders: {target} {issuer} {reason} {remaining} {expires} {type}")
            appendLine("# Edit here or via the Web Dashboard — both write to this file.")
            appendLine()
            appendLine("ban = ${messages.ban.toTomlString()}")
            appendLine("tempban = ${messages.tempban.toTomlString()}")
            appendLine("ipban = ${messages.ipban.toTomlString()}")
            appendLine("mute = ${messages.mute.toTomlString()}")
            appendLine("tempmute = ${messages.tempmute.toTomlString()}")
            appendLine("kick = ${messages.kick.toTomlString()}")
            appendLine("broadcast_issued = ${messages.broadcastIssued.toTomlString()}")
            appendLine("broadcast_revoked = ${messages.broadcastRevoked.toTomlString()}")
        }
        // Write atomically via a sibling temp file to avoid a half-written TOML
        // if the process dies mid-write (operator would lose all templates).
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, content)
        Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun String.toTomlString(): String =
        "\"\"\"" + this.replace("\\", "\\\\").replace("\"\"\"", "\\\"\"\"") + "\"\"\""
}
