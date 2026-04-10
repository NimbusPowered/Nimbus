"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { apiFetch } from "@/lib/api";
import { toast } from "sonner";
import {
  Plus,
  Trash2,
  BoxIcon,
  MoreVertical,
  Play,
  Square,
  RotateCw,
} from "lucide-react";
import { Field, FieldLabel, FieldDescription } from "@/components/ui/field";

interface DedicatedService {
  name: string;
  directory: string; // read-only, auto-derived from paths.dedicated + name
  port: number;
  software: string;
  version: string;
  memory: string;
  proxyEnabled: boolean;
  restartOnCrash: boolean;
  maxRestarts: number;
  jvmArgs: string[];
  jvmOptimize: boolean;
  state: string | null;
  pid: number | null;
  playerCount: number | null;
  uptime: string | null;
}

interface DedicatedListResponse {
  services: DedicatedService[];
  total: number;
}

const SOFTWARE_OPTIONS = [
  "PAPER",
  "PURPUR",
  "FOLIA",
  "PUFFERFISH",
  "LEAF",
  "VELOCITY",
  "FORGE",
  "NEOFORGE",
  "FABRIC",
  "CUSTOM",
];

function stateBadgeVariant(state: string | null): "default" | "secondary" | "destructive" | "outline" {
  if (!state) return "outline";
  if (state === "READY") return "default";
  if (state === "CRASHED" || state === "STOPPED") return "destructive";
  if (state === "STARTING" || state === "PREPARING" || state === "STOPPING") return "secondary";
  return "outline";
}

export default function DedicatedPage() {
  const [services, setServices] = useState<DedicatedService[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);

  // Create form state
  const [newName, setNewName] = useState("");
  const [newPort, setNewPort] = useState(25570);
  const [newSoftware, setNewSoftware] = useState("PAPER");
  const [newVersion, setNewVersion] = useState("1.21.4");
  const [newMemory, setNewMemory] = useState("2G");
  const [newProxyEnabled, setNewProxyEnabled] = useState(true);
  const [newRestartOnCrash, setNewRestartOnCrash] = useState(true);
  const [creating, setCreating] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [actioning, setActioning] = useState<string | null>(null);

  async function load() {
    try {
      const data = await apiFetch<DedicatedListResponse>("/api/dedicated");
      setServices(data.services);
    } catch {
      // handled
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, []);

  function resetCreateForm() {
    setNewName("");
    setNewPort(25570);
    setNewSoftware("PAPER");
    setNewVersion("1.21.4");
    setNewMemory("2G");
    setNewProxyEnabled(true);
    setNewRestartOnCrash(true);
  }

  async function createService() {
    if (!newName.trim()) return;
    setCreating(true);
    try {
      await apiFetch("/api/dedicated", {
        method: "POST",
        body: JSON.stringify({
          name: newName.trim(),
          port: newPort,
          software: newSoftware,
          version: newVersion,
          memory: newMemory,
          proxyEnabled: newProxyEnabled,
          restartOnCrash: newRestartOnCrash,
          maxRestarts: 5,
          jvmArgs: [],
          jvmOptimize: true,
        }),
      });
      toast.success(`Dedicated service '${newName}' created`);
      setCreateOpen(false);
      resetCreateForm();
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to create dedicated service");
    } finally {
      setCreating(false);
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await apiFetch(`/api/dedicated/${deleteTarget}`, { method: "DELETE" });
      toast.success(`Dedicated service '${deleteTarget}' deleted`);
      setDeleteTarget(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to delete");
    } finally {
      setDeleting(false);
    }
  }

  async function doAction(name: string, action: "start" | "stop" | "restart") {
    setActioning(name);
    try {
      await apiFetch(`/api/dedicated/${name}/${action}`, { method: "POST" });
      toast.success(`Dedicated service '${name}' ${action}ed`);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : `Failed to ${action}`);
    } finally {
      setActioning(null);
    }
  }

  if (loading) return <Skeleton className="h-96 rounded-xl" />;

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle>Dedicated Services ({services.length})</CardTitle>
            <p className="text-sm text-muted-foreground mt-1">
              Standalone servers with fixed ports and user-managed directories
            </p>
          </div>
          <Dialog
            open={createOpen}
            onOpenChange={(open) => {
              setCreateOpen(open);
              if (!open) resetCreateForm();
            }}
          >
            <DialogTrigger
              render={
                <Button>
                  <Plus className="mr-1 size-4" /> New Dedicated
                </Button>
              }
            />
            <DialogContent className="max-w-lg max-h-[85vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>Create Dedicated Service</DialogTitle>
                <DialogDescription>
                  A dedicated service points to an existing server directory with a fixed
                  name and port. No templates, no scaling.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-4 py-2">
                <Field>
                  <FieldLabel>Name</FieldLabel>
                  <Input
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    placeholder="e.g. sandbox"
                  />
                  <FieldDescription>
                    Unique identifier. The service directory will be auto-created at{" "}
                    <code className="font-mono text-xs">dedicated/{newName || "<name>"}/</code>.
                  </FieldDescription>
                </Field>

                <div className="grid grid-cols-2 gap-3">
                  <Field>
                    <FieldLabel>Port</FieldLabel>
                    <Input
                      type="number"
                      min={1}
                      max={65535}
                      value={newPort}
                      onChange={(e) => setNewPort(Number(e.target.value))}
                    />
                  </Field>
                  <Field>
                    <FieldLabel>Memory</FieldLabel>
                    <Input
                      value={newMemory}
                      onChange={(e) => setNewMemory(e.target.value)}
                      placeholder="2G"
                    />
                  </Field>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <Field>
                    <FieldLabel>Software</FieldLabel>
                    <Select value={newSoftware} onValueChange={(v) => v && setNewSoftware(v)}>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {SOFTWARE_OPTIONS.map((sw) => (
                            <SelectItem key={sw} value={sw}>
                              {sw}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel>Version</FieldLabel>
                    <Input
                      value={newVersion}
                      onChange={(e) => setNewVersion(e.target.value)}
                      placeholder="1.21.4"
                    />
                  </Field>
                </div>

                <Field>
                  <div className="flex items-center justify-between">
                    <div>
                      <FieldLabel>Proxy Enabled</FieldLabel>
                      <FieldDescription>
                        Register with Velocity proxy and patch forwarding config.
                      </FieldDescription>
                    </div>
                    <Switch
                      checked={newProxyEnabled}
                      onCheckedChange={setNewProxyEnabled}
                    />
                  </div>
                </Field>

                <Field>
                  <div className="flex items-center justify-between">
                    <div>
                      <FieldLabel>Restart on Crash</FieldLabel>
                      <FieldDescription>
                        Automatically restart if the process exits unexpectedly.
                      </FieldDescription>
                    </div>
                    <Switch
                      checked={newRestartOnCrash}
                      onCheckedChange={setNewRestartOnCrash}
                    />
                  </div>
                </Field>
              </div>
              <DialogFooter>
                <Button
                  onClick={createService}
                  disabled={creating || !newName.trim()}
                >
                  {creating ? "Creating..." : "Create"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardHeader>
        <CardContent>
          {services.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <BoxIcon className="size-10 text-muted-foreground/50 mb-3" />
              <p className="text-sm font-medium text-muted-foreground">
                No dedicated services configured
              </p>
              <p className="text-xs text-muted-foreground/70 mt-1">
                Create one to manage a standalone server
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>State</TableHead>
                  <TableHead>Software</TableHead>
                  <TableHead>Version</TableHead>
                  <TableHead>Port</TableHead>
                  <TableHead>Memory</TableHead>
                  <TableHead>Proxy</TableHead>
                  <TableHead>Players</TableHead>
                  <TableHead>Uptime</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {services.map((s) => {
                  const isRunning =
                    s.state !== null && s.state !== "STOPPED" && s.state !== "CRASHED";
                  return (
                    <TableRow key={s.name}>
                      <TableCell className="font-medium">{s.name}</TableCell>
                      <TableCell>
                        <Badge variant={stateBadgeVariant(s.state)}>
                          {s.state ?? "OFFLINE"}
                        </Badge>
                      </TableCell>
                      <TableCell>{s.software}</TableCell>
                      <TableCell>{s.version}</TableCell>
                      <TableCell>{s.port}</TableCell>
                      <TableCell>{s.memory}</TableCell>
                      <TableCell>
                        <Badge variant={s.proxyEnabled ? "default" : "outline"}>
                          {s.proxyEnabled ? "ON" : "OFF"}
                        </Badge>
                      </TableCell>
                      <TableCell>{s.playerCount ?? "—"}</TableCell>
                      <TableCell className="text-muted-foreground text-xs">
                        {s.uptime ?? "—"}
                      </TableCell>
                      <TableCell>
                        <DropdownMenu>
                          <DropdownMenuTrigger
                            render={
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8"
                                disabled={actioning === s.name}
                              >
                                <MoreVertical className="size-4" />
                              </Button>
                            }
                          />
                          <DropdownMenuContent align="end">
                            {!isRunning && (
                              <DropdownMenuItem onClick={() => doAction(s.name, "start")}>
                                <Play className="mr-2 size-4" /> Start
                              </DropdownMenuItem>
                            )}
                            {isRunning && (
                              <DropdownMenuItem onClick={() => doAction(s.name, "stop")}>
                                <Square className="mr-2 size-4" /> Stop
                              </DropdownMenuItem>
                            )}
                            {isRunning && (
                              <DropdownMenuItem onClick={() => doAction(s.name, "restart")}>
                                <RotateCw className="mr-2 size-4" /> Restart
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              onClick={() => setDeleteTarget(s.name)}
                              className="text-destructive"
                            >
                              <Trash2 className="mr-2 size-4" /> Delete
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Dedicated Service</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete &apos;{deleteTarget}&apos;? The server will
              be stopped if running. The server directory itself will not be deleted. This
              action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={confirmDelete} disabled={deleting}>
              {deleting ? "Deleting..." : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
