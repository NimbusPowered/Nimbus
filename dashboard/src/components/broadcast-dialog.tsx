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
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Field, FieldLabel, FieldDescription } from "@/components/ui/field";
import { Radio, Loader2 } from "@/lib/icons";

interface BroadcastResponse {
  success: boolean;
  message: string;
  services: number;
}

interface GroupSummary {
  name: string;
  type: string;
}

interface GroupListResponse {
  groups: GroupSummary[];
}

/**
 * Network broadcast dialog. Lets the operator send a /say (or velocity broadcast
 * on proxies) to every ready service in the network or a single group.
 *
 * Backend BroadcastRequest only supports {message, group?} — there is no
 * per-service target on the controller, so the UI only exposes "all" + group.
 */
export function BroadcastDialog() {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [target, setTarget] = useState<string>("__all__");
  const [groups, setGroups] = useState<GroupSummary[]>([]);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!open) return;
    apiFetch<GroupListResponse>("/api/groups", { silent: true })
      .then((d) => setGroups(d.groups ?? []))
      .catch(() => setGroups([]));
  }, [open]);

  async function send() {
    if (!message.trim()) return;
    setSending(true);
    try {
      const body: { message: string; group?: string } = { message: message.trim() };
      if (target !== "__all__") body.group = target;
      const result = await apiFetch<BroadcastResponse>("/api/broadcast", {
        method: "POST",
        body: JSON.stringify(body),
      });
      toast.success(result.message);
      setMessage("");
      setOpen(false);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Broadcast failed");
    } finally {
      setSending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={
          <Button variant="outline" size="sm">
            <Radio className="mr-1 size-4" /> Broadcast
          </Button>
        }
      />
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Broadcast message</DialogTitle>
          <DialogDescription>
            Send a message to all online players, or scope it to a single group.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <Field>
            <FieldLabel>Message</FieldLabel>
            <Input
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="Server restarting in 5 minutes"
              onKeyDown={(e) => {
                if (e.key === "Enter" && message.trim() && !sending) send();
              }}
              autoFocus
            />
            <FieldDescription>
              Sent via <code>/say</code> on backends, <code>velocity broadcast</code> on proxies.
            </FieldDescription>
          </Field>
          <Field>
            <FieldLabel>Target</FieldLabel>
            <Select value={target} onValueChange={(v) => v && setTarget(v)}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value="__all__">Whole network</SelectItem>
                  {groups.map((g) => (
                    <SelectItem key={g.name} value={g.name}>
                      Group: {g.name}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={sending}
          >
            Cancel
          </Button>
          <Button onClick={send} disabled={sending || !message.trim()}>
            {sending ? <Loader2 className="mr-1 size-4 animate-spin" /> : null}
            Send
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
