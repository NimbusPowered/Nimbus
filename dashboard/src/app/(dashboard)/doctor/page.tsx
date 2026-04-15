"use client";

import { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  CircleAlert,
  CircleCheck,
  CircleX,
  RefreshCw,
} from "@/lib/icons";
import { PageShell } from "@/components/page-shell";
import { useApiResource } from "@/hooks/use-api-resource";
import { cn } from "@/lib/utils";

type Level = "OK" | "WARN" | "FAIL";

interface Finding {
  level: Level;
  message: string;
  hint: string | null;
}

interface Section {
  name: string;
  findings: Finding[];
}

interface DoctorReport {
  sections: Section[];
  warnCount: number;
  failCount: number;
  status: "ok" | "warn" | "fail";
}

/**
 * Thin wrapper around the level → (icon, color, label) mapping so each
 * component doesn't reinvent it and the severity palette stays consistent.
 */
function levelMeta(level: Level) {
  switch (level) {
    case "OK":
      return {
        Icon: CircleCheck,
        color: "text-severity-ok",
        badgeClass:
          "border-severity-ok/30 bg-severity-ok/10 text-severity-ok",
      };
    case "WARN":
      return {
        Icon: CircleAlert,
        color: "text-severity-warn",
        badgeClass:
          "border-severity-warn/30 bg-severity-warn/10 text-severity-warn",
      };
    case "FAIL":
      return {
        Icon: CircleX,
        color: "text-severity-err",
        badgeClass:
          "border-severity-err/30 bg-severity-err/10 text-severity-err",
      };
  }
}

function statusSummary(report: DoctorReport) {
  if (report.failCount > 0) {
    return {
      tone: "fail" as const,
      label: `${report.failCount} failing, ${report.warnCount} warning${
        report.warnCount === 1 ? "" : "s"
      }`,
      helper: "Address the failures first — these block production readiness.",
    };
  }
  if (report.warnCount > 0) {
    return {
      tone: "warn" as const,
      label: `${report.warnCount} warning${report.warnCount === 1 ? "" : "s"}`,
      helper: "Deployment is functional, but worth reviewing.",
    };
  }
  return {
    tone: "ok" as const,
    label: "All checks passed",
    helper: "Every built-in and module check is happy.",
  };
}

export default function DoctorPage() {
  const [lastRun, setLastRun] = useState<Date | null>(null);

  const { data: report, loading, error, refetch } = useApiResource<DoctorReport>(
    "/api/doctor",
    {
      poll: 60_000,
      silent: true,
      onSuccess: () => setLastRun(new Date()),
    },
  );

  const summary = report ? statusSummary(report) : null;

  const actions = (
    <>
      {lastRun && (
        <span className="text-xs text-muted-foreground tabular-nums">
          Last run {lastRun.toLocaleTimeString()}
        </span>
      )}
      <Button
        size="sm"
        variant="outline"
        onClick={() => void refetch()}
        disabled={loading}
      >
        <RefreshCw className={cn("size-4", loading && "animate-spin")} />
        Run again
      </Button>
    </>
  );

  const status = loading && !report
    ? "loading"
    : error && !report
      ? "error"
      : "ready";

  return (
    <PageShell
      title="Doctor"
      description="Environment, configuration and runtime checks for this Nimbus deployment."
      actions={actions}
      status={status}
      skeleton="single"
      error={error}
      onRetry={refetch}
    >
      {report && summary && (
        <div className="space-y-4">
          <SummaryCard summary={summary} report={report} />
          {report.sections.map((section) => (
            <SectionCard key={section.name} section={section} />
          ))}
        </div>
      )}
    </PageShell>
  );
}

function SummaryCard({
  summary,
  report,
}: {
  summary: NonNullable<ReturnType<typeof statusSummary>>;
  report: DoctorReport;
}) {
  const totalFindings = report.sections.reduce(
    (acc, s) => acc + s.findings.length,
    0,
  );
  const okCount = totalFindings - report.warnCount - report.failCount;

  const toneClasses: Record<typeof summary.tone, string> = {
    ok: "border-severity-ok/30 bg-severity-ok/5",
    warn: "border-severity-warn/30 bg-severity-warn/5",
    fail: "border-severity-err/40 bg-severity-err/5",
  };

  const ToneIcon =
    summary.tone === "ok"
      ? CircleCheck
      : summary.tone === "warn"
        ? CircleAlert
        : CircleX;

  const iconColor =
    summary.tone === "ok"
      ? "text-severity-ok"
      : summary.tone === "warn"
        ? "text-severity-warn"
        : "text-severity-err";

  return (
    <Card className={cn("border", toneClasses[summary.tone])}>
      <CardContent className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          <ToneIcon className={cn("size-8 shrink-0", iconColor)} />
          <div className="min-w-0 space-y-0.5">
            <div className="text-lg font-semibold">{summary.label}</div>
            <div className="text-sm text-muted-foreground">
              {summary.helper}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="tabular-nums">
            <span className="font-medium text-severity-ok">{okCount}</span>{" "}
            ok
          </span>
          <span className="tabular-nums">
            <span className="font-medium text-severity-warn">
              {report.warnCount}
            </span>{" "}
            warn
          </span>
          <span className="tabular-nums">
            <span className="font-medium text-severity-err">
              {report.failCount}
            </span>{" "}
            fail
          </span>
          <span>·</span>
          <span>{report.sections.length} sections</span>
        </div>
      </CardContent>
    </Card>
  );
}

function SectionCard({ section }: { section: Section }) {
  const fail = section.findings.filter((f) => f.level === "FAIL").length;
  const warn = section.findings.filter((f) => f.level === "WARN").length;
  const headerBadge =
    fail > 0
      ? { tone: "FAIL" as Level, label: `${fail} failing` }
      : warn > 0
        ? { tone: "WARN" as Level, label: `${warn} warning${warn === 1 ? "" : "s"}` }
        : { tone: "OK" as Level, label: "all good" };

  const badgeMeta = levelMeta(headerBadge.tone);

  return (
    <Card>
      <CardContent className="p-0">
        <div className="flex items-center justify-between border-b px-6 py-3">
          <h2 className="font-heading text-sm font-semibold tracking-tight">
            {section.name}
          </h2>
          <Badge
            variant="outline"
            className={cn("text-[10px] uppercase tracking-wide", badgeMeta.badgeClass)}
          >
            {headerBadge.label}
          </Badge>
        </div>
        <ul className="divide-y">
          {section.findings.map((finding, i) => (
            <FindingRow key={i} finding={finding} />
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function FindingRow({ finding }: { finding: Finding }) {
  const { Icon, color } = levelMeta(finding.level);
  return (
    <li className="flex gap-3 px-6 py-3">
      <Icon className={cn("mt-0.5 size-4 shrink-0", color)} />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="text-sm text-foreground">{finding.message}</div>
        {finding.hint && finding.level !== "OK" && (
          <div className="text-xs text-muted-foreground">
            <span className="mr-1 text-muted-foreground/60">→</span>
            {finding.hint}
          </div>
        )}
      </div>
    </li>
  );
}
