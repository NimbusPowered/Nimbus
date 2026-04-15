import * as React from "react";
import { cn } from "@/lib/utils";
import { CircleAlert } from "@/lib/icons";
import { Button } from "@/components/ui/button";

/**
 * Canonical error state. Used by PageShell when a resource fetch fails, but
 * can also be rendered on its own inside custom layouts. Mirrors the visual
 * language of <EmptyState /> but uses the `severity-err` semantic token so the
 * same component lands the same way across every page.
 */
export function ErrorState({
  title = "Failed to load",
  description,
  onRetry,
  retryLabel = "Retry",
  className,
}: {
  title?: React.ReactNode;
  description?: React.ReactNode;
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center rounded-lg border border-dashed border-severity-err/40 bg-severity-err/5 px-6 py-12 text-center",
        className
      )}
    >
      <div className="mb-3 flex size-11 items-center justify-center rounded-full bg-background ring-1 ring-severity-err/30">
        <CircleAlert className="size-5 text-severity-err" />
      </div>
      <p className="text-sm font-medium text-foreground">{title}</p>
      {description && (
        <p className="mt-1 max-w-sm text-xs text-muted-foreground">
          {description}
        </p>
      )}
      {onRetry && (
        <Button
          variant="outline"
          size="sm"
          onClick={onRetry}
          className="mt-4"
        >
          {retryLabel}
        </Button>
      )}
    </div>
  );
}
