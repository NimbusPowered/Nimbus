"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { Card, CardContent } from "@/components/ui/card";

interface TopologyService {
  name: string;
  groupName: string;
  state: string;
  nodeId: string;
  sync?: { lastPushAt: string | null } | null;
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

interface ViewBox {
  x: number;
  y: number;
  w: number;
  h: number;
}

const BASE_W = 1200;
const BASE_H = 720;
const MIN_ZOOM = 0.25;
const MAX_ZOOM = 4;
const ANIM_MS = 260;

/**
 * Interactive cluster topology — controller in the center, nodes placed
 * radially around it, services orbiting each node. Supports pan (drag),
 * zoom (wheel / buttons / keyboard), hover-to-inspect, and a fit-to-content
 * action. Button-driven transitions animate on an ease-out curve; wheel and
 * drag remain 1:1 with the input device.
 */
export function ClusterTopology({
  nodes,
  services,
  controllerLabel = "Controller",
}: ClusterTopologyProps) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const panState = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    startVb: ViewBox;
  } | null>(null);
  const animRef = useRef<number | null>(null);
  const viewBoxRef = useRef<ViewBox>({ x: 0, y: 0, w: BASE_W, h: BASE_H });

  const [viewBox, setViewBox] = useState<ViewBox>({
    x: 0,
    y: 0,
    w: BASE_W,
    h: BASE_H,
  });
  const [isPanning, setIsPanning] = useState(false);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [hovered, setHovered] = useState<{
    title: string;
    body: Array<[string, string]>;
    accent: string;
    clientX: number;
    clientY: number;
  } | null>(null);

  useEffect(() => {
    viewBoxRef.current = viewBox;
  }, [viewBox]);

  // Deterministic radial layout, scaled to the larger logical canvas.
  const { svgNodes, svgServices, center, contentBbox } = useMemo(() => {
    const cx = BASE_W / 2;
    const cy = BASE_H / 2;
    const remoteNodes = nodes.filter((n) => n.nodeId !== "local");
    const localServices = services.filter(
      (s) => s.nodeId === "local" || s.nodeId === ""
    );
    const ring = Math.max(260, 200 + remoteNodes.length * 18);
    const n = Math.max(remoteNodes.length, 1);
    const arcStart = -Math.PI / 2;
    const arcSpan = remoteNodes.length <= 1 ? 0 : Math.PI * 2;

    const placedNodes = remoteNodes.map((node, i) => {
      const angle = arcStart + (arcSpan * i) / n;
      const x = cx + Math.cos(angle) * ring;
      const y = cy + Math.sin(angle) * ring;
      const nodeServices = services.filter((s) => s.nodeId === node.nodeId);
      return { node, x, y, angle, nodeServices };
    });

    const placedLocals = localServices.map((s, i) => {
      const rows = Math.ceil(localServices.length / 2);
      const col = i % 2;
      const row = Math.floor(i / 2);
      const x = cx - 190 - col * 34;
      const y = cy + (row - (rows - 1) / 2) * 30;
      return { service: s, x, y };
    });

    let minX = cx - 80;
    let minY = cy - 80;
    let maxX = cx + 80;
    let maxY = cy + 80;
    const grow = (x: number, y: number, r: number) => {
      if (x - r < minX) minX = x - r;
      if (y - r < minY) minY = y - r;
      if (x + r > maxX) maxX = x + r;
      if (y + r > maxY) maxY = y + r;
    };
    placedNodes.forEach(({ x, y, nodeServices }) => {
      grow(x, y, 54);
      const svcRing = 36 + 46;
      nodeServices.forEach((_, i) => {
        const a =
          (i * 2 * Math.PI) / Math.max(nodeServices.length, 1) - Math.PI / 2;
        grow(x + Math.cos(a) * svcRing, y + Math.sin(a) * svcRing, 14);
      });
    });
    placedLocals.forEach(({ x, y }) => grow(x, y, 16));

    return {
      svgNodes: placedNodes,
      svgServices: placedLocals,
      center: { cx, cy },
      contentBbox: { minX, minY, maxX, maxY },
    };
  }, [nodes, services]);

  const clientToSvg = useCallback(
    (clientX: number, clientY: number, vb: ViewBox) => {
      const svg = svgRef.current;
      if (!svg) return { x: 0, y: 0 };
      const rect = svg.getBoundingClientRect();
      const nx = (clientX - rect.left) / rect.width;
      const ny = (clientY - rect.top) / rect.height;
      return { x: vb.x + nx * vb.w, y: vb.y + ny * vb.h };
    },
    []
  );

  const cancelAnim = useCallback(() => {
    if (animRef.current != null) {
      cancelAnimationFrame(animRef.current);
      animRef.current = null;
    }
  }, []);

  const animateTo = useCallback(
    (target: ViewBox) => {
      cancelAnim();
      const start = performance.now();
      const from = { ...viewBoxRef.current };
      const tick = (now: number) => {
        const t = Math.min(1, (now - start) / ANIM_MS);
        // ease-out cubic (see ux §7 `easing`)
        const k = 1 - Math.pow(1 - t, 3);
        const next = {
          x: from.x + (target.x - from.x) * k,
          y: from.y + (target.y - from.y) * k,
          w: from.w + (target.w - from.w) * k,
          h: from.h + (target.h - from.h) * k,
        };
        setViewBox(next);
        if (t < 1) animRef.current = requestAnimationFrame(tick);
        else animRef.current = null;
      };
      animRef.current = requestAnimationFrame(tick);
    },
    [cancelAnim]
  );

  const zoomAt = useCallback(
    (clientX: number, clientY: number, factor: number, animated = false) => {
      const vb = viewBoxRef.current;
      const newW = vb.w / factor;
      const newH = vb.h / factor;
      const zoomLevel = BASE_W / newW;
      if (zoomLevel < MIN_ZOOM || zoomLevel > MAX_ZOOM) return;
      const { x: sx, y: sy } = clientToSvg(clientX, clientY, vb);
      const kx = (sx - vb.x) / vb.w;
      const ky = (sy - vb.y) / vb.h;
      const target: ViewBox = {
        x: sx - kx * newW,
        y: sy - ky * newH,
        w: newW,
        h: newH,
      };
      if (animated) animateTo(target);
      else {
        cancelAnim();
        setViewBox(target);
      }
    },
    [clientToSvg, animateTo, cancelAnim]
  );

  // React's wheel listener is passive by default — attach manually so we can
  // preventDefault and keep the page from scrolling behind us.
  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const handler = (e: WheelEvent) => {
      e.preventDefault();
      const factor = Math.pow(1.0015, -e.deltaY);
      zoomAt(e.clientX, e.clientY, factor, false);
    };
    svg.addEventListener("wheel", handler, { passive: false });
    return () => svg.removeEventListener("wheel", handler);
  }, [zoomAt]);

  const onPointerDown = useCallback(
    (e: ReactPointerEvent<SVGSVGElement>) => {
      if (e.button !== 0 && e.pointerType === "mouse") return;
      cancelAnim();
      (e.target as Element).setPointerCapture?.(e.pointerId);
      panState.current = {
        pointerId: e.pointerId,
        startX: e.clientX,
        startY: e.clientY,
        startVb: viewBoxRef.current,
      };
      setIsPanning(true);
      setHovered(null);
    },
    [cancelAnim]
  );

  const onPointerMove = useCallback(
    (e: ReactPointerEvent<SVGSVGElement>) => {
      const ps = panState.current;
      if (!ps || ps.pointerId !== e.pointerId) return;
      const svg = svgRef.current;
      if (!svg) return;
      const rect = svg.getBoundingClientRect();
      const dx = ((e.clientX - ps.startX) / rect.width) * ps.startVb.w;
      const dy = ((e.clientY - ps.startY) / rect.height) * ps.startVb.h;
      setViewBox({
        x: ps.startVb.x - dx,
        y: ps.startVb.y - dy,
        w: ps.startVb.w,
        h: ps.startVb.h,
      });
    },
    []
  );

  const endPan = useCallback(() => {
    panState.current = null;
    setIsPanning(false);
  }, []);

  const reset = useCallback(() => {
    animateTo({ x: 0, y: 0, w: BASE_W, h: BASE_H });
  }, [animateTo]);

  const fit = useCallback(() => {
    const pad = 80;
    const w = contentBbox.maxX - contentBbox.minX + pad * 2;
    const h = contentBbox.maxY - contentBbox.minY + pad * 2;
    const aspect = BASE_W / BASE_H;
    let vbW = w;
    let vbH = h;
    if (w / h > aspect) vbH = w / aspect;
    else vbW = h * aspect;
    animateTo({
      x: (contentBbox.minX + contentBbox.maxX) / 2 - vbW / 2,
      y: (contentBbox.minY + contentBbox.maxY) / 2 - vbH / 2,
      w: vbW,
      h: vbH,
    });
  }, [contentBbox, animateTo]);

  const zoomButton = useCallback(
    (factor: number) => {
      const svg = svgRef.current;
      if (!svg) return;
      const rect = svg.getBoundingClientRect();
      zoomAt(
        rect.left + rect.width / 2,
        rect.top + rect.height / 2,
        factor,
        true
      );
    },
    [zoomAt]
  );

  const onKeyDown = useCallback(
    (e: React.KeyboardEvent<SVGSVGElement>) => {
      if (e.key === "+" || e.key === "=") {
        e.preventDefault();
        zoomButton(1.25);
      } else if (e.key === "-" || e.key === "_") {
        e.preventDefault();
        zoomButton(1 / 1.25);
      } else if (e.key === "0") {
        e.preventDefault();
        reset();
      } else if (e.key === "f" || e.key === "F") {
        e.preventDefault();
        fit();
      } else if (
        e.key === "ArrowUp" ||
        e.key === "ArrowDown" ||
        e.key === "ArrowLeft" ||
        e.key === "ArrowRight"
      ) {
        e.preventDefault();
        const vb = viewBoxRef.current;
        const step = vb.w * 0.08;
        const dx =
          e.key === "ArrowLeft" ? -step : e.key === "ArrowRight" ? step : 0;
        const dy =
          e.key === "ArrowUp" ? -step : e.key === "ArrowDown" ? step : 0;
        animateTo({ x: vb.x + dx, y: vb.y + dy, w: vb.w, h: vb.h });
      }
    },
    [zoomButton, reset, fit, animateTo]
  );

  useEffect(() => () => cancelAnim(), [cancelAnim]);

  const stateColor = (state: string) => {
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
  const connectedNodes = nodes.filter(
    (n) => n.isConnected && n.nodeId !== "local"
  ).length;
  const totalRemoteNodes = nodes.filter((n) => n.nodeId !== "local").length;
  const zoomPct = Math.round((BASE_W / viewBox.w) * 100);

  return (
    <Card className="overflow-hidden border-slate-800/80">
      <CardContent className="p-0">
        <div className="relative select-none">
          <svg
            ref={svgRef}
            viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
            className="w-full h-[580px] bg-[radial-gradient(ellipse_at_center,_#0b1220_0%,_#020617_75%)] touch-none outline-none"
            style={{ cursor: isPanning ? "grabbing" : "grab" }}
            xmlns="http://www.w3.org/2000/svg"
            tabIndex={0}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={endPan}
            onPointerCancel={endPan}
            onPointerLeave={() => {
              setHovered(null);
              setHoveredId(null);
            }}
            onKeyDown={onKeyDown}
          >
            <defs>
              <radialGradient id="controller-glow" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="rgba(56, 189, 248, 0.32)" />
                <stop offset="60%" stopColor="rgba(56, 189, 248, 0.05)" />
                <stop offset="100%" stopColor="rgba(56, 189, 248, 0)" />
              </radialGradient>
              <radialGradient id="node-surface" cx="50%" cy="35%" r="65%">
                <stop offset="0%" stopColor="#1e293b" />
                <stop offset="100%" stopColor="#0b1220" />
              </radialGradient>
              <radialGradient
                id="controller-surface"
                cx="50%"
                cy="35%"
                r="65%"
              >
                <stop offset="0%" stopColor="#1e3a5f" />
                <stop offset="100%" stopColor="#0b1220" />
              </radialGradient>
              <pattern
                id="grid-fine"
                x="0"
                y="0"
                width="40"
                height="40"
                patternUnits="userSpaceOnUse"
              >
                <path
                  d="M 40 0 L 0 0 0 40"
                  fill="none"
                  stroke="rgba(148, 163, 184, 0.05)"
                  strokeWidth="1"
                />
              </pattern>
              <pattern
                id="grid-coarse"
                x="0"
                y="0"
                width="200"
                height="200"
                patternUnits="userSpaceOnUse"
              >
                <path
                  d="M 200 0 L 0 0 0 200"
                  fill="none"
                  stroke="rgba(148, 163, 184, 0.1)"
                  strokeWidth="1"
                />
              </pattern>
              <filter
                id="soft-shadow"
                x="-50%"
                y="-50%"
                width="200%"
                height="200%"
              >
                <feGaussianBlur stdDeviation="6" />
              </filter>
            </defs>

            {/* Expansive grid so panning feels like an infinite canvas */}
            <rect
              x={-5000}
              y={-5000}
              width={10000}
              height={10000}
              fill="url(#grid-fine)"
            />
            <rect
              x={-5000}
              y={-5000}
              width={10000}
              height={10000}
              fill="url(#grid-coarse)"
            />

            <circle
              cx={center.cx}
              cy={center.cy}
              r="260"
              fill="url(#controller-glow)"
            />

            {/* Curved bezier connections controller ↔ each remote node */}
            {svgNodes.map(({ node, x, y }) => {
              const mx = (center.cx + x) / 2;
              const my = (center.cy + y) / 2;
              const dx = x - center.cx;
              const dy = y - center.cy;
              const len = Math.hypot(dx, dy) || 1;
              const nxp = -dy / len;
              const nyp = dx / len;
              const bow = Math.min(60, len * 0.12);
              const cxp = mx + nxp * bow;
              const cyp = my + nyp * bow;
              const pathD = `M ${center.cx},${center.cy} Q ${cxp},${cyp} ${x},${y}`;
              const hot = hoveredId === `node:${node.nodeId}`;
              return (
                <g key={`link-${node.nodeId}`}>
                  <path
                    d={pathD}
                    fill="none"
                    stroke={node.isConnected ? "#38bdf8" : "#475569"}
                    strokeWidth={hot ? 2.5 : node.isConnected ? 1.5 : 1}
                    strokeDasharray={node.isConnected ? undefined : "4 4"}
                    opacity={hot ? 1 : node.isConnected ? 0.65 : 0.3}
                    strokeLinecap="round"
                    style={{ transition: "stroke-width 200ms, opacity 200ms" }}
                  />
                  {node.isConnected && (
                    <circle r="3.5" fill="#38bdf8" opacity="0.95">
                      <animateMotion
                        dur="2.6s"
                        repeatCount="indefinite"
                        path={pathD}
                      />
                    </circle>
                  )}
                </g>
              );
            })}

            {/* Controller node */}
            <g>
              <circle
                cx={center.cx}
                cy={center.cy + 4}
                r="56"
                fill="#000"
                opacity="0.45"
                filter="url(#soft-shadow)"
              />
              <circle
                cx={center.cx}
                cy={center.cy}
                r="54"
                fill="url(#controller-surface)"
                stroke="#38bdf8"
                strokeWidth="2"
              />
              <circle
                cx={center.cx}
                cy={center.cy}
                r="54"
                fill="none"
                stroke="#38bdf8"
                strokeWidth="2"
                opacity="0.4"
              >
                <animate
                  attributeName="r"
                  from="54"
                  to="72"
                  dur="2.6s"
                  repeatCount="indefinite"
                />
                <animate
                  attributeName="opacity"
                  from="0.45"
                  to="0"
                  dur="2.6s"
                  repeatCount="indefinite"
                />
              </circle>
              <text
                x={center.cx}
                y={center.cy - 3}
                textAnchor="middle"
                fontFamily="ui-monospace, monospace"
                fontSize="11"
                fill="#e2e8f0"
                fontWeight="600"
                letterSpacing="0.5"
                pointerEvents="none"
              >
                {controllerLabel.toUpperCase()}
              </text>
              <text
                x={center.cx}
                y={center.cy + 12}
                textAnchor="middle"
                fontFamily="ui-monospace, monospace"
                fontSize="9"
                fill="#64748b"
                pointerEvents="none"
              >
                {connectedNodes}/{totalRemoteNodes} nodes · {totalServices} svc
              </text>
            </g>

            {/* Local services hanging off the controller */}
            {svgServices.map(({ service, x, y }) => {
              const hot = hoveredId === `svc:${service.name}`;
              const svcColor = stateColor(service.state);
              return (
                <g key={`local-${service.name}`}>
                  <line
                    x1={center.cx - 54}
                    y1={center.cy}
                    x2={x + 6}
                    y2={y}
                    stroke={hot ? "#94a3b8" : "#475569"}
                    strokeWidth="1"
                    opacity={hot ? 0.9 : 0.5}
                    style={{ transition: "stroke 200ms, opacity 200ms" }}
                  />
                  <circle
                    cx={x}
                    cy={y}
                    r={hot ? 7.5 : 6}
                    fill={svcColor}
                    stroke={hot ? "#e2e8f0" : "#0f172a"}
                    strokeWidth="1.5"
                    style={{ transition: "r 180ms ease-out, stroke 180ms" }}
                    onPointerEnter={(e) => {
                      setHoveredId(`svc:${service.name}`);
                      setHovered({
                        title: service.name,
                        accent: svcColor,
                        body: [
                          ["state", service.state],
                          ["group", service.groupName],
                          ["node", "local"],
                          ...((service.sync
                            ? [["sync", "persistent"]]
                            : []) as Array<[string, string]>),
                        ],
                        clientX: e.clientX,
                        clientY: e.clientY,
                      });
                    }}
                    onPointerLeave={() => {
                      setHoveredId(null);
                      setHovered(null);
                    }}
                  />
                  <text
                    x={x - 14}
                    y={y + 3}
                    textAnchor="end"
                    fontFamily="ui-monospace, monospace"
                    fontSize="9"
                    fill={hot ? "#e2e8f0" : "#94a3b8"}
                    pointerEvents="none"
                    style={{ transition: "fill 180ms" }}
                  >
                    {service.name}
                  </text>
                </g>
              );
            })}

            {/* Remote nodes with their services */}
            {svgNodes.map(({ node, x, y, nodeServices }) => {
              const memoryPct =
                node.memoryTotalMb > 0
                  ? Math.min(
                      100,
                      (node.memoryUsedMb / node.memoryTotalMb) * 100
                    )
                  : 0;
              const svcRadius = 46;
              const hotNode = hoveredId === `node:${node.nodeId}`;
              const nodeAccent = node.isConnected ? "#22c55e" : "#475569";
              return (
                <g key={`node-${node.nodeId}`}>
                  <circle
                    cx={x}
                    cy={y + 4}
                    r="38"
                    fill="#000"
                    opacity="0.4"
                    filter="url(#soft-shadow)"
                  />
                  <circle
                    cx={x}
                    cy={y}
                    r={hotNode ? 38 : 36}
                    fill="url(#node-surface)"
                    stroke={nodeAccent}
                    strokeWidth={hotNode ? 2.5 : 2}
                    opacity={node.isConnected ? 1 : 0.6}
                    style={{
                      transition:
                        "r 180ms ease-out, stroke-width 180ms ease-out",
                    }}
                    onPointerEnter={(e) => {
                      setHoveredId(`node:${node.nodeId}`);
                      setHovered({
                        title: node.nodeId,
                        accent: nodeAccent,
                        body: [
                          [
                            "status",
                            node.isConnected ? "connected" : "offline",
                          ],
                          [
                            "services",
                            `${node.currentServices}/${node.maxServices}`,
                          ],
                          [
                            "memory",
                            `${Math.round(node.memoryUsedMb)} / ${Math.round(
                              node.memoryTotalMb
                            )} MB`,
                          ],
                          ["cpu", `${Math.round(node.cpuUsage)}%`],
                        ],
                        clientX: e.clientX,
                        clientY: e.clientY,
                      });
                    }}
                    onPointerLeave={() => {
                      setHoveredId(null);
                      setHovered(null);
                    }}
                  />
                  <circle
                    cx={x}
                    cy={y}
                    r="36"
                    fill="none"
                    stroke={nodeAccent}
                    strokeWidth="3"
                    strokeDasharray={`${(memoryPct / 100) * 226} 226`}
                    transform={`rotate(-90 ${x} ${y})`}
                    opacity="0.7"
                    pointerEvents="none"
                    strokeLinecap="round"
                  />
                  <text
                    x={x}
                    y={y - 3}
                    textAnchor="middle"
                    fontFamily="ui-monospace, monospace"
                    fontSize="10"
                    fill="#e2e8f0"
                    fontWeight="600"
                    letterSpacing="0.3"
                    pointerEvents="none"
                  >
                    {node.nodeId}
                  </text>
                  <text
                    x={x}
                    y={y + 10}
                    textAnchor="middle"
                    fontFamily="ui-monospace, monospace"
                    fontSize="8"
                    fill="#64748b"
                    pointerEvents="none"
                  >
                    {node.currentServices}/{node.maxServices}
                  </text>

                  {nodeServices.map((s, i) => {
                    const svcAngle =
                      (i * 2 * Math.PI) / Math.max(nodeServices.length, 1) -
                      Math.PI / 2;
                    const sx = x + Math.cos(svcAngle) * (36 + svcRadius);
                    const sy = y + Math.sin(svcAngle) * (36 + svcRadius);
                    const hot = hoveredId === `svc:${s.name}`;
                    const svcColor = stateColor(s.state);
                    return (
                      <g key={s.name}>
                        <line
                          x1={x + Math.cos(svcAngle) * 36}
                          y1={y + Math.sin(svcAngle) * 36}
                          x2={sx}
                          y2={sy}
                          stroke={hot ? "#94a3b8" : "#475569"}
                          strokeWidth="1"
                          opacity={hot ? 0.9 : 0.5}
                          style={{
                            transition: "stroke 180ms, opacity 180ms",
                          }}
                        />
                        <circle
                          cx={sx}
                          cy={sy}
                          r={hot ? 7.5 : 6}
                          fill={svcColor}
                          stroke={hot ? "#e2e8f0" : "#0f172a"}
                          strokeWidth="1.5"
                          style={{
                            transition: "r 180ms ease-out, stroke 180ms",
                          }}
                          onPointerEnter={(e) => {
                            setHoveredId(`svc:${s.name}`);
                            setHovered({
                              title: s.name,
                              accent: svcColor,
                              body: [
                                ["state", s.state],
                                ["group", s.groupName],
                                ["node", node.nodeId],
                                ...((s.sync
                                  ? [["sync", "persistent"]]
                                  : []) as Array<[string, string]>),
                              ],
                              clientX: e.clientX,
                              clientY: e.clientY,
                            });
                          }}
                          onPointerLeave={() => {
                            setHoveredId(null);
                            setHovered(null);
                          }}
                        />
                        <text
                          x={sx}
                          y={sy + 18}
                          textAnchor="middle"
                          fontFamily="ui-monospace, monospace"
                          fontSize="8"
                          fill={hot ? "#e2e8f0" : "#94a3b8"}
                          pointerEvents="none"
                          style={{ transition: "fill 180ms" }}
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

          {/* Zoom readout */}
          <div className="absolute top-3 left-3 flex items-center gap-1.5 text-[10px] font-mono text-slate-300 bg-slate-950/75 backdrop-blur-md rounded-md px-2.5 py-1.5 border border-slate-800 shadow-lg shadow-slate-950/40">
            <span className="text-slate-500">zoom</span>
            <span className="tabular-nums text-slate-200">{zoomPct}%</span>
          </div>

          {/* Floating controls */}
          <div className="absolute top-3 right-3 flex flex-col bg-slate-950/80 backdrop-blur-md rounded-lg border border-slate-800 shadow-lg shadow-slate-950/40 overflow-hidden">
            <ControlButton
              label="Zoom in (+)"
              onClick={() => zoomButton(1.25)}
            >
              <IconPlus />
            </ControlButton>
            <div className="h-px bg-slate-800/80" />
            <ControlButton
              label="Zoom out (−)"
              onClick={() => zoomButton(1 / 1.25)}
            >
              <IconMinus />
            </ControlButton>
            <div className="h-px bg-slate-800/80" />
            <ControlButton label="Fit to content (F)" onClick={fit}>
              <IconFit />
            </ControlButton>
            <div className="h-px bg-slate-800/80" />
            <ControlButton label="Reset view (0)" onClick={reset}>
              <IconReset />
            </ControlButton>
          </div>

          {/* Hover tooltip */}
          {hovered && (
            <div
              className="pointer-events-none absolute z-10 bg-slate-950/95 border border-slate-700 rounded-lg shadow-xl shadow-slate-950/60 px-3 py-2.5 text-[10px] font-mono min-w-[184px]"
              style={{
                left: tooltipLeft(hovered.clientX, svgRef.current),
                top: tooltipTop(hovered.clientY, svgRef.current),
              }}
            >
              <div className="flex items-center gap-2 mb-2 pb-2 border-b border-slate-800">
                <span
                  className="inline-block w-2 h-2 rounded-full shrink-0"
                  style={{
                    background: hovered.accent,
                    boxShadow: `0 0 8px ${hovered.accent}80`,
                  }}
                />
                <span className="text-slate-100 font-semibold text-[11px] tracking-wide truncate">
                  {hovered.title}
                </span>
              </div>
              <div className="flex flex-col gap-1">
                {hovered.body.map(([k, v]) => (
                  <div key={k} className="flex gap-3 justify-between">
                    <span className="text-slate-500">{k}</span>
                    <span className="text-slate-200 tabular-nums text-right">
                      {v}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Legend */}
          <div className="absolute bottom-3 left-3 flex items-center gap-3 text-[10px] font-mono text-slate-400 bg-slate-950/75 backdrop-blur-md rounded-md px-3 py-1.5 border border-slate-800 shadow-lg shadow-slate-950/40">
            <LegendDot color="#22c55e" label="ready" />
            <LegendDot color="#eab308" label="starting" />
            <LegendDot color="#f97316" label="stopping" />
            <LegendDot color="#ef4444" label="crashed" />
            <LegendDot color="#64748b" label="stopped" />
          </div>

          {/* Hint */}
          <div className="absolute bottom-3 right-3 text-[10px] font-mono text-slate-500 bg-slate-950/75 backdrop-blur-md rounded-md px-2.5 py-1.5 border border-slate-800 shadow-lg shadow-slate-950/40">
            drag · scroll ·{" "}
            <span className="text-slate-300">+ − 0 F</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function tooltipLeft(clientX: number, svg: SVGSVGElement | null) {
  if (!svg) return 0;
  const rect = svg.getBoundingClientRect();
  return Math.min(Math.max(clientX - rect.left + 16, 8), rect.width - 220);
}

function tooltipTop(clientY: number, svg: SVGSVGElement | null) {
  if (!svg) return 0;
  const rect = svg.getBoundingClientRect();
  return Math.min(Math.max(clientY - rect.top + 16, 8), rect.height - 140);
}

function ControlButton({
  children,
  label,
  onClick,
}: {
  children: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      className="w-8 h-8 flex items-center justify-center text-slate-400 hover:bg-slate-800/80 hover:text-slate-100 active:bg-slate-800 transition-colors duration-150"
    >
      {children}
    </button>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span
        className="inline-block w-1.5 h-1.5 rounded-full"
        style={{ background: color, boxShadow: `0 0 6px ${color}80` }}
      />
      <span className="tracking-wide">{label}</span>
    </span>
  );
}

function IconPlus() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
      <path
        d="M7 2v10M2 7h10"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function IconMinus() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
      <path
        d="M2 7h10"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function IconFit() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
      <path
        d="M2 5V2h3M12 5V2H9M2 9v3h3M12 9v3H9"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function IconReset() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
      <path
        d="M2 7a5 5 0 1 0 1.5-3.5L2 5"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M2 2v3h3"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
