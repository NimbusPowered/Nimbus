import Link from 'next/link';

const features = [
  {
    title: 'Multi-Node Cluster',
    description: 'Distribute game servers across machines with automatic placement, failover, and a built-in TCP load balancer.',
    link: '/docs/guide/multi-node',
  },
  {
    title: 'Smart Auto-Scaling',
    description: 'Instances scale up and down based on player count. Configurable thresholds, idle timeouts, and custom game states.',
    link: '/docs/guide/scaling',
  },
  {
    title: 'Zero-Config Proxy',
    description: 'Velocity auto-managed — forwarding, server list, MOTD, tab list, and chat sync. ViaVersion for mixed versions.',
    link: '/docs/guide/proxy-setup',
  },
  {
    title: 'Software Auto-Download',
    description: 'Paper, Pufferfish, Purpur, Velocity, Forge, Fabric, and NeoForge — server JARs downloaded and updated automatically.',
    link: '/docs/guide/installation',
  },
  {
    title: 'Modpack Import',
    description: 'Import any Modrinth modpack in one command. Auto-configured with proxy forwarding and concurrent downloads.',
    link: '/docs/guide/modpacks',
  },
  {
    title: 'REST API + WebSocket',
    description: 'Live event streams, bidirectional console access, file management, and permissions — all via API.',
    link: '/docs/reference/api',
  },
  {
    title: 'Game-Server Ready',
    description: 'Custom game states, smart routing, built-in permissions, and server-selector signs for minigame networks.',
    link: '/docs/guide/concepts',
  },
  {
    title: 'Lightweight & Fast',
    description: 'Faster than a Nimbus 2000. Single JAR, no bloat — coroutine-powered async, interactive console, and runs on just Java 21.',
    link: '/docs/guide/installation',
  },
];

const stats = [
  { number: '9', label: 'Server platforms' },
  { number: '1', label: 'JAR to deploy' },
  { number: '28', label: 'Console commands' },
  { number: '40+', label: 'API endpoints' },
];

export default function HomePage() {
  return (
    <main className="flex flex-col items-center">
      {/* Hero */}
      <section className="relative flex flex-col items-center text-center px-6 pt-24 pb-16 max-w-4xl mx-auto">
        <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight bg-gradient-to-br from-blue-800 via-sky-500 to-sky-300 bg-clip-text text-transparent">
          Nimbus
        </h1>
        <p className="mt-3 text-xl font-semibold text-fd-muted-foreground">
          Minecraft Cloud System
        </p>
        <p className="mt-4 text-lg text-fd-muted-foreground max-w-xl leading-relaxed">
          Dynamic server management from a single JAR — auto-scaling, multi-node clusters, and a powerful API without the bloat.
        </p>
        <div className="flex gap-3 mt-8">
          <Link
            href="/docs/guide/quickstart"
            className="px-6 py-2.5 rounded-lg bg-sky-500 text-white font-semibold hover:bg-sky-600 transition-colors shadow-lg shadow-sky-500/25"
          >
            Get Started
          </Link>
          <Link
            href="/docs/reference/api"
            className="px-6 py-2.5 rounded-lg border border-fd-border font-semibold hover:bg-fd-muted transition-colors"
          >
            API Docs
          </Link>
        </div>
      </section>

      {/* Terminal */}
      <section className="w-full max-w-[680px] px-6">
        <div className="terminal">
          <div className="terminal-header">
            <span className="terminal-title">nimbus</span>
          </div>
          <pre className="terminal-body">
{`\x1b[0m`}<span className="t-blue">{'   _  __ __ _   __ ___  _ __  ___'}</span>{'\n'}
<span className="t-bright-blue">{'  / |/ // // \\,\' // o.)/// /,\' _/'}</span>{'\n'}
<span className="t-cyan">{' / || // // \\,\' // o \\/ U /_\\ `. '}</span>{'\n'}
<span className="t-bright-cyan">{'/_/|_//_//_/ /_//___,\'\\_,\'/___,\''}</span>{'\n'}
<span className="t-dim">{'            C L O U D'}</span>{'\n'}
<span className="t-dim">{'  Network:'}</span>  <span className="t-bold">{'MyNetwork'}</span>{'\n'}
<span className="t-dim">{'  Version:'}</span>  <span className="t-cyan">{'v0.2.0'}</span>{'\n'}
<span className="t-dim">{'──────────────────────────────'}</span>{'\n'}
<span className="t-dim">{'[12:00:01]'}</span> <span className="t-yellow">{'▲ STARTING'}</span>  <span className="t-bold">{'Proxy-1'}</span> <span className="t-dim">{'(port=25565)'}</span>{'\n'}
<span className="t-dim">{'[12:00:04]'}</span> <span className="t-green">{'● READY'}</span>     <span className="t-bold">{'Proxy-1'}</span>{'\n'}
<span className="t-dim">{'[12:00:04]'}</span> <span className="t-yellow">{'▲ STARTING'}</span>  <span className="t-bold">{'Lobby-1'}</span> <span className="t-dim">{'(port=30000)'}</span>{'\n'}
<span className="t-dim">{'[12:00:08]'}</span> <span className="t-green">{'● READY'}</span>     <span className="t-bold">{'Lobby-1'}</span>{'\n'}
<span className="t-dim">{'[12:00:15]'}</span> <span className="t-green">{'↑ SCALE UP'}</span>  <span className="t-bold">{'BedWars'}</span>{' 1 → 2\n'}
<span className="t-prompt">{'nimbus'}</span> <span className="t-cyan">{'»'}</span> <span className="t-cursor">{'_'}</span>
          </pre>
        </div>
      </section>

      {/* Features */}
      <section className="w-full max-w-5xl px-6 py-16">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {features.map((feature) => (
            <Link
              key={feature.title}
              href={feature.link}
              className="group p-5 rounded-xl border border-fd-border bg-fd-card hover:border-sky-500/30 hover:shadow-lg hover:shadow-sky-500/5 transition-all hover:-translate-y-1"
            >
              <h3 className="font-bold text-sm">{feature.title}</h3>
              <p className="mt-2 text-sm text-fd-muted-foreground leading-relaxed">
                {feature.description}
              </p>
            </Link>
          ))}
        </div>
      </section>

      {/* Why Nimbus */}
      <section className="home-section">
        <h2 className="home-section-title">Why Nimbus?</h2>
        <p className="home-section-subtitle">No external dependencies. No complex setup. Just Java 21 and a single JAR.</p>

        <div className="overflow-x-auto">
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr className="border-b border-fd-border">
                <th className="text-left p-3"></th>
                <th className="text-left p-3 font-semibold">Nimbus</th>
                <th className="text-left p-3 font-semibold">Manual Setup</th>
                <th className="text-left p-3 font-semibold">Heavy Cloud Systems</th>
              </tr>
            </thead>
            <tbody className="text-fd-muted-foreground">
              <tr className="border-b border-fd-border"><td className="p-3 font-medium text-fd-foreground">Setup time</td><td className="p-3">Under 5 minutes</td><td className="p-3">Hours to days</td><td className="p-3">30+ minutes</td></tr>
              <tr className="border-b border-fd-border"><td className="p-3 font-medium text-fd-foreground">Dependencies</td><td className="p-3">Java 21 only</td><td className="p-3">Java, Velocity, config</td><td className="p-3">Java, Docker, databases, ...</td></tr>
              <tr className="border-b border-fd-border"><td className="p-3 font-medium text-fd-foreground">Proxy</td><td className="p-3">Automatic</td><td className="p-3">Manual config</td><td className="p-3">Varies</td></tr>
              <tr className="border-b border-fd-border"><td className="p-3 font-medium text-fd-foreground">Auto-scaling</td><td className="p-3">Built-in</td><td className="p-3">Not available</td><td className="p-3">Plugin-dependent</td></tr>
              <tr className="border-b border-fd-border"><td className="p-3 font-medium text-fd-foreground">Multi-node</td><td className="p-3">Built-in with TCP LB</td><td className="p-3">Not available</td><td className="p-3">Requires setup</td></tr>
              <tr className="border-b border-fd-border"><td className="p-3 font-medium text-fd-foreground">API</td><td className="p-3">REST + WebSocket</td><td className="p-3">None</td><td className="p-3">Varies</td></tr>
              <tr><td className="p-3 font-medium text-fd-foreground">Footprint</td><td className="p-3">Single JAR (~15MB)</td><td className="p-3">Multiple JARs + scripts</td><td className="p-3">Multiple services</td></tr>
            </tbody>
          </table>
        </div>

        <div className="auto-grid">
          <div className="auto-grid-header">What Nimbus does for you</div>
          <div className="auto-grid-items">
            {[
              'Downloads server JARs automatically',
              'Creates optimized server configs',
              "Applies Aikar's JVM flags",
              'Manages the Velocity proxy',
              'Scales instances by player count',
              'Stops empty servers automatically',
              'Auto-restarts on crash',
              'Deploys plugins to all servers',
              'Built-in permissions system',
              'Bedrock crossplay via Geyser',
            ].map((item) => (
              <div key={item} className="auto-item">
                <span>&#10003;</span> {item}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Stats */}
      <section className="home-section pb-20">
        <div className="stats-strip">
          {stats.map((stat) => (
            <div key={stat.label} className="stat-card">
              <div className="stat-number">{stat.number}</div>
              <div className="stat-label">{stat.label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Footer */}
      <footer className="w-full border-t border-fd-border py-8 text-center text-sm text-fd-muted-foreground">
        <p>Nimbus Cloud System</p>
        <p className="mt-1">&copy; 2025-2026 Jonas Laux</p>
      </footer>
    </main>
  );
}
