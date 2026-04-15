"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "@/lib/api";

/**
 * Standard polling cadences for the dashboard. Pick one of these instead of
 * inventing ad-hoc numbers so pages stay in sync on refresh behavior.
 */
export const POLL = {
  /** Live panels — stress, active console, live topology. */
  fast: 3_000,
  /** Default for status/list views. */
  normal: 5_000,
  /** Slow-moving data — audit log, history. */
  slow: 30_000,
  /** No polling — fetch once, refetch on demand. */
  off: 0,
} as const;

export type PollInterval = (typeof POLL)[keyof typeof POLL] | number;

export interface ApiResource<T> {
  /** Latest successful payload, or `null` until the first fetch resolves. */
  data: T | null;
  /** Latest error from `apiFetch`, or `null` while healthy. */
  error: Error | null;
  /** `true` until the first fetch resolves (successfully or otherwise). */
  loading: boolean;
  /** Imperatively refetch. Returns the new payload on success. */
  refetch: () => Promise<T | null>;
  /** Derived convenience flag: `!loading && !error && data == null`. */
  isEmpty: boolean;
}

export interface UseApiResourceOptions<T> {
  /**
   * Polling interval in milliseconds. Use the `POLL` constants. Polling is
   * automatically paused while `document.hidden === true` and resumed on
   * visibilitychange. Default: `POLL.off`.
   */
  poll?: PollInterval;
  /**
   * Disable the hook entirely (skip fetching). Useful when the path is not
   * yet known, e.g. waiting on a route param.
   */
  enabled?: boolean;
  /** Called once after every successful fetch. */
  onSuccess?: (data: T) => void;
  /** Called once after every failed fetch. */
  onError?: (error: Error) => void;
  /**
   * Pass `true` to skip the global error toast in `apiFetch`. Useful for
   * polling requests that you don't want to spam the user with on every tick.
   */
  silent?: boolean;
  /**
   * Treat an empty result as "not empty" by customizing the empty check.
   * Default considers `null`/`undefined`, empty arrays, and `{}` empty.
   */
  isEmpty?: (data: T) => boolean;
}

function defaultIsEmpty(value: unknown): boolean {
  if (value == null) return true;
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === "object") return Object.keys(value as object).length === 0;
  return false;
}

/**
 * Fetch a REST resource, optionally polled, with cancellation on unmount.
 *
 * Replaces the `useState` + `useEffect` boilerplate that every page reinvents
 * slightly differently. Pairs with <PageShell status={…} /> — pass the hook's
 * `loading` / `error` / `isEmpty` through and the shell renders the right
 * skeleton / error / empty UI automatically.
 *
 * @example
 * const { data, loading, error, refetch, isEmpty } =
 *   useApiResource<Service[]>("/api/services", { poll: POLL.normal });
 */
export function useApiResource<T>(
  path: string | null,
  options: UseApiResourceOptions<T> = {}
): ApiResource<T> {
  const {
    poll = POLL.off,
    enabled = true,
    onSuccess,
    onError,
    silent,
    isEmpty: isEmptyCheck,
  } = options;

  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState<boolean>(Boolean(path) && enabled);

  // Keep callbacks in refs so re-renders don't restart polling.
  const onSuccessRef = useRef(onSuccess);
  const onErrorRef = useRef(onError);
  onSuccessRef.current = onSuccess;
  onErrorRef.current = onError;

  const abortRef = useRef<AbortController | null>(null);

  const fetchOnce = useCallback(async (): Promise<T | null> => {
    if (!path || !enabled) return null;

    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;

    try {
      const result = await apiFetch<T>(path, {
        signal: ac.signal,
        silent,
      });
      if (ac.signal.aborted) return null;
      setData(result);
      setError(null);
      onSuccessRef.current?.(result);
      return result;
    } catch (e) {
      if (ac.signal.aborted) return null;
      const err = e instanceof Error ? e : new Error(String(e));
      // Fetch aborts surface as DOMException AbortError — don't treat as data error.
      if (err.name === "AbortError") return null;
      setError(err);
      onErrorRef.current?.(err);
      return null;
    } finally {
      if (!ac.signal.aborted) setLoading(false);
    }
  }, [path, enabled, silent]);

  // Initial + dependency-change fetch.
  useEffect(() => {
    if (!path || !enabled) {
      setLoading(false);
      return;
    }
    setLoading(true);
    void fetchOnce();
    return () => {
      abortRef.current?.abort();
    };
  }, [fetchOnce, path, enabled]);

  // Polling with visibility pause.
  useEffect(() => {
    if (!poll || !path || !enabled) return;

    let timer: ReturnType<typeof setInterval> | null = null;

    const start = () => {
      if (timer != null) return;
      timer = setInterval(() => {
        if (typeof document !== "undefined" && document.hidden) return;
        void fetchOnce();
      }, poll);
    };

    const stop = () => {
      if (timer != null) {
        clearInterval(timer);
        timer = null;
      }
    };

    const onVisibility = () => {
      if (document.hidden) {
        stop();
      } else {
        // Refetch immediately on wake, then resume polling.
        void fetchOnce();
        start();
      }
    };

    start();
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [poll, path, enabled, fetchOnce]);

  const isEmpty =
    !loading &&
    !error &&
    (data === null || (isEmptyCheck ? isEmptyCheck(data) : defaultIsEmpty(data)));

  return { data, error, loading, refetch: fetchOnce, isEmpty };
}
