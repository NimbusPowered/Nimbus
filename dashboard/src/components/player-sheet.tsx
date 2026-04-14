"use client";

import { useEffect, useState } from "react";
import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { apiFetch } from "@/lib/api";
import { statusColors } from "@/lib/status";
import { toast } from "sonner";
import { Plus, X, Gavel } from "@/lib/icons";
import { SectionLabel } from "@/components/section-label";

interface PlayerMeta {
  uuid: string;
  name: string;
  firstSeen: string;
  lastSeen: string;
  totalPlaytimeSeconds: number;
  online: boolean;
  currentService?: string;
}

interface SessionEntry {
  service: string;
  group: string;
  connectedAt: string;
  disconnectedAt: string | null;
}

interface PlayerPerms {
  uuid: string;
  name: string;
  groups: string[];
  prefix: string;
  suffix: string;
  displayGroup: string;
}

interface PunishmentSummary {
  id: number;
  type: string;
  reason: string;
  issuerName: string;
  issuedAt: string;
  expiresAt: string | null;
  active: boolean;
  scope: string;
  scopeTarget: string | null;
}

type PunishmentForm = {
  type: "BAN" | "TEMPBAN" | "MUTE" | "TEMPMUTE" | "KICK" | "WARN";
  duration: string;
  scope: "NETWORK" | "GROUP" | "SERVICE";
  scopeTarget: string;
  reason: string;
};

const DEFAULT_FORM: PunishmentForm = {
  type: "BAN",
  duration: "1d",
  scope: "NETWORK",
  scopeTarget: "",
  reason: "",
};

function punishmentTypeClass(type: string): string {
  if (["BAN", "TEMPBAN", "IPBAN"].includes(type))
    return "bg-red-500/15 text-red-600 border-red-500/30 dark:text-red-400";
  if (["MUTE", "TEMPMUTE"].includes(type))
    return "bg-orange-500/15 text-orange-600 border-orange-500/30 dark:text-orange-400";
  if (type === "KICK")
    return "bg-yellow-500/15 text-yellow-700 border-yellow-500/30 dark:text-yellow-400";
  return "bg-blue-500/15 text-blue-600 border-blue-500/30 dark:text-blue-400";
}

interface PermGroupInfo {
  name: string;
}

function formatPlaytime(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

/** Compact key/value row used in the player sheet. */
function MetaRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-1 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right">{children}</span>
    </div>
  );
}

export function PlayerSheet({
  uuid,
  open,
  onOpenChange,
}: {
  uuid: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [meta, setMeta] = useState<PlayerMeta | null>(null);
  const [history, setHistory] = useState<SessionEntry[]>([]);
  const [perms, setPerms] = useState<PlayerPerms | null>(null);
  const [allGroups, setAllGroups] = useState<PermGroupInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [newGroup, setNewGroup] = useState("");
  const [punishments, setPunishments] = useState<PunishmentSummary[]>([]);
  const [showPunishForm, setShowPunishForm] = useState(false);
  const [punishForm, setPunishForm] = useState<PunishmentForm>(DEFAULT_FORM);

  function reloadPerms() {
    if (!uuid) return;
    apiFetch<PlayerPerms>(`/api/permissions/players/${uuid}`)
      .then(setPerms)
      .catch(() => {});
  }

  function reloadPunishments() {
    if (!uuid) return;
    apiFetch<{ punishments: PunishmentSummary[] }>(
      `/api/punishments/player/${uuid}?limit=50`
    )
      .then((d) => setPunishments(d.punishments))
      .catch(() => {});
  }

  useEffect(() => {
    if (!uuid || !open) return;
    setLoading(true);
    setMeta(null);
    setHistory([]);
    setPerms(null);
    setPunishments([]);
    setNewGroup("");
    setShowPunishForm(false);
    setPunishForm(DEFAULT_FORM);

    Promise.all([
      apiFetch<PlayerMeta>(`/api/players/info/${uuid}`).catch(() => null),
      apiFetch<SessionEntry[]>(
        `/api/players/history/${uuid}?limit=20`
      ).catch(() => []),
      apiFetch<PlayerPerms>(`/api/permissions/players/${uuid}`).catch(
        () => null
      ),
      apiFetch<{ groups: PermGroupInfo[]; total: number }>(
        "/api/permissions/groups"
      )
        .then((d) => d.groups)
        .catch(() => []),
      apiFetch<{ punishments: PunishmentSummary[] }>(
        `/api/punishments/player/${uuid}?limit=50`
      )
        .then((d) => d.punishments)
        .catch(() => []),
    ]).then(([m, h, p, g, pn]) => {
      setMeta(m);
      setHistory(h);
      setPerms(p);
      setAllGroups(g);
      setPunishments(pn);
      setLoading(false);
    });
  }, [uuid, open]);

  async function issuePunishment() {
    if (!uuid || !punishForm.reason.trim()) return;
    const playerName = meta?.name ?? perms?.name ?? "";
    const needsDuration =
      punishForm.type === "TEMPBAN" || punishForm.type === "TEMPMUTE";
    const body: Record<string, unknown> = {
      type: punishForm.type,
      targetUuid: uuid,
      targetName: playerName,
      reason: punishForm.reason.trim(),
      issuer: "dashboard",
      issuerName: "Dashboard",
      scope: punishForm.scope,
    };
    if (needsDuration) body.duration = punishForm.duration;
    if (punishForm.scope !== "NETWORK") body.scopeTarget = punishForm.scopeTarget.trim();

    try {
      await apiFetch("/api/punishments", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      toast.success(`${punishForm.type} issued`);
      setShowPunishForm(false);
      setPunishForm(DEFAULT_FORM);
      reloadPunishments();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to issue punishment");
    }
  }

  async function revokePunishment(id: number) {
    try {
      await apiFetch(`/api/punishments/${id}`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ revokedBy: "dashboard" }),
      });
      toast.success("Revoked");
      reloadPunishments();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Revoke failed");
    }
  }

  async function addGroup() {
    if (!uuid || !newGroup.trim()) return;
    try {
      await apiFetch(`/api/permissions/players/${uuid}/groups`, {
        method: "POST",
        body: JSON.stringify({ group: newGroup.trim() }),
      });
      toast.success(`Group '${newGroup}' added`);
      setNewGroup("");
      reloadPerms();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to add group");
    }
  }

  async function removeGroup(group: string) {
    if (!uuid) return;
    try {
      await apiFetch(`/api/permissions/players/${uuid}/groups`, {
        method: "DELETE",
        body: JSON.stringify({ group }),
      });
      toast.success(`Group '${group}' removed`);
      reloadPerms();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to remove group");
    }
  }

  const playerName = meta?.name ?? perms?.name ?? "Unknown";
  const availableGroups = allGroups.filter(
    (g) => !perms?.groups.includes(g.name)
  );

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent size="lg">
        <SheetHeader>
          <div className="flex items-center gap-4">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={uuid ? `https://mc-heads.net/avatar/${uuid}/64` : undefined}
              alt={playerName}
              className="size-14 rounded-md ring-1 ring-border"
            />
            <div className="min-w-0 flex-1">
              <SheetTitle className="text-lg">{playerName}</SheetTitle>
              <SheetDescription className="font-mono text-xs">
                {uuid}
              </SheetDescription>
            </div>
          </div>
        </SheetHeader>

        {loading ? (
          <SheetBody>
            <Skeleton className="h-24 rounded-md" />
            <Skeleton className="h-40 rounded-md" />
          </SheetBody>
        ) : (
          <SheetBody>
            {meta && (
              <section className="space-y-2">
                <SectionLabel>Status</SectionLabel>
                <div className="divide-y">
                  <MetaRow label="Online">
                    <Badge
                      variant="outline"
                      className={
                        meta.online ? statusColors.online : statusColors.inactive
                      }
                    >
                      {meta.online ? "Online" : "Offline"}
                    </Badge>
                  </MetaRow>
                  {meta.currentService && (
                    <MetaRow label="Server">{meta.currentService}</MetaRow>
                  )}
                  <MetaRow label="First seen">
                    {formatDate(meta.firstSeen)}
                  </MetaRow>
                  <MetaRow label="Last seen">{formatDate(meta.lastSeen)}</MetaRow>
                  <MetaRow label="Playtime">
                    {formatPlaytime(meta.totalPlaytimeSeconds)}
                  </MetaRow>
                </div>
              </section>
            )}

            {perms && (
              <section className="space-y-3">
                <SectionLabel>Groups</SectionLabel>
                <div className="divide-y">
                  <MetaRow label="Display group">
                    <Badge variant="outline">{perms.displayGroup}</Badge>
                  </MetaRow>
                  {perms.prefix && <MetaRow label="Prefix">{perms.prefix}</MetaRow>}
                  {perms.suffix && <MetaRow label="Suffix">{perms.suffix}</MetaRow>}
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {perms.groups.map((g) => (
                    <Badge
                      key={g}
                      variant="secondary"
                      className="gap-1 pr-1"
                    >
                      {g}
                      <button
                        onClick={() => removeGroup(g)}
                        className="ml-0.5 hover:text-destructive"
                      >
                        <X className="size-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
                <div className="flex items-center gap-2">
                  {availableGroups.length > 0 ? (
                    <Select
                      value={newGroup}
                      onValueChange={(v) => v && setNewGroup(v)}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Select group…" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {availableGroups.map((g) => (
                            <SelectItem key={g.name} value={g.name}>
                              {g.name}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  ) : (
                    <Input
                      value={newGroup}
                      onChange={(e) => setNewGroup(e.target.value)}
                      placeholder="Group name"
                      onKeyDown={(e) => e.key === "Enter" && addGroup()}
                    />
                  )}
                  <Button
                    variant="outline"
                    onClick={addGroup}
                    disabled={!newGroup.trim()}
                  >
                    <Plus className="size-4" />
                  </Button>
                </div>
              </section>
            )}

            <section className="space-y-2">
              <div className="flex items-center justify-between">
                <SectionLabel>Punishments</SectionLabel>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setShowPunishForm((v) => !v)}
                >
                  <Gavel className="size-3.5 mr-1" />
                  {showPunishForm ? "Cancel" : "New"}
                </Button>
              </div>

              {showPunishForm && (
                <div className="rounded-md border p-3 space-y-2 bg-muted/30">
                  <div className="grid grid-cols-2 gap-2">
                    <Select
                      value={punishForm.type}
                      onValueChange={(v) =>
                        setPunishForm({
                          ...punishForm,
                          type: v as PunishmentForm["type"],
                        })
                      }
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="BAN">Ban (permanent)</SelectItem>
                          <SelectItem value="TEMPBAN">Ban (temporary)</SelectItem>
                          <SelectItem value="MUTE">Mute (permanent)</SelectItem>
                          <SelectItem value="TEMPMUTE">Mute (temporary)</SelectItem>
                          <SelectItem value="KICK">Kick</SelectItem>
                          <SelectItem value="WARN">Warn</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                    <Select
                      value={punishForm.scope}
                      onValueChange={(v) =>
                        setPunishForm({
                          ...punishForm,
                          scope: v as PunishmentForm["scope"],
                        })
                      }
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="NETWORK">Network-wide</SelectItem>
                          <SelectItem value="GROUP">Group</SelectItem>
                          <SelectItem value="SERVICE">Service</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </div>
                  {(punishForm.type === "TEMPBAN" ||
                    punishForm.type === "TEMPMUTE") && (
                    <Input
                      value={punishForm.duration}
                      onChange={(e) =>
                        setPunishForm({ ...punishForm, duration: e.target.value })
                      }
                      placeholder="Duration (e.g. 30m, 1d, 2w)"
                    />
                  )}
                  {punishForm.scope !== "NETWORK" && (
                    <Input
                      value={punishForm.scopeTarget}
                      onChange={(e) =>
                        setPunishForm({
                          ...punishForm,
                          scopeTarget: e.target.value,
                        })
                      }
                      placeholder={
                        punishForm.scope === "GROUP"
                          ? "Group name (e.g. BedWars)"
                          : "Service name (e.g. Lobby-1)"
                      }
                    />
                  )}
                  <Input
                    value={punishForm.reason}
                    onChange={(e) =>
                      setPunishForm({ ...punishForm, reason: e.target.value })
                    }
                    placeholder="Reason"
                    onKeyDown={(e) => e.key === "Enter" && issuePunishment()}
                  />
                  <Button
                    onClick={issuePunishment}
                    disabled={
                      !punishForm.reason.trim() ||
                      (punishForm.scope !== "NETWORK" &&
                        !punishForm.scopeTarget.trim())
                    }
                    size="sm"
                  >
                    Issue
                  </Button>
                </div>
              )}

              {punishments.length === 0 ? (
                <div className="text-xs text-muted-foreground">
                  No punishments on record.
                </div>
              ) : (
                <div className="space-y-1">
                  {punishments.map((p) => (
                    <div
                      key={p.id}
                      className="flex items-start justify-between gap-2 rounded-md border px-3 py-2 text-xs"
                    >
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <Badge
                            variant="outline"
                            className={punishmentTypeClass(p.type)}
                          >
                            {p.type}
                          </Badge>
                          {p.scope !== "NETWORK" && (
                            <Badge
                              variant="outline"
                              className="border-amber-500/40 text-amber-600 dark:text-amber-400"
                            >
                              {p.scope.toLowerCase()}
                              {p.scopeTarget && <span>: {p.scopeTarget}</span>}
                            </Badge>
                          )}
                          {!p.active && (
                            <Badge variant="secondary" className="text-[10px]">
                              inactive
                            </Badge>
                          )}
                        </div>
                        <div className="mt-1 truncate">{p.reason || "—"}</div>
                        <div className="text-muted-foreground">
                          by {p.issuerName} · {formatDate(p.issuedAt)}
                          {p.expiresAt && <> · until {formatDate(p.expiresAt)}</>}
                        </div>
                      </div>
                      {p.active && (
                        <button
                          onClick={() => revokePunishment(p.id)}
                          className="text-muted-foreground hover:text-destructive"
                          title="Revoke"
                        >
                          <X className="size-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>

            {history.length > 0 && (
              <section className="space-y-2">
                <SectionLabel>Session history</SectionLabel>
                <div className="space-y-1">
                  {history.map((entry, i) => (
                    <div
                      key={i}
                      className="flex items-center justify-between rounded-md border px-3 py-2 text-xs"
                    >
                      <div>
                        <span className="font-medium">{entry.service}</span>
                        <span className="text-muted-foreground ml-2">
                          {entry.group}
                        </span>
                      </div>
                      <div className="text-muted-foreground">
                        {formatDate(entry.connectedAt)}
                        {entry.disconnectedAt && (
                          <span> – {formatDate(entry.disconnectedAt)}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            )}
          </SheetBody>
        )}
      </SheetContent>
    </Sheet>
  );
}
