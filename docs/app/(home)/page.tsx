import Link from 'next/link';
import Image from 'next/image';
import {
  ArrowRight,
  CloudIcon,
  GamepadIcon,
  GlobeIcon,
  HeartIcon,
  LayersIcon,
  MonitorIcon,
  PackageIcon,
  ShieldIcon,
  TerminalIcon,
  ZapIcon,
  CpuIcon,
  GaugeIcon,
  NetworkIcon,
} from 'lucide-react';
import { InstallTabs } from './install-tabs';

const basePath = '';

type Feature = {
  icon: React.ReactNode;
  title: string;
  desc: string;
  span?: string;
  accent?: React.ReactNode;
};

const features: Feature[] = [
  {
    icon: <ZapIcon className="size-5" />,
    title: 'Smart Auto-Scaling',
    desc: 'Instances scale based on player count — configurable thresholds, idle timeouts, time-of-day schedules, and predictive warm-up.',
    span: 'sm:col-span-2 lg:col-span-2 lg:row-span-2',
    accent: (
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 opacity-60">
        <div className="absolute inset-x-6 bottom-6 flex items-end gap-1.5">
          {[28, 44, 36, 58, 72, 64, 88, 76, 92, 68, 80, 100].map((h, i) => (
            <div
              key={i}
              className="flex-1 rounded-sm bg-gradient-to-t from-fd-primary/60 to-fd-primary/10"
              style={{ height: `${h}%` }}
            />
          ))}
        </div>
      </div>
    ),
  },
  {
    icon: <LayersIcon className="size-5" />,
    title: 'Multi-Node Cluster',
    desc: 'Distribute services across machines with automatic placement, failover, and mTLS cluster transport.',
    span: 'sm:col-span-2 lg:col-span-2',
  },
  {
    icon: <GlobeIcon className="size-5" />,
    title: 'Zero-Config Proxy',
    desc: 'Velocity auto-managed — forwarding, server list, MOTD, tab list and chat sync.',
  },
  {
    icon: <PackageIcon className="size-5" />,
    title: 'Auto-Download',
    desc: 'Paper, Purpur, Leaf, Velocity, Forge, Fabric, NeoForge — fetched on demand.',
  },
  {
    icon: <ShieldIcon className="size-5" />,
    title: 'Built-in Permissions',
    desc: 'Groups, inheritance, tracks, audit log — no external plugin required.',
  },
  {
    icon: <TerminalIcon className="size-5" />,
    title: 'REST API + WebSocket',
    desc: 'Live events, bidirectional console, full programmatic control.',
  },
  {
    icon: <GamepadIcon className="size-5" />,
    title: 'Bedrock Support',
    desc: 'Geyser + Floodgate auto-configured — one network, all platforms.',
  },
  {
    icon: <MonitorIcon className="size-5" />,
    title: 'Interactive Console',
    desc: '30+ commands with tab-completion, live logs and setup wizard.',
  },
];

const stats = [
  { value: '1', label: 'Fat JAR', sub: 'no external services' },
  { value: '9+', label: 'Server platforms', sub: 'Paper, Velocity, Fabric…' },
  { value: '30+', label: 'Console commands', sub: 'with tab-completion' },
  { value: '0', label: 'External deps', sub: 'just Java 21' },
];

const flow = [
  {
    icon: <TerminalIcon className="size-4" />,
    title: 'Install',
    desc: 'One curl command installs the controller. Java 21 is auto-provisioned.',
  },
  {
    icon: <NetworkIcon className="size-4" />,
    title: 'Configure',
    desc: 'Declare groups and dedicated services in TOML. Templates stack and compose.',
  },
  {
    icon: <GaugeIcon className="size-4" />,
    title: 'Scale',
    desc: 'Nimbus boots your proxy, spawns lobbies, and scales on player count live.',
  },
];

const platforms = [
  'Paper', 'Purpur', 'Pufferfish', 'Leaf', 'Folia', 'Velocity',
  'Forge', 'Fabric', 'NeoForge', 'Vanilla',
];

const serverSoftware = [
  {
    name: 'Paper',
    tagline: 'The industry standard',
    ratings: { performance: 4, stability: 5, plugins: 5, customization: 3 },
    minVersion: '1.8.8+',
    bestFor: 'Safe default for any server — largest community, fastest patches',
    logo: 'https://raw.githubusercontent.com/PaperMC/website/main/src/assets/brand/paper.svg',
  },
  {
    name: 'Purpur',
    tagline: 'Your Minecraft, your way',
    ratings: { performance: 4, stability: 4, plugins: 5, customization: 5 },
    minVersion: '1.14+',
    bestFor: 'Hundreds of extra config options for gameplay tweaks',
    logo: 'https://raw.githubusercontent.com/PurpurMC/PurpurWebsite/master/public/images/purpur.svg',
  },
  {
    name: 'Pufferfish',
    tagline: 'Built for large servers',
    ratings: { performance: 5, stability: 4, plugins: 5, customization: 3 },
    minVersion: '1.17.1+',
    bestFor: 'Entity-heavy servers — DAB + SIMD optimizations for 100+ players',
    logo: 'https://avatars.githubusercontent.com/u/87461856?v=4',
  },
  {
    name: 'Leaf',
    tagline: 'Experimental, bleeding-edge',
    ratings: { performance: 4, stability: 3, plugins: 5, customization: 3 },
    minVersion: '1.19.3+',
    bestFor: 'Aggregates patches from multiple forks — for adventurous admins',
    logo: 'https://raw.githubusercontent.com/Winds-Studio/Leaf/ver/1.21.11/public/image/leaf_logo.png',
  },
] as const;

const ratingLabels: Record<string, string> = {
  performance: 'Performance',
  stability: 'Stability',
  plugins: 'Plugin Compatibility',
  customization: 'Customization',
};

const footerDocs = [
  { label: 'Quick Start', href: '/docs/guide/quickstart' },
  { label: 'Installation', href: '/docs/guide/installation' },
  { label: 'Configuration', href: '/docs/config/nimbus-toml' },
  { label: 'Commands', href: '/docs/guide/commands' },
];

const footerDev = [
  { label: 'REST API', href: '/docs/reference/api' },
  { label: 'WebSocket', href: '/docs/reference/websocket' },
  { label: 'Architecture', href: '/docs/developer/architecture' },
  { label: 'Modules', href: '/docs/developer/modules' },
];

const footerCommunity = [
  { label: 'GitHub', href: 'https://github.com/NimbusPowered/Nimbus' },
  { label: 'Issues', href: 'https://github.com/NimbusPowered/Nimbus/issues' },
  { label: 'Releases', href: 'https://github.com/NimbusPowered/Nimbus/releases' },
];

type ServiceKind = 'proxy' | 'lobby' | 'game';
type ServiceState = 'ready' | 'starting' | 'prepared';

type AgentNode = {
  id: string;
  label: string;
  capacity: { used: number; max: number };
  services: { name: string; kind: ServiceKind; state: ServiceState; players?: number }[];
};

const controllerModules = ['api', 'scaling', 'events', 'perms'] as const;

const topologyAgentsRaw: AgentNode[] = [
  {
    id: 'w1',
    label: 'worker-1',
    capacity: { used: 2, max: 8 },
    services: [
      { name: 'Proxy-1', kind: 'proxy', state: 'ready' },
      { name: 'Lobby-1', kind: 'lobby', state: 'ready', players: 18 },
    ],
  },
  {
    id: 'w2',
    label: 'worker-2',
    capacity: { used: 3, max: 8 },
    services: [
      { name: 'BedWars-1', kind: 'game',  state: 'ready', players: 14 },
      { name: 'BedWars-2', kind: 'game',  state: 'starting' },
      { name: 'Lobby-2',   kind: 'lobby', state: 'prepared' },
    ],
  },
];

// Proxy's player count = sum of all backend players, since every player
// connects through the proxy. Derive it once so the numbers stay consistent.
const backendPlayers = topologyAgentsRaw.reduce(
  (a, n) => a + n.services.reduce((b, s) => b + (s.kind !== 'proxy' ? s.players ?? 0 : 0), 0),
  0,
);

const topologyAgents: AgentNode[] = topologyAgentsRaw.map((n) => ({
  ...n,
  services: n.services.map((s) =>
    s.kind === 'proxy' && s.state === 'ready'
      ? { ...s, players: backendPlayers }
      : s,
  ),
}));

const serviceKindStyle: Record<ServiceKind, string> = {
  proxy: 'border-fd-primary/30 bg-fd-primary/10 text-fd-primary',
  lobby: 'border-sky-500/30 bg-sky-500/10 text-sky-500 dark:text-sky-400',
  game:  'border-violet-500/30 bg-violet-500/10 text-violet-500 dark:text-violet-400',
};

const serviceStateMeta: Record<ServiceState, { dot: string; label: string }> = {
  ready:    { dot: 'bg-emerald-500',                label: 'READY' },
  starting: { dot: 'bg-amber-500',                  label: 'STARTING' },
  prepared: { dot: 'bg-fd-muted-foreground/40',     label: 'PREPARED' },
};

function TopologyPreview() {
  // Layout: 300x300 viewBox — controller top-center, 2 agents bottom.
  // Lines carry a flowing dash highlight + a data packet traveling along
  // the offset-path. Agent x-positions (75, 225) correspond to the HTML
  // grid's two columns after px padding, so lines visually meet the boxes.
  const lines = [
    { id: 'ctrl-w1', d: 'M150 60 C 150 130, 75 130, 75 200' },
    { id: 'ctrl-w2', d: 'M150 60 C 150 130, 225 130, 225 200' },
  ];

  const totalServices = topologyAgents.reduce((a, n) => a + n.services.length, 0);
  // Unique players on the network = backend sum (the proxy just routes them).
  const totalPlayers = backendPlayers;

  return (
    <div className="relative w-full overflow-hidden rounded-2xl border border-fd-border bg-fd-card/85 shadow-[0_20px_60px_-20px] shadow-fd-primary/25 backdrop-blur">
      {/* Titlebar */}
      <div className="flex items-center justify-between border-b border-fd-border/70 bg-fd-muted/30 px-4 py-2.5">
        <div className="flex items-center gap-2">
          <div className="flex gap-1.5">
            <span className="size-2.5 rounded-full bg-red-400/70" />
            <span className="size-2.5 rounded-full bg-yellow-400/70" />
            <span className="size-2.5 rounded-full bg-green-400/70" />
          </div>
          <span className="ml-2 text-xs text-fd-muted-foreground">cluster topology</span>
        </div>
        <div className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wider text-emerald-500">
          <span className="nimbus-live-dot size-1.5 rounded-full bg-emerald-500" />
          live
        </div>
      </div>

      {/* Canvas */}
      <div className="relative px-5 py-6">
        {/* Subtle dotted bg */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-50 [background-image:radial-gradient(var(--color-fd-border)_1px,transparent_1px)] [background-size:18px_18px] [mask-image:radial-gradient(ellipse_at_center,black_55%,transparent_85%)]"
        />

        {/* SVG connection lines (behind boxes) */}
        <svg
          viewBox="0 0 300 300"
          preserveAspectRatio="none"
          className="absolute inset-0 h-full w-full"
          aria-hidden
        >
          {lines.map((l) => (
            <g key={l.id}>
              <path d={l.d} fill="none" stroke="var(--color-fd-border)" strokeWidth="1.25" />
              <path
                d={l.d}
                fill="none"
                stroke="var(--color-fd-primary)"
                strokeOpacity="0.55"
                strokeWidth="1.25"
                className="nimbus-flow"
              />
            </g>
          ))}
          {lines.map((l, i) => {
            const duration = 2.4;
            const delay = -(i * duration) / lines.length;
            return (
              <circle
                key={`packet-${l.id}`}
                r="2.5"
                fill="var(--color-fd-primary)"
                style={{
                  offsetPath: `path('${l.d}')`,
                  animation: `nimbus-packet ${duration}s ${delay}s linear infinite`,
                  filter: 'drop-shadow(0 0 4px var(--color-fd-primary))',
                }}
              />
            );
          })}
        </svg>

        {/* Controller (top) — orchestration only, no services */}
        <div className="relative z-10 flex justify-center">
          <div className="relative">
            <span className="nimbus-halo absolute inset-0 -z-10 rounded-xl bg-fd-primary/25" />
            <div className="relative flex flex-col gap-1.5 rounded-xl border border-fd-primary/40 bg-fd-card px-4 py-2.5 shadow-sm shadow-fd-primary/10">
              <div className="flex items-center gap-2.5">
                <div className="flex size-7 items-center justify-center rounded-md bg-fd-primary/15 text-fd-primary">
                  <CloudIcon className="size-4" />
                </div>
                <div className="text-left">
                  <div className="text-sm font-semibold leading-tight">Controller</div>
                  <div className="text-[10px] leading-tight text-fd-muted-foreground">
                    orchestrates · no services
                  </div>
                </div>
                <span className="nimbus-live-dot ml-1 size-1.5 rounded-full bg-emerald-500" />
              </div>
              <div className="flex flex-wrap gap-1">
                {controllerModules.map((m) => (
                  <span
                    key={m}
                    className="rounded border border-fd-border bg-fd-muted/40 px-1.5 py-px text-[9px] font-medium text-fd-muted-foreground"
                  >
                    {m}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Spacer — SVG lines cross through */}
        <div aria-hidden className="h-16" />

        {/* Agent nodes */}
        <div className="relative z-10 grid grid-cols-2 gap-3">
          {topologyAgents.map((n) => {
            const pct = (n.capacity.used / n.capacity.max) * 100;
            return (
              <div
                key={n.id}
                className="nimbus-fade-in flex flex-col gap-2 rounded-xl border border-fd-border bg-fd-card/90 p-3"
              >
                <div className="flex items-center gap-2">
                  <span className="flex size-5 items-center justify-center rounded-md bg-fd-muted/60 text-fd-muted-foreground">
                    <CpuIcon className="size-3" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-xs font-semibold leading-tight">{n.label}</div>
                    <div className="text-[10px] leading-tight text-fd-muted-foreground">agent node</div>
                  </div>
                  <span className="nimbus-live-dot size-1.5 shrink-0 rounded-full bg-emerald-500" />
                </div>

                {/* Capacity bar */}
                <div>
                  <div className="flex items-center justify-between text-[9px] text-fd-muted-foreground">
                    <span className="uppercase tracking-wider">services</span>
                    <span className="tabular-nums">{n.capacity.used}/{n.capacity.max}</span>
                  </div>
                  <div className="mt-0.5 h-1 w-full overflow-hidden rounded-full bg-fd-border">
                    <div className="h-full rounded-full bg-fd-primary/70" style={{ width: `${pct}%` }} />
                  </div>
                </div>

                <div className="flex flex-col gap-1">
                  {n.services.map((s) => {
                    const meta = serviceStateMeta[s.state];
                    const isStarting = s.state === 'starting';
                    return (
                      <div
                        key={s.name}
                        className={`flex items-center gap-1.5 rounded-md border px-2 py-1 text-[10px] font-medium ${serviceKindStyle[s.kind]}`}
                      >
                        <span className={`size-1 shrink-0 rounded-full ${meta.dot} ${isStarting ? 'nimbus-live-dot' : ''}`} />
                        <span className="truncate">{s.name}</span>
                        {typeof s.players === 'number' ? (
                          <span className="ml-auto tabular-nums opacity-70">{s.players}p</span>
                        ) : (
                          <span className="ml-auto text-[8px] font-semibold tracking-wider opacity-60">
                            {meta.label}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Footer metrics */}
      <div className="grid grid-cols-3 divide-x divide-fd-border/70 border-t border-fd-border/70 bg-gradient-to-b from-transparent to-fd-primary/[0.04]">
        <div className="px-4 py-2.5">
          <div className="text-[10px] font-medium uppercase tracking-wider text-fd-muted-foreground">Agents</div>
          <div className="text-sm font-semibold tabular-nums">{topologyAgents.length}</div>
        </div>
        <div className="px-4 py-2.5">
          <div className="text-[10px] font-medium uppercase tracking-wider text-fd-muted-foreground">Services</div>
          <div className="text-sm font-semibold tabular-nums">{totalServices}</div>
        </div>
        <div className="px-4 py-2.5">
          <div className="text-[10px] font-medium uppercase tracking-wider text-fd-muted-foreground">Players</div>
          <div className="text-sm font-semibold tabular-nums">{totalPlayers}</div>
        </div>
      </div>
    </div>
  );
}

function GridBg() {
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute inset-0 -z-10 opacity-[0.35] [background-image:linear-gradient(var(--color-fd-border)_1px,transparent_1px),linear-gradient(90deg,var(--color-fd-border)_1px,transparent_1px)] [background-size:44px_44px] [mask-image:radial-gradient(ellipse_at_center,black_40%,transparent_75%)]"
    />
  );
}

export default function Page() {
  return (
    <main className="pt-4 pb-0">
      {/* Hero */}
      <section className="mx-auto w-full max-w-fd-container px-6">
        <div className="relative overflow-hidden rounded-2xl border border-fd-border">
        <GridBg />
        <div className="absolute inset-0 -z-10">
          <div className="absolute inset-0 bg-gradient-to-br from-fd-primary/10 via-transparent to-fd-primary/5" />
          <div className="absolute -top-24 left-1/4 h-[420px] w-[620px] rounded-full bg-fd-primary/12 blur-[120px]" />
          <div className="absolute bottom-0 right-0 h-[320px] w-[420px] rounded-full bg-fd-primary/6 blur-[100px]" />
        </div>

        <div className="grid gap-12 px-6 py-16 md:px-12 md:py-20 lg:grid-cols-[1.05fr_1fr] lg:items-center lg:gap-10 lg:px-16">
          {/* Copy */}
          <div className="flex flex-col max-lg:items-center max-lg:text-center">
            <h1 className="text-4xl font-semibold leading-[1.05] tracking-tight xl:text-6xl">
              Deploy servers,
              <br />
              not <span className="bg-gradient-to-r from-fd-primary to-fd-primary/60 bg-clip-text text-transparent">complexity</span>.
            </h1>

            <p className="mt-6 max-w-xl text-base leading-relaxed text-fd-muted-foreground">
              A lightweight Minecraft cloud system in a single JAR. Auto-scaling,
              multi-node clusters, and a powerful API — without the bloat of a
              panel or the chaos of bash scripts.
            </p>

            <div className="mt-10 flex flex-row flex-wrap items-center gap-3">
              <Link
                href="/docs/guide/quickstart"
                className="group inline-flex items-center justify-center gap-2 rounded-full bg-fd-primary px-6 py-3 text-sm font-medium tracking-tight text-fd-primary-foreground transition-all duration-300 ease-out hover:scale-[1.02] hover:shadow-lg hover:shadow-fd-primary/25 active:scale-[0.98]"
              >
                Get Started
                <ArrowRight className="size-4 transition-transform duration-300 ease-out group-hover:translate-x-0.5" />
              </Link>
              <Link
                href="/docs/guide/introduction"
                className="inline-flex items-center justify-center gap-2 rounded-full border border-fd-border bg-fd-secondary/60 px-6 py-3 text-sm font-medium tracking-tight text-fd-secondary-foreground transition-all duration-300 ease-out hover:border-fd-primary/30 hover:bg-fd-accent active:scale-[0.98]"
              >
                Read the docs
              </Link>
            </div>

            {/* Trust bar */}
            <ul className="mt-8 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-fd-muted-foreground">
              <li className="flex items-center gap-1.5">
                <span className="size-1.5 rounded-full bg-emerald-500" /> MIT licensed
              </li>
              <li className="flex items-center gap-1.5">
                <CpuIcon className="size-3.5" /> Java 21
              </li>
              <li className="flex items-center gap-1.5">
                <CloudIcon className="size-3.5" /> Self-hosted
              </li>
              <li className="flex items-center gap-1.5">
                <ShieldIcon className="size-3.5" /> mTLS clustering
              </li>
            </ul>
          </div>

          {/* Terminal preview */}
          <div className="relative max-lg:mx-auto max-lg:w-full max-lg:max-w-lg">
            <div className="absolute -inset-4 -z-10 rounded-3xl bg-gradient-to-tr from-fd-primary/15 to-transparent blur-2xl" />
            <TopologyPreview />
          </div>
        </div>
        </div>
      </section>

      {/* Install */}
      <section className="mx-auto mt-14 max-w-fd-container px-6">
        <div className="mx-auto max-w-3xl">
          <p className="mb-3 text-center text-sm font-medium text-fd-muted-foreground">
            One command to install
          </p>
          <InstallTabs />
        </div>
      </section>

      {/* Stats band */}
      <section className="mx-auto mt-16 max-w-fd-container px-6">
        <div className="grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-fd-border bg-fd-border lg:grid-cols-4">
          {stats.map((s) => (
            <div key={s.label} className="flex flex-col gap-1 bg-fd-card px-6 py-8 text-center">
              <div className="text-3xl font-semibold tracking-tight text-fd-primary sm:text-4xl">
                {s.value}
              </div>
              <div className="text-sm font-medium">{s.label}</div>
              <div className="text-xs text-fd-muted-foreground">{s.sub}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Features — bento */}
      <section className="mx-auto mt-24 max-w-fd-container px-6">
        <div className="mb-10 text-center">
          <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Everything you need.
          </h2>
          <p className="mt-3 text-base text-fd-muted-foreground">
            From auto-scaling to API access — Nimbus handles the infrastructure.
          </p>
        </div>

        <div className="grid auto-rows-[minmax(180px,auto)] grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <div
              key={f.title}
              className={`group relative flex flex-col gap-3 overflow-hidden rounded-xl border border-fd-border bg-fd-card p-6 transition-all duration-300 ease-out hover:-translate-y-0.5 hover:border-fd-primary/30 hover:shadow-md hover:shadow-fd-primary/5 ${f.span ?? ''}`}
            >
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-fd-primary/10 text-fd-primary transition-colors duration-300 ease-out group-hover:bg-fd-primary/15">
                {f.icon}
              </div>
              <div className="flex-1">
                <p className="font-medium">{f.title}</p>
                <p className="mt-1.5 text-sm leading-relaxed text-fd-muted-foreground">
                  {f.desc}
                </p>
              </div>
              {f.accent}
            </div>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section className="mx-auto mt-24 max-w-fd-container px-6">
        <div className="mb-10 text-center">
          <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            From zero to running network.
          </h2>
          <p className="mt-3 text-base text-fd-muted-foreground">
            Three steps. No panel, no database, no YAML graveyard.
          </p>
        </div>

        <ol className="grid gap-4 sm:grid-cols-3">
          {flow.map((step, i) => (
            <li
              key={step.title}
              className="relative flex flex-col gap-3 rounded-xl border border-fd-border bg-fd-card p-6"
            >
              <div className="flex items-center gap-3">
                <span className="flex size-7 items-center justify-center rounded-full border border-fd-primary/30 bg-fd-primary/10 text-xs font-semibold text-fd-primary">
                  {i + 1}
                </span>
                <span className="flex items-center gap-1.5 text-sm font-medium">
                  {step.icon}
                  {step.title}
                </span>
              </div>
              <p className="text-sm leading-relaxed text-fd-muted-foreground">
                {step.desc}
              </p>
            </li>
          ))}
        </ol>
      </section>

      {/* Platforms */}
      <section className="mx-auto mt-24 max-w-fd-container px-6 text-center">
        <p className="mb-5 text-sm font-medium text-fd-muted-foreground">
          Supports 9 server platforms
        </p>
        <div className="flex flex-wrap justify-center gap-2.5">
          {platforms.map((name) => (
            <span
              key={name}
              className="rounded-full border border-fd-border bg-fd-card px-4 py-2 text-sm font-medium transition-colors duration-300 ease-out hover:border-fd-primary/30 hover:text-fd-primary"
            >
              {name}
            </span>
          ))}
        </div>
      </section>

      {/* Server Software Comparison */}
      <section className="mx-auto mt-24 max-w-fd-container px-6">
        <div className="mb-10 text-center">
          <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            Choose your server software.
          </h2>
          <p className="mt-3 text-base text-fd-muted-foreground">
            All Paper-based — same plugins, different strengths.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {serverSoftware.map((sw) => (
            <div
              key={sw.name}
              className="group relative flex flex-col rounded-xl border border-fd-border bg-fd-card p-5 transition-all duration-300 ease-out hover:-translate-y-0.5 hover:border-fd-primary/30 hover:shadow-md hover:shadow-fd-primary/5"
            >
              <div className="mb-1 flex items-center gap-3">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={sw.logo}
                  alt={sw.name}
                  className="size-9 shrink-0 rounded-lg object-contain"
                />
                <div className="min-w-0">
                  <h3 className="font-semibold leading-tight">{sw.name}</h3>
                  <p className="truncate text-xs text-fd-muted-foreground">{sw.tagline}</p>
                </div>
              </div>

              <div className="mt-4 mb-4 space-y-2.5">
                {Object.entries(sw.ratings).map(([key, value]) => (
                  <div key={key}>
                    <div className="mb-1 flex items-center justify-between">
                      <span className="text-xs text-fd-muted-foreground">
                        {ratingLabels[key]}
                      </span>
                      <span className="text-xs font-medium text-fd-muted-foreground">
                        {value}/5
                      </span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-fd-border">
                      <div
                        className="h-full rounded-full bg-fd-primary/70"
                        style={{ width: `${(value / 5) * 100}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>

              <p className="mt-auto text-xs leading-relaxed text-fd-muted-foreground">
                {sw.bestFor}
              </p>

              <span className="mt-3 inline-block w-fit rounded-full border border-fd-border px-2 py-0.5 text-xs text-fd-muted-foreground">
                {sw.minVersion}
              </span>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="relative mx-auto mt-24 max-w-3xl overflow-hidden rounded-2xl border border-fd-border px-6 py-16 text-center">
        <GridBg />
        <div className="absolute inset-0 -z-10">
          <div className="absolute inset-0 bg-gradient-to-t from-fd-primary/8 via-transparent to-transparent" />
        </div>

        <CloudIcon className="mx-auto mb-4 size-10 text-fd-primary" />
        <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Up and running in minutes.
        </h2>
        <p className="mx-auto mt-3 max-w-lg text-base text-fd-muted-foreground">
          One command to install, one JAR to run. No Docker, no database —
          just Java 21 and your network is live.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/docs/guide/quickstart"
            className="group inline-flex items-center gap-2 rounded-full bg-fd-primary px-6 py-3 text-sm font-medium text-fd-primary-foreground transition-all duration-300 ease-out hover:scale-[1.02] hover:shadow-lg hover:shadow-fd-primary/25 active:scale-[0.98]"
          >
            Quick Start
            <ArrowRight className="size-4 transition-transform duration-300 ease-out group-hover:translate-x-0.5" />
          </Link>
          <Link
            href="/docs/reference/api"
            className="inline-flex items-center gap-2 rounded-full border border-fd-border px-6 py-3 text-sm font-medium transition-all duration-300 ease-out hover:border-fd-accent hover:bg-fd-accent active:scale-[0.98]"
          >
            API Reference
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="mx-auto mt-24 w-full max-w-fd-container px-6">
        <div className="border-t border-fd-border pb-10 pt-12">
          <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <div className="mb-3 flex items-center gap-2.5">
                <Image
                  src={`${basePath}/icon.png`}
                  alt="Nimbus"
                  width={28}
                  height={28}
                  className="rounded-md"
                />
                <span className="font-semibold tracking-tight">Nimbus</span>
              </div>
              <p className="text-sm leading-relaxed text-fd-muted-foreground">
                Lightweight Minecraft cloud system.
                <br />
                One JAR, zero complexity.
              </p>
            </div>

            <div>
              <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-fd-muted-foreground">
                Documentation
              </p>
              <ul className="space-y-2.5">
                {footerDocs.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-sm text-fd-muted-foreground transition-colors duration-200 hover:text-fd-foreground"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-fd-muted-foreground">
                Developer
              </p>
              <ul className="space-y-2.5">
                {footerDev.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-sm text-fd-muted-foreground transition-colors duration-200 hover:text-fd-foreground"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-fd-muted-foreground">
                Community
              </p>
              <ul className="space-y-2.5">
                {footerCommunity.map((link) => (
                  <li key={link.href}>
                    <a
                      href={link.href}
                      target="_blank"
                      rel="noreferrer noopener"
                      className="text-sm text-fd-muted-foreground transition-colors duration-200 hover:text-fd-foreground"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          <div className="mt-10 flex flex-col items-center gap-3 border-t border-fd-border pt-6 sm:flex-row sm:justify-between">
            <p className="text-xs text-fd-muted-foreground">
              &copy; {new Date().getFullYear()} NimbusPowered. Open source under MIT.
            </p>
            <p className="flex items-center gap-1.5 text-xs text-fd-muted-foreground">
              Built with <HeartIcon className="size-3 text-fd-primary" /> and Kotlin
            </p>
          </div>
        </div>
      </footer>
    </main>
  );
}
