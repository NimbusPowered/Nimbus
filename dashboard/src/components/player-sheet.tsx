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
import { Plus, X, ChevronDown, Send, Gavel } from "@/lib/icons";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { SectionLabel } from "@/components/section-label";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";

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
  const [punishmentsOpen, setPunishmentsOpen] = useState(false);
  const [sessionsOpen, setSessionsOpen] = useState(false);
  // Player-actions state (kick / transfer). Network broadcast lives in
  // <BroadcastDialog /> in the site header — it isn't player-scoped.
  const [kickOpen, setKickOpen] = useState(false);
  const [kickReason, setKickReason] = useState("");
  const [kickBusy, setKickBusy] = useState(false);
  const [sendOpen, setSendOpen] = useState(false);
  const [sendTarget, setSendTarget] = useState("");
  const [sendBusy, setSendBusy] = useState(false);
  const [sendOptions, setSendOptions] = useState<string[]>([]);

  async function loadSendTargets() {
    try {
      // Service list — Velocity's /send accepts either a service name or a group name.
      const resp = await apiFetch<{ services: { name: string; state: string }[] }>(
        "/api/services",
        { silent: true }
      );
      const ready = resp.services
        .filter((s) => s.state === "READY")
        .map((s) => s.name);
      setSendOptions(ready);
    } catch {
      setSendOptions([]);
    }
  }

  async function doKick() {
    if (!meta?.name) return;
    setKickBusy(true);
    try {
      await apiFetch(`/api/players/${encodeURIComponent(meta.name)}/kick`, {
        method: "POST",
        body: JSON.stringify({
          reason: kickReason.trim() || "You have been kicked from the network.",
        }),
      });
      toast.success(`Kicked ${meta.name}`);
      setKickOpen(false);
      setKickReason("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Kick failed");
    } finally {
      setKickBusy(false);
    }
  }

  async function doSend() {
    if (!meta?.name || !sendTarget.trim()) return;
    setSendBusy(true);
    try {
      await apiFetch(`/api/players/${encodeURIComponent(meta.name)}/send`, {
        method: "POST",
        body: JSON.stringify({ targetService: sendTarget.trim() }),
      });
      toast.success(`Sent ${meta.name} to ${sendTarget}`);
      setSendOpen(false);
      setSendTarget("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Transfer failed");
    } finally {
      setSendBusy(false);
    }
  }

  function reloadPerms() {
    if (!uuid) return;
    apiFetch<PlayerPerms>(`/api/permissions/players/${uuid}`)
      .then(setPerms)
      .catch(() => {});
  }

  useEffect(() => {
    if (!uuid || !open) return;
    setLoading(true);
    setMeta(null);
    setHistory([]);
    setPerms(null);
    setPunishments([]);
    setPunishmentsOpen(false);
    setSessionsOpen(false);
    setNewGroup("");

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
      // Open the punishments section by default if anything is active —
      // makes sure staff see enforcement state at a glance.
      setPunishmentsOpen(pn.some((x) => x.active));
      setLoading(false);
    });
  }, [uuid, open]);


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
            {meta?.online && (
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    loadSendTargets();
                    setSendOpen(true);
                  }}
                >
                  <Send className="mr-1 size-4" /> Transfer
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setKickOpen(true)}
                  className="border-red-500/40 text-red-600 hover:bg-red-500/10 dark:text-red-400"
                >
                  <Gavel className="mr-1 size-4" /> Kick
                </Button>
              </div>
            )}

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

            <Collapsible open={punishmentsOpen} onOpenChange={setPunishmentsOpen}>
              <CollapsibleTrigger
                render={
                  <button
                    type="button"
                    className="flex w-full items-center justify-between gap-2 border-b pb-1.5 text-left focus-visible:outline-none cursor-pointer"
                  />
                }
              >
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                    Punishments
                  </span>
                  <Badge variant="secondary" className="text-[10px] font-normal">
                    {punishments.length}
                  </Badge>
                  {punishments.some((p) => p.active) && (
                    <Badge
                      variant="outline"
                      className="text-[10px] border-red-500/40 text-red-600 dark:text-red-400"
                    >
                      {punishments.filter((p) => p.active).length} active
                    </Badge>
                  )}
                </div>
                <ChevronDown
                  className={cn(
                    "size-4 text-muted-foreground transition-transform",
                    punishmentsOpen && "rotate-180"
                  )}
                />
              </CollapsibleTrigger>
              <CollapsibleContent className="pt-2">
                {punishments.length === 0 ? (
                  <div className="text-xs text-muted-foreground">
                    No punishments on record.
                  </div>
                ) : (
                  <div className="space-y-1">
                    {punishments.map((p) => (
                      <div
                        key={p.id}
                        className="rounded-md border px-3 py-2 text-xs"
                      >
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
                        <div className="mt-1 break-words">{p.reason || "—"}</div>
                        <div className="text-muted-foreground">
                          by {p.issuerName} · {formatDate(p.issuedAt)}
                          {p.expiresAt && <> · until {formatDate(p.expiresAt)}</>}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
                <div className="pt-2 text-[11px] text-muted-foreground">
                  Manage punishments on the{" "}
                  <span className="font-medium">Punishments</span> page.
                </div>
              </CollapsibleContent>
            </Collapsible>

            {history.length > 0 && (
              <Collapsible open={sessionsOpen} onOpenChange={setSessionsOpen}>
                <CollapsibleTrigger
                  render={
                    <button
                      type="button"
                      className="flex w-full items-center justify-between gap-2 border-b pb-1.5 text-left focus-visible:outline-none cursor-pointer"
                    />
                  }
                >
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      Session history
                    </span>
                    <Badge
                      variant="secondary"
                      className="text-[10px] font-normal"
                    >
                      {history.length}
                    </Badge>
                  </div>
                  <ChevronDown
                    className={cn(
                      "size-4 text-muted-foreground transition-transform",
                      sessionsOpen && "rotate-180"
                    )}
                  />
                </CollapsibleTrigger>
                <CollapsibleContent className="pt-2">
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
                </CollapsibleContent>
              </Collapsible>
            )}
          </SheetBody>
        )}
      </SheetContent>

      <Dialog open={kickOpen} onOpenChange={setKickOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Kick {meta?.name}</DialogTitle>
            <DialogDescription>
              Disconnect this player from the network. They can rejoin immediately
              unless you also issue a ban.
            </DialogDescription>
          </DialogHeader>
          <div className="py-2">
            <Input
              value={kickReason}
              onChange={(e) => setKickReason(e.target.value)}
              placeholder="Reason (shown to the player)"
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setKickOpen(false)}
              disabled={kickBusy}
            >
              Cancel
            </Button>
            <Button onClick={doKick} disabled={kickBusy} className="bg-red-600 hover:bg-red-700">
              Kick
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={sendOpen} onOpenChange={setSendOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Transfer {meta?.name}</DialogTitle>
            <DialogDescription>
              Send the player to another service. Velocity&apos;s <code>/send</code>
              also accepts a group name (it picks the lightest backend).
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            {sendOptions.length > 0 && (
              <Select value={sendTarget} onValueChange={(v) => v && setSendTarget(v)}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select ready service…" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {sendOptions.map((opt) => (
                      <SelectItem key={opt} value={opt}>
                        {opt}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            )}
            <Input
              value={sendTarget}
              onChange={(e) => setSendTarget(e.target.value)}
              placeholder="…or type a service / group name"
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setSendOpen(false)}
              disabled={sendBusy}
            >
              Cancel
            </Button>
            <Button onClick={doSend} disabled={sendBusy || !sendTarget.trim()}>
              Transfer
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Sheet>
  );
}
