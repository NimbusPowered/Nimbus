package dev.nimbuspowered.nimbus.punishments.velocity;

import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player mute snapshot, populated eagerly on server connect / PUNISHMENT_ISSUED.
 *
 * Needed because we can't safely deny a signed chat message from an async task —
 * Velocity's signature verification races the deny result and the client trips
 * into "A Proxy Plugin caused an illegal protocol state". The chat handler must
 * read a ready answer synchronously; this cache is how we get one.
 *
 * Entries are keyed by (player, current-group, current-service) so a scoped
 * mute stops applying the moment the player hops to a different server. When
 * the player's current service changes we re-fetch to refresh the entry.
 */
public class MuteCache {

    /** Keyed by player UUID — one active mute per player (network-wide or scoped). */
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    /**
     * @return the cached mute record (with kickMessage), or null if the player
     *         isn't muted in the given context.
     */
    public JsonObject get(UUID uuid, String group, String service) {
        Entry e = entries.get(uuid);
        if (e == null) return null;
        // Exact scope match: if the cached entry was fetched for the player's
        // current server, use it; otherwise we'd rather miss than serve a stale
        // answer (the chat handler falls back to "allow" on miss).
        if (!equalOrBothNull(e.group, group) || !equalOrBothNull(e.service, service)) return null;
        return e.record;
    }

    /** Store the record (or null = clean) for a context. */
    public void put(UUID uuid, String group, String service, JsonObject record) {
        entries.put(uuid, new Entry(group, service, record));
    }

    public void invalidate(UUID uuid) {
        entries.remove(uuid);
    }

    private static boolean equalOrBothNull(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private record Entry(String group, String service, JsonObject record) {}
}
