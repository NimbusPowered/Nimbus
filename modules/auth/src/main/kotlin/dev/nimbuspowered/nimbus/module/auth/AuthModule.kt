package dev.nimbuspowered.nimbus.module.auth

import dev.nimbuspowered.nimbus.NimbusVersion
import dev.nimbuspowered.nimbus.database.DatabaseManager
import dev.nimbuspowered.nimbus.event.EventBus
import dev.nimbuspowered.nimbus.event.NimbusEvent
import dev.nimbuspowered.nimbus.module.AuthLevel
import dev.nimbuspowered.nimbus.module.ModuleContext
import dev.nimbuspowered.nimbus.module.NimbusModule
import dev.nimbuspowered.nimbus.module.SessionValidator
import dev.nimbuspowered.nimbus.module.auth.migrations.AuthV8000_Sessions
import dev.nimbuspowered.nimbus.module.auth.migrations.AuthV8001_Challenges
import dev.nimbuspowered.nimbus.module.auth.migrations.AuthV8002_Totp
import dev.nimbuspowered.nimbus.module.auth.migrations.AuthV8003_RecoveryCodes
import dev.nimbuspowered.nimbus.module.auth.routes.authRoutes
import dev.nimbuspowered.nimbus.module.auth.service.LoginChallengeService
import dev.nimbuspowered.nimbus.module.auth.service.PermissionResolver
import dev.nimbuspowered.nimbus.module.auth.service.SessionService
import dev.nimbuspowered.nimbus.module.service
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Dashboard authentication + session management module.
 *
 * Phase 1 scope:
 *   - Unified `dashboard_login_challenges` schema + sessions + TOTP tables
 *   - 6-digit code and magic-link challenge issuance + consumption
 *   - Session issue / validate / revoke backed by `sha256(token)` in DB
 *   - `AuthPrincipal` sealed class + `hasPermission` extension (in modules-api)
 *
 * Phase 2 will wire `PermissionResolver` into the Perms module and add the
 * `requirePermission()` guards on every existing core route.
 */
class AuthModule : NimbusModule {
    override val id = "auth"
    override val name = "Auth"
    override val version: String get() = NimbusVersion.version
    override val description = "Dashboard authentication and role-based access control"

    private val logger = LoggerFactory.getLogger(AuthModule::class.java)

    @Volatile
    private lateinit var authConfig: AuthConfig

    private lateinit var configStore: AuthConfigStore
    private lateinit var challengeService: LoginChallengeService
    private lateinit var sessionService: SessionService
    private lateinit var permissionResolver: PermissionResolver
    private lateinit var context: ModuleContext

    override suspend fun init(context: ModuleContext) {
        this.context = context
        val db = context.service<DatabaseManager>()
            ?: error("Auth module requires DatabaseManager")

        configStore = AuthConfigStore(
            moduleDir = context.moduleConfigDir(id),
            baseDir = context.baseDir
        )
        authConfig = configStore.loadOrCreate()
        // Ensure session encryption key exists (used by TOTP in Phase 4).
        // Generated now so the file is in place before 2FA work begins.
        configStore.ensureKey(authConfig)

        context.registerMigrations(listOf(
            AuthV8000_Sessions,
            AuthV8001_Challenges,
            AuthV8002_Totp,
            AuthV8003_RecoveryCodes
        ))

        val configSupplier = { authConfig }
        challengeService = LoginChallengeService(db, configSupplier)
        sessionService = SessionService(db, configSupplier)
        permissionResolver = PermissionResolver(context)

        context.registerRoutes(
            block = { authRoutes(challengeService, sessionService, permissionResolver, configSupplier) },
            auth = AuthLevel.NONE  // Endpoints handle their own auth (cluster/session bearer)
        )

        // Expose services so the core middleware (Phase 1.5) and future modules
        // can validate session tokens without reaching back into the DB themselves.
        context.registerService(LoginChallengeService::class.java, challengeService)
        context.registerService(SessionService::class.java, sessionService)
        // SessionValidator is the shared interface the core middleware reads —
        // by publishing an adapter the core can accept session tokens without
        // compile-time coupling to the auth module's concrete classes.
        context.registerService(SessionValidator::class.java, SessionValidator { raw ->
            sessionService.validate(raw)
        })

        logger.info("Auth module initialised (magic_link_enabled={})",
            authConfig.loginChallenge.magicLinkEnabled)
    }

    override suspend fun enable() {
        // Perms-module presence check. If perms is disabled we fall back to
        // token-only mode: MC login still issues sessions but with an empty
        // permission set (so only API-token admin holders reach any route).
        if (!permissionResolver.isPermsAvailable()) {
            logger.warn("Perms module is not loaded — dashboard RBAC unavailable. " +
                "API tokens still work as admin; MC-account sessions will have no permissions.")
            return
        }

        // Subscribe to Perms mutation events so dashboard sessions pick up
        // group/node changes without requiring a re-login. We update the
        // cached permissions_snapshot for every active session of the
        // affected UUID, and also refresh every session whenever a group
        // definition changes (since any member of that group is affected).
        val eventBus = context.service<EventBus>() ?: run {
            logger.warn("EventBus not available — permission refresh on mutation disabled.")
            return
        }

        context.scope.launch {
            eventBus.subscribe().collect { event ->
                if (event !is NimbusEvent.ModuleEvent || event.moduleId != "perms") return@collect
                try {
                    handlePermsEvent(event)
                } catch (e: Exception) {
                    logger.warn("Failed to handle perms event {}: {}", event.type, e.message)
                }
            }
        }
    }

    /**
     * Refresh cached session permissions in response to a perms mutation.
     *
     * - PLAYER_* events carry a specific UUID → refresh just that user.
     * - PERMISSION_GROUP_* / PERMISSION_TRACK_* events affect every group
     *   member → refresh every active session (cheap: one indexed UPDATE).
     */
    private suspend fun handlePermsEvent(event: NimbusEvent.ModuleEvent) {
        when (event.type) {
            "PLAYER_PERMISSIONS_UPDATED",
            "PLAYER_PROMOTED",
            "PLAYER_DEMOTED" -> {
                val uuid = event.data["uuid"] ?: return
                val fresh = permissionResolver.resolve(uuid)
                val updated = sessionService.refreshPermissionsFor(uuid, fresh)
                if (updated > 0) {
                    logger.debug("Refreshed permissions_snapshot for {} active session(s) of {}", updated, uuid)
                }
            }
            "PERMISSION_GROUP_CREATED",
            "PERMISSION_GROUP_UPDATED",
            "PERMISSION_GROUP_DELETED",
            "PERMISSION_TRACK_CREATED",
            "PERMISSION_TRACK_DELETED" -> {
                // Group-scope change — recompute every active session so any
                // affected member's cached snapshot stays current. The perms
                // resolver is an in-memory lookup so this is cheap.
                sessionService.refreshAllActive { uuid -> permissionResolver.resolve(uuid) }
            }
        }
    }

    override fun disable() {
        // Services are stateless in Phase 1; nothing to tear down.
    }

    /** Test / introspection accessor. */
    fun currentConfig(): AuthConfig = authConfig
}
