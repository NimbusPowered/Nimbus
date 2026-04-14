package dev.nimbuspowered.nimbus.module.punishments

import java.time.Duration

/**
 * Parses human-friendly duration strings used by punishment commands.
 *
 * Accepted formats:
 * - `perm`, `permanent`, `never`, `0` → null (permanent)
 * - Suffixed numbers: `30s`, `15m`, `2h`, `7d`, `4w`, `6mo`, `1y`
 * - Combined: `1d12h`, `2h30m`
 *
 * Returns `null` for permanent, `Duration` otherwise.
 */
object DurationParser {

    private val pattern = Regex("(\\d+)(mo|[smhdwy])", RegexOption.IGNORE_CASE)

    /**
     * @throws IllegalArgumentException if the input has no recognizable units
     *         (to distinguish from an intentionally permanent `perm` value)
     */
    fun parse(input: String): Duration? {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Duration is empty")
        if (trimmed == "perm" || trimmed == "permanent" || trimmed == "never" || trimmed == "0") return null

        var total = Duration.ZERO
        var matched = false
        for (match in pattern.findAll(trimmed)) {
            matched = true
            val amount = match.groupValues[1].toLong()
            val unit = match.groupValues[2]
            total = total.plus(when (unit) {
                "s" -> Duration.ofSeconds(amount)
                "m" -> Duration.ofMinutes(amount)
                "h" -> Duration.ofHours(amount)
                "d" -> Duration.ofDays(amount)
                "w" -> Duration.ofDays(amount * 7)
                "mo" -> Duration.ofDays(amount * 30)
                "y" -> Duration.ofDays(amount * 365)
                else -> throw IllegalArgumentException("Unknown duration unit '$unit'")
            })
        }
        if (!matched) throw IllegalArgumentException("Unparseable duration '$input' (try e.g. 30m, 7d, perm)")
        return total
    }

    /** Format a duration for display: `7d 12h`, `30m`, `permanent`. */
    fun format(duration: Duration?): String {
        if (duration == null) return "permanent"
        val days = duration.toDays()
        val hours = duration.minusDays(days).toHours()
        val minutes = duration.minusDays(days).minusHours(hours).toMinutes()
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (parts.isEmpty()) parts.add("${duration.seconds}s")
        return parts.joinToString(" ")
    }
}
