package dev.nimbuspowered.nimbus.module.auth

import dev.nimbuspowered.nimbus.NimbusVersion
import dev.nimbuspowered.nimbus.database.DatabaseManager
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
import dev.nimbuspowered.nimbus.module.auth.service.SessionService
import dev.nimbuspowered.nimbus.module.service
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

    override suspend fun init(context: ModuleContext) {
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

        context.registerRoutes(
            block = { authRoutes(challengeService, sessionService, configSupplier) },
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
        // Nothing to start in Phase 1. Phase 2 will hook PermissionsChanged events here.
    }

    override fun disable() {
        // Services are stateless in Phase 1; nothing to tear down.
    }

    /** Test / introspection accessor. */
    fun currentConfig(): AuthConfig = authConfig
}
