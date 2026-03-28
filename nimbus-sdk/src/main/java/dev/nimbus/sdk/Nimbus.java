package dev.nimbus.sdk;

import dev.nimbus.sdk.event.TypedEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Main entry point for the Nimbus SDK.
 * <p>
 * Call {@link #init()} once on startup — everything else is available through this class.
 *
 * <pre>{@code
 * // In your plugin's onEnable():
 * Nimbus.init();
 * Nimbus.setState("WAITING");
 *
 * // When game starts:
 * Nimbus.setState("INGAME");
 *
 * // Route a player:
 * Nimbus.route("Steve", "BedWars", RoutingStrategy.LEAST_PLAYERS);
 *
 * // Listen to events:
 * Nimbus.on(ServiceReadyEvent.class, e -> ...);
 *
 * // Send message to another service:
 * Nimbus.message("Lobby-1", "game_ended", Map.of("winner", "Steve"));
 *
 * // Query (from cache, instant):
 * int players = Nimbus.players("BedWars");
 * List<NimbusService> servers = Nimbus.services("BedWars");
 * }</pre>
 */
public final class Nimbus {

    private static NimbusSelfService self;
    private static ServiceCache cache;
    private static PlayerTracker tracker;
    private static NimbusEventStream eventStream;
    private static ServiceRouter router;
    private static boolean initialized = false;

    private Nimbus() {}

    /**
     * Initialize the SDK. Auto-discovers service identity from JVM properties.
     * Starts the service cache and player tracker automatically.
     *
     * @throws IllegalStateException if not running in a Nimbus-managed service
     */
    public static void init() {
        if (initialized) return;

        self = NimbusSelfService.fromSystemProperties();
        cache = new ServiceCache(self.getClient());
        tracker = new PlayerTracker(self.getClient());
        router = new ServiceRouter(self.getClient());
        eventStream = self.getClient().createEventStream();

        cache.start();
        tracker.start();
        eventStream.connect();

        initialized = true;
    }

    /**
     * Initialize with explicit connection details (for external tools, not running on a Nimbus server).
     */
    public static void init(String apiUrl, String token) {
        if (initialized) return;

        NimbusClient client = new NimbusClient(apiUrl, token);
        cache = new ServiceCache(client);
        tracker = new PlayerTracker(client);
        router = new ServiceRouter(client);
        eventStream = client.createEventStream();

        cache.start();
        tracker.start();
        eventStream.connect();

        initialized = true;
    }

    /**
     * Check if running inside a Nimbus-managed service.
     */
    public static boolean isManaged() {
        return NimbusSelfService.isNimbusManaged();
    }

    /** Shut down all SDK components. Call in onDisable(). */
    public static void shutdown() {
        if (!initialized) return;
        if (eventStream != null) eventStream.close();
        if (cache != null) cache.close();
        if (tracker != null) tracker.close();
        initialized = false;
    }

    // ── Identity ──────────────────────────────────────────────────────

    /** This service's name (e.g. "BedWars-1"). */
    public static String name() {
        requireSelf();
        return self.getServiceName();
    }

    /** This service's group (e.g. "BedWars"). */
    public static String group() {
        requireSelf();
        return self.getGroupName();
    }

    // ── Custom State ──────────────────────────────────────────────────

    /** Set custom state (e.g. "WAITING", "INGAME", "ENDING"). */
    public static CompletableFuture<Void> setState(String state) {
        requireSelf();
        return self.setCustomState(state);
    }

    /** Clear custom state (server becomes routable again). */
    public static CompletableFuture<Void> clearState() {
        requireSelf();
        return self.clearCustomState();
    }

    // ── Routing ───────────────────────────────────────────────────────

    /** Route a player to the best server in a group. */
    public static CompletableFuture<NimbusService> route(String player, String group, RoutingStrategy strategy) {
        requireInit();
        return router.routePlayer(player, group, strategy);
    }

    /** Find the best service in a group (from cache, instant). */
    public static NimbusService bestServer(String group, RoutingStrategy strategy) {
        requireInit();
        return cache.findBest(group, strategy);
    }

    // ── Queries (from cache) ──────────────────────────────────────────

    /** Get all services in a group (from cache). */
    public static java.util.List<NimbusService> services(String group) {
        requireInit();
        return cache.getByGroup(group);
    }

    /** Get all services (from cache). */
    public static java.util.List<NimbusService> services() {
        requireInit();
        return cache.getAll();
    }

    /** Get routable services in a group (READY + no custom state). */
    public static java.util.List<NimbusService> routable(String group) {
        requireInit();
        return cache.getRoutable(group);
    }

    /** Player count for a group (from cache). */
    public static int players(String group) {
        requireInit();
        return tracker.getPlayerCount(group);
    }

    /** Total player count (from cache). */
    public static int players() {
        requireInit();
        return tracker.getTotalPlayers();
    }

    /** Service count for a group (from cache). */
    public static int serviceCount(String group) {
        requireInit();
        return cache.getServiceCount(group);
    }

    // ── Events ────────────────────────────────────────────────────────

    /** Listen to a typed event. */
    public static <T extends TypedEvent> void on(Class<T> eventClass, Consumer<T> handler) {
        requireInit();
        eventStream.on(eventClass, handler);
    }

    /** Listen to a raw event by type string. */
    public static void on(String eventType, Consumer<NimbusEvent> handler) {
        requireInit();
        eventStream.onEvent(eventType, handler);
    }

    /** Listen to changes in a group's service list. */
    public static void onChange(String group, Consumer<java.util.List<NimbusService>> handler) {
        requireInit();
        cache.onChange(group, handler);
    }

    /** Listen to player count changes for a group. */
    public static void onPlayers(String group, java.util.function.BiConsumer<String, Integer> handler) {
        requireInit();
        tracker.onPlayerCountChange(group, handler);
    }

    // ── Messaging ─────────────────────────────────────────────────────

    /** Send a message to another service. */
    public static CompletableFuture<Void> message(String target, String channel, Map<String, String> data) {
        requireSelf();
        return self.sendMessage(target, channel, data);
    }

    /** Send a message to another service (no data). */
    public static CompletableFuture<Void> message(String target, String channel) {
        requireSelf();
        return self.sendMessage(target, channel);
    }

    // ── Direct Access ─────────────────────────────────────────────────

    /** Get the underlying client for advanced API calls. */
    public static NimbusClient client() {
        requireInit();
        return self != null ? self.getClient() : cache != null ? null : null;
    }

    /** Get the service cache. */
    public static ServiceCache cache() {
        requireInit();
        return cache;
    }

    /** Get the service router. */
    public static ServiceRouter router() {
        requireInit();
        return router;
    }

    /** Get the event stream. */
    public static NimbusEventStream events() {
        requireInit();
        return eventStream;
    }

    // ── Internal ──────────────────────────────────────────────────────

    private static void requireInit() {
        if (!initialized) throw new IllegalStateException("Nimbus.init() has not been called");
    }

    private static void requireSelf() {
        requireInit();
        if (self == null) throw new IllegalStateException("Not running in a Nimbus-managed service");
    }
}
