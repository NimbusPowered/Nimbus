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

type Pos = { x: number; y: number };

const BASE_W = 1600;
const BASE_H = 900;
const MIN_ZOOM = 0.25;
const MAX_ZOOM = 4;
const ANIM_MS = 260;

const CTRL_W = 240;
const CTRL_H = 72;
const NODE_W = 240;
const NODE_H = 80;
const SVC_W = 220;
const SVC_H = 44;
const NODE_GAP_X = 60;
const SVC_GAP_Y = 10;
const CTRL_KEY = "__controller__";

/**
 * Railway-inspired cluster topology: rounded card nodes connected by cubic
 * bezier edges, free-form draggable within a pan/zoom canvas. Controller at
 * top, nodes in a row below, services stacked under their parent node.
 * Local services live to the left of the controller.
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
  const dragState = useRef<{
    pointerId: number;
    key: string;
    startX: number;
    startY: number;
    startPos: Pos;
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
  const [positions, setPositions] = useState<Record<string, Pos>>({});
  const hasAutoFit = useRef(false);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [draggingKey, setDraggingKey] = useState<string | null>(null);

  useEffect(() => {
    viewBoxRef.current = viewBox;
  }, [viewBox]);

  const remoteNodes = useMemo(
    () => nodes.filter((n) => n.nodeId !== "local"),
    [nodes]
  );
  const localServices = useMemo(
    () => services.filter((s) => s.nodeId === "local" || s.nodeId === ""),
    [services]
  );

  // Auto-layout: compute a position for any element that doesn't already
  // have one persisted. Existing drag positions are preserved across polls.
  useEffect(() => {
    setPositions((prev) => {
      const next: Record<string, Pos> = { ...prev };
      const put = (key: string, pos: Pos) => {
        if (!next[key]) next[key] = pos;
      };

      // Controller: centered at top.
      const ctrlX = (BASE_W - CTRL_W) / 2;
      const ctrlY = 80;
      put(CTRL_KEY, { x: ctrlX, y: ctrlY });

      // Nodes: evenly spaced row below controller, centered horizontally.
      const nodeGap = 80;
      const totalNodesW =
        remoteNodes.length * NODE_W +
        Math.max(0, remoteNodes.length - 1) * nodeGap;
      const nodesStartX = (BASE_W - totalNodesW) / 2;
      const nodesY = ctrlY + CTRL_H + 120;

      remoteNodes.forEach((n, i) => {
        put(`node:${n.nodeId}`, {
          x: nodesStartX + i * (NODE_W + nodeGap),
          y: nodesY,
        });
      });

      // Services stacked below their parent node, centered underneath.
      const svcGap = 12;
      remoteNodes.forEach((n) => {
        const parentPos = next[`node:${n.nodeId}`];
        if (!parentPos) return;
        const siblings = services.filter((s) => s.nodeId === n.nodeId);
        siblings.forEach((s, i) => {
          put(`svc:${s.name}`, {
            x: parentPos.x + (NODE_W - SVC_W) / 2,
            y: parentPos.y + NODE_H + 48 + i * (SVC_H + svcGap),
          });
        });
      });

      // Local services: grid below controller when no remote nodes (single-node),
      // vertical stack to the left when remote nodes exist.
      const ctrlPos = next[CTRL_KEY]!;
      if (remoteNodes.length > 0) {
        localServices.forEach((s, i) => {
          put(`svc:${s.name}`, {
            x: ctrlPos.x - SVC_W - 100,
            y: ctrlPos.y + i * (SVC_H + svcGap),
          });
        });
      } else {
        const cols = Math.min(3, Math.max(1, localServices.length));
        const colGap = 24;
        const rowGap = 16;
        const gridW = cols * SVC_W + (cols - 1) * colGap;
        const startX = (BASE_W - gridW) / 2;
        const startY = ctrlPos.y + CTRL_H + 100;
        localServices.forEach((s, i) => {
          const col = i % cols;
          const row = Math.floor(i / cols);
          put(`svc:${s.name}`, {
            x: startX + col * (SVC_W + colGap),
            y: startY + row * (SVC_H + rowGap),
          });
        });
      }

      // Drop entries whose element no longer exists.
      const alive = new Set<string>([CTRL_KEY]);
      remoteNodes.forEach((n) => alive.add(`node:${n.nodeId}`));
      services.forEach((s) => alive.add(`svc:${s.name}`));
      Object.keys(next).forEach((k) => {
        if (!alive.has(k)) delete next[k];
      });

      return next;
    });
  }, [remoteNodes, localServices, services]);

  // ---------- Viewport helpers ----------

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
        const k = 1 - Math.pow(1 - t, 3);
        setViewBox({
          x: from.x + (target.x - from.x) * k,
          y: from.y + (target.y - from.y) * k,
          w: from.w + (target.w - from.w) * k,
          h: from.h + (target.h - from.h) * k,
        });
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
      const level = BASE_W / newW;
      if (level < MIN_ZOOM || level > MAX_ZOOM) return;
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

  // ---------- Pan ----------

  const onBackgroundPointerDown = useCallback(
    (e: ReactPointerEvent<SVGSVGElement>) => {
      if (e.button !== 0 && e.pointerType === "mouse") return;
      // Only pan when the target is the canvas background, not a card.
      const target = e.target as Element;
      if (target.closest("[data-draggable]")) return;
      cancelAnim();
      (e.currentTarget as Element).setPointerCapture?.(e.pointerId);
      panState.current = {
        pointerId: e.pointerId,
        startX: e.clientX,
        startY: e.clientY,
        startVb: viewBoxRef.current,
      };
      setIsPanning(true);
    },
    [cancelAnim]
  );

  const onPointerMove = useCallback(
    (e: ReactPointerEvent<SVGSVGElement>) => {
      const ds = dragState.current;
      if (ds && ds.pointerId === e.pointerId) {
        const vb = viewBoxRef.current;
        const svg = svgRef.current;
        if (!svg) return;
        const rect = svg.getBoundingClientRect();
        const dx = ((e.clientX - ds.startX) / rect.width) * vb.w;
        const dy = ((e.clientY - ds.startY) / rect.height) * vb.h;
        setPositions((p) => ({
          ...p,
          [ds.key]: { x: ds.startPos.x + dx, y: ds.startPos.y + dy },
        }));
        return;
      }
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

  const endInteraction = useCallback(() => {
    panState.current = null;
    dragState.current = null;
    setIsPanning(false);
    setDraggingKey(null);
  }, []);

  // ---------- Card drag ----------

  const startCardDrag = useCallback(
    (e: ReactPointerEvent<SVGGElement>, key: string) => {
      e.stopPropagation();
      if (e.button !== 0 && e.pointerType === "mouse") return;
      const pos = positions[key];
      if (!pos) return;
      (svgRef.current as Element | null)?.setPointerCapture?.(e.pointerId);
      dragState.current = {
        pointerId: e.pointerId,
        key,
        startX: e.clientX,
        startY: e.clientY,
        startPos: pos,
      };
      setDraggingKey(key);
    },
    [positions]
  );

  // ---------- Controls ----------

  const contentBbox = useMemo(() => {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    const add = (x: number, y: number, w: number, h: number) => {
      if (x < minX) minX = x;
      if (y < minY) minY = y;
      if (x + w > maxX) maxX = x + w;
      if (y + h > maxY) maxY = y + h;
    };
    const ctrl = positions[CTRL_KEY];
    if (ctrl) add(ctrl.x, ctrl.y, CTRL_W, CTRL_H);
    remoteNodes.forEach((n) => {
      const p = positions[`node:${n.nodeId}`];
      if (p) add(p.x, p.y, NODE_W, NODE_H);
    });
    services.forEach((s) => {
      const p = positions[`svc:${s.name}`];
      if (p) add(p.x, p.y, SVC_W, SVC_H);
    });
    if (!isFinite(minX)) {
      minX = 0;
      minY = 0;
      maxX = BASE_W;
      maxY = BASE_H;
    }
    return { minX, minY, maxX, maxY };
  }, [positions, remoteNodes, services]);

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

  // Auto-fit on first meaningful layout so users don't see a mostly-empty canvas.
  useEffect(() => {
    if (hasAutoFit.current) return;
    const hasContent = Object.keys(positions).length > 1;
    if (!hasContent) return;
    hasAutoFit.current = true;
    // Small timeout so the SVG has mounted and contentBbox is accurate.
    const id = setTimeout(fit, 80);
    return () => clearTimeout(id);
  }, [positions, fit]);

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

  // ---------- Styling helpers ----------

  const stateColor = (state: string) => {
    switch (state) {
      case "READY":
        return "#10b981"; // emerald-500
      case "STARTING":
      case "PREPARING":
      case "PREPARED":
        return "#f59e0b"; // amber-500
      case "STOPPING":
      case "DRAINING":
        return "#fb923c"; // orange-400
      case "CRASHED":
        return "#f43f5e"; // rose-500
      case "STOPPED":
        return "#71717a"; // zinc-500
      default:
        return "#71717a";
    }
  };

  const totalServices = services.length;
  const connectedNodes = nodes.filter(
    (n) => n.isConnected && n.nodeId !== "local"
  ).length;
  const totalRemoteNodes = remoteNodes.length;
  const zoomPct = Math.round((BASE_W / viewBox.w) * 100);
  const ctrlPos = positions[CTRL_KEY];

  // ---------- Obstacle-aware orthogonal routing ----------
  // Every card in the scene is collected as a rect. For each edge we find
  // the widest horizontal GAP between source-bottom and target-top (a Y
  // band where no card exists), then route through the center of that gap.
  // This guarantees the horizontal segment never crosses a card.

  const EDGE_PAD = 10; // clearance around cards

  type ORect = { x: number; y: number; w: number; h: number };

  const cardRects = useMemo(() => {
    const rects: Record<string, ORect> = {};
    const ctrl = positions[CTRL_KEY];
    if (ctrl) rects[CTRL_KEY] = { x: ctrl.x, y: ctrl.y, w: CTRL_W, h: CTRL_H };
    remoteNodes.forEach((n) => {
      const p = positions[`node:${n.nodeId}`];
      if (p) rects[`node:${n.nodeId}`] = { x: p.x, y: p.y, w: NODE_W, h: NODE_H };
    });
    services.forEach((s) => {
      const p = positions[`svc:${s.name}`];
      if (p) rects[`svc:${s.name}`] = { x: p.x, y: p.y, w: SVC_W, h: SVC_H };
    });
    return rects;
  }, [positions, remoteNodes, services]);

  // 3-segment step path with sharp 90° corners.
  function buildStep(
    x1: number, y1: number, x2: number, y2: number, cy: number
  ): string {
    if (Math.abs(x2 - x1) < 1) return `M ${x1},${y1} L ${x2},${y2}`;
    return `M ${x1},${y1} L ${x1},${cy} L ${x2},${cy} L ${x2},${y2}`;
  }

  // Find the best Y channel in the gap between y1 and y2. Looks at every
  // obstacle whose X range overlaps the edge's horizontal span, collects
  // the Y intervals they occupy, then returns the center of the widest
  // remaining gap. Falls back to midpoint if no gap is wide enough.
  function findChannel(
    y1: number, y2: number,
    x1: number, x2: number,
    obs: ORect[]
  ): number {
    // Normalize so minY < maxY — handles both downward and upward routing.
    const minY = Math.min(y1, y2);
    const maxY = Math.max(y1, y2);
    const lo = Math.min(x1, x2) - EDGE_PAD;
    const hi = Math.max(x1, x2) + EDGE_PAD;

    const bands: [number, number][] = [];
    for (const o of obs) {
      if (o.x + o.w + EDGE_PAD < lo || o.x - EDGE_PAD > hi) continue;
      const top = o.y - EDGE_PAD;
      const bot = o.y + o.h + EDGE_PAD;
      if (bot <= minY || top >= maxY) continue;
      bands.push([Math.max(minY, top), Math.min(maxY, bot)]);
    }

    if (bands.length === 0) return (y1 + y2) / 2;

    // Merge overlapping bands.
    bands.sort((a, b) => a[0] - b[0]);
    const merged: [number, number][] = [bands[0]];
    for (let i = 1; i < bands.length; i++) {
      const prev = merged[merged.length - 1];
      if (bands[i][0] <= prev[1]) {
        prev[1] = Math.max(prev[1], bands[i][1]);
      } else {
        merged.push(bands[i]);
      }
    }

    // Find the widest gap between merged bands, including edges.
    let bestCenter = (y1 + y2) / 2;
    let bestSize = 0;

    let prev = minY;
    for (const [bTop, bBot] of merged) {
      const gapSize = bTop - prev;
      if (gapSize > bestSize) {
        bestSize = gapSize;
        bestCenter = (prev + bTop) / 2;
      }
      prev = bBot;
    }
    // Final gap after last band.
    const finalGap = maxY - prev;
    if (finalGap > bestSize) {
      bestCenter = (prev + maxY) / 2;
    }

    return bestCenter;
  }

  // Main routing function: orthogonal step path that avoids all cards
  // except the source and target (identified by excludeKeys).
  const routeEdge = useCallback(
    (
      x1: number, y1: number,
      x2: number, y2: number,
      excludeKeys: string[]
    ): string => {
      // Straight vertical — no horizontal segment needed.
      if (Math.abs(x1 - x2) < 2) return `M ${x1},${y1} L ${x2},${y2}`;

      const obs = Object.entries(cardRects)
        .filter(([k]) => !excludeKeys.includes(k))
        .map(([, r]) => r);

      const cy = findChannel(y1, y2, x1, x2, obs);
      return buildStep(x1, y1, x2, y2, cy);
    },
    [cardRects]
  );

  // Dynamic port selection + routing. Picks the best attachment side on
  // each card based on relative position, then routes through the correct
  // axis (vertical step for top/bottom ports, horizontal step for left/right).
  function connect(
    srcKey: string,
    srcR: ORect,
    dstKey: string,
    dstR: ORect
  ): string {
    const scx = srcR.x + srcR.w / 2;
    const scy = srcR.y + srcR.h / 2;
    const dcx = dstR.x + dstR.w / 2;
    const dcy = dstR.y + dstR.h / 2;
    const dx = dcx - scx;
    const dy = dcy - scy;

    // Prefer vertical routing (natural for tree layout) unless target is
    // clearly to the side (|dx| > |dy| * 1.3).
    const useH = Math.abs(dx) > Math.abs(dy) * 1.3;

    if (useH) {
      // Horizontal flow: exit right/left → enter left/right.
      let x1: number, y1: number, x2: number, y2: number;
      if (dx >= 0) {
        x1 = srcR.x + srcR.w;
        y1 = scy;
        x2 = dstR.x;
        y2 = dcy;
      } else {
        x1 = srcR.x;
        y1 = scy;
        x2 = dstR.x + dstR.w;
        y2 = dcy;
      }
      if (Math.abs(y1 - y2) < 1) return `M ${x1},${y1} L ${x2},${y2}`;

      // Find X channel (vertical segment in horizontal flow).
      const obs = Object.entries(cardRects)
        .filter(([k]) => k !== srcKey && k !== dstKey)
        .map(([, r]) => r);
      const yLo = Math.min(y1, y2) - EDGE_PAD;
      const yHi = Math.max(y1, y2) + EDGE_PAD;
      const xLo = Math.min(x1, x2);
      const xHi = Math.max(x1, x2);
      const bands: [number, number][] = [];
      for (const o of obs) {
        if (o.y + o.h + EDGE_PAD < yLo || o.y - EDGE_PAD > yHi) continue;
        const l = o.x - EDGE_PAD;
        const rr = o.x + o.w + EDGE_PAD;
        if (rr <= xLo || l >= xHi) continue;
        bands.push([Math.max(xLo, l), Math.min(xHi, rr)]);
      }
      let cx = (x1 + x2) / 2;
      if (bands.length > 0) {
        bands.sort((a, b) => a[0] - b[0]);
        const merged: [number, number][] = [bands[0]];
        for (let i = 1; i < bands.length; i++) {
          const prev = merged[merged.length - 1];
          if (bands[i][0] <= prev[1]) prev[1] = Math.max(prev[1], bands[i][1]);
          else merged.push(bands[i]);
        }
        let bestSz = 0;
        let prev = xLo;
        for (const [bL, bR] of merged) {
          if (bL - prev > bestSz) { bestSz = bL - prev; cx = (prev + bL) / 2; }
          prev = bR;
        }
        if (xHi - prev > bestSz) cx = (prev + xHi) / 2;
      }

      const down = y2 > y1;
      const right = x2 > x1;
      return `M ${x1},${y1} L ${cx},${y1} L ${cx},${y2} L ${x2},${y2}`;
    }

    // Vertical flow: exit bottom/top → enter top/bottom.
    let x1: number, y1: number, x2: number, y2: number;
    if (dy >= 0) {
      x1 = scx;
      y1 = srcR.y + srcR.h;
      x2 = dcx;
      y2 = dstR.y;
    } else {
      x1 = scx;
      y1 = srcR.y;
      x2 = dcx;
      y2 = dstR.y + dstR.h;
    }
    return routeEdge(x1, y1, x2, y2, [srcKey, dstKey]);
  }

  return (
    <Card className="overflow-hidden border-border">
      <CardContent className="p-0">
        <div className="relative select-none">
          <svg
            ref={svgRef}
            viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
            className="w-full h-[620px] touch-none outline-none"
            style={{
              cursor: draggingKey
                ? "grabbing"
                : isPanning
                  ? "grabbing"
                  : "grab",
              fontFamily: "var(--font-space), ui-sans-serif, system-ui",
            }}
            xmlns="http://www.w3.org/2000/svg"
            tabIndex={0}
            onPointerDown={onBackgroundPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={endInteraction}
            onPointerCancel={endInteraction}
            onKeyDown={onKeyDown}
          >
            <defs>
              <pattern
                id="dots"
                x="0"
                y="0"
                width="32"
                height="32"
                patternUnits="userSpaceOnUse"
              >
                <circle cx="1" cy="1" r="1" fill="rgba(161, 161, 170, 0.22)" />
              </pattern>
            </defs>

            {/* Dotted canvas background (Railway / Excalidraw vibe) */}
            <rect
              x={-5000}
              y={-5000}
              width={10000}
              height={10000}
              fill="url(#dots)"
            />

            {/* Edges: controller → node */}
            {ctrlPos &&
              remoteNodes.map((n) => {
                const np = positions[`node:${n.nodeId}`];
                if (!np) return null;
                const nk = `node:${n.nodeId}`;
                const d = connect(
                  CTRL_KEY, { x: ctrlPos.x, y: ctrlPos.y, w: CTRL_W, h: CTRL_H },
                  nk, { x: np.x, y: np.y, w: NODE_W, h: NODE_H }
                );
                const hot =
                  hoveredId === `node:${n.nodeId}` ||
                  draggingKey === `node:${n.nodeId}`;
                return (
                  <g key={`edge-ctrl-${n.nodeId}`}>
                    <path
                      d={d}
                      fill="none"
                      stroke={
                        hot ? "#71717a" : n.isConnected ? "rgba(255,255,255,0.18)" : "rgba(255,255,255,0.08)"
                      }
                      strokeWidth={1.5}
                      strokeDasharray={n.isConnected ? undefined : "4 4"}
                      strokeLinecap="round"
                    />
                    {n.isConnected && (
                      <circle r="3" fill="#38bdf8" opacity="0">
                        {/*
                          2.5s cycle: the dot travels the path in the first
                          0.9s (travel fraction = 0.36), then stays hidden for
                          the remainder so each heartbeat reads as one pulse.
                        */}
                        <animateMotion
                          dur="2.5s"
                          repeatCount="indefinite"
                          path={d}
                          keyPoints="0;1;1"
                          keyTimes="0;0.36;1"
                          calcMode="linear"
                        />
                        <animate
                          attributeName="opacity"
                          values="0;1;1;0;0"
                          keyTimes="0;0.04;0.32;0.36;1"
                          dur="2.5s"
                          repeatCount="indefinite"
                        />
                      </circle>
                    )}
                  </g>
                );
              })}

            {/* Edges: node → service (vertical stack) with cascading pulse.
                 The service pulse waits for the controller→node dot to arrive
                 (0.36 of the 2.5s cycle = 0.9s), then a smaller dot travels
                 from node to service over the next 0.5s. */}
            {remoteNodes.map((n) => {
              const np = positions[`node:${n.nodeId}`];
              if (!np) return null;
              const siblings = services.filter((s) => s.nodeId === n.nodeId);
              return siblings.map((s, svcIdx) => {
                const sp = positions[`svc:${s.name}`];
                if (!sp) return null;
                const nk = `node:${n.nodeId}`;
                const sk = `svc:${s.name}`;
                const d = connect(
                  nk, { x: np.x, y: np.y, w: NODE_W, h: NODE_H },
                  sk, { x: sp.x, y: sp.y, w: SVC_W, h: SVC_H }
                );
                const hot =
                  hoveredId === `svc:${s.name}` ||
                  draggingKey === `svc:${s.name}`;
                // Brief pause after the parent dot arrives (0.36), then stagger
                // each service so pulses fan out sequentially.
                const delay = 0.44;
                const stagger = svcIdx * 0.024;
                const enterStart = delay + stagger;
                const enterEnd = Math.min(enterStart + 0.16, 0.80);
                return (
                  <g key={`edge-node-${s.name}`}>
                    <path
                      d={d}
                      fill="none"
                      stroke={hot ? "#71717a" : "rgba(255,255,255,0.08)"}
                      strokeWidth={1.5}
                      strokeLinecap="round"
                    />
                    {n.isConnected && (
                      <circle r="2" fill="#10b981" opacity="0">
                        <animateMotion
                          dur="2.5s"
                          repeatCount="indefinite"
                          path={d}
                          keyPoints={`0;0;1;1`}
                          keyTimes={`0;${enterStart};${enterEnd};1`}
                          calcMode="linear"
                        />
                        <animate
                          attributeName="opacity"
                          values={`0;0;0.9;0.9;0;0`}
                          keyTimes={`0;${enterStart};${enterStart + 0.02};${enterEnd - 0.02};${enterEnd};1`}
                          dur="2.5s"
                          repeatCount="indefinite"
                        />
                      </circle>
                    )}
                  </g>
                );
              });
            })}

            {/* Edges: controller ↔ local service. Horizontal routing when
                 the service sits beside the controller (remote nodes case),
                 vertical routing when it's below (single-node case). */}
            {ctrlPos &&
              localServices.map((s, i) => {
                const sp = positions[`svc:${s.name}`];
                if (!sp) return null;
                const svcKey = `svc:${s.name}`;
                const d = connect(
                  CTRL_KEY, { x: ctrlPos.x, y: ctrlPos.y, w: CTRL_W, h: CTRL_H },
                  svcKey, { x: sp.x, y: sp.y, w: SVC_W, h: SVC_H }
                );
                const hot = hoveredId === `svc:${s.name}`;
                const stagger = i * 0.016;
                return (
                  <g key={`edge-local-${s.name}`}>
                    <path
                      d={d}
                      fill="none"
                      stroke={hot ? "#71717a" : "rgba(255,255,255,0.08)"}
                      strokeWidth={1.5}
                      strokeLinecap="round"
                    />
                    <circle r="2" fill="#38bdf8" opacity="0">
                      <animateMotion
                        dur="2.5s"
                        repeatCount="indefinite"
                        path={d}
                        keyPoints={`0;0;1;1`}
                        keyTimes={`0;${stagger};${0.36 + stagger};1`}
                        calcMode="linear"
                      />
                      <animate
                        attributeName="opacity"
                        values={`0;0;0.9;0.9;0;0`}
                        keyTimes={`0;${stagger + 0.02};${stagger + 0.04};${0.34 + stagger};${0.36 + stagger};1`}
                        dur="2.5s"
                        repeatCount="indefinite"
                      />
                    </circle>
                  </g>
                );
              })}

            {/* Controller card */}
            {ctrlPos && (
              <CardG
                x={ctrlPos.x}
                y={ctrlPos.y}
                w={CTRL_W}
                h={CTRL_H}
                accent="#38bdf8"
                hot={hoveredId === CTRL_KEY || draggingKey === CTRL_KEY}
                onPointerDown={(e) => startCardDrag(e, CTRL_KEY)}
                onPointerEnter={() => setHoveredId(CTRL_KEY)}
                onPointerLeave={() => setHoveredId(null)}
              >
                <text
                  x={20}
                  y={28}
                  
                  fontSize="11"
                  fill="#fafafa"
                  fontWeight="600"
                  letterSpacing="0.5"
                  pointerEvents="none"
                >
                  {controllerLabel.toUpperCase()}
                </text>
                <text
                  x={20}
                  y={46}
                  
                  fontSize="10"
                  fill="#71717a"
                  pointerEvents="none"
                >
                  {connectedNodes}/{totalRemoteNodes} nodes · {totalServices}{" "}
                  svc
                </text>
                <circle
                  cx={CTRL_W - 20}
                  cy={CTRL_H / 2}
                  r="4"
                  fill="#38bdf8"
                  pointerEvents="none"
                />
              </CardG>
            )}

            {/* Node cards */}
            {remoteNodes.map((n) => {
              const p = positions[`node:${n.nodeId}`];
              if (!p) return null;
              const accent = n.isConnected ? "#10b981" : "#71717a";
              const memPct =
                n.memoryTotalMb > 0
                  ? Math.min(100, (n.memoryUsedMb / n.memoryTotalMb) * 100)
                  : 0;
              const cpuPct = Math.max(0, Math.min(100, n.cpuUsage));
              const hot =
                hoveredId === `node:${n.nodeId}` ||
                draggingKey === `node:${n.nodeId}`;
              return (
                <CardG
                  key={`node-${n.nodeId}`}
                  x={p.x}
                  y={p.y}
                  w={NODE_W}
                  h={NODE_H}
                  accent={accent}
                  hot={hot}
                  onPointerDown={(e) =>
                    startCardDrag(e, `node:${n.nodeId}`)
                  }
                  onPointerEnter={() => setHoveredId(`node:${n.nodeId}`)}
                  onPointerLeave={() => setHoveredId(null)}
                >
                  <circle
                    cx={20}
                    cy={22}
                    r="4"
                    fill={accent}
                    pointerEvents="none"
                  />
                  <text
                    x={32}
                    y={26}
                    
                    fontSize="11"
                    fill="#fafafa"
                    fontWeight="600"
                    pointerEvents="none"
                  >
                    {truncate(n.nodeId, 16)}
                  </text>
                  <text
                    x={NODE_W - 16}
                    y={26}
                    textAnchor="end"
                    
                    fontSize="10"
                    fill="#71717a"
                    pointerEvents="none"
                  >
                    {n.currentServices}/{n.maxServices}
                  </text>

                  {/* CPU + MEM stacked bars */}
                  <Meter
                    x={16}
                    y={42}
                    w={NODE_W - 32}
                    label="CPU"
                    pct={cpuPct}
                    accent="#38bdf8"
                  />
                  <Meter
                    x={16}
                    y={58}
                    w={NODE_W - 32}
                    label="MEM"
                    pct={memPct}
                    accent={accent}
                  />
                </CardG>
              );
            })}

            {/* Service cards */}
            {services.map((s) => {
              const p = positions[`svc:${s.name}`];
              if (!p) return null;
              const color = stateColor(s.state);
              const hot =
                hoveredId === `svc:${s.name}` ||
                draggingKey === `svc:${s.name}`;
              return (
                <CardG
                  key={`svc-${s.name}`}
                  x={p.x}
                  y={p.y}
                  w={SVC_W}
                  h={SVC_H}
                  accent={color}
                  hot={hot}
                  onPointerDown={(e) =>
                    startCardDrag(e, `svc:${s.name}`)
                  }
                  onPointerEnter={() => setHoveredId(`svc:${s.name}`)}
                  onPointerLeave={() => setHoveredId(null)}
                >
                  <circle
                    cx={18}
                    cy={SVC_H / 2}
                    r="4"
                    fill={color}
                    pointerEvents="none"
                  />
                  <text
                    x={30}
                    y={SVC_H / 2 + 4}
                    
                    fontSize="11"
                    fill="#fafafa"
                    fontWeight="500"
                    pointerEvents="none"
                  >
                    {truncate(s.name, 18)}
                  </text>
                  <text
                    x={SVC_W - 14}
                    y={SVC_H / 2 + 4}
                    textAnchor="end"
                    
                    fontSize="9"
                    fill={color}
                    fontWeight="600"
                    letterSpacing="0.3"
                    pointerEvents="none"
                  >
                    {s.state}
                  </text>
                </CardG>
              );
            })}
          </svg>

          {/* Zoom readout */}
          <div className="absolute top-3 left-3 flex items-center gap-1.5 text-[10px] [font-family:var(--font-space)] text-foreground/90 bg-card/90 backdrop-blur-sm rounded-md px-2.5 py-1.5 border border-border">
            <span className="text-muted-foreground">zoom</span>
            <span className="tabular-nums text-foreground">{zoomPct}%</span>
          </div>

          {/* Floating controls */}
          <div className="absolute top-3 right-3 flex flex-col bg-card/90 backdrop-blur-sm rounded-md border border-border overflow-hidden">
            <ControlButton
              label="Zoom in (+)"
              onClick={() => zoomButton(1.25)}
            >
              <IconPlus />
            </ControlButton>
            <div className="h-px bg-border" />
            <ControlButton
              label="Zoom out (−)"
              onClick={() => zoomButton(1 / 1.25)}
            >
              <IconMinus />
            </ControlButton>
            <div className="h-px bg-border" />
            <ControlButton label="Fit to content (F)" onClick={fit}>
              <IconFit />
            </ControlButton>
            <div className="h-px bg-border" />
            <ControlButton label="Reset view (0)" onClick={reset}>
              <IconReset />
            </ControlButton>
          </div>

          {/* Legend */}
          <div className="absolute bottom-3 left-3 flex items-center gap-3 text-[10px] [font-family:var(--font-space)] text-muted-foreground bg-card/90 backdrop-blur-sm rounded-md px-3 py-1.5 border border-border">
            <LegendDot color="#10b981" label="ready" />
            <LegendDot color="#f59e0b" label="starting" />
            <LegendDot color="#fb923c" label="stopping" />
            <LegendDot color="#f43f5e" label="crashed" />
            <LegendDot color="#71717a" label="stopped" />
          </div>

          {/* Hint */}
          <div className="absolute bottom-3 right-3 text-[10px] [font-family:var(--font-space)] text-muted-foreground bg-card/90 backdrop-blur-sm rounded-md px-2.5 py-1.5 border border-border">
            drag cards · scroll · <span className="text-foreground/90">+ − 0 F</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// ---------- Sub-components ----------

interface CardGProps {
  x: number;
  y: number;
  w: number;
  h: number;
  accent: string;
  hot: boolean;
  onPointerDown: (e: ReactPointerEvent<SVGGElement>) => void;
  onPointerEnter: () => void;
  onPointerLeave: () => void;
  children: React.ReactNode;
}

function CardG({
  x,
  y,
  w,
  h,
  accent,
  hot,
  onPointerDown,
  onPointerEnter,
  onPointerLeave,
  children,
}: CardGProps) {
  return (
    <g
      data-draggable="true"
      transform={`translate(${x},${y})`}
      onPointerDown={onPointerDown}
      onPointerEnter={onPointerEnter}
      onPointerLeave={onPointerLeave}
      style={{ cursor: "grab" }}
    >
      {/* Outer ring on hover — shadcn-style focus ring */}
      {hot && (
        <rect
          x={-2}
          y={-2}
          width={w + 4}
          height={h + 4}
          rx={14}
          ry={14}
          fill="none"
          stroke={accent}
          strokeWidth={1}
          opacity={0.3}
        />
      )}
      <rect
        x={0}
        y={0}
        width={w}
        height={h}
        rx={12}
        ry={12}
        fill="var(--card, #1a2030)"
        stroke={hot ? accent : "rgba(255,255,255,0.1)"}
        strokeWidth={1}
      />
      {children}
    </g>
  );
}

function Meter({
  x,
  y,
  w,
  label,
  pct,
  accent,
}: {
  x: number;
  y: number;
  w: number;
  label: string;
  pct: number;
  accent: string;
}) {
  const barW = w - 44;
  return (
    <g pointerEvents="none">
      <text
        x={x}
        y={y + 8}
        
        fontSize="8"
        fill="#71717a"
        letterSpacing="0.5"
      >
        {label}
      </text>
      <rect
        x={x + 26}
        y={y + 2}
        width={barW}
        height={6}
        rx={3}
        ry={3}
        fill="rgba(255,255,255,0.08)"
      />
      <rect
        x={x + 26}
        y={y + 2}
        width={(barW * pct) / 100}
        height={6}
        rx={3}
        ry={3}
        fill={accent}
        opacity="0.9"
      />
      <text
        x={x + w}
        y={y + 8}
        textAnchor="end"
        
        fontSize="8"
        fill="#a1a1aa"
        className="tabular-nums"
      >
        {Math.round(pct)}%
      </text>
    </g>
  );
}

function truncate(s: string, max: number) {
  return s.length > max ? s.slice(0, max - 1) + "…" : s;
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
      className="w-8 h-8 flex items-center justify-center text-muted-foreground hover:bg-accent/50 hover:text-foreground active:bg-accent transition-colors duration-150"
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
        style={{ background: color }}
      />
      <span>{label}</span>
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
