"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";
import {
  Field,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  setApiTokenCredentials,
  setUserSessionCredentials,
  type UserInfo,
} from "@/lib/api";
import { buildControllerUrl, controllerFetch } from "@/lib/controller-url";

type McMode = "code" | "magic";

type LoginStep =
  | { kind: "initial" }
  | { kind: "totp"; challengeId: string };

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

/**
 * Extract a friendly error message from a JSON error body returned by the
 * controller. Falls back to HTTP status text when the server returned no
 * message (e.g. a CORS preflight rejection).
 */
async function readError(res: Response, fallback: string): Promise<string> {
  const body: ApiErrorBody = await res.json().catch(() => ({}));
  return body.message || body.error || `${fallback} (${res.status})`;
}

/**
 * Renders a dashboard-hosted, controller-agnostic login flow.
 *
 * Two top-level tabs:
 *   - Minecraft Account — either an in-game `/dashboard login` code or a
 *     "magic link" delivered via in-game chat.
 *   - API Token — the legacy long-lived controller token (admin-only).
 *
 * When the user has enabled TOTP, consuming a challenge returns
 * `totpRequired: true` and the form swaps to a single-input TOTP step
 * bound to the returned `challengeId`.
 */
export function LoginForm({
  className,
  ...props
}: React.ComponentProps<"div">) {
  const router = useRouter();

  const [host, setHost] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState<LoginStep>({ kind: "initial" });

  // API-token tab state
  const [apiToken, setApiToken] = useState("");

  // Minecraft tab state
  const [mcMode, setMcMode] = useState<McMode>("code");
  const [mcCode, setMcCode] = useState("");
  const [mcName, setMcName] = useState("");
  const [linkSent, setLinkSent] = useState(false);
  const [linkTtl, setLinkTtl] = useState(0);
  const linkTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // TOTP step state
  const [totpCode, setTotpCode] = useState("");

  useEffect(() => {
    return () => {
      if (linkTimer.current) clearInterval(linkTimer.current);
    };
  }, []);

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

  function resetToInitial() {
    setStep({ kind: "initial" });
    setTotpCode("");
  }

  /**
   * Finalize a successful auth exchange: persist the session token and
   * navigate into the dashboard. The caller guarantees `token` is set.
   */
  function finalizeUserSession(url: string, token: string) {
    setUserSessionCredentials(url, token);
    router.push("/");
  }

  async function handleApiTokenSubmit(url: string) {
    const res = await controllerFetch(url, "/api/status", {
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
    setApiTokenCredentials(url, apiToken);
    router.push("/");
  }

  async function handleConsumeChallenge(url: string, challenge: string) {
    const res = await controllerFetch(url, "/api/auth/consume-challenge", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ challenge }),
    });

    if (!res.ok) {
      setError(await readError(res, "Login failed"));
      return;
    }

    const body: ConsumeChallengeResponse = await res.json();

    if (body.totpRequired && body.challengeId) {
      setStep({ kind: "totp", challengeId: body.challengeId });
      return;
    }

    if (body.token) {
      finalizeUserSession(url, body.token);
      return;
    }

    setError("Unexpected response from controller");
  }

  async function handleSendMagicLink(url: string) {
    const res = await controllerFetch(url, "/api/auth/deliver-magic-link", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: mcName.trim() }),
    });

    if (res.status === 404) {
      setError("That player isn't online right now.");
      return;
    }
    if (res.status === 403) {
      setError("Dashboard login is disabled on this controller.");
      return;
    }
    if (!res.ok) {
      setError(await readError(res, "Could not send magic link"));
      return;
    }

    const body = await res.json().catch(() => ({} as { ttlSeconds?: number }));
    const ttl = typeof body.ttlSeconds === "number" ? body.ttlSeconds : 60;
    setLinkSent(true);
    startLinkCountdown(ttl);
  }

  async function handleTotpSubmit(url: string, challengeId: string) {
    const res = await controllerFetch(url, "/api/auth/totp-verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ challengeId, code: totpCode.trim() }),
    });

    if (!res.ok) {
      setError(await readError(res, "Invalid TOTP code"));
      return;
    }

    const body: TotpVerifyResponse = await res.json();
    finalizeUserSession(url, body.token);
  }

  async function handleSubmit(e: React.FormEvent, activeTab: string) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const url = buildControllerUrl(host);

      if (step.kind === "totp") {
        await handleTotpSubmit(url, step.challengeId);
        return;
      }

      if (activeTab === "api-token") {
        await handleApiTokenSubmit(url);
        return;
      }

      if (mcMode === "code") {
        await handleConsumeChallenge(url, mcCode.trim());
      } else {
        if (linkSent) return; // countdown blocks resend
        await handleSendMagicLink(url);
      }
    } catch (err) {
      const isNetworkError =
        err instanceof TypeError &&
        (err.message === "Failed to fetch" ||
          err.message.includes("NetworkError"));
      if (isNetworkError) {
        setError(
          "Could not reach the Nimbus controller. Check that the address is correct, the controller is running, and the API port is open."
        );
      } else {
        setError("Could not connect to Nimbus controller");
      }
    } finally {
      setLoading(false);
    }
  }

  const [activeTab, setActiveTab] = useState<string>("minecraft");

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader className="text-center">
          <div className="flex justify-center mb-2">
            <Image
              src="/banner.svg"
              alt="Nimbus"
              width={320}
              height={88}
              priority
              className="h-auto w-[320px]"
            />
          </div>
          <CardDescription>
            {step.kind === "totp"
              ? "Two-factor authentication"
              : "Connect to your Nimbus controller"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={(e) => handleSubmit(e, activeTab)}>
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
                  disabled={step.kind === "totp"}
                />
                <p className="text-xs text-muted-foreground mt-1">
                  Port defaults to 8080 if not specified
                </p>
              </Field>

              {step.kind === "totp" ? (
                <>
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
                    <p className="text-xs text-muted-foreground mt-1">
                      Enter the code from your authenticator app, or a recovery
                      code if you&apos;ve lost access.
                    </p>
                  </Field>
                  {error && <p className="text-sm text-destructive">{error}</p>}
                  <Field>
                    <Button type="submit" className="w-full" disabled={loading}>
                      {loading ? "Verifying..." : "Verify"}
                    </Button>
                  </Field>
                  <Field>
                    <Button
                      type="button"
                      variant="ghost"
                      className="w-full"
                      onClick={resetToInitial}
                    >
                      Back
                    </Button>
                  </Field>
                </>
              ) : (
                <Tabs
                  value={activeTab}
                  onValueChange={(v) => {
                    setActiveTab(String(v));
                    setError("");
                  }}
                >
                  <TabsList className="w-full">
                    <TabsTrigger value="minecraft">
                      Minecraft Account
                    </TabsTrigger>
                    <TabsTrigger value="api-token">API Token</TabsTrigger>
                  </TabsList>

                  <TabsContent value="minecraft" className="mt-4">
                    <FieldGroup>
                      <Field>
                        <ToggleGroup
                          type="single"
                          value={mcMode}
                          onValueChange={(v) => {
                            if (v) setMcMode(v as McMode);
                          }}
                          className="w-full"
                        >
                          <ToggleGroupItem
                            value="code"
                            className="flex-1"
                            aria-label="Login code"
                          >
                            Login Code
                          </ToggleGroupItem>
                          <ToggleGroupItem
                            value="magic"
                            className="flex-1"
                            aria-label="Magic link"
                          >
                            Magic Link
                          </ToggleGroupItem>
                        </ToggleGroup>
                      </Field>

                      {mcMode === "code" ? (
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
                          />
                          <p className="text-xs text-muted-foreground mt-1">
                            Type <code>/dashboard login</code> on any Nimbus
                            server to get your code.
                          </p>
                        </Field>
                      ) : (
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
                            disabled={linkSent}
                          />
                          {linkSent && (
                            <p className="text-xs text-muted-foreground mt-1">
                              Check your in-game chat ✨ (expires in {linkTtl}s)
                            </p>
                          )}
                        </Field>
                      )}

                      {error && (
                        <p className="text-sm text-destructive">{error}</p>
                      )}

                      <Field>
                        <Button
                          type="submit"
                          className="w-full"
                          disabled={
                            loading || (mcMode === "magic" && linkSent)
                          }
                        >
                          {loading
                            ? "Working..."
                            : mcMode === "code"
                              ? "Sign in"
                              : linkSent
                                ? `Link sent (${linkTtl}s)`
                                : "Send link"}
                        </Button>
                      </Field>
                    </FieldGroup>
                  </TabsContent>

                  <TabsContent value="api-token" className="mt-4">
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
                        />
                      </Field>
                      {error && (
                        <p className="text-sm text-destructive">{error}</p>
                      )}
                      <Field>
                        <Button
                          type="submit"
                          className="w-full"
                          disabled={loading}
                        >
                          {loading ? "Connecting..." : "Connect"}
                        </Button>
                      </Field>
                    </FieldGroup>
                  </TabsContent>
                </Tabs>
              )}
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
