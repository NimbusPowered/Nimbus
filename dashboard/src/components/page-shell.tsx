import * as React from "react";
import { cn } from "@/lib/utils";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Skeleton presets used by PageShell while a resource is loading. Pick the
 * one that most closely matches the page's ready-state layout so the loading
 * placeholder doesn't jump on settle.
 */
export type PageShellSkeleton = "grid" | "table" | "form" | "single";

export interface PageShellEmptyState {
  icon?: React.ComponentType<{ className?: string }>;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
}

export interface PageShellProps {
  /** Page title rendered in the <PageHeader />. */
  title: React.ReactNode;
  /** Optional sub-line underneath the title. */
  description?: React.ReactNode;
  /** Header action buttons (right-aligned). Always visible, even in loading. */
  actions?: React.ReactNode;
  /**
   * Current resource state — drives which body is rendered. Pass a hook's
   * `loading / error / data` through; PageShell picks the right UI.
   *
   *  - `loading` → skeleton of the shape given by `skeleton`
   *  - `error`   → <ErrorState /> with the message + retry action
   *  - `empty`   → <EmptyState /> from `emptyState` prop
   *  - `ready`   → `children`
   *
   * Defaults to `ready` so PageShell can be used as a thin wrapper.
   */
  status?: "loading" | "error" | "empty" | "ready";
  /** Shape of the loading skeleton. Default: `single`. */
  skeleton?: PageShellSkeleton;
  /** Error message displayed in the error state. */
  error?: Error | string | null;
  /** Optional callback wired to the retry button in the error state. */
  onRetry?: () => void;
  /** Content for the empty state when `status === "empty"`. */
  emptyState?: PageShellEmptyState;
  /** Ready-state body. */
  children?: React.ReactNode;
  /** Additional class on the outer wrapper. */
  className?: string;
}

function SkeletonFor({ shape }: { shape: PageShellSkeleton }) {
  switch (shape) {
    case "grid":
      return (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
      );
    case "table":
      return (
        <div className="space-y-2">
          <Skeleton className="h-10 rounded-md" />
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-12 rounded-md" />
          ))}
        </div>
      );
    case "form":
      return (
        <div className="max-w-xl space-y-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-14 rounded-md" />
          ))}
        </div>
      );
    case "single":
    default:
      return <Skeleton className="h-96 rounded-xl" />;
  }
}

/**
 * Standard page layout for every dashboard route.
 *
 * Wraps <PageHeader /> with a unified loading / error / empty / ready state
 * switch so every page handles those four cases identically. Use it in
 * conjunction with `useApiResource` for the minimum-boilerplate shape:
 *
 * @example
 * const { data, loading, error, isEmpty, refetch } =
 *   useApiResource<Service[]>("/api/services", { poll: POLL.normal });
 *
 * return (
 *   <PageShell
 *     title="Services"
 *     description="Running lobby & game server instances."
 *     status={loading ? "loading" : error ? "error" : isEmpty ? "empty" : "ready"}
 *     error={error}
 *     onRetry={refetch}
 *     skeleton="table"
 *     emptyState={{ icon: ServerIcon, title: "No services yet" }}
 *     actions={<Button>Refresh</Button>}
 *   >
 *     <ServiceTable rows={data!} />
 *   </PageShell>
 * );
 */
export function PageShell({
  title,
  description,
  actions,
  status = "ready",
  skeleton = "single",
  error,
  onRetry,
  emptyState,
  children,
  className,
}: PageShellProps) {
  const errorMessage =
    typeof error === "string" ? error : error?.message ?? undefined;

  return (
    <div className={cn("flex flex-col gap-6", className)}>
      <PageHeader title={title} description={description} actions={actions} />

      {status === "loading" && <SkeletonFor shape={skeleton} />}

      {status === "error" && (
        <ErrorState description={errorMessage} onRetry={onRetry} />
      )}

      {status === "empty" && emptyState && (
        <EmptyState
          icon={emptyState.icon}
          title={emptyState.title}
          description={emptyState.description}
          action={emptyState.action}
        />
      )}

      {status === "ready" && children}
    </div>
  );
}
