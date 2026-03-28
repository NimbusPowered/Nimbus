package dev.nimbus.signs;

import dev.nimbus.sdk.RoutingStrategy;
import org.bukkit.Location;

/**
 * A configured Nimbus sign that displays live server info.
 * <p>
 * Can target either a group (routes to best server) or a specific service.
 */
public class NimbusSign {

    private final String id;
    private final Location location;
    private final String target;
    private final boolean serviceTarget;
    private final RoutingStrategy strategy;
    private final String line1;
    private final String line2;
    private final String line3;
    private final String line4Available;
    private final String line4Unavailable;

    public NimbusSign(String id, Location location, String target, boolean serviceTarget,
                      RoutingStrategy strategy, String line1, String line2, String line3,
                      String line4Available, String line4Unavailable) {
        this.id = id;
        this.location = location;
        this.target = target;
        this.serviceTarget = serviceTarget;
        this.strategy = strategy;
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
        this.line4Available = line4Available;
        this.line4Unavailable = line4Unavailable;
    }

    public String getId() { return id; }
    public Location getLocation() { return location; }

    /** The target — either a group name (e.g. "BedWars") or service name (e.g. "Survival-1"). */
    public String getTarget() { return target; }

    /** True if targeting a specific service, false if targeting a group. */
    public boolean isServiceTarget() { return serviceTarget; }

    /** Routing strategy (only used for group targets). */
    public RoutingStrategy getStrategy() { return strategy; }

    // Line templates with placeholders
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getLine3() { return line3; }
    public String getLine4Available() { return line4Available; }
    public String getLine4Unavailable() { return line4Unavailable; }
}
