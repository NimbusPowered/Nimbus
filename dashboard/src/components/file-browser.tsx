"use client";

import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
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
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Download,
  File as FileIcon,
  Folder,
  FolderPlus,
  ImageIcon,
  Loader2,
  Pencil,
  RefreshCw,
  Trash2,
  Upload,
  ZoomIn,
} from "@/lib/icons";
import { apiFetch, getApiUrl, getToken } from "@/lib/api";
import { cn } from "@/lib/utils";
import { FileEditor, isEditableExtension, isImageExtension } from "@/components/file-editor";
import { FileImagePreview } from "@/components/file-image-preview";

// ── Types ──────────────────────────────────────────────────────────

export interface FileEntry {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  lastModified?: string | null;
}

interface FileListResponse {
  scope: string;
  path: string;
  entries: FileEntry[];
  total: number;
}

interface FileContentResponse {
  scope: string;
  path: string;
  content: string;
  size: number;
}

// Scopes accepted by FileRoutes.kt (templates + services writable, groups read-only)
export const FILE_SCOPES = [
  { value: "templates", label: "Templates", readOnly: false },
  { value: "services", label: "Services", readOnly: false },
  { value: "groups", label: "Groups (read-only)", readOnly: true },
] as const;

export type FileScope = (typeof FILE_SCOPES)[number]["value"];

// ── Helpers ────────────────────────────────────────────────────────

function joinPath(...parts: string[]): string {
  return parts
    .map((p) => p.replace(/^\/+|\/+$/g, ""))
    .filter(Boolean)
    .join("/");
}

function parentPath(p: string): string {
  const cleaned = p.replace(/^\/+|\/+$/g, "");
  if (!cleaned) return "";
  const idx = cleaned.lastIndexOf("/");
  return idx === -1 ? "" : cleaned.slice(0, idx);
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return "—";
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let i = 0;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(size >= 10 || i === 0 ? 0 : 1)} ${units[i]}`;
}

function formatDate(iso?: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

// ── Component ──────────────────────────────────────────────────────

export function FileBrowser() {
  const [scope, setScope] = React.useState<FileScope>("templates");
  const [path, setPath] = React.useState<string>("");
  const [data, setData] = React.useState<FileListResponse | null>(null);
  const [loading, setLoading] = React.useState<boolean>(true);
  const [error, setError] = React.useState<string | null>(null);

  const [deleteTarget, setDeleteTarget] = React.useState<FileEntry | null>(null);
  const [deleting, setDeleting] = React.useState(false);

  const [mkdirOpen, setMkdirOpen] = React.useState(false);
  const [mkdirName, setMkdirName] = React.useState("");
  const [mkdirBusy, setMkdirBusy] = React.useState(false);

  const [uploading, setUploading] = React.useState(false);
  const [uploadProgress, setUploadProgress] = React.useState<number | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const [editorTarget, setEditorTarget] = React.useState<{
    path: string;
    content: string;
  } | null>(null);

  const [previewPath, setPreviewPath] = React.useState<string | null>(null);

  const scopeMeta = FILE_SCOPES.find((s) => s.value === scope)!;
  const readOnly = scopeMeta.readOnly;

  const load = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = path
        ? `/api/files/${scope}/${path}`
        : `/api/files/${scope}`;
      const result = await apiFetch<FileListResponse>(url, { silent: true });
      setData(result);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [scope, path]);

  React.useEffect(() => {
    void load();
  }, [load]);

  // Reset path when scope changes
  const onScopeChange = (next: string | null) => {
    if (!next) return;
    setScope(next as FileScope);
    setPath("");
  };

  // ── Navigation ────────────────────────────────────────────────────
  const breadcrumbParts = path
    ? path.split("/").filter(Boolean)
    : [];

  const onEntryClick = async (entry: FileEntry) => {
    if (entry.isDirectory) {
      setPath(entry.path);
      return;
    }
    // File: editable → editor; image → preview; otherwise → download.
    if (isEditableExtension(entry.name)) {
      await openEditor(entry);
    } else if (isImageExtension(entry.name)) {
      setPreviewPath(entry.path);
    } else {
      downloadFile(entry);
    }
  };

  const openEditor = async (entry: FileEntry) => {
    try {
      const result = await apiFetch<FileContentResponse>(
        `/api/files/${scope}/${entry.path}`
      );
      setEditorTarget({ path: entry.path, content: result.content });
    } catch (e) {
      // apiFetch already toasts
      void e;
    }
  };

  const downloadFile = (entry: FileEntry) => {
    // The GET endpoint returns the file as an attachment for binary files.
    // We open it in a new tab with the Authorization header via fetch → blob.
    void (async () => {
      try {
        const apiUrl = getApiUrl();
        const token = getToken();
        const url = `${apiUrl}/api/files/${scope}/${entry.path}`;
        const res = await fetch(url, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!res.ok) {
          throw new Error(`Download failed: ${res.status}`);
        }
        const blob = await res.blob();
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = entry.name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(a.href);
      } catch (e) {
        toast.error("Download failed", {
          description: e instanceof Error ? e.message : String(e),
        });
      }
    })();
  };

  // ── Mutations ─────────────────────────────────────────────────────
  const onSaveEdit = async (newContent: string) => {
    if (!editorTarget) return;
    try {
      await apiFetch(`/api/files/${scope}/${editorTarget.path}`, {
        method: "PUT",
        body: JSON.stringify({ content: newContent }),
      });
      toast.success("File saved", { description: editorTarget.path });
      setEditorTarget(null);
      void load();
    } catch {
      // apiFetch already toasts
    }
  };

  const doDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await apiFetch(`/api/files/${scope}/${deleteTarget.path}`, {
        method: "DELETE",
      });
      toast.success("Deleted", { description: deleteTarget.path });
      setDeleteTarget(null);
      void load();
    } catch {
      // toast already shown
    } finally {
      setDeleting(false);
    }
  };

  const doMkdir = async () => {
    const name = mkdirName.trim();
    if (!name) return;
    if (name.includes("/") || name.includes("..")) {
      toast.error("Invalid folder name");
      return;
    }
    setMkdirBusy(true);
    try {
      const target = joinPath(path, name);
      await apiFetch(`/api/files/${scope}/${target}?mkdir`, { method: "POST" });
      toast.success("Folder created", { description: target });
      setMkdirOpen(false);
      setMkdirName("");
      void load();
    } catch {
      // toast already shown
    } finally {
      setMkdirBusy(false);
    }
  };

  const onUploadClick = () => fileInputRef.current?.click();

  const onFileSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-uploading the same file
    if (!file) return;
    void uploadFile(file);
  };

  const uploadFile = (file: File) =>
    new Promise<void>((resolve) => {
      setUploading(true);
      setUploadProgress(0);

      const apiUrl = getApiUrl();
      const token = getToken();
      const target = path ? `${path}/` : "";
      const url = `${apiUrl}/api/files/${scope}/${target}`;

      const form = new FormData();
      form.append("file", file, file.name);

      const xhr = new XMLHttpRequest();
      xhr.open("POST", url, true);
      xhr.setRequestHeader("Authorization", `Bearer ${token}`);

      xhr.upload.onprogress = (ev) => {
        if (ev.lengthComputable) {
          setUploadProgress(Math.round((ev.loaded / ev.total) * 100));
        }
      };
      xhr.onload = () => {
        setUploading(false);
        setUploadProgress(null);
        if (xhr.status >= 200 && xhr.status < 300) {
          toast.success("Uploaded", { description: file.name });
          void load();
        } else {
          let msg = `Upload failed: ${xhr.status}`;
          try {
            const body = JSON.parse(xhr.responseText);
            msg = body.error || body.message || msg;
          } catch {
            // ignore
          }
          toast.error("Upload failed", { description: msg });
        }
        resolve();
      };
      xhr.onerror = () => {
        setUploading(false);
        setUploadProgress(null);
        toast.error("Upload failed", { description: "Network error" });
        resolve();
      };
      xhr.send(form);
    });

  // ── Render ────────────────────────────────────────────────────────
  const rows = data?.entries ?? [];

  return (
    <div className="flex flex-col gap-4">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-2">
        <Select value={scope} onValueChange={onScopeChange}>
          <SelectTrigger className="w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {FILE_SCOPES.map((s) => (
              <SelectItem key={s.value} value={s.value}>
                {s.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex-1" />

        <Button
          variant="outline"
          size="sm"
          onClick={() => void load()}
          disabled={loading}
        >
          <RefreshCw className={cn("size-4", loading && "animate-spin")} />
          Refresh
        </Button>

        {!readOnly && (
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setMkdirOpen(true)}
            >
              <FolderPlus className="size-4" />
              New Folder
            </Button>
            <Button size="sm" onClick={onUploadClick} disabled={uploading}>
              {uploading ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Upload className="size-4" />
              )}
              {uploading
                ? uploadProgress !== null
                  ? `Uploading ${uploadProgress}%`
                  : "Uploading…"
                : "Upload"}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              onChange={onFileSelected}
            />
          </>
        )}
      </div>

      {/* Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {path ? (
              <BreadcrumbLink
                render={
                  <button
                    type="button"
                    onClick={() => setPath("")}
                    className="cursor-pointer"
                  />
                }
              >
                {scopeMeta.label}
              </BreadcrumbLink>
            ) : (
              <BreadcrumbPage>{scopeMeta.label}</BreadcrumbPage>
            )}
          </BreadcrumbItem>
          {breadcrumbParts.map((part, i) => {
            const target = breadcrumbParts.slice(0, i + 1).join("/");
            const isLast = i === breadcrumbParts.length - 1;
            return (
              <React.Fragment key={target}>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  {isLast ? (
                    <BreadcrumbPage>{part}</BreadcrumbPage>
                  ) : (
                    <BreadcrumbLink
                      render={
                        <button
                          type="button"
                          onClick={() => setPath(target)}
                          className="cursor-pointer"
                        />
                      }
                    >
                      {part}
                    </BreadcrumbLink>
                  )}
                </BreadcrumbItem>
              </React.Fragment>
            );
          })}
        </BreadcrumbList>
      </Breadcrumb>

      {/* Body */}
      {loading ? (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-12 rounded-md" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-md border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
          {error}
        </div>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[50%]">Name</TableHead>
                <TableHead>Size</TableHead>
                <TableHead>Modified</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {path && (
                <TableRow
                  className="cursor-pointer"
                  onClick={() => setPath(parentPath(path))}
                >
                  <TableCell className="font-mono text-muted-foreground">
                    ..
                  </TableCell>
                  <TableCell>—</TableCell>
                  <TableCell>—</TableCell>
                  <TableCell />
                </TableRow>
              )}
              {rows.length === 0 && !path && (
                <TableRow>
                  <TableCell
                    colSpan={4}
                    className="text-center text-muted-foreground"
                  >
                    Empty directory
                  </TableCell>
                </TableRow>
              )}
              {rows.map((entry) => {
                const editable =
                  !entry.isDirectory && isEditableExtension(entry.name);
                const isImage =
                  !entry.isDirectory && isImageExtension(entry.name);
                return (
                  <TableRow
                    key={entry.path}
                    className="cursor-pointer"
                    onClick={() => void onEntryClick(entry)}
                  >
                    <TableCell className="font-medium">
                      <span className="flex items-center gap-2">
                        {entry.isDirectory ? (
                          <Folder className="size-4 text-muted-foreground" />
                        ) : isImage ? (
                          <ImageIcon className="size-4 text-muted-foreground" />
                        ) : (
                          <FileIcon className="size-4 text-muted-foreground" />
                        )}
                        <span>{entry.name}</span>
                      </span>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {entry.isDirectory ? "—" : formatBytes(entry.size)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(entry.lastModified)}
                    </TableCell>
                    <TableCell
                      className="text-right"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <div className="flex justify-end gap-1">
                        {!entry.isDirectory && editable && !readOnly && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => void openEditor(entry)}
                            title="Edit"
                          >
                            <Pencil className="size-4" />
                          </Button>
                        )}
                        {isImage && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => setPreviewPath(entry.path)}
                            title="Preview"
                          >
                            <ZoomIn className="size-4" />
                          </Button>
                        )}
                        {!entry.isDirectory && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => downloadFile(entry)}
                            title="Download"
                          >
                            <Download className="size-4" />
                          </Button>
                        )}
                        {!readOnly && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => setDeleteTarget(entry)}
                            title="Delete"
                          >
                            <Trash2 className="size-4 text-destructive" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Delete confirmation */}
      <Dialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {deleteTarget?.isDirectory ? "folder" : "file"}?</DialogTitle>
            <DialogDescription>
              This will permanently remove{" "}
              <span className="font-mono">{deleteTarget?.path}</span>
              {deleteTarget?.isDirectory
                ? " and all files inside it."
                : "."}{" "}
              This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteTarget(null)}
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={doDelete}
              disabled={deleting}
            >
              {deleting && <Loader2 className="size-4 animate-spin" />}
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* mkdir dialog */}
      <Dialog open={mkdirOpen} onOpenChange={setMkdirOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New Folder</DialogTitle>
            <DialogDescription>
              Creates a directory inside{" "}
              <span className="font-mono">
                {path || "/"}
              </span>
              .
            </DialogDescription>
          </DialogHeader>
          <div className="py-2">
            <Input
              placeholder="folder-name"
              value={mkdirName}
              onChange={(e) => setMkdirName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") void doMkdir();
              }}
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setMkdirOpen(false);
                setMkdirName("");
              }}
              disabled={mkdirBusy}
            >
              Cancel
            </Button>
            <Button onClick={doMkdir} disabled={mkdirBusy || !mkdirName.trim()}>
              {mkdirBusy && <Loader2 className="size-4 animate-spin" />}
              Create
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Editor sheet */}
      {editorTarget && (
        <FileEditor
          path={editorTarget.path}
          initialContent={editorTarget.content}
          readOnly={readOnly}
          onClose={() => setEditorTarget(null)}
          onSave={onSaveEdit}
        />
      )}

      {/* Image preview lightbox */}
      {previewPath && (
        <FileImagePreview
          scope={scope}
          path={previewPath}
          onClose={() => setPreviewPath(null)}
        />
      )}
    </div>
  );
}
