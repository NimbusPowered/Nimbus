"use client";

import * as React from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Loader2 } from "@/lib/icons";
import { getApiUrl, getToken } from "@/lib/api";
import { toast } from "sonner";

export interface FileImagePreviewProps {
  /** Scope: templates | services | groups */
  scope: string;
  /** Relative path within the scope, e.g. "global/banner.png" */
  path: string;
  onClose: () => void;
}

export function FileImagePreview({ scope, path, onClose }: FileImagePreviewProps) {
  const [objectUrl, setObjectUrl] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  const filename = path.includes("/")
    ? path.slice(path.lastIndexOf("/") + 1)
    : path;

  React.useEffect(() => {
    let revoked = false;
    let blobUrl: string | null = null;

    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const apiUrl = getApiUrl();
        const token = getToken();
        const res = await fetch(`${apiUrl}/api/files/${scope}/${path}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!res.ok) {
          throw new Error(`Failed to load image: ${res.status} ${res.statusText}`);
        }
        const blob = await res.blob();
        if (!revoked) {
          blobUrl = URL.createObjectURL(blob);
          setObjectUrl(blobUrl);
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        if (!revoked) {
          setError(msg);
          toast.error("Image load failed", { description: msg });
        }
      } finally {
        if (!revoked) setLoading(false);
      }
    };

    void load();

    return () => {
      revoked = true;
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [scope, path]);

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle className="font-mono text-sm truncate pr-8">
            {filename}
          </DialogTitle>
          <DialogDescription>{scope}/{path}</DialogDescription>
        </DialogHeader>

        <div className="flex items-center justify-center min-h-[240px] max-h-[70vh] overflow-hidden rounded-xl bg-muted/30">
          {loading && (
            <Loader2 className="size-8 animate-spin text-muted-foreground" />
          )}
          {!loading && error && (
            <p className="text-sm text-severity-err text-center px-4">{error}</p>
          )}
          {!loading && !error && objectUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={objectUrl}
              alt={filename}
              className="max-h-[70vh] max-w-full object-contain"
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
