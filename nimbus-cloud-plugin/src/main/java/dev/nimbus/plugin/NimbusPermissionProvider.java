package dev.nimbus.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity PermissionProvider that loads permissions from the Nimbus Core API.
 * Caches effective permissions per player for fast lookups.
 */
public class NimbusPermissionProvider implements PermissionProvider {

    private final NimbusApiClient apiClient;
    private final Logger logger;
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public NimbusPermissionProvider(NimbusApiClient apiClient, Logger logger) {
        this.apiClient = apiClient;
        this.logger = logger;
    }

    @Override
    public PermissionFunction createFunction(PermissionSubject subject) {
        if (!(subject instanceof Player player)) {
            return PermissionFunction.ALWAYS_UNDEFINED;
        }

        // Load permissions async on first access, return UNDEFINED until loaded
        UUID uuid = player.getUniqueId();
        loadPermissions(uuid);

        return permission -> {
            Set<String> perms = cache.get(uuid);
            if (perms == null) return Tristate.UNDEFINED;
            return matchesPermission(perms, permission) ? Tristate.TRUE : Tristate.FALSE;
        };
    }

    /**
     * Registers the player with the API (saves UUID + name) and loads their effective permissions.
     */
    public void loadPermissions(UUID uuid, String playerName) {
        JsonObject body = new JsonObject();
        body.addProperty("name", playerName);

        // PUT registers the player and returns effective permissions in one call
        apiClient.put("/api/permissions/players/" + uuid, body)
            .thenAccept(result -> {
                if (!result.isSuccess()) {
                    logger.warn("Failed to load permissions for {}: HTTP {}", playerName, result.statusCode());
                    return;
                }
                cacheFromResponse(uuid, playerName, result);
            })
            .exceptionally(e -> {
                logger.warn("Failed to load permissions for {}: {}", playerName, e.getMessage());
                return null;
            });
    }

    /**
     * Loads permissions from the API without registering (for refresh).
     */
    public void loadPermissions(UUID uuid) {
        apiClient.get("/api/permissions/players/" + uuid)
            .thenAccept(result -> {
                if (!result.isSuccess()) {
                    logger.warn("Failed to load permissions for {}: HTTP {}", uuid, result.statusCode());
                    return;
                }
                cacheFromResponse(uuid, uuid.toString(), result);
            })
            .exceptionally(e -> {
                logger.warn("Failed to load permissions for {}: {}", uuid, e.getMessage());
                return null;
            });
    }

    private void cacheFromResponse(UUID uuid, String label, NimbusApiClient.ApiResult result) {
        try {
            JsonObject json = result.asJson();
            JsonArray permsArray = json.getAsJsonArray("effectivePermissions");
            Set<String> permissions = new HashSet<>();
            for (JsonElement elem : permsArray) {
                permissions.add(elem.getAsString());
            }
            cache.put(uuid, permissions);
            logger.debug("Loaded {} permissions for {}", permissions.size(), label);
        } catch (Exception e) {
            logger.warn("Failed to parse permissions for {}: {}", label, e.getMessage());
        }
    }

    /**
     * Invalidates the cached permissions for a player, forcing a reload on next check.
     */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Invalidates all cached permissions.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Reloads permissions for a specific player (invalidate + load).
     */
    public void refresh(UUID uuid) {
        invalidate(uuid);
        loadPermissions(uuid);
    }

    /**
     * Gets the cached permissions for a player (for debugging).
     */
    public Set<String> getCachedPermissions(UUID uuid) {
        return cache.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * Checks if a specific permission is matched by the set of effective permissions,
     * including wildcard support.
     */
    static boolean matchesPermission(Set<String> effective, String permission) {
        if (effective.contains(permission)) return true;
        if (effective.contains("*")) return true;

        String[] parts = permission.split("\\.");
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) prefix.append(".");
            prefix.append(parts[i]);

            // Check wildcard at this level: e.g., "nimbus.*" matches "nimbus.cloud.list"
            if (i < parts.length - 1) {
                String wildcard = prefix + ".*";
                if (effective.contains(wildcard)) return true;
            }
        }

        return false;
    }
}
