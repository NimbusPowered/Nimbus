"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Network } from "@/lib/icons";
import { statusColors } from "@/lib/status";

interface LbBackend {
  name: string;
  host: string;
  port: number;
  playerCount: number;
  health: string;
  connectionCount: number;
}

interface LoadBalancerStatus {
  enabled: boolean;
  bind: string;
  port: number;
  strategy: string;
  proxyProtocol: boolean;
  totalConnections: number;
  activeConnections: number;
  rejectedConnections: number;
  backends: LbBackend[];
}

function healthClass(health: string): string {
  switch (health.toUpperCase()) {
    case "HEALTHY":
    case "UP":
      return statusColors.online;
    case "UNHEALTHY":
    case "DOWN":
      return statusColors.maintenance;
    default:
      return statusColors.inactive;
  }
}

/**
 * Read-only load-balancer status panel. Backend health, active connections
 * and per-backend addresses, polled every 5s. Hidden when the controller has
 * no LB enabled (404 → enabled=false).
 */
export function LoadBalancerCard() {
  const [status, setStatus] = useState<LoadBalancerStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [available, setAvailable] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const s = await apiFetch<LoadBalancerStatus>("/api/loadbalancer", {
          silent: true,
        });
        if (!cancelled) {
          setStatus(s);
          setAvailable(true);
        }
      } catch {
        // Most likely 404 — load balancer not enabled. Hide the card.
        if (!cancelled) setAvailable(false);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    const t = setInterval(load, 5000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, []);

  if (!loading && !available) return null;

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <div className="flex items-center gap-2">
          <Network className="size-4 text-muted-foreground" />
          <CardTitle>Load balancer</CardTitle>
          {status && (
            <Badge variant="outline" className="font-mono text-xs">
              {status.strategy}
            </Badge>
          )}
        </div>
        {status && (
          <div className="text-xs text-muted-foreground">
            {status.bind}:{status.port}
            {status.proxyProtocol && (
              <Badge variant="secondary" className="ml-2 text-[10px]">
                PROXY
              </Badge>
            )}
          </div>
        )}
      </CardHeader>
      <CardContent className="space-y-3">
        {loading ? (
          <Skeleton className="h-32 rounded-md" />
        ) : status ? (
          <>
            <div className="grid grid-cols-3 gap-2 text-xs">
              <Stat label="Active" value={status.activeConnections} />
              <Stat label="Total" value={status.totalConnections} />
              <Stat
                label="Rejected"
                value={status.rejectedConnections}
                tone={status.rejectedConnections > 0 ? "warn" : undefined}
              />
            </div>
            {status.backends.length === 0 ? (
              <div className="rounded-md border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
                No backends registered.
              </div>
            ) : (
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Backend</TableHead>
                      <TableHead>Address</TableHead>
                      <TableHead className="text-right">Players</TableHead>
                      <TableHead className="text-right">Conns</TableHead>
                      <TableHead>Health</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {status.backends.map((b) => (
                      <TableRow key={`${b.host}:${b.port}`}>
                        <TableCell className="font-medium">{b.name}</TableCell>
                        <TableCell className="font-mono text-xs text-muted-foreground">
                          {b.host}:{b.port}
                        </TableCell>
                        <TableCell className="text-right">
                          {b.playerCount}
                        </TableCell>
                        <TableCell className="text-right">
                          {b.connectionCount}
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant="outline"
                            className={healthClass(b.health)}
                          >
                            {b.health}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: "warn";
}) {
  return (
    <div className="rounded-md border px-3 py-2">
      <div className="text-[10px] uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
      <div
        className={
          tone === "warn"
            ? "font-mono text-base font-medium text-amber-600 dark:text-amber-400"
            : "font-mono text-base font-medium"
        }
      >
        {value}
      </div>
    </div>
  );
}
