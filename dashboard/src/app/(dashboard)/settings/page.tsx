"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { apiFetch } from "@/lib/api";
import { dotColors } from "@/lib/status";
import { toast } from "sonner";
import { Save, RefreshCw } from "@/lib/icons";
import { Field, FieldLabel, FieldDescription } from "@/components/ui/field";
import { PageShell } from "@/components/page-shell";
import { dashboardVersion, channel, channelLabel } from "@/lib/version";
import { cn } from "@/lib/utils";
import { MaintenanceWhitelistCard } from "@/components/maintenance-whitelist-card";

interface ConfigResponse {
  networkName: string;
  consoleColored: boolean;
  consoleLogEvents: boolean;
}

interface ModuleInfo {
  id: string;
  name: string;
  version: string;
  loaded: boolean;
}

interface ControllerInfo {
  version?: string;
  networkName?: string;
}

export default function SettingsPage() {
  const [, setConfig] = useState<ConfigResponse | null>(null);
  const [modules, setModules] = useState<ModuleInfo[]>([]);
  const [controllerInfo, setControllerInfo] = useState<ControllerInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [networkName, setNetworkName] = useState("");
  const [consoleColored, setConsoleColored] = useState(true);
  const [consoleLogEvents, setConsoleLogEvents] = useState(true);

  useEffect(() => {
    Promise.all([
      apiFetch<ConfigResponse>("/api/config").catch(() => null),
      apiFetch<{ modules: ModuleInfo[] }>("/api/modules")
        .then((d) => d.modules)
        .catch(() => []),
      apiFetch<ControllerInfo>("/api/controller/info").catch(() => null),
    ]).then(([c, m, info]) => {
      if (c) {
        setConfig(c);
        setNetworkName(c.networkName);
        setConsoleColored(c.consoleColored);
        setConsoleLogEvents(c.consoleLogEvents);
      }
      setModules(m);
      setControllerInfo(info);
      setLoading(false);
    });
  }, []);

  async function saveConfig() {
    setSaving(true);
    try {
      await apiFetch("/api/config", {
        method: "PATCH",
        body: JSON.stringify({
          networkName,
          consoleColored,
          consoleLogEvents,
        }),
      });
      toast.success("Config saved");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  }

  async function reloadGroups() {
    try {
      await apiFetch("/api/reload", { method: "POST" });
      toast.success("Groups reloaded");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Reload failed");
    }
  }

  return (
    <PageShell
      title="Settings"
      description="Network identity, console preferences and loaded modules."
      status={loading ? "loading" : "ready"}
      skeleton="form"
      actions={
        <>
          <Button variant="outline" onClick={reloadGroups}>
            <RefreshCw className="mr-1 size-4" /> Reload groups
          </Button>
          <Button onClick={saveConfig} disabled={saving || loading}>
            <Save className="mr-1 size-4" />
            {saving ? "Saving…" : "Save changes"}
          </Button>
        </>
      }
    >
      <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Network</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Field>
                <FieldLabel>Network name</FieldLabel>
                <Input
                  value={networkName}
                  onChange={(e) => setNetworkName(e.target.value)}
                />
                <FieldDescription>Shown in MOTD and dashboard</FieldDescription>
              </Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Console</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between gap-4">
                <FieldLabel>Colored output</FieldLabel>
                <Switch
                  checked={consoleColored}
                  onCheckedChange={setConsoleColored}
                />
              </div>
              <div className="flex items-center justify-between gap-4">
                <FieldLabel>Log events to console</FieldLabel>
                <Switch
                  checked={consoleLogEvents}
                  onCheckedChange={setConsoleLogEvents}
                />
              </div>
            </CardContent>
          </Card>

          <Card className="md:col-span-2">
            <CardHeader>
              <CardTitle>Version</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="flex items-center justify-between rounded-md border px-4 py-3">
                  <div>
                    <div className="text-xs text-muted-foreground">Dashboard</div>
                    <div className="font-mono text-sm">{dashboardVersion}</div>
                  </div>
                  {channelLabel && (
                    <span
                      className={cn(
                        "rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-wide",
                        channel === "beta" &&
                          "bg-sky-500/15 text-sky-600 dark:bg-sky-400/15 dark:text-sky-300",
                        channel === "alpha" &&
                          "bg-amber-500/15 text-amber-700 dark:bg-amber-400/15 dark:text-amber-300"
                      )}
                    >
                      {channelLabel}
                    </span>
                  )}
                </div>
                <div className="flex items-center justify-between rounded-md border px-4 py-3">
                  <div>
                    <div className="text-xs text-muted-foreground">Controller</div>
                    <div className="font-mono text-sm">
                      {controllerInfo?.version ?? "unknown"}
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <MaintenanceWhitelistCard />

          <Card className="md:col-span-2">
            <CardHeader>
              <CardTitle>Modules</CardTitle>
            </CardHeader>
            <CardContent>
              {modules.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No modules installed
                </p>
              ) : (
                <div className="grid gap-2 md:grid-cols-2">
                  {modules.map((m) => (
                    <div
                      key={m.id}
                      className="flex items-center justify-between rounded-md border px-4 py-3"
                    >
                      <div>
                        <div className="font-medium">{m.name}</div>
                        <div className="text-xs text-muted-foreground">
                          {m.id} v{m.version}
                        </div>
                      </div>
                      <div
                        className={`size-2 rounded-full ${
                          m.loaded ? dotColors.active : dotColors.inactive
                        }`}
                      />
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
        </Card>
      </div>
    </PageShell>
  );
}
