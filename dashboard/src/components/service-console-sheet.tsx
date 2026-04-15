"use client";

import { useEffect, useRef, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiWebSocketReconnect } from "@/lib/api";
import { statusColors } from "@/lib/status";
import { AnsiLine } from "@/components/ansi-line";
import { Send } from "@/lib/icons";
import { toast } from "sonner";

const MAX_LINES = 1000;

type ConnectionState = "connecting" | "connected" | "reconnecting" | "closed";

/**
 * Per-service console drawer. Streams stdout from
 * `/api/services/{name}/console` (each WS text frame = one log line) and
 * sends user-typed commands as raw text frames back to the controller.
 *
 * The sheet only opens a connection while `open && serviceName` are set;
 * closing the sheet tears it down and clears the buffered lines so the
 * next open starts clean.
 */
export function ServiceConsoleSheet({
  serviceName,
  open,
  onOpenChange,
}: {
  serviceName: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [lines, setLines] = useState<string[]>([]);
  const [command, setCommand] = useState("");
  const [state, setState] = useState<ConnectionState>("connecting");
  const sendRef = useRef<((msg: string) => void) | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const errorToastedRef = useRef(false);

  useEffect(() => {
    if (!open || !serviceName) return;

    // Reset must happen before we open the socket so no stale lines from a
    // previous service leak in. Consistent with the existing pattern in
    // auth.tsx / changelog-card.tsx / cluster-topology.tsx.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLines([]);
    setState("connecting");
    errorToastedRef.current = false;
    let opened = false;

    const { send, cleanup } = apiWebSocketReconnect(
      `/api/services/${encodeURIComponent(serviceName)}/console`,
      {
        onOpen: () => {
          opened = true;
          setState("connected");
          sendRef.current = send;
        },
        onMessage: (event) => {
          // Per-service WS sends raw text frames, one stdout line each.
          const text =
            typeof event.data === "string" ? event.data : String(event.data);
          setLines((prev) => {
            const next = [...prev, text];
            return next.length > MAX_LINES ? next.slice(-MAX_LINES) : next;
          });
        },
        onClose: () => {
          setState((prev) => (prev === "closed" ? prev : "reconnecting"));
        },
        onError: () => {
          if (!opened && !errorToastedRef.current) {
            errorToastedRef.current = true;
            toast.error("Console connection failed", {
              description: `Could not attach to ${serviceName}.`,
            });
          }
        },
      }
    );

    sendRef.current = send;

    return () => {
      setState("closed");
      sendRef.current = null;
      cleanup();
    };
  }, [open, serviceName]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [lines]);

  function sendCommand(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = command.trim();
    if (!trimmed || !sendRef.current || state !== "connected") return;
    sendRef.current(trimmed);
    setLines((prev) => {
      const next = [...prev, `> ${trimmed}`];
      return next.length > MAX_LINES ? next.slice(-MAX_LINES) : next;
    });
    setCommand("");
  }

  const stateLabel =
    state === "connected"
      ? "Connected"
      : state === "reconnecting"
        ? "Reconnecting…"
        : state === "closed"
          ? "Closed"
          : "Connecting…";

  const stateClass =
    state === "connected"
      ? statusColors.online
      : state === "reconnecting"
        ? statusColors.active
        : statusColors.inactive;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent size="xl" className="sm:max-w-2xl">
        <SheetHeader>
          <div className="flex items-center justify-between gap-2 pr-10">
            <SheetTitle>{serviceName ?? "Console"}</SheetTitle>
            <Badge variant="outline" className={stateClass}>
              {stateLabel}
            </Badge>
          </div>
          <SheetDescription>
            Live stdout stream — type a command and press Enter to send it to
            the server console.
          </SheetDescription>
        </SheetHeader>

        <div className="flex flex-1 flex-col min-h-0 px-6 pb-6 pt-2 gap-2">
          <div className="flex-1 min-h-0 overflow-y-auto rounded-md bg-black p-3 font-mono text-xs text-gray-300 scrollbar-thin">
            {lines.length === 0 ? (
              <div className="text-gray-500">
                {state === "connected"
                  ? "Waiting for output…"
                  : "Connecting…"}
              </div>
            ) : (
              lines.map((line, i) => <AnsiLine key={i} text={line} />)
            )}
            <div ref={endRef} />
          </div>
          <form onSubmit={sendCommand} className="flex items-center gap-2">
            <Input
              value={command}
              onChange={(e) => setCommand(e.target.value)}
              placeholder="Enter command…"
              className="font-mono"
              disabled={state !== "connected"}
              autoFocus
            />
            <Button
              type="submit"
              size="icon"
              disabled={state !== "connected" || !command.trim()}
            >
              <Send className="size-4" />
            </Button>
          </form>
        </div>
      </SheetContent>
    </Sheet>
  );
}
