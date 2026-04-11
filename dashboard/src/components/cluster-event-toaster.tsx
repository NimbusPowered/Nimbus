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
          const service = msg.data.service ?? "?";
          const files = msg.data.filesReceived ?? "0";
          const bytes = Number(msg.data.bytesReceived ?? "0");
          const mb = (bytes / (1024 * 1024)).toFixed(1);
          toast.success(`Sync complete: ${service}`, {
            description: `${files} files · ${mb} MB`,
            duration: 4000,
          });
        } else if (msg.type === "SYNC_FAILED") {
          const service = msg.data.service ?? "?";
          const reason = msg.data.reason ?? "unknown";
          toast.error(`Sync failed: ${service}`, {
            description: reason,
            duration: 10000,
          });
        }
      },
    });

    return cleanup;
  }, []);

  return null;
}
