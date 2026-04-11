"use client";

import { useMemo } from "react";
import { Card, CardContent } from "@/components/ui/card";

interface TopologyService {
  name: string;
  groupName: string;
  state: string;
  nodeId: string;
  sync?: { inFlight: boolean } | null;
}

interface TopologyNode {
  nodeId: string;
  isConnected: boolean;
  currentServices: number;
  maxServices: number;
  cpuUsage: number;
  memoryUsedMb: number;
  memoryTotalMb: number;
  services: string[];
}

interface ClusterTopologyProps {
  nodes: TopologyNode[];
  services: TopologyService[];
  controllerLabel?: string;
}

/**
 * Techy cluster topology: controller in the center, nodes placed radially
 * around it, each with its services as orbiting dots. Connection lines pulse
 * when the node is healthy, dim when disconnected, and there's a marching-ants
 * stroke for in-flight sync pushes.
 *
 * Pure SVG + CSS, no layout libs, redraws every poll via React re-render.
 */
export function ClusterTopology({
  nodes,
  services,
  controllerLabel = "Controller",
}: ClusterTopologyProps) {
  // Deterministic radial layout: place nodes evenly around the controller.
  const { svgNodes, svgServices, center } = useMemo(() => {
    const cx = 400;
    const cy = 260;
    const nodeRadius = 170;
    const svcRadius = 46;

    // Local services (nodeId === "local") hang off the controller itself.
    const remoteNodes = nodes.filter((n) => n.nodeId !== "local");
    const localServices = services.filter(
      (s) => s.nodeId === "local" || s.nodeId === ""
    );

    const n = Math.max(remoteNodes.length, 1);
    // Tighter arc when there are few nodes so everything stays on-canvas.
    const arcStart = -Math.PI / 2; // top
    const arcSpan = remoteNodes.length <= 1 ? 0 : Math.PI * 2;
    const svgNodes = remoteNodes.map((node, i) => {
      const angle = arcStart + (arcSpan * i) / n;
      const x = cx + Math.cos(angle) * nodeRadius;
      const y = cy + Math.sin(angle) * nodeRadius;
      const nodeServices = services.filter((s) => s.nodeId === node.nodeId);
      return { node, x, y, angle, nodeServices };
    });

    return {
      center: { cx, cy },
      svgNodes,
      svgServices: localServices.map((s, i) => {
        // Stack local services to the left of the controller.
        const x = cx - 140 - (i % 2) * 20;
        const y = cy + (i - (localServices.length - 1) / 2) * 26;
        return { service: s, x, y };
      }),
    };
  }, [nodes, services]);

  const serviceDotColor = (state: string) => {
    switch (state) {
      case "READY":
        return "#22c55e";
      case "STARTING":
      case "PREPARING":
      case "PREPARED":
        return "#eab308";
      case "STOPPING":
      case "DRAINING":
        return "#f97316";
      case "CRASHED":
        return "#ef4444";
      case "STOPPED":
        return "#64748b";
      default:
        return "#64748b";
    }
  };

  const totalServices = services.length;
  const connectedNodes = nodes.filter((n) => n.isConnected && n.nodeId !== "local").length;
  const totalRemoteNodes = nodes.filter((n) => n.nodeId !== "local").length;

  return (
    <Card className="overflow-hidden">
      <CardContent className="p-0">
        <div className="relative">
          <svg
            viewBox="0 0 800 520"
            className="w-full h-[520px] bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950"
            xmlns="http://www.w3.org/2000/svg"
          >
            <defs>
              {/* Soft radial glow under the controller */}
              <radialGradient id="controller-glow" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="rgba(56, 189, 248, 0.35)" />
                <stop offset="60%" stopColor="rgba(56, 189, 248, 0.05)" />
                <stop offset="100%" stopColor="rgba(56, 189, 248, 0)" />
              </radialGradient>
              {/* Marching-ants pattern for sync in-flight */}
              <pattern
                id="ants"
                x="0"
                y="0"
                width="8"
                height="2"
                patternUnits="userSpaceOnUse"
              >
                <rect width="4" height="2" fill="#38bdf8" />
                <rect x="4" width="4" height="2" fill="transparent" />
              </pattern>
              {/* Grid */}
              <pattern
                id="grid"
                x="0"
                y="0"
                width="40"
                height="40"
                patternUnits="userSpaceOnUse"
              >
                <path
                  d="M 40 0 L 0 0 0 40"
                  fill="none"
                  stroke="rgba(148, 163, 184, 0.08)"
                  strokeWidth="1"
                />
              </pattern>
              {/* Arrow marker for connection directionality */}
              <marker
                id="arrow"
                viewBox="0 0 10 10"
                refX="8"
                refY="5"
                markerWidth="5"
                markerHeight="5"
                orient="auto"
              >
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#38bdf8" opacity="0.6" />
              </marker>
            </defs>

            <rect width="800" height="520" fill="url(#grid)" />
            <circle
              cx={center.cx}
              cy={center.cy}
              r="180"
              fill="url(#controller-glow)"
            />

            {/* Connection lines controller ↔ each remote node */}
            {svgNodes.map(({ node, x, y }) => {
              const inFlight = services.some(
                (s) => s.nodeId === node.nodeId && s.sync?.inFlight
              );
              return (
                <g key={`link-${node.nodeId}`}>
                  {/* Base line */}
                  <line
                    x1={center.cx}
                    y1={center.cy}
                    x2={x}
                    y2={y}
                    stroke={node.isConnected ? "#38bdf8" : "#475569"}
                    strokeWidth={node.isConnected ? 1.5 : 1}
                    strokeDasharray={node.isConnected ? undefined : "4 4"}
                    opacity={node.isConnected ? 0.7 : 0.35}
                    markerEnd={node.isConnected ? "url(#arrow)" : undefined}
                  />
                  {/* Pulse ring along the connection when connected */}
                  {node.isConnected && (
                    <circle r="4" fill="#38bdf8" opacity="0.9">
                      <animateMotion
                        dur={inFlight ? "1.2s" : "2.4s"}
                        repeatCount="indefinite"
                        path={`M ${center.cx},${center.cy} L ${x},${y}`}
                      />
                    </circle>
                  )}
                  {/* Marching ants on in-flight sync */}
                  {inFlight && (
                    <line
                      x1={center.cx}
                      y1={center.cy}
                      x2={x}
                      y2={y}
                      stroke="url(#ants)"
                      strokeWidth="3"
                      opacity="0.8"
                    >
                      <animate
                        attributeName="stroke-dashoffset"
                        from="0"
                        to="-16"
                        dur="0.6s"
                        repeatCount="indefinite"
                      />
                    </line>
                  )}
                </g>
              );
            })}

            {/* Controller node */}
            <g>
              <circle
                cx={center.cx}
                cy={center.cy}
                r="52"
                fill="#0f172a"
                stroke="#38bdf8"
                strokeWidth="2"
              />
              <circle
                cx={center.cx}
                cy={center.cy}
                r="52"
                fill="none"
                stroke="#38bdf8"
                strokeWidth="2"
                opacity="0.4"
              >
                <animate
                  attributeName="r"
                  from="52"
                  to="68"
                  dur="2.4s"
                  repeatCount="indefinite"
                />
                <animate
                  attributeName="opacity"
                  from="0.5"
                  to="0"
                  dur="2.4s"
                  repeatCount="indefinite"
                />
              </circle>
              <text
                x={center.cx}
                y={center.cy - 4}
                textAnchor="middle"
                fontFamily="ui-monospace, monospace"
                fontSize="11"
                fill="#e2e8f0"
                fontWeight="600"
              >
                {controllerLabel.toUpperCase()}
              </text>
              <text
                x={center.cx}
                y={center.cy + 10}
                textAnchor="middle"
                fontFamily="ui-monospace, monospace"
                fontSize="9"
                fill="#64748b"
              >
                {connectedNodes}/{totalRemoteNodes} nodes · {totalServices} svc
              </text>
            </g>

            {/* Local services hanging off the controller */}
            {svgServices.map(({ service, x, y }) => (
              <g key={`local-${service.name}`}>
                <line
                  x1={center.cx - 52}
                  y1={center.cy}
                  x2={x + 6}
                  y2={y}
                  stroke="#475569"
                  strokeWidth="1"
                  opacity="0.5"
                />
                <circle
                  cx={x}
                  cy={y}
                  r="5"
                  fill={serviceDotColor(service.state)}
                  stroke="#0f172a"
                  strokeWidth="1.5"
                />
                <text
                  x={x - 10}
                  y={y + 3}
                  textAnchor="end"
                  fontFamily="ui-monospace, monospace"
                  fontSize="9"
                  fill="#94a3b8"
                >
                  {service.name}
                </text>
              </g>
            ))}

            {/* Remote nodes with their services */}
            {svgNodes.map(({ node, x, y, nodeServices }) => {
              const memoryPct =
                node.memoryTotalMb > 0
                  ? Math.min(100, (node.memoryUsedMb / node.memoryTotalMb) * 100)
                  : 0;
              return (
                <g key={`node-${node.nodeId}`}>
                  {/* Node circle */}
                  <circle
                    cx={x}
                    cy={y}
                    r="36"
                    fill="#0f172a"
                    stroke={node.isConnected ? "#22c55e" : "#475569"}
                    strokeWidth="2"
                    opacity={node.isConnected ? 1 : 0.6}
                  />
                  {/* Memory usage arc */}
                  <circle
                    cx={x}
                    cy={y}
                    r="36"
                    fill="none"
                    stroke={node.isConnected ? "#22c55e" : "#475569"}
                    strokeWidth="3"
                    strokeDasharray={`${(memoryPct / 100) * 226} 226`}
                    transform={`rotate(-90 ${x} ${y})`}
                    opacity="0.6"
                  />
                  <text
                    x={x}
                    y={y - 4}
                    textAnchor="middle"
                    fontFamily="ui-monospace, monospace"
                    fontSize="10"
                    fill="#e2e8f0"
                    fontWeight="600"
                  >
                    {node.nodeId}
                  </text>
                  <text
                    x={x}
                    y={y + 8}
                    textAnchor="middle"
                    fontFamily="ui-monospace, monospace"
                    fontSize="8"
                    fill="#64748b"
                  >
                    {node.currentServices}/{node.maxServices}
                  </text>

                  {/* Service dots orbiting the node */}
                  {nodeServices.map((s, i) => {
                    const svcAngle =
                      (i * 2 * Math.PI) / Math.max(nodeServices.length, 1) -
                      Math.PI / 2;
                    const sx = x + Math.cos(svcAngle) * (36 + svcRadius);
                    const sy = y + Math.sin(svcAngle) * (36 + svcRadius);
                    return (
                      <g key={s.name}>
                        <line
                          x1={x + Math.cos(svcAngle) * 36}
                          y1={y + Math.sin(svcAngle) * 36}
                          x2={sx}
                          y2={sy}
                          stroke="#475569"
                          strokeWidth="1"
                          opacity="0.5"
                        />
                        <circle
                          cx={sx}
                          cy={sy}
                          r="6"
                          fill={serviceDotColor(s.state)}
                          stroke="#0f172a"
                          strokeWidth="1.5"
                        >
                          {s.sync?.inFlight && (
                            <animate
                              attributeName="r"
                              values="6;8;6"
                              dur="1s"
                              repeatCount="indefinite"
                            />
                          )}
                        </circle>
                        <text
                          x={sx}
                          y={sy + 18}
                          textAnchor="middle"
                          fontFamily="ui-monospace, monospace"
                          fontSize="8"
                          fill="#94a3b8"
                        >
                          {s.name}
                        </text>
                      </g>
                    );
                  })}
                </g>
              );
            })}
          </svg>

          {/* Legend */}
          <div className="absolute bottom-3 left-3 flex flex-wrap gap-3 text-[10px] font-mono text-slate-400 bg-slate-950/70 backdrop-blur rounded-md px-3 py-2 border border-slate-800">
            <LegendDot color="#22c55e" label="READY" />
            <LegendDot color="#eab308" label="STARTING" />
            <LegendDot color="#f97316" label="STOPPING" />
            <LegendDot color="#ef4444" label="CRASHED" />
            <LegendDot color="#64748b" label="STOPPED" />
            <span className="text-slate-500">· ring = memory · ants = sync</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span
        className="inline-block w-2 h-2 rounded-full"
        style={{ background: color }}
      />
      {label}
    </span>
  );
}
