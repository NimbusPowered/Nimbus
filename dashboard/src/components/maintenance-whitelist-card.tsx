"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Plus, Trash2, Shield } from "@/lib/icons";

interface GlobalMaintenance {
  enabled: boolean;
  motdLine1: string;
  motdLine2: string;
  protocolText: string;
  kickMessage: string;
  whitelist: string[];
}

interface MaintenanceStatus {
  global: GlobalMaintenance;
  groups: Record<string, { enabled: boolean; kickMessage: string }>;
}

/**
 * Maintenance toggle + whitelist management. Backend whitelist entries are
 * free-form strings (UUID or player name) — the controller does not enforce
 * a {uuid, name} shape.
 */
export function MaintenanceWhitelistCard() {
  const [status, setStatus] = useState<MaintenanceStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [toggling, setToggling] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [newEntry, setNewEntry] = useState("");
  const [adding, setAdding] = useState(false);

  async function load() {
    try {
      const s = await apiFetch<MaintenanceStatus>("/api/maintenance");
      setStatus(s);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function toggleGlobal(enabled: boolean) {
    setToggling(true);
    try {
      const updated = await apiFetch<MaintenanceStatus>(
        "/api/maintenance/global",
        {
          method: "POST",
          body: JSON.stringify({ enabled }),
        }
      );
      setStatus(updated);
      toast.success(
        enabled ? "Maintenance enabled" : "Maintenance disabled"
      );
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Toggle failed");
    } finally {
      setToggling(false);
    }
  }

  async function addEntry() {
    const entry = newEntry.trim();
    if (!entry) return;
    setAdding(true);
    try {
      await apiFetch("/api/maintenance/whitelist", {
        method: "POST",
        body: JSON.stringify({ entry }),
      });
      toast.success(`Added '${entry}' to whitelist`);
      setNewEntry("");
      setAddOpen(false);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Add failed");
    } finally {
      setAdding(false);
    }
  }

  async function removeEntry(entry: string) {
    try {
      await apiFetch("/api/maintenance/whitelist", {
        method: "DELETE",
        body: JSON.stringify({ entry }),
      });
      toast.success(`Removed '${entry}'`);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Remove failed");
    }
  }

  return (
    <Card className="md:col-span-2">
      <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0">
        <div className="flex items-center gap-2">
          <Shield className="size-4 text-muted-foreground" />
          <CardTitle>Maintenance</CardTitle>
          {status?.global.enabled && (
            <Badge
              variant="outline"
              className="border-amber-500/40 text-amber-600 dark:text-amber-400"
            >
              Active
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-muted-foreground">
            Global maintenance
          </span>
          <Switch
            checked={status?.global.enabled ?? false}
            disabled={toggling || loading}
            onCheckedChange={toggleGlobal}
          />
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {loading ? (
          <Skeleton className="h-32 rounded-md" />
        ) : (
          <>
            <div className="flex items-center justify-between">
              <p className="text-xs text-muted-foreground">
                Whitelisted players bypass maintenance kicks. Entries can be
                player names or UUIDs.
              </p>
              <Dialog open={addOpen} onOpenChange={setAddOpen}>
                <DialogTrigger
                  render={
                    <Button variant="outline" size="sm">
                      <Plus className="mr-1 size-3.5" /> Add
                    </Button>
                  }
                />
                <DialogContent className="max-w-md">
                  <DialogHeader>
                    <DialogTitle>Add to whitelist</DialogTitle>
                    <DialogDescription>
                      Player name or UUID. Whitelisted players can connect
                      while maintenance is active.
                    </DialogDescription>
                  </DialogHeader>
                  <div className="py-2">
                    <Input
                      value={newEntry}
                      onChange={(e) => setNewEntry(e.target.value)}
                      placeholder="Notch or 069a79f4-44e9-4726-a5be-fca90e38aaf5"
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && newEntry.trim() && !adding) addEntry();
                      }}
                      autoFocus
                    />
                  </div>
                  <DialogFooter>
                    <Button
                      variant="outline"
                      onClick={() => setAddOpen(false)}
                      disabled={adding}
                    >
                      Cancel
                    </Button>
                    <Button onClick={addEntry} disabled={adding || !newEntry.trim()}>
                      Add
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>

            {status && status.global.whitelist.length === 0 ? (
              <div className="rounded-md border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
                Whitelist is empty.
              </div>
            ) : (
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Entry</TableHead>
                      <TableHead className="w-16 text-right pr-4" />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {status?.global.whitelist.map((entry) => (
                      <TableRow key={entry}>
                        <TableCell className="font-mono text-xs break-all">
                          {entry}
                        </TableCell>
                        <TableCell className="text-right pr-4">
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => removeEntry(entry)}
                            className="text-muted-foreground hover:text-destructive"
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
