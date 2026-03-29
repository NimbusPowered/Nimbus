# Modpack Import

Nimbus can import any **Modrinth modpack** and turn it into a fully managed, auto-scaling server group — with a single command.

No manual downloading, no extracting ZIPs, no hunting for the right modloader version. Just point Nimbus at a modpack and it handles everything.

## Supported Sources

| Source | Example |
|--------|---------|
| Modrinth slug | `import adrenaserver` |
| Modrinth URL | `import https://modrinth.com/modpack/adrenaserver` |
| Local `.mrpack` file | `import /path/to/modpack.mrpack` |

## Supported Modloaders

| Modloader | Auto-configured | Proxy Forwarding |
|-----------|----------------|-----------------|
| Fabric | ✅ | Fabric Proxy Mod |
| Forge | ✅ | Forge forwarding mod |
| NeoForge | ✅ | NeoForge forwarding mod |
| Quilt | ✅ (via Fabric) | Fabric Proxy Mod |

## Quick Start

Just run the `import` command with a Modrinth slug or URL:

<div class="terminal">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body">
<span class="t-prompt">nimbus</span> <span class="t-cyan">»</span> import adrenaserver
<span class="t-bold">Import Modpack</span>
<span class="t-dim">Resolving modpack...</span> <span class="t-green">✓</span>
<span class="t-bold">Adrenaserver</span> <span class="t-dim">v1.5.2</span>
<span class="t-dim">MC 1.21.4 · Fabric 0.16.9</span>
<span class="t-dim">47 server mods (12 client-only skipped)</span>
Group name: <span class="t-bold">Adrenaserver</span>
Service type <span class="t-dim">[DYNAMIC]</span><span class="t-dim">:</span> <span class="t-bold">DYNAMIC</span>
Memory <span class="t-dim">[2G]</span><span class="t-dim">:</span> <span class="t-bold">2G</span>
Min instances <span class="t-dim">[1]</span><span class="t-dim">:</span> <span class="t-bold">1</span>
Max instances <span class="t-dim">[4]</span><span class="t-dim">:</span> <span class="t-bold">4</span>
<span class="t-bold">Installing...</span>
<span class="t-dim">↓</span> Fabric 0.16.9 <span class="t-green">✓</span>
<span class="t-dim">↓</span> Mods 47/47 <span class="t-green">✓</span>
<span class="t-dim">↓</span> Configs &amp; overrides <span class="t-green">✓</span>
<span class="t-dim">↓</span> Proxy mods <span class="t-green">✓</span>
<span class="t-green">✓</span> groups/adrenaserver.toml
<span class="t-green">✓</span> Group configs reloaded
<span class="t-green t-bold">Modpack 'Adrenaserver' imported as group 'Adrenaserver'!</span>
Start an instance now? <span class="t-dim">[Y/n]</span><span class="t-dim">:</span> <span class="t-bold">y</span>
<span class="t-green">✓</span> Service start initiated.
</pre>
</div>

That's it. Your modded server is live and accepting players through the proxy.

## How It Works

Behind the scenes, the `import` command runs through six stages:

1. **Resolve** — Downloads the `.mrpack` from Modrinth (or reads a local file). Modrinth slugs and URLs are both resolved via the Modrinth API.
2. **Parse** — Reads `modrinth.index.json` inside the pack for the mod list, Minecraft version, and required modloader.
3. **Configure** — Interactive prompts ask for group name, service type, memory allocation, and scaling limits. Sensible defaults are pre-filled based on the pack metadata.
4. **Download** — Fetches the correct modloader server JAR and all server-side mods. Downloads run 8 at a time with SHA1 verification to ensure integrity. Client-only mods are automatically skipped.
5. **Install** — Extracts config overrides from the pack, installs the appropriate proxy forwarding mod for Velocity, and auto-accepts the EULA.
6. **Create** — Generates a group config file in `groups/<name>.toml` with all the settings you chose.

## What Gets Installed

After import, your template directory looks like this:

```
templates/Adrenaserver/
├── fabric-server-launch.jar
├── mods/
│   ├── lithium-0.14.3.jar
│   ├── fabric-api-0.100.1.jar
│   └── ... (47 mods)
├── config/
│   └── ... (extracted from overrides)
└── eula.txt (auto-accepted)
```

A matching group config is created at `groups/Adrenaserver.toml` with the modloader, memory, and scaling settings you selected.

## Auto-Scaling Modded Servers

Imported modpacks are configured as **DYNAMIC** groups by default. This means Nimbus will automatically spin up new instances when player demand increases and shut them down when they're empty — just like it does for vanilla server groups.

The scaling engine monitors player counts across all instances and applies the same fill-rate rules described in the [Auto-Scaling Guide](./scaling.md). You get elastic modded infrastructure without lifting a finger.

::: tip Memory Recommendation
Modded servers typically need **2-4G** per instance. Heavy modpacks (200+ mods) may need **4-6G**. Set this during import or adjust later in `groups/<name>.toml` under the `jvm_memory` key.
:::

## Automatic Proxy Forwarding

One of Nimbus's strongest features for modded servers: **proxy forwarding mods are installed and configured automatically**. You never need to manually download or set up forwarding mods.

| Modloader | Auto-installed Mod | Dependencies |
|---|---|---|
| **Fabric / Quilt** | [FabricProxy-Lite](https://modrinth.com/mod/fabricproxy-lite) | Fabric API (also auto-installed) |
| **Forge** | [proxy-compatible-forge](https://modrinth.com/mod/proxy-compatible-forge) | None |
| **NeoForge** | [proxy-compatible-forge](https://modrinth.com/mod/proxy-compatible-forge) | None |

When Nimbus starts a modded service, it:

1. Checks the template's `mods/` directory for an existing forwarding mod
2. If missing, downloads the correct mod from Modrinth (matched to your MC version)
3. Configures the mod with the correct [forwarding mode](/guide/proxy-setup#adaptive-forwarding) (modern or legacy)
4. Copies the `forwarding.secret` for modern forwarding mode

No manual configuration needed. Players connect to your Velocity proxy on port `25565` and get forwarded to modded servers as if they were vanilla.

::: tip
This works for both imported modpacks AND manually created modded groups. Any Forge, Fabric, or NeoForge group automatically gets proxy forwarding support.
:::

## Updating a Modpack

To update to a new modpack version, simply re-run the `import` command:

<div class="terminal">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body">
<span class="t-prompt">nimbus</span> <span class="t-cyan">»</span> import adrenaserver
<span class="t-bold">Import Modpack</span>
<span class="t-dim">Resolving modpack...</span> <span class="t-green">✓</span>
<span class="t-bold">Adrenaserver</span> <span class="t-dim">v1.6.0</span>
<span class="t-dim">MC 1.21.5 · Fabric 0.17.0</span>
<span class="t-dim">49 server mods (+2 new, 3 updated)</span>
</pre>
</div>

Alternatively, you can manually update individual mods by replacing JAR files in the `templates/<name>/mods/` directory.

::: warning
Re-importing will overwrite the existing template. For **STATIC** services, world data in `services/` is preserved since it lives outside the template. For **DYNAMIC** services, a fresh template copy is applied on every start, so changes to the template take effect immediately.
:::

## Examples

### Fabric Performance Pack

A lightweight server optimization pack — perfect for high-player-count lobbies:

<div class="terminal">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body">
<span class="t-prompt">nimbus</span> <span class="t-cyan">»</span> import adrenaserver
<span class="t-bold">Import Modpack</span>
<span class="t-dim">Resolving modpack...</span> <span class="t-green">✓</span>
<span class="t-bold">Adrenaserver</span> <span class="t-dim">v1.5.2</span>
<span class="t-dim">MC 1.21.4 · Fabric 0.16.9</span>
<span class="t-dim">47 server mods (12 client-only skipped)</span>
<span class="t-green t-bold">Modpack 'Adrenaserver' imported as group 'Adrenaserver'!</span>
</pre>
</div>

### Forge Modpack

Import a heavyweight Forge kitchen-sink pack straight from a Modrinth URL:

<div class="terminal">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body">
<span class="t-prompt">nimbus</span> <span class="t-cyan">»</span> import https://modrinth.com/modpack/all-the-mods-10
<span class="t-bold">Import Modpack</span>
<span class="t-dim">Resolving modpack...</span> <span class="t-green">✓</span>
<span class="t-bold">All the Mods 10</span> <span class="t-dim">v2.40</span>
<span class="t-dim">MC 1.21.1 · NeoForge 21.1.77</span>
<span class="t-dim">341 server mods (89 client-only skipped)</span>
<span class="t-bold">Installing...</span>
<span class="t-dim">↓</span> NeoForge 21.1.77 <span class="t-green">✓</span>
<span class="t-dim">↓</span> Mods 341/341 <span class="t-green">✓</span>
<span class="t-dim">↓</span> Configs &amp; overrides <span class="t-green">✓</span>
<span class="t-dim">↓</span> Proxy mods <span class="t-green">✓</span>
<span class="t-green t-bold">Modpack 'All the Mods 10' imported as group 'AllTheMods10'!</span>
</pre>
</div>

### Local Modpack File

Import a custom pack you built yourself or downloaded manually:

<div class="terminal">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body">
<span class="t-prompt">nimbus</span> <span class="t-cyan">»</span> import ./my-custom-pack.mrpack
<span class="t-bold">Import Modpack</span>
<span class="t-dim">Resolving modpack...</span> <span class="t-green">✓</span>
<span class="t-bold">My Custom Pack</span> <span class="t-dim">v1.0.0</span>
<span class="t-dim">MC 1.20.4 · Quilt 0.26.4 (via Fabric)</span>
<span class="t-dim">23 server mods</span>
<span class="t-green t-bold">Modpack 'My Custom Pack' imported as group 'MyCustomPack'!</span>
</pre>
</div>
