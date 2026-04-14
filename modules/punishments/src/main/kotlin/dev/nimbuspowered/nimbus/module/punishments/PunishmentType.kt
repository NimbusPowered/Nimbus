package dev.nimbuspowered.nimbus.module.punishments

/**
 * Types of punishments supported by the module.
 *
 * - [BAN], [TEMPBAN], [IPBAN]: prevent login at the proxy layer
 * - [MUTE], [TEMPMUTE]: prevent chat at the backend layer
 * - [KICK]: one-shot, recorded for history but not enforced
 * - [WARN]: recorded only, no enforcement
 */
enum class PunishmentType {
    BAN,
    TEMPBAN,
    IPBAN,
    MUTE,
    TEMPMUTE,
    KICK,
    WARN;

    /** True if the punishment prevents proxy login (checked on PreLogin). */
    fun blocksLogin(): Boolean = this == BAN || this == TEMPBAN || this == IPBAN

    /** True if the punishment prevents chat (checked on chat event). */
    fun blocksChat(): Boolean = this == MUTE || this == TEMPMUTE

    /** True if the punishment has a time-limited duration. */
    fun isTemporary(): Boolean = this == TEMPBAN || this == TEMPMUTE

    /** True if the punishment can be actively revoked (unban/unmute). */
    fun isRevocable(): Boolean = blocksLogin() || blocksChat()
}
