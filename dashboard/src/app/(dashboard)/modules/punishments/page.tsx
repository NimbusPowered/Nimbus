"use client";

import { useCallback, useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";
import {
  Gavel,
  Shield,
  UserCheck,
  Clock,
  Search,
  X,
  Save,
  RefreshCw,
} from "@/lib/icons";

interface Punishment {
  id: number;
  type: string;
  targetUuid: string;
  targetName: string;
  targetIp: string | null;
  reason: string;
  issuer: string;
  issuerName: string;
  issuedAt: string;
  expiresAt: string | null;
  active: boolean;
  revokedBy: string | null;
  revokedAt: string | null;
  revokeReason: string | null;
  scope: string;                 // "NETWORK" | "GROUP" | "SERVICE"
  scopeTarget: string | null;    // group or service name for scoped bans
}

interface ListResponse {
  punishments: Punishment[];
  total: number;
}

function formatRelative(iso: string | null): string {
  if (!iso) return "never";
  try {
    const date = new Date(iso);
    const diff = (date.getTime() - Date.now()) / 1000;
    const abs = Math.abs(diff);
    if (abs < 60) return "now";
    if (abs < 3600) return `${Math.round(abs / 60)}m`;
    if (abs < 86400) return `${Math.round(abs / 3600)}h`;
    return `${Math.round(abs / 86400)}d`;
  } catch {
    return iso;
  }
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

/**
 * Styling for punishment type badges.
 * BAN/TEMPBAN/IPBAN read as "severe" (red), MUTE variants as "chat" (orange),
 * KICK as "instant" (yellow), WARN as informational (blue).
 */
function typeClass(type: string): string {
  switch (type) {
    case "BAN":
    case "TEMPBAN":
    case "IPBAN":
      return "bg-red-500/15 text-red-600 border-red-500/30 dark:text-red-400";
    case "MUTE":
    case "TEMPMUTE":
      return "bg-orange-500/15 text-orange-600 border-orange-500/30 dark:text-orange-400";
    case "KICK":
      return "bg-yellow-500/15 text-yellow-700 border-yellow-500/30 dark:text-yellow-400";
    case "WARN":
      return "bg-blue-500/15 text-blue-600 border-blue-500/30 dark:text-blue-400";
    default:
      return "";
  }
}

export default function PunishmentsModulePage() {
  const [active, setActive] = useState<Punishment[]>([]);
  const [all, setAll] = useState<Punishment[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [working, setWorking] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [activeRes, allRes] = await Promise.all([
        apiFetch<ListResponse>("/api/punishments?active=true&limit=500").catch(
          () => ({ punishments: [], total: 0 })
        ),
        apiFetch<ListResponse>("/api/punishments?active=false&limit=500").catch(
          () => ({ punishments: [], total: 0 })
        ),
      ]);
      setActive(activeRes.punishments);
      setAll(allRes.punishments);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const revoke = async (id: number) => {
    if (working !== null) return;
    setWorking(id);
    try {
      await apiFetch(`/api/punishments/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ revokedBy: "dashboard", reason: "Revoked via dashboard" }),
      });
      await load();
    } catch (e) {
      console.error("Failed to revoke:", e);
    } finally {
      setWorking(null);
    }
  };

  const stats = {
    activeBans: active.filter((p) =>
      ["BAN", "TEMPBAN", "IPBAN"].includes(p.type)
    ).length,
    activeMutes: active.filter((p) =>
      ["MUTE", "TEMPMUTE"].includes(p.type)
    ).length,
    totalRecords: all.length,
  };

  const filtered = (list: Punishment[]) => {
    if (!search.trim()) return list;
    const q = search.toLowerCase();
    return list.filter(
      (p) =>
        p.targetName.toLowerCase().includes(q) ||
        p.targetUuid.toLowerCase().includes(q) ||
        p.issuerName.toLowerCase().includes(q) ||
        p.reason.toLowerCase().includes(q)
    );
  };

  const searchInput = (
    <div className="relative w-64">
      <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
      <Input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search player, issuer, reason…"
        className="pl-9"
      />
    </div>
  );

  return (
    <>
      <PageHeader
        title="Punishments"
        description="Network-wide bans, mutes, kicks and warnings."
        actions={searchInput}
      />

      {loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-3">
            <StatCard
              label="Active bans"
              icon={Shield}
              tone="primary"
              value={stats.activeBans}
              hint="login blocked"
            />
            <StatCard
              label="Active mutes"
              icon={UserCheck}
              value={stats.activeMutes}
              hint="chat blocked"
            />
            <StatCard
              label="Total records"
              icon={Gavel}
              value={stats.totalRecords}
              hint="all-time"
            />
          </div>

          <Tabs defaultValue="active">
            <TabsList>
              <TabsTrigger value="active">
                Active ({filtered(active).length})
              </TabsTrigger>
              <TabsTrigger value="history">
                History ({filtered(all).length})
              </TabsTrigger>
              <TabsTrigger value="messages">Messages</TabsTrigger>
            </TabsList>

            <TabsContent value="active" className="mt-4">
              <PunishmentTable
                rows={filtered(active)}
                onRevoke={revoke}
                revokable
                working={working}
                emptyTitle={search ? "No matches" : "No active punishments"}
                emptyHint={
                  search
                    ? `Nothing matches "${search}".`
                    : "When staff or API clients issue punishments, they show up here."
                }
              />
            </TabsContent>

            <TabsContent value="history" className="mt-4">
              <PunishmentTable
                rows={filtered(all)}
                onRevoke={revoke}
                revokable={false}
                working={working}
                emptyTitle={search ? "No matches" : "No punishment history"}
                emptyHint={
                  search
                    ? `Nothing matches "${search}".`
                    : "Complete record of every punishment ever issued."
                }
              />
            </TabsContent>

            <TabsContent value="messages" className="mt-4">
              <MessagesEditor />
            </TabsContent>
          </Tabs>
        </div>
      )}
    </>
  );
}

interface PunishmentTableProps {
  rows: Punishment[];
  onRevoke: (id: number) => void;
  revokable: boolean;
  working: number | null;
  emptyTitle: string;
  emptyHint: string;
}

function PunishmentTable({
  rows,
  onRevoke,
  revokable,
  working,
  emptyTitle,
  emptyHint,
}: PunishmentTableProps) {
  if (rows.length === 0) {
    return <EmptyState icon={Gavel} title={emptyTitle} description={emptyHint} />;
  }
  return (
    <Card>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="pl-6 w-14" />
              <TableHead>Player</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Scope</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Issuer</TableHead>
              <TableHead>Issued</TableHead>
              <TableHead>
                <Clock className="inline size-3.5 mr-1" />
                Expires
              </TableHead>
              {revokable && <TableHead className="text-right pr-6">Action</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((p) => (
              <TableRow key={p.id}>
                <TableCell className="pl-6">
                  <img
                    src={`https://mc-heads.net/avatar/${p.targetUuid}/32`}
                    alt={p.targetName}
                    className="size-8 min-w-8 rounded-sm"
                  />
                </TableCell>
                <TableCell className="font-medium">
                  {p.targetName}
                  {p.targetIp && (
                    <span className="block text-xs text-muted-foreground">
                      {p.targetIp}
                    </span>
                  )}
                </TableCell>
                <TableCell>
                  <Badge variant="outline" className={typeClass(p.type)}>
                    {p.type}
                  </Badge>
                </TableCell>
                <TableCell>
                  {p.scope === "NETWORK" ? (
                    <Badge variant="secondary" className="text-xs">
                      network
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="text-xs border-amber-500/40 text-amber-600 dark:text-amber-400"
                    >
                      {p.scope.toLowerCase()}
                      {p.scopeTarget && (
                        <span className="ml-1 opacity-70">
                          : {p.scopeTarget}
                        </span>
                      )}
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="max-w-xs truncate text-sm">
                  {p.reason || <span className="text-muted-foreground">—</span>}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {p.issuerName}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  <span title={formatDate(p.issuedAt)}>
                    {formatRelative(p.issuedAt)} ago
                  </span>
                </TableCell>
                <TableCell className="text-sm">
                  {p.expiresAt ? (
                    <span
                      className="text-muted-foreground"
                      title={formatDate(p.expiresAt)}
                    >
                      in {formatRelative(p.expiresAt)}
                    </span>
                  ) : p.active ? (
                    <span className="text-red-600 dark:text-red-400">
                      permanent
                    </span>
                  ) : (
                    <span className="text-muted-foreground">
                      revoked {formatRelative(p.revokedAt)} ago
                    </span>
                  )}
                </TableCell>
                {revokable && (
                  <TableCell className="text-right pr-6">
                    {p.active && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onRevoke(p.id)}
                        disabled={working !== null}
                      >
                        <X className="size-3.5 mr-1" />
                        Revoke
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

// ── Messages editor tab ─────────────────────────────────────

interface MessageTemplates {
  ban: string;
  tempban: string;
  ipban: string;
  mute: string;
  tempmute: string;
  kick: string;
  broadcast_issued: string;
  broadcast_revoked: string;
}

const MESSAGE_FIELDS: Array<{ key: keyof MessageTemplates; label: string; hint: string }> = [
  { key: "ban", label: "Ban", hint: "Permanent network-wide ban" },
  { key: "tempban", label: "Tempban", hint: "Temporary ban — use {remaining}" },
  { key: "ipban", label: "IP Ban", hint: "Keyed on IP; shown on reconnect" },
  { key: "mute", label: "Mute", hint: "Sent to player when chat is blocked" },
  { key: "tempmute", label: "Tempmute", hint: "Sent per chat attempt while muted" },
  { key: "kick", label: "Kick / Warn", hint: "One-shot disconnect, also used for warnings" },
  { key: "broadcast_issued", label: "Audit: issued", hint: "Console/audit log line" },
  { key: "broadcast_revoked", label: "Audit: revoked", hint: "Console/audit log line" },
];

function MessagesEditor() {
  const [templates, setTemplates] = useState<MessageTemplates | null>(null);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = useCallback(async () => {
    try {
      const t = await apiFetch<MessageTemplates>("/api/punishments/messages");
      setTemplates(t);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load messages");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const save = async () => {
    if (!templates) return;
    setWorking(true);
    setError(null);
    setSaved(false);
    try {
      const t = await apiFetch<MessageTemplates>("/api/punishments/messages", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(templates),
      });
      setTemplates(t);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Save failed");
    } finally {
      setWorking(false);
    }
  };

  if (!templates) {
    return <Skeleton className="h-96 rounded-xl" />;
  }

  return (
    <Card>
      <CardContent className="p-6 space-y-4">
        <div className="rounded-md bg-muted/40 p-3 text-sm">
          <div className="font-medium mb-1">Placeholders</div>
          <div className="text-muted-foreground text-xs space-x-3">
            <code>{"{target}"}</code>
            <code>{"{issuer}"}</code>
            <code>{"{reason}"}</code>
            <code>{"{remaining}"}</code>
            <code>{"{expires}"}</code>
            <code>{"{type}"}</code>
          </div>
          <div className="text-muted-foreground text-xs mt-2">
            Color codes: <code>&amp;c</code> red, <code>&amp;a</code> green,{" "}
            <code>&amp;7</code> gray, <code>&amp;l</code> bold, <code>\n</code> newline.
          </div>
        </div>

        {error && (
          <div className="rounded-md border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-600 dark:text-red-400">
            {error}
          </div>
        )}

        <div className="grid gap-4 md:grid-cols-2">
          {MESSAGE_FIELDS.map((field) => (
            <div key={field.key} className="space-y-1">
              <label className="text-xs font-medium flex items-center justify-between">
                <span>{field.label}</span>
                <span className="text-muted-foreground font-normal">{field.hint}</span>
              </label>
              <textarea
                className="w-full min-h-[96px] rounded-md border border-input bg-background p-2 text-xs font-mono resize-y focus:outline-none focus:ring-2 focus:ring-ring"
                value={templates[field.key]}
                onChange={(e) =>
                  setTemplates({ ...templates, [field.key]: e.target.value })
                }
                spellCheck={false}
              />
            </div>
          ))}
        </div>

        <div className="flex items-center gap-2 pt-2 border-t">
          <Button size="sm" onClick={save} disabled={working}>
            <Save className="size-4 mr-1" />
            Save
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={load}
            disabled={working}
          >
            <RefreshCw className="size-4 mr-1" />
            Reload
          </Button>
          {saved && (
            <span className="text-xs text-green-600 dark:text-green-400">
              Saved — rendering live on new punishments
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
