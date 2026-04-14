package dev.nimbuspowered.nimbus.module.punishments

import dev.nimbuspowered.nimbus.NimbusVersion
import dev.nimbuspowered.nimbus.config.NimbusConfig
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.BOLD
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.DIM
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.RESET
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.error
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.info
import dev.nimbuspowered.nimbus.console.ConsoleFormatter.success
import dev.nimbuspowered.nimbus.database.DatabaseManager
import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.module.DashboardConfig
import dev.nimbuspowered.nimbus.module.DashboardSection
import dev.nimbuspowered.nimbus.module.ModuleContext
import dev.nimbuspowered.nimbus.module.NimbusModule
import dev.nimbuspowered.nimbus.module.PluginDeployment
import dev.nimbuspowered.nimbus.module.punishments.commands.PlayerResolver
import dev.nimbuspowered.nimbus.module.punishments.commands.PunishCommand
import dev.nimbuspowered.nimbus.module.punishments.migrations.PunishmentsV1_Baseline
import dev.nimbuspowered.nimbus.module.punishments.routes.punishmentRoutes
import dev.nimbuspowered.nimbus.module.service
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PunishmentsModule : NimbusModule {
    override val id = "punishments"
    override val name = "Punishments"
    override val version: String get() = NimbusVersion.version
    override val description = "Network-wide bans, mutes, kicks, and warnings"

    override val dashboardConfig = DashboardConfig(
        icon = "Gavel",
        apiPrefix = "/api/punishments",
        sections = listOf(
            DashboardSection("Active", "table", "?active=true"),
            DashboardSection("All", "table", "?active=false")
        )
    )

    private lateinit var manager: PunishmentManager
    private lateinit var ctx: ModuleContext
    private lateinit var messages: PunishmentsMessages

    override suspend fun init(context: ModuleContext) {
        ctx = context
        val db = context.service<DatabaseManager>()!!
        val eventBus = context.service<EventBus>()!!
        val config = context.service<NimbusConfig>()

        context.registerMigrations(listOf(PunishmentsV1_Baseline))
        manager = PunishmentManager(db)

        messages = PunishmentsMessagesLoader.loadOrCreate(context.moduleConfigDir(id))

        if (config?.punishments?.deployPlugin != false) {
            context.registerPluginDeployment(PluginDeployment(
                resourcePath = "plugins/nimbus-punishments.jar",
                fileName = "nimbus-punishments.jar",
                displayName = "NimbusPunishments"
            ))
        }

        registerEventFormatters(context)

        val resolver = buildPlayerResolver(context)
        context.registerCommand(PunishCommand(manager, eventBus, resolver))
        context.registerRoutes({ punishmentRoutes(manager, eventBus) })

        context.registerCompleter("punish") { args, prefix ->
            when (args.size) {
                1 -> listOf("ban", "tempban", "ipban", "mute", "tempmute", "kick", "warn",
                            "unban", "unmute", "history", "list")
                    .filter { it.startsWith(prefix, ignoreCase = true) }
                else -> emptyList()
            }
        }

        // Expose manager so other modules (e.g. web dashboard bridges) can query cached state
        context.registerService(PunishmentManager::class.java, manager)
    }

    /**
     * Build a [PlayerResolver] backed by the core's player cache if available.
     *
     * Priority:
     *   1. An online player registry (ServiceRegistry / PlayerTracker) — fastest
     *   2. Direct lookup of the input as a UUID — for admins who type UUIDs
     *   3. Fallback: treat the input as a name and store a zero-uuid marker
     *      (still creates an audit trail, but won't block logins until the real UUID arrives)
     */
    private fun buildPlayerResolver(context: ModuleContext): PlayerResolver {
        return PlayerResolver { input ->
            // UUID input — standard length + hyphens
            if (isUuid(input)) return@PlayerResolver input to input.take(16)
            // TODO: query core's player registry via reflection-safe service lookup once available.
            null
        }
    }

    private fun isUuid(s: String): Boolean =
        s.length == 36 && s[8] == '-' && s[13] == '-' && s[18] == '-' && s[23] == '-'

    private fun registerEventFormatters(context: ModuleContext) {
        context.registerEventFormatter("PUNISHMENT_ISSUED") { data ->
            val type = data["type"] ?: "?"
            val target = data["target"] ?: "?"
            val issuer = data["issuer"] ?: "?"
            val reason = data["reason"]?.takeIf { it.isNotBlank() } ?: "no reason"
            "${error("⚠ PUNISH")} $BOLD$type$RESET on $BOLD$target$RESET $DIM by $issuer — $reason$RESET"
        }
        context.registerEventFormatter("PUNISHMENT_REVOKED") { data ->
            val type = data["type"] ?: "?"
            val target = data["target"] ?: "?"
            val revokedBy = data["revokedBy"] ?: "?"
            "${success("✓ UNPUNISH")} $BOLD$target$RESET ($type) $DIM by $revokedBy$RESET"
        }
        context.registerEventFormatter("PUNISHMENT_EXPIRED") { data ->
            val target = data["target"] ?: "?"
            val type = data["type"] ?: "?"
            "${info("⏱ EXPIRED")} $type on $BOLD$target$RESET"
        }
    }

    override suspend fun enable() {
        manager.init()
        val config = ctx.service<NimbusConfig>()
        val interval = (config?.punishments?.expiryCheckInterval ?: 30).coerceAtLeast(5) * 1000L
        val eventBus = ctx.service<EventBus>()!!

        ctx.scope.launch {
            while (isActive) {
                delay(interval)
                try {
                    val expired = manager.expireOverdue()
                    expired.forEach { eventBus.emit(PunishmentsEvents.expired(it)) }
                } catch (_: Exception) {}
            }
        }
    }

    override fun disable() {}

    /** Expose the loaded messages so other parts (e.g. Bridge-facing endpoint) can read them. */
    fun messages(): PunishmentsMessages = messages
}
