"use client";

import { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Search, ScrollText } from "@/lib/icons";
import { PageShell } from "@/components/page-shell";
import { useApiResource, POLL } from "@/hooks/use-api-resource";

interface AuditEntry {
  timestamp: string;
  actor: string;
  action: string;
  target: string;
  details: string | null;
}

interface AuditResponse {
  entries: AuditEntry[];
  limit: number;
  offset: number;
}

function formatTimestamp(ts: string): string {
  return new Date(ts).toLocaleString();
}

export default function AuditPage() {
  const [actionFilter, setActionFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");
  const [debouncedAction, setDebouncedAction] = useState("");
  const [debouncedActor, setDebouncedActor] = useState("");

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedAction(actionFilter);
      setDebouncedActor(actorFilter);
    }, 300);
    return () => clearTimeout(t);
  }, [actionFilter, actorFilter]);

  const params = new URLSearchParams({ limit: "100" });
  if (debouncedAction) params.set("action", debouncedAction);
  if (debouncedActor) params.set("actor", debouncedActor);

  const { data, loading, error, refetch, isEmpty } =
    useApiResource<AuditResponse>(`/api/audit?${params.toString()}`, {
      poll: POLL.slow,
      silent: true,
      isEmpty: (d) => d.entries.length === 0,
    });

  const entries = data?.entries ?? [];

  const filters = (
    <>
      <div className="relative">
        <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
        <Input
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
          placeholder="Filter action…"
          className="pl-9 w-40"
        />
      </div>
      <div className="relative">
        <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
        <Input
          value={actorFilter}
          onChange={(e) => setActorFilter(e.target.value)}
          placeholder="Filter actor…"
          className="pl-9 w-40"
        />
      </div>
    </>
  );

  const status = loading
    ? "loading"
    : error
      ? "error"
      : isEmpty
        ? "empty"
        : "ready";

  return (
    <PageShell
      title="Audit Log"
      description={`${entries.length} most recent entries · every state change is recorded.`}
      actions={filters}
      status={status}
      skeleton="table"
      error={error}
      onRetry={refetch}
      emptyState={{
        icon: ScrollText,
        title: "No audit entries",
        description:
          "Nothing matches your current filters, or nothing has happened yet.",
      }}
    >
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">Time</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Target</TableHead>
                <TableHead>Details</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.map((e, i) => (
                <TableRow key={i}>
                  <TableCell className="pl-6 text-xs text-muted-foreground whitespace-nowrap">
                    {formatTimestamp(e.timestamp)}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className="text-xs">
                      {e.actor}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    {e.action}
                  </TableCell>
                  <TableCell className="text-sm">{e.target}</TableCell>
                  <TableCell className="text-xs text-muted-foreground max-w-xs truncate">
                    {e.details || "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </PageShell>
  );
}
