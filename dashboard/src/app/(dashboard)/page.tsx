"use client";

import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Activity,
  Server,
  Users,
  Clock,
  FolderTree,
  ExternalLinkIcon,
} from "@/lib/icons";
import { PageShell } from "@/components/page-shell";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";
import { useApiResource, POLL } from "@/hooks/use-api-resource";
import {
  SystemStatsCard,
  type SystemInfo,
  SystemBar,
} from "@/components/system-stats-card";
import { ChangelogCard } from "@/components/changelog-card";
import { cn } from "@/lib/utils";

interface GroupStatus {
  name: string;
  instances: number;
  maxInstances: number;
  players: number;
  maxPlayers: number;
  software: string;
  version: string;
}

interface StatusData {
  networkName: string;
  online: boolean;
  uptimeSeconds: number;
  totalServices: number;
  totalPlayers: number;
  groups: GroupStatus[];
}

interface ControllerInfo {
  version: string;
  startedAt: string;
  uptimeSeconds: number;
  jvmMemoryUsedMb: number;
  jvmMemoryMaxMb: number;
  jvmMemoryAllocatedMb: number;
  servicesMaxMemoryMb: number;
  servicesAllocatedMemoryMb: number;
  servicesUsedMemoryMb: number;
  runningServices: number;
  system: SystemInfo;
  updateAvailable: boolean;
  latestVersion: string | null;
  updateType: string | null;
  releaseUrl: string | null;
}

function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function formatMb(mb: number): string {
  if (mb >= 1024) return `${(mb / 1024).toFixed(1)} GB`;
  return `${mb} MB`;
}

export default function OverviewPage() {
  const {
    data: status,
    loading: statusLoading,
    error: statusError,
    refetch: refetchStatus,
  } = useApiResource<StatusData>("/api/status", { poll: POLL.normal });
  const {
    data: info,
    loading: infoLoading,
  } = useApiResource<ControllerInfo>("/api/controller/info", {
    poll: POLL.normal,
  });
  const loading = statusLoading || infoLoading;

  const servicesPct =
    info && info.servicesMaxMemoryMb > 0
      ? Math.round((info.servicesAllocatedMemoryMb / info.servicesMaxMemoryMb) * 100)
      : 0;
  const jvmPct =
    info && info.jvmMemoryMaxMb > 0
      ? Math.round((info.jvmMemoryUsedMb / info.jvmMemoryMaxMb) * 100)
      : 0;

  return (
    <PageShell
      title="Dashboard"
      description={
        status
          ? `${status.networkName} · live status of the whole cluster`
          : "Live status of the whole cluster"
      }
      status={
        loading && !status ? "loading" : statusError && !status ? "error" : "ready"
      }
      error={statusError}
      onRetry={refetchStatus}
      skeleton="grid"
    >
      <div className="space-y-6">
          {/* Top-level KPI row */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Status"
              icon={Activity}
              tone={status?.online ? "primary" : "destructive"}
              value={status?.online ? "Online" : "Offline"}
              hint={
                info
                  ? `Nimbus v${info.version}`
                  : status?.networkName ?? "Nimbus"
              }
            />
            <StatCard
              label="Services"
              icon={Server}
              value={status?.totalServices ?? 0}
              hint={`${status?.groups?.length ?? 0} groups`}
            />
            <StatCard
              label="Players"
              icon={Users}
              value={status?.totalPlayers ?? 0}
              hint="online"
            />
            <StatCard
              label="Uptime"
              icon={Clock}
              value={status ? formatUptime(status.uptimeSeconds) : "—"}
              hint={
                info
                  ? new Date(info.startedAt).toLocaleDateString()
                  : "controller"
              }
            />
          </div>

          {/* Update banner */}
          {info?.updateAvailable && info.latestVersion && (
            <div className="rounded-lg border border-primary/50 bg-primary/5 p-4 flex items-center justify-between flex-wrap gap-3">
              <div className="flex items-center gap-3">
                <Badge>Update available</Badge>
                <span className="text-sm">
                  v{info.version} → <strong>v{info.latestVersion}</strong>
                  {info.updateType && (
                    <span className="text-xs text-muted-foreground ml-1">
                      ({info.updateType.toLowerCase()})
                    </span>
                  )}
                </span>
              </div>
              {info.releaseUrl && (
                <Button
                  variant="outline"
                  size="sm"
                  nativeButton={false}
                  render={
                    <a
                      href={info.releaseUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <ExternalLinkIcon className="mr-1 size-3.5" />
                      View release
                    </a>
                  }
                />
              )}
            </div>
          )}

          {/* Controller host: live system + nimbus runtime side by side */}
          <div className="grid gap-4 lg:grid-cols-2">
            {info && (
              <SystemStatsCard
                title="Controller host"
                subtitle={info.system.hostname}
                system={info.system}
                headerRight={<Badge variant="outline">v{info.version}</Badge>}
              />
            )}

            {/* Nimbus runtime — services budget + JVM heap + uptime */}
            {info && (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Server className="size-4 text-muted-foreground" />
                    Nimbus runtime
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-5">
                  <div>
                    <div className="flex items-center justify-between text-sm mb-1">
                      <span className="text-muted-foreground">
                        Services memory
                      </span>
                      <span className="font-mono text-xs">
                        {formatMb(info.servicesAllocatedMemoryMb)} /{" "}
                        {formatMb(info.servicesMaxMemoryMb)}
                      </span>
                    </div>
                    <SystemBar pct={servicesPct} />
                    <div className="text-xs text-muted-foreground mt-1">
                      {info.runningServices} running · actual{" "}
                      {formatMb(info.servicesUsedMemoryMb)} · {servicesPct}%
                      allocated
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between text-sm mb-1">
                      <span className="text-muted-foreground">
                        Controller JVM heap
                      </span>
                      <span className="font-mono text-xs">
                        {formatMb(info.jvmMemoryUsedMb)} /{" "}
                        {formatMb(info.jvmMemoryMaxMb)}
                      </span>
                    </div>
                    <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                      <div
                        className={cn(
                          "h-full transition-all",
                          jvmPct > 90
                            ? "bg-destructive"
                            : jvmPct > 75
                            ? "bg-yellow-500"
                            : "bg-muted-foreground/60"
                        )}
                        style={{ width: `${jvmPct}%` }}
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs pt-1 border-t">
                    <div>
                      <div className="text-muted-foreground">Uptime</div>
                      <div className="font-mono">
                        {formatUptime(info.uptimeSeconds)}
                      </div>
                    </div>
                    <div>
                      <div className="text-muted-foreground">Started</div>
                      <div className="font-mono">
                        {new Date(info.startedAt).toLocaleString()}
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}
          </div>

          {/* Groups */}
          <Card>
            <CardHeader>
              <CardTitle>Groups</CardTitle>
            </CardHeader>
            <CardContent>
              {!status?.groups?.length ? (
                <EmptyState
                  icon={FolderTree}
                  title="No groups configured"
                  description="Create your first group to start running services."
                />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Software</TableHead>
                      <TableHead>Version</TableHead>
                      <TableHead className="text-right">Instances</TableHead>
                      <TableHead className="text-right">Players</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {status.groups.map((group) => (
                      <TableRow key={group.name} className="cursor-pointer">
                        <TableCell className="font-medium">
                          <Link
                            href={`/groups/${group.name}`}
                            className="hover:underline"
                          >
                            {group.name}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{group.software}</Badge>
                        </TableCell>
                        <TableCell>{group.version}</TableCell>
                        <TableCell className="text-right">
                          {group.instances}/{group.maxInstances}
                        </TableCell>
                        <TableCell className="text-right">
                          {group.players}/{group.maxPlayers}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

        {/* Changelog — at the bottom, collapsible per version */}
        <ChangelogCard />
      </div>
    </PageShell>
  );
}
