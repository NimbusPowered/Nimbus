"use client";

import { Badge } from "@/components/ui/badge";
import { statusColors } from "@/lib/status";
import { Network } from "@/lib/icons";
import { PageShell } from "@/components/page-shell";
import { useApiResource, POLL } from "@/hooks/use-api-resource";
import {
  SystemStatsCard,
  type SystemInfo,
} from "@/components/system-stats-card";
import { ClusterTopology } from "@/components/cluster-topology";
import { ClusterBootstrapCard } from "@/components/cluster-bootstrap-card";
import { LoadBalancerCard } from "@/components/loadbalancer-card";

interface Node {
  nodeId: string;
  host: string;
  maxMemory: string;
  maxServices: number;
  currentServices: number;
  cpuUsage: number;
  memoryUsedMb: number;
  memoryTotalMb: number;
  isConnected: boolean;
  agentVersion: string;
  services: string[];
  system: SystemInfo | null;
}

interface NodeListResponse {
  nodes: Node[];
  total: number;
}

interface TopologyService {
  name: string;
  groupName: string;
  state: string;
  nodeId: string;
  sync?: { lastPushAt: string | null } | null;
}

interface ServiceListResponse {
  services: TopologyService[];
  total: number;
}

export default function NodesPage() {
  const nodesRes = useApiResource<NodeListResponse>("/api/nodes", {
    poll: POLL.normal,
    silent: true,
  });
  const servicesRes = useApiResource<ServiceListResponse>("/api/services", {
    poll: POLL.normal,
    silent: true,
  });

  const nodes = nodesRes.data?.nodes ?? [];
  const services = servicesRes.data?.services ?? [];

  const loading = nodesRes.loading || servicesRes.loading;
  const error = nodesRes.error ?? servicesRes.error;
  const isEmpty = !loading && !error && nodes.length === 0;

  const status = loading
    ? "loading"
    : error
      ? "error"
      : isEmpty
        ? "empty"
        : "ready";

  return (
    <>
      <PageShell
        title="Nodes"
        description={`${nodes.length} node${
          nodes.length === 1 ? "" : "s"
        } · remote agents that run services on this controller's behalf.`}
        status={status}
        skeleton="grid"
        error={error}
        onRetry={() => {
          void nodesRes.refetch();
          void servicesRes.refetch();
        }}
        emptyState={{
          icon: Network,
          title: "No cluster nodes",
          description:
            "This controller is running in single-node mode. Install an agent on another host to scale out.",
        }}
      >
        <div className="space-y-4">
          <ClusterTopology nodes={nodes} services={services} />
          <div className="grid gap-4 lg:grid-cols-2">
            {nodes.map((node) => (
            <SystemStatsCard
              key={node.nodeId}
              title={node.nodeId}
              subtitle={`${node.host} · ${node.currentServices}/${node.maxServices} services · budget ${node.maxMemory}`}
              system={
                node.system ?? {
                  hostname: node.host,
                  osName: "unknown",
                  osVersion: "",
                  osArch: "",
                  cpuModel: "",
                  availableProcessors: 0,
                  systemCpuLoad: node.cpuUsage,
                  processCpuLoad: -1,
                  systemMemoryUsedMb: node.memoryUsedMb,
                  systemMemoryTotalMb: node.memoryTotalMb,
                  javaVersion: "",
                  javaVendor: "",
                }
              }
              headerRight={
                <div className="flex flex-col items-end gap-1">
                  <Badge
                    variant="outline"
                    className={
                      node.isConnected
                        ? statusColors.online
                        : statusColors.inactive
                    }
                  >
                    {node.isConnected ? "Connected" : "Disconnected"}
                  </Badge>
                  <span className="text-xs text-muted-foreground font-mono">
                    agent {node.agentVersion}
                  </span>
                </div>
              }
              />
            ))}
          </div>
        </div>
      </PageShell>

      {/* Render outside PageShell so these are visible on the empty state too —
          the bootstrap helper is most useful when there are zero agents yet. */}
      <div className="mt-4 space-y-4">
        <LoadBalancerCard />
        <ClusterBootstrapCard />
      </div>
    </>
  );
}
