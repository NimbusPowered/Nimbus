"use client";

import { useEffect } from "react";
import { toast } from "sonner";
import { apiWebSocketReconnect } from "@/lib/api";

interface EventMessage {
  type: string;
  timestamp: string;
  data: Record<string, string>;
}

/**
 * Subscribes to the controller event stream and raises toasts for
 * cluster-health events the user should see immediately:
 * PlacementBlocked (a service couldn't be scheduled) and
 * SyncCompleted/SyncFailed (stateful service snapshot results).
 *
 * Mounted once inside the dashboard layout. Silent on the login page
 * because it's mounted inside AuthProvider which gates on a valid token.
 */
export function ClusterEventToaster() {
  useEffect(() => {
    // Dedupe repeat PlacementBlocked toasts while the condition persists.
    const lastPlacementReason = new Map<string, string>();

    const { cleanup } = apiWebSocketReconnect("/api/events", {
      onMessage: (event) => {
        const raw = typeof event.data === "string" ? event.data : "";
        if (!raw) return;
        let msg: EventMessage;
        try {
          msg = JSON.parse(raw) as EventMessage;
        } catch {
          return;
        }
        if (!msg?.type) return;

        if (msg.type === "PLACEMENT_BLOCKED") {
          const group = msg.data.group ?? "?";
          const reason = msg.data.reason ?? "unknown";
          if (lastPlacementReason.get(group) === reason) return;
          lastPlacementReason.set(group, reason);
          toast.warning(`Placement blocked: ${group}`, {
            description: reason,
            duration: 8000,
          });
        } else if (msg.type === "SYNC_COMPLETED") {
          lastPlacementReason.clear();
          // Graceful stop landed — tone it down: success toast only if the user
          // is on a page that benefits (otherwise it's noisy ambient activity).
        } else if (msg.type === "SYNC_FAILED") {
          // This is the one case worth shouting about: a persistent static
          // service tried to push its state on stop and it did NOT land, so
          // the canonical on the controller is stale.
          const service = msg.data.service ?? "?";
          const reason = msg.data.reason ?? "unknown";
          toast.error(`Persistence failed: ${service}`, {
            description: `State not saved — ${reason}`,
            duration: 10000,
          });
        }
      },
    });

    return cleanup;
  }, []);

  return null;
}
