"use client";

import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import * as api from "@/lib/api";
import { FileEntry } from "@/lib/types";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import {
  FolderOpen,
  FolderPlus,
  File,
  FileText,
  FileCode,
  FileJson,
  FileCog,
  Package,
  Upload,
  ChevronRight,
  Home,
  Save,
  Trash2,
  RefreshCw,
  X,
  FolderUp,
} from "lucide-react";

type Scope = "templates" | "services" | "groups";

function formatSize(bytes: number): string {
  if (bytes === 0) return "-";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
}

function getFileIcon(entry: FileEntry) {
  if (entry.isDirectory) {
    return <FolderOpen className="h-4 w-4 text-blue-400" />;
  }

  const ext = entry.name.split(".").pop()?.toLowerCase();
  switch (ext) {
    case "jar":
      return <Package className="h-4 w-4 text-orange-400" />;
    case "yml":
    case "yaml":
      return <FileCog className="h-4 w-4 text-yellow-400" />;
    case "toml":
      return <FileCog className="h-4 w-4 text-amber-400" />;
    case "json":
      return <FileJson className="h-4 w-4 text-green-400" />;
    case "properties":
      return <FileCode className="h-4 w-4 text-purple-400" />;
    case "txt":
    case "log":
    case "md":
      return <FileText className="h-4 w-4 text-muted-foreground" />;
    default:
      return <File className="h-4 w-4 text-muted-foreground" />;
  }
}

function buildScopePath(scope: Scope, path: string, name?: string): string {
  let base = path === "/" ? scope : `${scope}/${path}`;
  if (name) base += (path === "/" && !base.endsWith("/") ? "/" : "/") + name;
  // fix double slashes
  return base.replace(/\/+/g, "/").replace(/\/$/, "") || scope;
}

export default function FilesPage() {
  const [scope, setScope] = useState<Scope>("templates");
  const [path, setPath] = useState("/");
  const [entries, setEntries] = useState<FileEntry[]>([]);
  const [loading, setLoading] = useState(false);

  // Edit dialog
  const [editDialog, setEditDialog] = useState<{
    path: string;
    content: string;
  } | null>(null);

  // Create folder dialog
  const [createFolderOpen, setCreateFolderOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");

  // Upload state
  const [isDragOver, setIsDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const dragCounterRef = useRef(0);

  const isReadOnly = scope === "groups";

  const pathParts = useMemo(() => {
    if (path === "/") return [];
    return path.split("/").filter(Boolean);
  }, [path]);

  const loadEntries = useCallback(async () => {
    setLoading(true);
    try {
      const fullPath = path === "/" ? scope : `${scope}/${path}`;
      const data = await api.listFiles(fullPath);
      const sorted = (data.entries ?? []).sort((a: FileEntry, b: FileEntry) => {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
      setEntries(sorted);
    } catch {
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }, [scope, path]);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  // Navigation

  function navigate(entry: FileEntry) {
    if (entry.isDirectory) {
      setPath(path === "/" ? entry.name : `${path}/${entry.name}`);
    } else {
      openFile(entry);
    }
  }

  function navigateToIndex(index: number) {
    if (index < 0) {
      setPath("/");
    } else {
      setPath(pathParts.slice(0, index + 1).join("/"));
    }
  }

  function goUp() {
    if (path === "/") return;
    const parts = path.split("/");
    parts.pop();
    setPath(parts.length === 0 ? "/" : parts.join("/"));
  }

  // File operations

  async function openFile(entry: FileEntry) {
    try {
      const fullPath = buildScopePath(scope, path, entry.name);
      const data = await api.readFile(fullPath);
      if (data.content !== undefined) {
        setEditDialog({ path: fullPath, content: data.content });
      } else {
        toast.info("Binary file -- download not supported in browser");
      }
    } catch (e) {
      toast.error(
        `Failed to read file: ${e instanceof Error ? e.message : "Unknown error"}`
      );
    }
  }

  async function handleSave() {
    if (!editDialog) return;
    try {
      await api.writeFile(editDialog.path, editDialog.content);
      toast.success("File saved");
      setEditDialog(null);
      loadEntries();
    } catch (e) {
      toast.error(
        `Failed to save: ${e instanceof Error ? e.message : "Unknown error"}`
      );
    }
  }

  async function handleDelete(entry: FileEntry) {
    const fullPath = buildScopePath(scope, path, entry.name);
    if (!confirm(`Delete "${entry.name}"?`)) return;
    try {
      await api.deleteFile(fullPath);
      toast.success(`Deleted "${entry.name}"`);
      loadEntries();
    } catch (e) {
      toast.error(
        `Failed: ${e instanceof Error ? e.message : "Unknown error"}`
      );
    }
  }

  // Upload

  async function handleUploadFiles(files: FileList | File[]) {
    if (isReadOnly) {
      toast.error("Groups scope is read-only");
      return;
    }
    const fileArray = Array.from(files);
    if (fileArray.length === 0) return;

    setUploading(true);
    let succeeded = 0;
    let failed = 0;

    for (const file of fileArray) {
      const targetPath = buildScopePath(scope, path, file.name);
      try {
        toast.loading(`Uploading ${file.name}...`, { id: `upload-${file.name}` });
        await api.uploadFile(targetPath, file);
        toast.success(`Uploaded ${file.name}`, { id: `upload-${file.name}` });
        succeeded++;
      } catch (e) {
        toast.error(
          `Failed to upload ${file.name}: ${e instanceof Error ? e.message : "Unknown error"}`,
          { id: `upload-${file.name}` }
        );
        failed++;
      }
    }

    setUploading(false);
    if (succeeded > 0) {
      loadEntries();
    }
    if (fileArray.length > 1) {
      toast.info(`Upload complete: ${succeeded} succeeded, ${failed} failed`);
    }
  }

  function handleFileInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      handleUploadFiles(e.target.files);
      e.target.value = "";
    }
  }

  // Drag and drop handlers

  function handleDragEnter(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current++;
    if (e.dataTransfer.types.includes("Files")) {
      setIsDragOver(true);
    }
  }

  function handleDragLeave(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current--;
    if (dragCounterRef.current === 0) {
      setIsDragOver(false);
    }
  }

  function handleDragOver(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current = 0;
    setIsDragOver(false);

    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleUploadFiles(e.dataTransfer.files);
    }
  }

  // Create folder

  async function handleCreateFolder() {
    if (!newFolderName.trim()) return;
    const targetPath = buildScopePath(scope, path, newFolderName.trim());
    try {
      await api.createDirectory(targetPath);
      toast.success(`Created folder "${newFolderName.trim()}"`);
      setCreateFolderOpen(false);
      setNewFolderName("");
      loadEntries();
    } catch (e) {
      toast.error(
        `Failed to create folder: ${e instanceof Error ? e.message : "Unknown error"}`
      );
    }
  }

  // Render

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Files</h1>
        <p className="text-muted-foreground">
          Browse, edit, and manage server files
        </p>
      </div>

      <Tabs
        value={scope}
        onValueChange={(v) => {
          if (v) {
            setScope(v as Scope);
            setPath("/");
          }
        }}
      >
        <TabsList>
          <TabsTrigger value="templates">Templates</TabsTrigger>
          <TabsTrigger value="services">Services</TabsTrigger>
          <TabsTrigger value="groups">Groups</TabsTrigger>
        </TabsList>

        <TabsContent value={scope}>
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between gap-4">
                {/* Breadcrumb navigation */}
                <nav className="flex items-center gap-1 text-sm min-w-0 overflow-x-auto">
                  <button
                    onClick={() => setPath("/")}
                    className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors shrink-0"
                  >
                    <Home className="h-4 w-4" />
                    <span>{scope}</span>
                  </button>
                  {pathParts.map((part, i) => (
                    <span key={i} className="flex items-center gap-1 shrink-0">
                      <ChevronRight className="h-3 w-3 text-muted-foreground/50" />
                      <button
                        onClick={() => navigateToIndex(i)}
                        className={
                          i === pathParts.length - 1
                            ? "font-medium text-foreground"
                            : "text-muted-foreground hover:text-foreground transition-colors"
                        }
                      >
                        {part}
                      </button>
                    </span>
                  ))}
                </nav>

                {/* Actions */}
                <div className="flex items-center gap-2 shrink-0">
                  {!isReadOnly && (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setCreateFolderOpen(true)}
                      >
                        <FolderPlus className="mr-2 h-4 w-4" />
                        New Folder
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={uploading}
                      >
                        <Upload className="mr-2 h-4 w-4" />
                        Upload
                      </Button>
                      <input
                        ref={fileInputRef}
                        type="file"
                        multiple
                        className="hidden"
                        onChange={handleFileInputChange}
                      />
                    </>
                  )}
                  <Button variant="outline" size="sm" onClick={loadEntries}>
                    <RefreshCw className="mr-2 h-4 w-4" />
                    Refresh
                  </Button>
                </div>
              </div>
            </CardHeader>

            <CardContent className="p-6 pt-0">
              {/* Drop zone wrapper */}
              <div
                className="relative"
                onDragEnter={!isReadOnly ? handleDragEnter : undefined}
                onDragLeave={!isReadOnly ? handleDragLeave : undefined}
                onDragOver={!isReadOnly ? handleDragOver : undefined}
                onDrop={!isReadOnly ? handleDrop : undefined}
              >
                {/* Drag overlay */}
                {isDragOver && !isReadOnly && (
                  <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg border-2 border-dashed border-primary bg-primary/5 backdrop-blur-[2px]">
                    <div className="flex flex-col items-center gap-2 text-primary">
                      <Upload className="h-8 w-8" />
                      <p className="text-sm font-medium">
                        Drop files here to upload
                      </p>
                    </div>
                  </div>
                )}

                {loading ? (
                  <div className="space-y-2">
                    {[...Array(5)].map((_, i) => (
                      <div
                        key={i}
                        className="h-12 animate-pulse rounded-md bg-muted"
                      />
                    ))}
                  </div>
                ) : entries.length === 0 && path === "/" ? (
                  /* Empty root state */
                  <div className="flex flex-col items-center justify-center py-16 text-center">
                    <FolderOpen className="h-12 w-12 text-muted-foreground/40 mb-4" />
                    <h3 className="text-lg font-medium mb-1">
                      No files yet
                    </h3>
                    <p className="text-muted-foreground text-sm mb-6 max-w-sm">
                      This directory is empty.
                      {!isReadOnly &&
                        " Upload files or create a folder to get started."}
                    </p>
                    {!isReadOnly && (
                      <div className="flex gap-3">
                        <Button
                          variant="outline"
                          onClick={() => setCreateFolderOpen(true)}
                        >
                          <FolderPlus className="mr-2 h-4 w-4" />
                          Create Folder
                        </Button>
                        <Button
                          onClick={() => fileInputRef.current?.click()}
                          disabled={uploading}
                        >
                          <Upload className="mr-2 h-4 w-4" />
                          Upload Files
                        </Button>
                      </div>
                    )}
                  </div>
                ) : entries.length === 0 && path !== "/" ? (
                  /* Empty subdirectory state */
                  <div className="flex flex-col items-center justify-center py-16 text-center">
                    <FolderOpen className="h-12 w-12 text-muted-foreground/40 mb-4" />
                    <h3 className="text-lg font-medium mb-1">
                      Empty folder
                    </h3>
                    <p className="text-muted-foreground text-sm mb-6 max-w-sm">
                      {!isReadOnly
                        ? "Drag and drop files here, or use the upload button above."
                        : "This folder has no contents."}
                    </p>
                    <Button variant="outline" onClick={goUp}>
                      <FolderUp className="mr-2 h-4 w-4" />
                      Go Back
                    </Button>
                  </div>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        <TableHead className="w-[50%]">Name</TableHead>
                        <TableHead className="w-[15%]">Size</TableHead>
                        <TableHead className="w-[25%]">Modified</TableHead>
                        <TableHead className="w-[10%] text-right">
                          Actions
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {path !== "/" && (
                        <TableRow
                          className="cursor-pointer group"
                          onClick={goUp}
                        >
                          <TableCell className="py-3">
                            <div className="flex items-center gap-3">
                              <FolderUp className="h-4 w-4 text-muted-foreground" />
                              <span className="text-muted-foreground group-hover:text-foreground transition-colors">
                                ..
                              </span>
                            </div>
                          </TableCell>
                          <TableCell />
                          <TableCell />
                          <TableCell />
                        </TableRow>
                      )}
                      {entries.map((entry) => (
                        <TableRow
                          key={entry.name}
                          className="cursor-pointer group"
                          onClick={() => navigate(entry)}
                        >
                          <TableCell className="py-3">
                            <div className="flex items-center gap-3">
                              {getFileIcon(entry)}
                              <span className="group-hover:text-foreground transition-colors">
                                {entry.name}
                              </span>
                              {entry.isDirectory && (
                                <Badge
                                  variant="secondary"
                                  className="text-[10px] px-1.5 py-0"
                                >
                                  folder
                                </Badge>
                              )}
                            </div>
                          </TableCell>
                          <TableCell className="text-muted-foreground text-sm">
                            {entry.isDirectory ? "-" : formatSize(entry.size)}
                          </TableCell>
                          <TableCell className="text-muted-foreground text-xs">
                            {entry.lastModified
                              ? new Date(entry.lastModified).toLocaleString()
                              : "-"}
                          </TableCell>
                          <TableCell className="text-right">
                            {!isReadOnly && !entry.isDirectory && (
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8 opacity-0 group-hover:opacity-100 transition-opacity"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleDelete(entry);
                                }}
                              >
                                <Trash2 className="h-4 w-4 text-destructive" />
                              </Button>
                            )}
                            {!isReadOnly && entry.isDirectory && (
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8 opacity-0 group-hover:opacity-100 transition-opacity"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleDelete(entry);
                                }}
                              >
                                <Trash2 className="h-4 w-4 text-destructive" />
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}

                {/* Subtle upload hint at bottom when directory has files */}
                {!isReadOnly && entries.length > 0 && !loading && (
                  <div className="mt-4 flex items-center justify-center rounded-lg border border-dashed border-muted-foreground/20 py-3 text-xs text-muted-foreground/50 transition-colors hover:border-muted-foreground/40 hover:text-muted-foreground/70">
                    <Upload className="mr-2 h-3 w-3" />
                    Drag and drop files here to upload
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Edit file dialog */}
      <Dialog open={!!editDialog} onOpenChange={() => setEditDialog(null)}>
        <DialogContent className="max-w-3xl max-h-[80vh]">
          <DialogHeader>
            <DialogTitle className="font-mono text-sm truncate">
              {editDialog?.path}
            </DialogTitle>
          </DialogHeader>
          <Textarea
            className="min-h-[400px] font-mono text-sm"
            value={editDialog?.content ?? ""}
            onChange={(e) =>
              setEditDialog(
                editDialog
                  ? { ...editDialog, content: e.target.value }
                  : null
              )
            }
            readOnly={isReadOnly}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDialog(null)}>
              Cancel
            </Button>
            {!isReadOnly && (
              <Button onClick={handleSave}>
                <Save className="mr-2 h-4 w-4" />
                Save
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create folder dialog */}
      <Dialog open={createFolderOpen} onOpenChange={setCreateFolderOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Create New Folder</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <label className="text-sm text-muted-foreground mb-2 block">
              Folder name
            </label>
            <Input
              placeholder="my-folder"
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleCreateFolder();
              }}
              autoFocus
            />
            <p className="text-xs text-muted-foreground mt-2">
              Will be created in: /{scope}
              {path !== "/" ? `/${path}` : ""}/{newFolderName || "..."}
            </p>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setCreateFolderOpen(false);
                setNewFolderName("");
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={handleCreateFolder}
              disabled={!newFolderName.trim()}
            >
              <FolderPlus className="mr-2 h-4 w-4" />
              Create
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
