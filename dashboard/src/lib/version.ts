/**
 * Dashboard version info.
 *
 * The version string is injected at build time from `package.json` via
 * `next.config.ts` (see `NEXT_PUBLIC_DASHBOARD_VERSION`). Kept coupled to the
 * controller release train (major.minor = controller, patch/prerelease =
 * dashboard-specific bumps), e.g. `0.9.1-beta.1`.
 *
 * Release channel is derived from the prerelease tag:
 *   `-alpha.N` → "alpha", `-beta.N` → "beta", anything else → "stable".
 */

export type ReleaseChannel = "alpha" | "beta" | "stable";

const raw = (process.env.NEXT_PUBLIC_DASHBOARD_VERSION ?? "0.0.0-dev").trim();

function detectChannel(version: string): ReleaseChannel {
  if (/-alpha(\.|$)/i.test(version)) return "alpha";
  if (/-beta(\.|$)/i.test(version)) return "beta";
  return "stable";
}

/** The raw dashboard version string, e.g. `0.9.1-beta.1`. */
export const dashboardVersion: string = raw;

/** Detected release channel, used for the ALPHA/BETA badge. */
export const channel: ReleaseChannel = detectChannel(raw);

/** `true` when the dashboard is a prerelease build (alpha or beta). */
export const isPrerelease: boolean = channel !== "stable";

/** Human-readable label for the channel (uppercase), or `null` when stable. */
export const channelLabel: string | null =
  channel === "stable" ? null : channel.toUpperCase();
