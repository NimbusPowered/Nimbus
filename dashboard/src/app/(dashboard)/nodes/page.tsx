"use client";

import { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch } from "@/lib/api";
import { statusColors } from "@/lib/status";
import { toast } from "sonner";
import { Network } from "@/lib/icons";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import {
  SystemStatsCard,
  type SystemInfo,
} from "@/components/system-stats-card";
import { ClusterTopology } from "@/components/cluster-topology";

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
  sync?: { inFlight: boolean } | null;
}

interface ServiceListResponse {
  services: TopologyService[];
  total: number;
}

export default function NodesPage() {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [services, setServices] = useState<TopologyService[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const [nodeData, svcData] = await Promise.all([
          apiFetch<NodeListResponse>("/api/nodes"),
          apiFetch<ServiceListResponse>("/api/services"),
        ]);
        setNodes(nodeData.nodes);
        setServices(svcData.services);
      } catch (e) {
        toast.error(e instanceof Error ? e.message : "Failed to load nodes");
      } finally {
        setLoading(false);
      }
    }
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  return (
    <>
      <PageHeader
        title="Nodes"
        description={`${nodes.length} node${
          nodes.length === 1 ? "" : "s"
        } · remote agents that run services on this controller's behalf.`}
      />

      {!loading && nodes.length > 0 && (
        <ClusterTopology nodes={nodes} services={services} />
      )}

      {loading ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-80 rounded-xl" />
          <Skeleton className="h-80 rounded-xl" />
        </div>
      ) : nodes.length === 0 ? (
        <Card>
          <CardContent className="p-6">
            <EmptyState
              icon={Network}
              title="No cluster nodes"
              description="This controller is running in single-node mode. Install an agent on another host to scale out."
            />
          </CardContent>
        </Card>
      ) : (
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
      )}
    </>
  );
}
