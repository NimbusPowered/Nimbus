"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Loader2 } from "@/lib/icons";

interface GroupDeleteDialogProps {
  groupName: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onDeleted?: () => void;
}

/**
 * Destructive group-delete confirmation. Requires the user to type the
 * exact group name before the Delete button enables — same pattern GitHub
 * uses for repo deletion. The controller still rejects deletion if any
 * services are running, so the typed confirmation is purely a guard
 * against accidental clicks.
 */
export function GroupDeleteDialog({
  groupName,
  open,
  onOpenChange,
  onDeleted,
}: GroupDeleteDialogProps) {
  const [confirmText, setConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!open) setConfirmText("");
  }, [open]);

  async function confirm() {
    if (!groupName || confirmText !== groupName) return;
    setDeleting(true);
    try {
      await apiFetch(`/api/groups/${groupName}`, { method: "DELETE" });
      toast.success(`Group '${groupName}' deleted`);
      onOpenChange(false);
      onDeleted?.();
    } catch {
      // apiFetch already toasted
    } finally {
      setDeleting(false);
    }
  }

  const matches = !!groupName && confirmText === groupName;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete Group</DialogTitle>
          <DialogDescription>
            This permanently removes the group config from disk and unregisters
            it from the controller. All running services in this group must be
            stopped first. This action cannot be undone.
          </DialogDescription>
        </DialogHeader>

        <Field>
          <FieldLabel>
            Type <span className="font-mono font-medium">{groupName}</span> to
            confirm
          </FieldLabel>
          <Input
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder={groupName ?? ""}
            autoComplete="off"
            spellCheck={false}
          />
        </Field>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={confirm}
            disabled={!matches || deleting}
          >
            {deleting ? (
              <>
                <Loader2 className="size-4 animate-spin mr-1" /> Deleting…
              </>
            ) : (
              "Delete Group"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
