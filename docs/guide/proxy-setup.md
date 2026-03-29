# Proxy Setup

Nimbus uses [Velocity](https://papermc.io/software/velocity) as its proxy layer. The proxy sits in front of all backend servers, handling player connections, server switching, and cross-server features like tab lists and chat formatting.

## How it works

When Nimbus starts, it:

1. Creates a Velocity proxy instance from your proxy group config
2. Auto-generates `velocity.toml` with all backend servers listed
3. Deploys the **nimbus-bridge** plugin to the proxy
4. Keeps the server list in sync as services start and stop

You never need to manually edit `velocity.toml` -- Nimbus manages it entirely.

## Proxy group configuration

Create a proxy group in `groups/proxy.toml`:

```toml
[group]
name = "Proxy"
type = "STATIC"
software = "VELOCITY"
version = "3.4.0"

[group.resources]
memory = "512M"
max_players = 500

[group.scaling]
min_instances = 1
max_instances = 1

[group.jvm]
args = ["-XX:+UseG1GC"]
```

::: tip
The proxy should almost always be `STATIC` with a single instance. For high-traffic networks, see [Multiple Proxies](#multiple-proxies) below.
:::

## Adaptive Forwarding Mode {#adaptive-forwarding}

Nimbus **automatically determines the correct forwarding mode** for your entire network. You never need to configure this manually -- it is calculated every time a service starts, based on all your group configurations.

::: tip Zero-config forwarding
This is one of the things that makes Nimbus special. In a traditional setup, you'd need to manually configure forwarding in `velocity.toml`, copy `forwarding.secret` files, patch `spigot.yml` or `paper-global.yml`, and install forwarding mods on modded servers. Nimbus does **all of this automatically**.
:::

### How it works

When Nimbus starts any service, it evaluates all backend groups:

1. If **all** backend groups run Minecraft 1.13 or newer → **modern forwarding** (recommended, more secure)
2. If **any** backend group runs a version older than 1.13 → **legacy/BungeeCord forwarding** (for compatibility)

This is recalculated dynamically. If you add a 1.12.2 group to a network that was using modern forwarding, Nimbus will automatically switch to legacy mode for all new services.

### Modern forwarding (default)

Used when all backends are 1.13+. Modern forwarding uses a shared secret (`forwarding.secret`) to cryptographically verify that connections actually come from the proxy.

Nimbus configures this automatically for every server type:
- **Velocity** — Sets `player-info-forwarding-mode = "modern"` in `velocity.toml`
- **Paper/Purpur** — Enables velocity support in `paper-global.yml` and copies the forwarding secret
- **Fabric** — Configures [FabricProxy-Lite](https://modrinth.com/mod/fabricproxy-lite) with the secret (auto-installed)
- **Forge/NeoForge** — Configures [proxy-compatible-forge](https://modrinth.com/mod/proxy-compatible-forge) with the secret (auto-installed)

### Legacy (BungeeCord) forwarding

Used when any backend runs a pre-1.13 version. Less secure, but the only option for older servers.

Nimbus detects and configures this automatically:
- Sets `player-info-forwarding-mode = "legacy"` in `velocity.toml`
- Enables `bungeecord: true` in each backend's `spigot.yml`
- Configures FabricProxy-Lite / proxy-compatible-forge in legacy mode for modded servers

::: warning
Legacy forwarding trusts any connection claiming to be from the proxy. Make sure your backend servers are not directly accessible from the internet -- only the proxy port (25565) should be public.
:::

### Compatibility checks

Nimbus runs automatic compatibility checks when starting up. If it detects a forwarding conflict (e.g., a pre-1.13 server forcing legacy mode while a Forge server requires modern forwarding), it will warn you in the console with a clear explanation of the problem and how to fix it.

## Automatic Proxy Forwarding Mods {#auto-forwarding-mods}

When you create a modded server group (Fabric, Forge, or NeoForge), Nimbus **automatically downloads and installs** the correct proxy forwarding mod so players can connect through Velocity seamlessly. No manual mod installation needed.

| Server Software | Auto-installed Mod | Source |
|---|---|---|
| **Fabric** / **Quilt** | [FabricProxy-Lite](https://modrinth.com/mod/fabricproxy-lite) + Fabric API | Modrinth |
| **Forge** | [proxy-compatible-forge](https://modrinth.com/mod/proxy-compatible-forge) | Modrinth |
| **NeoForge** | [proxy-compatible-forge](https://modrinth.com/mod/proxy-compatible-forge) | Modrinth |

This happens transparently when a service is started:

1. Nimbus checks the template's `mods/` directory for an existing forwarding mod
2. If none is found, it downloads the correct mod from Modrinth (matched to your MC version)
3. The mod is configured automatically with the correct forwarding mode and secret

::: tip
For Fabric servers, Nimbus also auto-installs **Fabric API** as a dependency, since FabricProxy-Lite requires it.
:::

The forwarding mod configuration is patched to match the [adaptive forwarding mode](#adaptive-forwarding) — if your network uses modern forwarding, the mod gets the `forwarding.secret`; if legacy, it gets BungeeCord mode. This is fully automatic.

## Via plugins (multi-version support)

To allow players on different Minecraft versions to connect through a single proxy, Nimbus supports automatic deployment of Via plugins:

| Plugin | Purpose |
|---|---|
| **ViaVersion** | Allows newer clients to connect to older servers |
| **ViaBackwards** | Allows older clients to connect to newer servers |
| **ViaRewind** | Extends ViaBackwards support to very old versions (1.7-1.8) |

::: info Important
Via plugins are deployed **only on backend servers**, never on the proxy itself. This is the recommended setup for Velocity-based networks -- the proxy handles connections natively, and Via plugins on backends handle protocol translation.
:::

To enable Via plugins, place them in the global template directory:

```
templates/
  global/
    plugins/
      ViaVersion.jar
      ViaBackwards.jar
```

Every backend server will receive these plugins automatically.

## Velocity Auto-Patching (6-hourly updates) {#auto-patching}

Nimbus keeps your Velocity proxy up to date automatically. A background job checks the PaperMC API for new Velocity releases **every 6 hours** and applies updates without manual intervention.

::: info No downtime required
Updates are downloaded and staged automatically, but only take effect on the next proxy restart. Your running proxy is never interrupted. For dynamic proxy instances, new instances automatically pick up the latest version from the updated template.
:::

### How it works

1. **Check** — Every 6 hours (starting 60 seconds after boot), Nimbus queries the PaperMC API for the latest Velocity version
2. **Compare** — If the latest version matches your current version, nothing happens
3. **Download** — If a new version is found, the JAR is downloaded to the proxy template directory
4. **Stage** — For **static** proxy instances, the new JAR is copied into the working directory
5. **Persist** — The version in `groups/proxy.toml` is updated automatically so the change survives restarts
6. **Notify** — Events are emitted for monitoring and integrations

### Events

| Event | When | Payload |
|---|---|---|
| `ProxyUpdateAvailable` | New version detected | `oldVersion`, `newVersion` |
| `ProxyUpdateApplied` | Download complete, JAR staged | `oldVersion`, `newVersion` |

You can listen for these events via the [WebSocket API](/reference/websocket) to trigger alerts, Discord notifications, or automated restart scripts.

<div class="terminal">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body">
<span class="t-dim">[18:00:01]</span> <span class="t-cyan">ℹ</span> New Velocity version available: <span class="t-yellow">3.4.0</span> → <span class="t-green">3.5.0</span>
<span class="t-dim">[18:00:04]</span> <span class="t-cyan">ℹ</span> Downloaded Velocity 3.5.0 (12.4 MB)
<span class="t-dim">[18:00:04]</span> <span class="t-cyan">ℹ</span> Updated Velocity JAR for static service 'Proxy-1' <span class="t-dim">(restart to apply)</span>
<span class="t-dim">[18:00:04]</span> <span class="t-green">✓</span> Velocity updated: 3.4.0 → 3.5.0 <span class="t-dim">(restart proxy to apply)</span>
</pre>
</div>

::: tip
If the download fails (network issue, API outage), Nimbus silently keeps the old version and retries at the next 6-hour interval. No action needed from you.
:::

## Proxy synchronization

Nimbus provides centralized configuration for tab lists, MOTD, and chat formatting that is synced to all proxy instances in real-time via the API and WebSocket events.

Configuration is stored in `proxy/proxy.toml`:

### Tab list

```toml
[tablist]
header = "\n<gradient:#58a6ff:#56d4dd><bold>MY NETWORK</bold></gradient>\n"
footer = "\n<gray>Online</gray> <white>»</white> <gradient:#56d4dd:#b392f0>{online}</gradient><dark_gray>/</dark_gray><gray>{max}</gray>\n"
player_format = "{prefix}{player}{suffix}"
update_interval = 5
```

**Placeholders:**

| Placeholder | Description |
|---|---|
| `{online}` | Total players online |
| `{max}` | Max player slots |
| `{player}` | Player's username |
| `{prefix}` | Player's permission group prefix |
| `{suffix}` | Player's permission group suffix |
| `{server}` | Current server name (e.g., `Lobby-1`) |
| `{group}` | Current group name (e.g., `Lobby`) |

Per-player tab name overrides are also supported via the SDK:

```java
Nimbus.setTabName(player.getUniqueId(), "<red>[RED] {player}");
Nimbus.clearTabName(player.getUniqueId());
```

### MOTD

```toml
[motd]
line1 = "  <gradient:#58a6ff:#56d4dd:#b392f0><bold>MY NETWORK</bold></gradient>"
line2 = "  <gray>» </gray><gradient:#56d364:#56d4dd>{online} players online</gradient>"
max_players = -1
player_count_offset = 0
```

| Field | Description |
|---|---|
| `line1` / `line2` | MiniMessage-formatted MOTD lines |
| `max_players` | Override max player count shown (`-1` = use Velocity default) |
| `player_count_offset` | Add to the displayed online count (useful for appearance) |

### Chat format

```toml
[chat]
format = "{prefix}{player}{suffix} <dark_gray>» <gray>{message}"
enabled = true
```

Set `enabled = false` to let another plugin handle chat formatting.

::: tip
All proxy sync settings can be changed at runtime via the REST API (`PUT /api/proxy/tablist`, `PUT /api/proxy/motd`, `PUT /api/proxy/chat`). Changes are pushed to all proxies instantly via WebSocket.
:::

## nimbus-bridge plugin

The bridge plugin is automatically deployed to all proxy instances. It provides:

### Commands

| Command | Permission | Description |
|---|---|---|
| `/hub`, `/lobby`, `/l` | None | Send player to a lobby server |
| `/cloud status` | `nimbus.cloud.status` | Network overview |
| `/cloud list [group]` | `nimbus.cloud.list` | List running services |
| `/cloud info <service>` | `nimbus.cloud.info` | Service details |
| `/cloud start <group>` | `nimbus.cloud.start` | Start a new instance |
| `/cloud stop <service>` | `nimbus.cloud.stop` | Stop a service |
| `/cloud restart <service>` | `nimbus.cloud.restart` | Restart a service |
| `/cloud exec <service> <cmd>` | `nimbus.cloud.exec` | Execute console command |
| `/cloud players` | `nimbus.cloud.players` | Online player list |
| `/cloud send <player> <service>` | `nimbus.cloud.send` | Transfer a player |
| `/cloud groups` | `nimbus.cloud.groups` | List all groups |
| `/cloud setstate <service> <state>` | `nimbus.cloud.setstate` | Set custom state |
| `/cloud reload` | `nimbus.cloud.reload` | Reload Nimbus config |
| `/cloud shutdown` | `nimbus.cloud.shutdown` | Shut down the network |
| `/cloud perms ...` | `nimbus.cloud.perms` | Permission management |

The base permission `nimbus.cloud` is required to use any `/cloud` subcommand. The `/cloud` command is also aliased as `/nimbus`.

### Connection handling

- Players are automatically sent to a lobby server on connect
- If kicked from a non-lobby server, players are redirected back to the lobby
- If no lobby is available, the player is disconnected with an error message
- Lobby servers are identified by group names starting with "Lobby" (case-insensitive)

### Permission integration

The bridge integrates with Nimbus's [permission system](/config/permissions) and provides:

- Automatic permission loading on player login
- Real-time permission updates via WebSocket events
- Wildcard permission matching (e.g., `nimbus.cloud.*`)
- Permission cache that invalidates on disconnect

## Multiple proxies {#multiple-proxies}

For large networks, you can run multiple Velocity proxies behind a TCP load balancer (e.g., HAProxy, nginx). Change the proxy group scaling:

```toml
[group.scaling]
min_instances = 2
max_instances = 4
```

Each proxy instance receives the same bridge plugin and sync configuration. All proxies connect to the same Nimbus API and receive the same real-time events.

::: warning
Multiple proxies require an external load balancer. Nimbus does not include a TCP proxy -- it only manages the Velocity instances.
:::

## Next steps

- [Server Groups](/guide/server-groups) -- Configure backend servers
- [Auto-Scaling](/guide/scaling) -- Set up dynamic scaling
- [Bridge Plugin](/developer/bridge) -- Detailed bridge reference
