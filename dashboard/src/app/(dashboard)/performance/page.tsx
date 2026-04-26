"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { PageShell } from "@/components/page-shell";
import { StatCard } from "@/components/stat-card";
import { Activity, AlertTriangle, Clock, MemoryStick, Users } from "@/lib/icons";
import {
  useNetworkPlayerHistory,
  usePerformanceSummary,
} from "@/lib/metrics";
import { cn } from "@/lib/utils";

const playerHistoryConfig: ChartConfig = {
  totalPlayers: { label: "Players", color: "var(--chart-1)" },
};

function formatStartup(seconds: number): string {
  if (seconds < 1) return "< 1s";
  return `${seconds.toFixed(1)}s`;
}

function formatMemory(usedMb: number, maxMb: number): string {
  const pct = maxMb > 0 ? Math.round((usedMb / maxMb) * 100) : 0;
  const fmt = (mb: number) =>
    mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`;
  return `${fmt(usedMb)} / ${fmt(maxMb)} (${pct}%)`;
}

function UptimeBadge({ pct }: { pct: number }) {
  return (
    <span
      className={cn(
        "text-2xl font-semibold tabular-nums",
        pct >= 99
          ? "text-[color:var(--severity-ok)]"
          : pct >= 95
          ? "text-[color:var(--severity-warn)]"
          : "text-[color:var(--severity-err)]"
      )}
    >
      {pct.toFixed(2)}%
    </span>
  );
}

export default function PerformancePage() {
  const { data: summary, loading: summaryLoading, error: summaryError, refetch } =
    usePerformanceSummary();
  const { data: playerHistory, loading: historyLoading } =
    useNetworkPlayerHistory(240);

  const memPct =
    summary && summary.totalMemoryMaxMb > 0
      ? Math.round(
          (summary.totalMemoryUsedMb / summary.totalMemoryMaxMb) * 100
        )
      : 0;

  return (
    <PageShell
      title="Performance"
      description="Network-wide KPIs, group scorecards, and player activity"
      status={
        summaryLoading && !summary
          ? "loading"
          : summaryError && !summary
          ? "error"
          : "ready"
      }
      error={summaryError}
      onRetry={refetch}
      skeleton="grid"
    >
      <div className="space-y-6">
        {/* KPI cards */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card className="overflow-hidden">
            <CardContent className="p-5">
              <div className="flex items-center justify-between gap-3">
                <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Uptime
                </div>
                <Activity className="size-4 text-muted-foreground" />
              </div>
              <div className="mt-2">
                {summary ? (
                  <UptimeBadge pct={summary.uptimePercent} />
                ) : (
                  <Skeleton className="h-8 w-20" />
                )}
              </div>
              <div className="mt-1 text-xs text-muted-foreground">
                service availability
              </div>
            </CardContent>
          </Card>

          <StatCard
            label="Crashes (24h)"
            icon={AlertTriangle}
            value={summary?.crashesLast24h ?? "—"}
            hint={
              summary
                ? `${summary.crashesLast7d} in last 7 days`
                : "loading…"
            }
          />

          <StatCard
            label="Avg Startup"
            icon={Clock}
            value={
              summary ? formatStartup(summary.averageStartupSeconds) : "—"
            }
            hint="time to READY"
          />

          <StatCard
            label="Memory Usage"
            icon={MemoryStick}
            value={`${memPct}%`}
            hint={
              summary
                ? formatMemory(
                    summary.totalMemoryUsedMb,
                    summary.totalMemoryMaxMb
                  )
                : "loading…"
            }
          />
        </div>

        {/* Group performance table */}
        <Card>
          <CardHeader>
            <CardTitle>Group Performance</CardTitle>
          </CardHeader>
          <CardContent>
            {summaryLoading && !summary ? (
              <div className="space-y-2">
                {[...Array(3)].map((_, i) => (
                  <Skeleton key={i} className="h-9 w-full" />
                ))}
              </div>
            ) : !summary?.groups?.length ? (
              <p className="text-sm text-muted-foreground py-4 text-center">
                No group data available.
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Group</TableHead>
                    <TableHead className="text-right">Services</TableHead>
                    <TableHead className="text-right">Players</TableHead>
                    <TableHead className="text-right">Memory %</TableHead>
                    <TableHead className="text-right">TPS</TableHead>
                    <TableHead className="text-right">Crashes 24h</TableHead>
                    <TableHead className="text-right">Avg Startup</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {summary.groups.map((g) => (
                    <TableRow key={g.groupName}>
                      <TableCell className="font-medium">
                        {g.groupName}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {g.readyServiceCount}/{g.serviceCount}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {g.playerCount}/{g.maxPlayers}
                      </TableCell>
                      <TableCell
                        className={cn(
                          "text-right tabular-nums",
                          g.averageMemoryPercent > 90
                            ? "text-[color:var(--severity-err)]"
                            : g.averageMemoryPercent > 75
                            ? "text-[color:var(--severity-warn)]"
                            : ""
                        )}
                      >
                        {g.averageMemoryPercent.toFixed(0)}%
                      </TableCell>
                      <TableCell
                        className={cn(
                          "text-right tabular-nums",
                          g.averageTps > 0 && g.averageTps < 15
                            ? "text-[color:var(--severity-err)]"
                            : g.averageTps > 0 && g.averageTps < 18
                            ? "text-[color:var(--severity-warn)]"
                            : ""
                        )}
                      >
                        {g.averageTps > 0 ? g.averageTps.toFixed(1) : "—"}
                      </TableCell>
                      <TableCell
                        className={cn(
                          "text-right tabular-nums",
                          g.crashesLast24h > 0
                            ? "text-[color:var(--severity-err)]"
                            : ""
                        )}
                      >
                        {g.crashesLast24h}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatStartup(g.averageStartupSeconds)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        {/* Player activity chart (4h window) */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0 pb-3">
            <CardTitle className="flex items-center gap-2">
              <Users className="size-4 text-muted-foreground" />
              Player Activity (4h)
            </CardTitle>
            {summary && (
              <span className="text-2xl font-semibold tabular-nums">
                {summary.networkPlayers}
                <span className="ml-1 text-xs font-normal text-muted-foreground">
                  online
                </span>
              </span>
            )}
          </CardHeader>
          <CardContent className="pb-4">
            {historyLoading ? (
              <Skeleton className="h-56 w-full" />
            ) : !playerHistory || playerHistory.length < 2 ? (
              <div className="flex h-56 items-center justify-center text-xs text-muted-foreground">
                Collecting samples… history requires at least two data points.
              </div>
            ) : (
              <ChartContainer
                config={playerHistoryConfig}
                className="!aspect-auto h-56 w-full"
              >
                <AreaChart
                  data={playerHistory.map((s) => ({
                    time: new Date(s.timestamp).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    }),
                    totalPlayers: s.totalPlayers,
                  }))}
                >
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="time"
                    tick={{ fontSize: 10 }}
                    interval="preserveStartEnd"
                    minTickGap={40}
                  />
                  <YAxis
                    tick={{ fontSize: 10 }}
                    width={32}
                    allowDecimals={false}
                    domain={[0, "auto"]}
                  />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <Area
                    type="monotone"
                    dataKey="totalPlayers"
                    fill="var(--color-totalPlayers)"
                    fillOpacity={0.25}
                    stroke="var(--color-totalPlayers)"
                    strokeWidth={2}
                  />
                </AreaChart>
              </ChartContainer>
            )}
          </CardContent>
        </Card>
      </div>
    </PageShell>
  );
}
