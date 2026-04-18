package dev.nimbuspowered.nimbus.module.auth.service

import dev.nimbuspowered.nimbus.module.ModuleContext
import dev.nimbuspowered.nimbus.module.PermissionSet
import dev.nimbuspowered.nimbus.module.perms.PermissionManager
import org.slf4j.LoggerFactory

/**
 * Bridges the Auth module to the Perms module.
 *
 * Phase 2: resolves the effective permission set for a UUID by asking the
 * Perms module's [PermissionManager] for its flattened node list (inherited
 * groups + tracks + contextual perms with no server/world scope).
 *
 * If the Perms module isn't loaded (opt-out or missing JAR), the resolver
 * falls back to [PermissionSet.EMPTY] — the Auth module logs a warning and
 * keeps the dashboard in token-only mode (MC login returns empty permissions,
 * so only holders of `nimbus.dashboard.admin` via API token can reach anything).
 */
class PermissionResolver(private val context: ModuleContext) {

    private val logger = LoggerFactory.getLogger(PermissionResolver::class.java)

    /**
     * Locate the Perms module's manager lazily. We re-resolve on every call
     * because modules can load/unload at runtime and we don't want to cache a
     * stale reference.
     */
    private fun permissionManager(): PermissionManager? =
        context.getService(PermissionManager::class.java)

    /** `true` when the Perms module is loaded and its manager is reachable. */
    fun isPermsAvailable(): Boolean = permissionManager() != null

    /**
     * Resolve the full set of permission nodes for the given MC UUID, ignoring
     * server/world context (dashboard operates network-wide).
     *
     * Returns [PermissionSet.EMPTY] if Perms is unavailable or the player has
     * no assignments yet — callers should treat that as "no dashboard access"
     * unless the principal is an API-token (which bypasses permission checks).
     */
    fun resolve(uuid: String): PermissionSet {
        val mgr = permissionManager() ?: run {
            logger.debug("Perms module not available — returning empty permission set for {}", uuid)
            return PermissionSet.EMPTY
        }
        return try {
            val nodes = mgr.getEffectivePermissions(uuid, server = null, world = null)
            PermissionSet(nodes)
        } catch (e: Exception) {
            logger.warn("Failed to resolve permissions for {}: {}", uuid, e.message)
            PermissionSet.EMPTY
        }
    }
}
