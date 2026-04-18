"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  setApiTokenCredentials,
  setUserSessionCredentials,
  type UserInfo,
} from "@/lib/api";
import { buildControllerUrl, controllerFetch } from "@/lib/controller-url";
import { ArrowLeft } from "@/lib/icons";

type Screen =
  | "connect"
  | "method"
  | "mc-method"
  | "code"
  | "magic-link"
  | "api-token"
  | "totp";

type McMethod = "code" | "magic-link";

interface ConsumeChallengeResponse {
  token?: string;
  expiresAt?: number;
  user?: UserInfo;
  totpRequired?: boolean;
  challengeId?: string;
}

interface TotpVerifyResponse {
  token: string;
  expiresAt: number;
  user: UserInfo;
}

interface ApiErrorBody {
  success?: boolean;
  message?: string;
  error?: string;
}

async function readError(res: Response, fallback: string): Promise<string> {
  const body: ApiErrorBody = await res.json().catch(() => ({}));
  return body.message || body.error || `${fallback} (${res.status})`;
}

function friendlyNetworkError(err: unknown): string {
  const isNetworkError =
    err instanceof TypeError &&
    (err.message === "Failed to fetch" ||
      err.message.includes("NetworkError"));
  return isNetworkError
    ? "Could not reach the Nimbus controller. Check that the address is correct, the controller is running, and the API port is open."
    : "Could not connect to Nimbus controller";
}

/**
 * Step-based login flow. Only one screen is rendered at a time; the screen
 * state machine acts as implicit routing without touching Next's router.
 *
 * Flow:
 *   connect → method → {mc-method → {code|magic-link} | api-token}
 *   code|magic-link → (optional) totp
 */
export function LoginForm({
  className,
  ...props
}: React.ComponentProps<"div">) {
  const router = useRouter();

  const [screen, setScreen] = useState<Screen>("connect");
  const [host, setHost] = useState("");
  const [resolvedUrl, setResolvedUrl] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // API-token screen state
  const [apiToken, setApiToken] = useState("");

  // Code screen state
  const [mcCode, setMcCode] = useState("");

  // Magic-link screen state
  const [mcName, setMcName] = useState("");
  const [linkSent, setLinkSent] = useState(false);
  const [linkTtl, setLinkTtl] = useState(0);
  const linkTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // TOTP state
  const [totpCode, setTotpCode] = useState("");
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [lastMcMethod, setLastMcMethod] = useState<McMethod>("code");

  useEffect(() => {
    return () => {
      if (linkTimer.current) clearInterval(linkTimer.current);
    };
  }, []);

  function go(next: Screen) {
    setError("");
    setScreen(next);
  }

  function back() {
    setError("");
    switch (screen) {
      case "method":
        setScreen("connect");
        break;
      case "mc-method":
        setScreen("method");
        break;
      case "code":
        setScreen("mc-method");
        break;
      case "magic-link":
        setScreen("mc-method");
        break;
      case "api-token":
        setScreen("method");
        break;
      case "totp":
        setScreen(lastMcMethod === "magic-link" ? "magic-link" : "code");
        break;
      default:
        break;
    }
  }

  function startLinkCountdown(ttlSeconds: number) {
    setLinkTtl(ttlSeconds);
    if (linkTimer.current) clearInterval(linkTimer.current);
    linkTimer.current = setInterval(() => {
      setLinkTtl((t) => {
        if (t <= 1) {
          if (linkTimer.current) clearInterval(linkTimer.current);
          setLinkSent(false);
          return 0;
        }
        return t - 1;
      });
    }, 1000);
  }

  // ---- actions ---------------------------------------------------------

  async function handleConnect(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const url = buildControllerUrl(host);
      const res = await controllerFetch(url, "/api/status");
      // /api/status requires auth — any response (incl. 401) proves the
      // controller is reachable. Treat a non-5xx/non-network result as OK.
      if (res.status >= 500) {
        setError(`Controller error (${res.status}). Try again.`);
        return;
      }
      setResolvedUrl(url);
      go("method");
    } catch (err) {
      setError(friendlyNetworkError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleCodeSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await controllerFetch(
        resolvedUrl,
        "/api/auth/consume-challenge",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ challenge: mcCode.trim() }),
        }
      );
      if (!res.ok) {
        setError(await readError(res, "Login failed"));
        return;
      }
      const body: ConsumeChallengeResponse = await res.json();
      if (body.totpRequired && body.challengeId) {
        setChallengeId(body.challengeId);
        setLastMcMethod("code");
        setTotpCode("");
        go("totp");
        return;
      }
      if (body.token) {
        setUserSessionCredentials(resolvedUrl, body.token);
        router.push("/");
        return;
      }
      setError("Unexpected response from controller");
    } catch (err) {
      setError(friendlyNetworkError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleSendMagicLink(e: React.FormEvent) {
    e.preventDefault();
    if (linkSent) return;
    setError("");
    setLoading(true);
    try {
      const res = await controllerFetch(
        resolvedUrl,
        "/api/auth/deliver-magic-link",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: mcName.trim() }),
        }
      );
      if (res.status === 404) {
        setError("You need to be online on a Nimbus server first.");
        return;
      }
      if (res.status === 403) {
        setError("Magic link login is disabled on this network.");
        return;
      }
      if (!res.ok) {
        setError(await readError(res, "Could not send magic link"));
        return;
      }
      const body = await res.json().catch(() => ({} as { ttlSeconds?: number }));
      const ttl = typeof body.ttlSeconds === "number" ? body.ttlSeconds : 60;
      setLastMcMethod("magic-link");
      setLinkSent(true);
      startLinkCountdown(ttl);
    } catch (err) {
      setError(friendlyNetworkError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleApiTokenSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await controllerFetch(resolvedUrl, "/api/status", {
        headers: { Authorization: `Bearer ${apiToken}` },
      });
      if (res.status === 401) {
        setError("Invalid API token");
        return;
      }
      if (!res.ok) {
        setError(`Connection failed (${res.status})`);
        return;
      }
      setApiTokenCredentials(resolvedUrl, apiToken);
      router.push("/");
    } catch (err) {
      setError(friendlyNetworkError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleTotpSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!challengeId) return;
    setError("");
    setLoading(true);
    try {
      const res = await controllerFetch(
        resolvedUrl,
        "/api/auth/totp-verify",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ challengeId, code: totpCode.trim() }),
        }
      );
      if (!res.ok) {
        setError(await readError(res, "Invalid TOTP code"));
        return;
      }
      const body: TotpVerifyResponse = await res.json();
      setUserSessionCredentials(resolvedUrl, body.token);
      router.push("/");
    } catch (err) {
      setError(friendlyNetworkError(err));
    } finally {
      setLoading(false);
    }
  }

  // ---- rendering -------------------------------------------------------

  const animated =
    "animate-in fade-in-0 duration-200";

  const showBack = screen !== "connect";

  const description =
    screen === "connect"
      ? "Connect to your Nimbus controller"
      : screen === "totp"
        ? "Two-factor authentication"
        : undefined;

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card className="relative">
        {showBack && (
          <button
            type="button"
            onClick={back}
            aria-label="Back"
            className="absolute left-3 top-3 inline-flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <ArrowLeft className="size-4" />
          </button>
        )}

        <CardHeader className="text-center">
          <div className="mb-2 flex justify-center">
            <NimbusLogo className="h-16 w-16" />
          </div>
          <span className="sr-only">Nimbus</span>
          {description && <CardDescription>{description}</CardDescription>}
          {screen !== "connect" && resolvedUrl && (
            <p className="mt-1 truncate text-xs text-muted-foreground">
              Connected to{" "}
              <span className="font-mono">{resolvedUrl}</span>
            </p>
          )}
        </CardHeader>

        <CardContent>
          {screen === "connect" && (
            <form onSubmit={handleConnect} className={animated}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="host">Controller Address</FieldLabel>
                  <Input
                    id="host"
                    type="text"
                    placeholder="IP or hostname (e.g. 192.168.1.100)"
                    value={host}
                    onChange={(e) => setHost(e.target.value)}
                    required
                    autoFocus
                  />
                  <p className="mt-1 text-xs text-muted-foreground">
                    Port defaults to 8080 if not specified
                  </p>
                </Field>
                {error && (
                  <p
                    role="alert"
                    className="text-sm text-[color:var(--severity-err)]"
                  >
                    {error}
                  </p>
                )}
                <Field>
                  <Button type="submit" className="w-full" disabled={loading}>
                    {loading ? "Connecting…" : "Continue"}
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          )}

          {screen === "method" && (
            <div className={cn("flex flex-col gap-3", animated)}>
              <MethodCard
                title="Minecraft Account"
                description="Sign in with an in-game code or a magic link."
                primary
                icon={<SkinHeadIcon />}
                onClick={() => go("mc-method")}
              />
              <MethodCard
                title="API Token"
                description="Use a long-lived controller token (admin-only)."
                icon={<CoinIcon />}
                onClick={() => go("api-token")}
              />
            </div>
          )}

          {screen === "mc-method" && (
            <div className={cn("flex flex-col gap-3", animated)}>
              <MethodCard
                title="Login code"
                description="Type /nimbus dashboard login on any Nimbus server."
                primary
                onClick={() => go("code")}
              />
              <MethodCard
                title="Magic link ✨"
                description="Get a clickable sign-in link in your in-game chat."
                onClick={() => go("magic-link")}
              />
            </div>
          )}

          {screen === "code" && (
            <form onSubmit={handleCodeSubmit} className={animated}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="mc-code">Login code</FieldLabel>
                  <Input
                    id="mc-code"
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="123456"
                    value={mcCode}
                    onChange={(e) => setMcCode(e.target.value)}
                    required
                    autoComplete="one-time-code"
                    autoFocus
                  />
                  <p className="mt-1 text-xs text-muted-foreground">
                    Code not working? Make sure you typed{" "}
                    <code>/nimbus dashboard login</code> in-game.
                  </p>
                </Field>
                {error && (
                  <p
                    role="alert"
                    className="text-sm text-[color:var(--severity-err)]"
                  >
                    {error}
                  </p>
                )}
                <Field>
                  <Button type="submit" className="w-full" disabled={loading}>
                    {loading ? "Signing in…" : "Sign in"}
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          )}

          {screen === "magic-link" && (
            <form onSubmit={handleSendMagicLink} className={animated}>
              <FieldGroup>
                {!linkSent ? (
                  <Field>
                    <FieldLabel htmlFor="mc-name">
                      Minecraft username
                    </FieldLabel>
                    <Input
                      id="mc-name"
                      type="text"
                      placeholder="Notch"
                      value={mcName}
                      onChange={(e) => setMcName(e.target.value)}
                      required
                      autoFocus
                    />
                  </Field>
                ) : (
                  <div className="flex flex-col items-center gap-2 py-4 text-center">
                    <p className="text-sm">Check your in-game chat ✨</p>
                    <p className="text-xs text-muted-foreground">
                      Link expires in {linkTtl}s
                    </p>
                  </div>
                )}
                {error && (
                  <div className="flex flex-col gap-2">
                    <p
                      role="alert"
                      className="text-sm text-[color:var(--severity-err)]"
                    >
                      {error}
                    </p>
                    {error.includes("disabled") && (
                      <button
                        type="button"
                        onClick={() => go("code")}
                        className="text-left text-xs text-muted-foreground underline underline-offset-4 hover:text-foreground"
                      >
                        Use code instead
                      </button>
                    )}
                  </div>
                )}
                <Field>
                  {linkSent && linkTtl === 0 ? (
                    <Button
                      type="button"
                      className="w-full"
                      onClick={() => {
                        setLinkSent(false);
                        setError("");
                      }}
                    >
                      Send another link
                    </Button>
                  ) : (
                    <Button
                      type="submit"
                      className="w-full"
                      disabled={loading || linkSent}
                    >
                      {loading
                        ? "Sending…"
                        : linkSent
                          ? `Link sent (${linkTtl}s)`
                          : "Send link"}
                    </Button>
                  )}
                </Field>
              </FieldGroup>
            </form>
          )}

          {screen === "api-token" && (
            <form onSubmit={handleApiTokenSubmit} className={animated}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="token">API Token</FieldLabel>
                  <Input
                    id="token"
                    type="password"
                    placeholder="Enter your API token"
                    value={apiToken}
                    onChange={(e) => setApiToken(e.target.value)}
                    required
                    autoFocus
                  />
                </Field>
                {error && (
                  <p
                    role="alert"
                    className="text-sm text-[color:var(--severity-err)]"
                  >
                    {error}
                  </p>
                )}
                <Field>
                  <Button type="submit" className="w-full" disabled={loading}>
                    {loading ? "Connecting…" : "Sign in"}
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          )}

          {screen === "totp" && (
            <form onSubmit={handleTotpSubmit} className={animated}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="totp">Authenticator code</FieldLabel>
                  <Input
                    id="totp"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    placeholder="6-digit code or recovery code"
                    value={totpCode}
                    onChange={(e) => setTotpCode(e.target.value)}
                    required
                    autoFocus
                  />
                  <p className="mt-1 text-xs text-muted-foreground">
                    Lost your device? Use a recovery code.
                  </p>
                </Field>
                {error && (
                  <p
                    role="alert"
                    className="text-sm text-[color:var(--severity-err)]"
                  >
                    {error}
                  </p>
                )}
                <Field>
                  <Button type="submit" className="w-full" disabled={loading}>
                    {loading ? "Verifying…" : "Verify"}
                  </Button>
                </Field>
              </FieldGroup>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function MethodCard({
  title,
  description,
  primary,
  onClick,
  icon,
}: {
  title: string;
  description: string;
  primary?: boolean;
  onClick: () => void;
  icon?: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "group flex w-full items-center gap-3 rounded-lg border p-4 text-left transition-colors",
        primary
          ? "border-primary/40 bg-primary/5 hover:border-primary hover:bg-primary/10"
          : "border-border hover:bg-muted"
      )}
    >
      {icon && <div className="shrink-0">{icon}</div>}
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="font-medium">{title}</span>
        <span className="text-xs text-muted-foreground">{description}</span>
      </div>
    </button>
  );
}

/**
 * Vector Nimbus mark — stylised cloud with a monospace "N" carved out.
 * Pure SVG so it stays crisp at any size; uses the banner palette
 * (#7aa2f7 → #7dcfff) so it visually matches the ASCII banner used
 * elsewhere in the app. Safe fallback until we ship a hi-res logo asset.
 */
function NimbusLogo({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 64 64"
      aria-hidden="true"
      className={className}
    >
      <defs>
        <linearGradient id="nimbus-logo-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#7aa2f7" />
          <stop offset="55%" stopColor="#89b4fa" />
          <stop offset="100%" stopColor="#7dcfff" />
        </linearGradient>
      </defs>
      {/* Cloud silhouette — three overlapping circles + a base */}
      <g fill="url(#nimbus-logo-grad)">
        <circle cx="22" cy="30" r="12" />
        <circle cx="40" cy="26" r="13" />
        <circle cx="48" cy="34" r="10" />
        <rect x="16" y="32" width="36" height="14" rx="7" />
      </g>
      {/* Carved "N" — uses the card's background colour so it reads as a cutout */}
      <g fill="var(--card, #ffffff)" fontFamily="'JetBrains Mono', ui-monospace, monospace" fontWeight="700">
        <text
          x="32"
          y="42"
          textAnchor="middle"
          fontSize="22"
          letterSpacing="-1"
        >
          N
        </text>
      </g>
    </svg>
  );
}

/**
 * Classic 8×8 Steve face — pixel-art Minecraft head. shape-rendering keeps
 * the rects crisp at any zoom so it stays pixel-perfect on HiDPI screens.
 */
function SkinHeadIcon({ className }: { className?: string }) {
  // Palette — canonical Steve colours, toned down a touch for light/dark themes.
  const H = "#2C1A0A"; // hair
  const S = "#C08D6A"; // skin
  const SS = "#9B6B4D"; // skin shadow (cheek edge)
  const W = "#F5F5F5"; // eye white
  const I = "#3B6CD1"; // iris
  const M = "#6B3F2F"; // mouth
  // 8 rows × 8 cols. '.' = skin fill default.
  const rows: string[] = [
    "HHHHHHHH",
    "HHHHHHHH",
    "HHHHHHHH",
    "HSSSSSSH",
    "SWISSWIS",
    "SSSSSSSS",
    "sMMMMMMs", // mouth row with shadow edges
    "sSSSSSSs",
  ];
  const color: Record<string, string> = {
    H, S, s: SS, W, I, M,
  };
  return (
    <svg
      viewBox="0 0 8 8"
      width={40}
      height={40}
      shapeRendering="crispEdges"
      aria-hidden="true"
      className={className}
    >
      {rows.flatMap((row, y) =>
        row.split("").map((ch, x) => (
          <rect
            key={`${x}-${y}`}
            x={x}
            y={y}
            width={1}
            height={1}
            fill={color[ch] ?? S}
          />
        ))
      )}
    </svg>
  );
}

/**
 * Pixel-art gold coin with a small "N" for Nimbus. 10×10 grid, same crisp
 * edges as the skin head so the two visually rhyme.
 */
function CoinIcon({ className }: { className?: string }) {
  const O = "#7A5A10"; // outline
  const G = "#F0B820"; // gold fill
  const D = "#B4860F"; // gold shadow
  const L = "#FFF1A6"; // highlight
  // 10 rows × 10 cols. '.' = transparent.
  const rows: string[] = [
    "...OOOO...",
    "..OGGGGO..",
    ".OGLGGDGO.", // top highlight + right-side shadow start
    ".OGGGDDGO.",
    "OGGOGGODGO",
    "OGGGOGGODO",
    ".OGGOGGGO.",
    ".OGGGGDGO.",
    "..OGGDGO..",
    "...OOOO...",
  ];
  const color: Record<string, string> = { O, G, D, L };
  return (
    <svg
      viewBox="0 0 10 10"
      width={40}
      height={40}
      shapeRendering="crispEdges"
      aria-hidden="true"
      className={className}
    >
      {rows.flatMap((row, y) =>
        row.split("").map((ch, x) =>
          ch === "." ? null : (
            <rect
              key={`${x}-${y}`}
              x={x}
              y={y}
              width={1}
              height={1}
              fill={color[ch] ?? G}
            />
          )
        )
      )}
    </svg>
  );
}
