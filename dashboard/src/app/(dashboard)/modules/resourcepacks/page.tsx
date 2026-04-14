"use client";

import { useCallback, useEffect, useRef, useState } from "react";
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
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";
import {
  Package,
  Signpost,
  Upload,
  Trash2,
  X,
  Plus,
} from "@/lib/icons";

interface ResourcePack {
  id: number;
  packUuid: string;
  name: string;
  source: string;            // "URL" | "LOCAL"
  url: string;
  sha1Hash: string;
  promptMessage: string;
  force: boolean;
  fileSize: number;
  uploadedAt: string;
  uploadedBy: string;
}

interface Assignment {
  id: number;
  packId: number;
  scope: string;             // "GLOBAL" | "GROUP" | "SERVICE"
  target: string;
  priority: number;
}

interface PackListResponse {
  packs: ResourcePack[];
  total: number;
}

function formatSize(bytes: number): string {
  if (bytes === 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default function ResourcePacksModulePage() {
  const [packs, setPacks] = useState<ResourcePack[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddUrl, setShowAddUrl] = useState(false);
  const [showAssign, setShowAssign] = useState<number | null>(null);

  // Add-URL form state
  const [urlName, setUrlName] = useState("");
  const [urlValue, setUrlValue] = useState("");
  const [urlSha1, setUrlSha1] = useState("");
  const [urlPrompt, setUrlPrompt] = useState("");
  const [urlForce, setUrlForce] = useState(false);

  // Assign form state
  const [assignScope, setAssignScope] = useState<"GLOBAL" | "GROUP" | "SERVICE">(
    "GLOBAL"
  );
  const [assignTarget, setAssignTarget] = useState("");
  const [assignPriority, setAssignPriority] = useState(0);

  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    try {
      const [packRes, assignRes] = await Promise.all([
        apiFetch<PackListResponse>("/api/resourcepacks").catch(() => ({
          packs: [],
          total: 0,
        })),
        apiFetch<Assignment[]>("/api/resourcepacks/assignments").catch(() => []),
      ]);
      setPacks(packRes.packs);
      setAssignments(assignRes);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const submitUrlPack = async () => {
    if (!urlName.trim() || !urlValue.trim() || urlSha1.length !== 40) return;
    setWorking(true);
    setError(null);
    try {
      await apiFetch<ResourcePack>("/api/resourcepacks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: urlName.trim(),
          url: urlValue.trim(),
          sha1Hash: urlSha1.toLowerCase(),
          promptMessage: urlPrompt,
          force: urlForce,
        }),
      });
      setShowAddUrl(false);
      setUrlName("");
      setUrlValue("");
      setUrlSha1("");
      setUrlPrompt("");
      setUrlForce(false);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to add pack");
    } finally {
      setWorking(false);
    }
  };

  const uploadFile = async (file: File) => {
    const name = prompt("Pack name:", file.name.replace(/\.zip$/i, ""));
    if (!name) return;
    setWorking(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      // apiFetch doesn't handle multipart — call fetch directly with auth header
      const token = localStorage.getItem("nimbus_token");
      const apiUrl = localStorage.getItem("nimbus_api_url") || "";
      const res = await fetch(
        `${apiUrl}/api/resourcepacks/upload?name=${encodeURIComponent(name)}`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
          body: form,
        }
      );
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || `Upload failed: ${res.status}`);
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setWorking(false);
    }
  };

  const deletePack = async (id: number) => {
    if (!confirm("Delete this pack and all its assignments?")) return;
    setWorking(true);
    try {
      await apiFetch(`/api/resourcepacks/${id}`, { method: "DELETE" });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Delete failed");
    } finally {
      setWorking(false);
    }
  };

  const submitAssign = async () => {
    if (showAssign === null) return;
    if (assignScope !== "GLOBAL" && !assignTarget.trim()) return;
    setWorking(true);
    setError(null);
    try {
      await apiFetch(`/api/resourcepacks/${showAssign}/assignments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          scope: assignScope,
          target: assignScope === "GLOBAL" ? "" : assignTarget.trim(),
          priority: assignPriority,
        }),
      });
      setShowAssign(null);
      setAssignTarget("");
      setAssignPriority(0);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Assign failed");
    } finally {
      setWorking(false);
    }
  };

  const removeAssignment = async (id: number) => {
    setWorking(true);
    try {
      await apiFetch(`/api/resourcepacks/assignments/${id}`, { method: "DELETE" });
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Remove failed");
    } finally {
      setWorking(false);
    }
  };

  const assignmentsByPack = assignments.reduce<Record<number, Assignment[]>>(
    (acc, a) => {
      (acc[a.packId] ??= []).push(a);
      return acc;
    },
    {}
  );

  const totalSize = packs
    .filter((p) => p.source === "LOCAL")
    .reduce((sum, p) => sum + p.fileSize, 0);

  const actions = (
    <div className="flex gap-2">
      <Button
        variant="outline"
        size="sm"
        onClick={() => setShowAddUrl(true)}
        disabled={working}
      >
        <Plus className="size-4 mr-1" />
        Add URL
      </Button>
      <input
        ref={fileInputRef}
        type="file"
        accept=".zip"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) uploadFile(file);
          e.target.value = "";
        }}
        disabled={working}
      />
      <Button
        variant="default"
        size="sm"
        onClick={() => fileInputRef.current?.click()}
        disabled={working}
      >
        <Upload className="size-4 mr-1" />
        Upload .zip
      </Button>
    </div>
  );

  return (
    <>
      <PageHeader
        title="Resource Packs"
        description="Network-wide pack registry with GLOBAL / GROUP / SERVICE assignments."
        actions={actions}
      />

      {error && (
        <div className="mb-4 rounded-md border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-600 dark:text-red-400">
          {error}
          <button
            onClick={() => setError(null)}
            className="ml-2 underline"
            type="button"
          >
            dismiss
          </button>
        </div>
      )}

      {loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-3">
            <StatCard
              label="Packs registered"
              icon={Package}
              tone="primary"
              value={packs.length}
            />
            <StatCard
              label="Assignments"
              icon={Signpost}
              value={assignments.length}
              hint="across scopes"
            />
            <StatCard
              label="Local storage"
              icon={Upload}
              value={formatSize(totalSize)}
              hint="hosted files"
            />
          </div>

          {showAddUrl && (
            <Card>
              <CardContent className="p-4 space-y-3">
                <h3 className="font-medium">Add URL pack</h3>
                <div className="grid gap-3 md:grid-cols-2">
                  <Input
                    placeholder="Display name"
                    value={urlName}
                    onChange={(e) => setUrlName(e.target.value)}
                  />
                  <Input
                    placeholder="https://…/pack.zip"
                    value={urlValue}
                    onChange={(e) => setUrlValue(e.target.value)}
                  />
                  <Input
                    placeholder="SHA-1 hash (40 hex chars)"
                    value={urlSha1}
                    onChange={(e) => setUrlSha1(e.target.value)}
                    maxLength={40}
                  />
                  <Input
                    placeholder="Prompt message (optional)"
                    value={urlPrompt}
                    onChange={(e) => setUrlPrompt(e.target.value)}
                  />
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={urlForce}
                    onChange={(e) => setUrlForce(e.target.checked)}
                  />
                  Force (kick on decline)
                </label>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    onClick={submitUrlPack}
                    disabled={
                      working ||
                      !urlName.trim() ||
                      !urlValue.trim() ||
                      urlSha1.length !== 40
                    }
                  >
                    Create
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setShowAddUrl(false)}
                  >
                    Cancel
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}

          {packs.length === 0 ? (
            <EmptyState
              icon={Package}
              title="No resource packs"
              description="Add a URL pack or upload a .zip — then assign it to a group, service, or globally."
            />
          ) : (
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-6">Name</TableHead>
                      <TableHead>Source</TableHead>
                      <TableHead>Size</TableHead>
                      <TableHead>Assignments</TableHead>
                      <TableHead className="text-right pr-6">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {packs.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell className="pl-6">
                          <div className="font-medium">
                            {p.name}
                            {p.force && (
                              <Badge
                                variant="outline"
                                className="ml-2 text-[10px] border-orange-500/40 text-orange-600 dark:text-orange-400"
                              >
                                FORCE
                              </Badge>
                            )}
                          </div>
                          <div
                            className="text-xs text-muted-foreground truncate max-w-xs"
                            title={p.url}
                          >
                            {p.url}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{p.source}</Badge>
                        </TableCell>
                        <TableCell className="text-sm">
                          {formatSize(p.fileSize)}
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-1">
                            {(assignmentsByPack[p.id] ?? []).map((a) => (
                              <Badge
                                key={a.id}
                                variant="secondary"
                                className="text-xs gap-1"
                              >
                                {a.scope}
                                {a.target && `:${a.target}`}
                                <button
                                  className="hover:text-red-500"
                                  onClick={() => removeAssignment(a.id)}
                                  type="button"
                                  aria-label="Remove"
                                >
                                  <X className="size-3" />
                                </button>
                              </Badge>
                            ))}
                            {!assignmentsByPack[p.id]?.length && (
                              <span className="text-xs text-muted-foreground">
                                unused
                              </span>
                            )}
                          </div>
                        </TableCell>
                        <TableCell className="text-right pr-6">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setShowAssign(p.id)}
                          >
                            Assign
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => deletePack(p.id)}
                            disabled={working}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {showAssign !== null && (
            <Card>
              <CardContent className="p-4 space-y-3">
                <h3 className="font-medium">
                  Assign pack #{showAssign}
                </h3>
                <div className="flex flex-wrap gap-3">
                  <div>
                    <label className="text-xs text-muted-foreground block mb-1">
                      Scope
                    </label>
                    <select
                      value={assignScope}
                      onChange={(e) =>
                        setAssignScope(e.target.value as typeof assignScope)
                      }
                      className="border rounded-md px-2 py-1.5 text-sm"
                    >
                      <option value="GLOBAL">GLOBAL</option>
                      <option value="GROUP">GROUP</option>
                      <option value="SERVICE">SERVICE</option>
                    </select>
                  </div>
                  {assignScope !== "GLOBAL" && (
                    <div className="flex-1 min-w-48">
                      <label className="text-xs text-muted-foreground block mb-1">
                        {assignScope === "GROUP"
                          ? "Group name"
                          : "Service name"}
                      </label>
                      <Input
                        value={assignTarget}
                        onChange={(e) => setAssignTarget(e.target.value)}
                        placeholder={
                          assignScope === "GROUP" ? "Lobby" : "Lobby-1"
                        }
                      />
                    </div>
                  )}
                  <div className="w-32">
                    <label className="text-xs text-muted-foreground block mb-1">
                      Priority
                    </label>
                    <Input
                      type="number"
                      value={assignPriority}
                      onChange={(e) =>
                        setAssignPriority(Number(e.target.value))
                      }
                    />
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button size="sm" onClick={submitAssign} disabled={working}>
                    Assign
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setShowAssign(null)}
                  >
                    Cancel
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </>
  );
}
