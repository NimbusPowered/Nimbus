"use client";

import { useState } from "react";
import { getApiUrl } from "@/lib/api";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { ChevronDown, Network, Save } from "@/lib/icons";
import { Field, FieldLabel, FieldDescription } from "@/components/ui/field";
import { cn } from "@/lib/utils";

interface BootstrapResponse {
  fingerprint: string;
  certPem: string;
  wsUrl: string;
  validUntil: string;
  sans: string[];
}

/**
 * Cluster-bootstrap helper. The /api/cluster/bootstrap endpoint is gated by
 * the **cluster token** (a separate shared secret distributed to agents),
 * not the dashboard's REST API token, so the operator must paste it here.
 *
 * Cert material is public-key — it is fine to display in the browser. The
 * cluster token itself is never echoed back; we only hold it in component
 * state to make the request.
 */
export function ClusterBootstrapCard() {
  const [token, setToken] = useState("");
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<BootstrapResponse | null>(null);
  const [pemOpen, setPemOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function fetchBootstrap() {
    if (!token.trim()) return;
    const apiUrl = getApiUrl();
    if (!apiUrl) {
      toast.error("Dashboard is not connected to a controller");
      return;
    }
    setLoading(true);
    setError(null);
    setData(null);
    try {
      const res = await fetch(`${apiUrl}/api/cluster/bootstrap`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token.trim()}` },
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        const msg =
          body.error ||
          body.message ||
          (res.status === 401 || res.status === 403
            ? "Invalid cluster token"
            : `Bootstrap failed (${res.status})`);
        setError(msg);
        return;
      }
      const json = (await res.json()) as BootstrapResponse;
      setData(json);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }

  async function copy(value: string, label: string) {
    try {
      await navigator.clipboard.writeText(value);
      toast.success(`${label} copied`);
    } catch {
      toast.error("Clipboard unavailable");
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center gap-2 space-y-0">
        <Network className="size-4 text-muted-foreground" />
        <CardTitle>Cluster bootstrap</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="rounded-md border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-700 dark:text-amber-400">
          <strong>Cluster token required.</strong> Bootstrap is gated on the
          shared agent secret, not your dashboard API token. The cert material
          shown is public-key — it&apos;s safe to copy and pin on agents.
        </div>

        <Field>
          <FieldLabel>Cluster token</FieldLabel>
          <div className="flex gap-2">
            <Input
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="config/nimbus.toml → [cluster] token = …"
              autoComplete="off"
            />
            <Button onClick={fetchBootstrap} disabled={loading || !token.trim()}>
              {loading ? "Fetching…" : "Fetch"}
            </Button>
          </div>
          <FieldDescription>
            Or run <code>cluster bootstrap-url</code> in the controller console.
          </FieldDescription>
        </Field>

        {error && (
          <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}

        {data && (
          <div className="space-y-3">
            <BootstrapField label="WebSocket URL" value={data.wsUrl} onCopy={copy} />
            <BootstrapField
              label="Cert fingerprint"
              value={data.fingerprint}
              onCopy={copy}
              mono
            />
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <div className="text-muted-foreground">Valid until</div>
                <div className="font-mono">{data.validUntil}</div>
              </div>
              <div>
                <div className="text-muted-foreground">SANs</div>
                <div className="font-mono break-all">
                  {data.sans.length > 0 ? data.sans.join(", ") : "—"}
                </div>
              </div>
            </div>

            <Collapsible open={pemOpen} onOpenChange={setPemOpen}>
              <CollapsibleTrigger
                render={
                  <button
                    type="button"
                    className="flex w-full items-center justify-between gap-2 border-b pb-1.5 text-left focus-visible:outline-none cursor-pointer"
                  />
                }
              >
                <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  Certificate (PEM)
                </span>
                <ChevronDown
                  className={cn(
                    "size-4 text-muted-foreground transition-transform",
                    pemOpen && "rotate-180"
                  )}
                />
              </CollapsibleTrigger>
              <CollapsibleContent className="pt-2 space-y-2">
                <pre className="max-h-56 overflow-auto rounded-md border bg-muted/30 p-2 text-[10px] font-mono whitespace-pre-wrap break-all">
                  {data.certPem}
                </pre>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => copy(data.certPem, "PEM")}
                >
                  <Save className="mr-1 size-4" /> Copy PEM
                </Button>
              </CollapsibleContent>
            </Collapsible>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function BootstrapField({
  label,
  value,
  onCopy,
  mono,
}: {
  label: string;
  value: string;
  onCopy: (v: string, label: string) => void;
  mono?: boolean;
}) {
  return (
    <div className="space-y-1">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="flex gap-2">
        <Input
          readOnly
          value={value}
          className={mono ? "font-mono text-xs" : "text-xs"}
        />
        <Button variant="outline" size="sm" onClick={() => onCopy(value, label)}>
          Copy
        </Button>
      </div>
    </div>
  );
}
