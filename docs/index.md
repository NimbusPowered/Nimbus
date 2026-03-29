---
layout: home
hero:
  name: "Nimbus"
  text: "Minecraft Cloud System"
  tagline: "Dynamic server management from a single JAR — auto-scaling, modpack support, and a powerful API without the bloat."
  actions:
    - theme: brand
      text: Get Started
      link: /guide/quickstart
    - theme: alt
      text: API Reference
      link: /reference/api

features:
  - icon:
      src: /icons/package.svg
      width: 40
      height: 40
    title: "Modpack Import"
    details: "Import any Modrinth modpack with a single command. Fabric, Forge, NeoForge — auto-configured with proxy forwarding and concurrent downloads."
    link: /guide/modpacks
    linkText: Learn more
  - icon:
      src: /icons/trending-up.svg
      width: 40
      height: 40
    title: "Smart Auto-Scaling"
    details: "Servers spin up and down based on real-time player count. Configurable thresholds, idle timeouts, and custom game states."
    link: /guide/scaling
    linkText: How it works
  - icon:
      src: /icons/zap.svg
      width: 40
      height: 40
    title: "Zero-Config Proxy"
    details: "Velocity proxy auto-managed — forwarding, server list, tab list, MOTD, and chat sync. ViaVersion auto-deployed for mixed versions."
    link: /guide/proxy-setup
    linkText: Proxy setup
  - icon:
      src: /icons/plug.svg
      width: 40
      height: 40
    title: "REST API + WebSocket"
    details: "Real-time event streams, bidirectional console access, file management, and permission control — all via API."
    link: /reference/api
    linkText: API docs
  - icon:
      src: /icons/gamepad.svg
      width: 40
      height: 40
    title: "Game-Server Ready"
    details: "Custom game states, smart player routing, permission system, and display signs — built for minigame networks."
    link: /guide/concepts
    linkText: Core concepts
  - icon:
      src: /icons/rocket.svg
      width: 40
      height: 40
    title: "Lightweight & Fast"
    details: "Single JAR, no database, no web UI. Coroutine-based async with auto-download of Paper, Purpur, Velocity, and modded servers."
    link: /guide/installation
    linkText: Get started
---

<div class="terminal" style="max-width: 680px; margin: 3rem auto 0;">
  <div class="terminal-header">
    <span class="terminal-title">nimbus</span>
  </div>
  <pre class="terminal-body nimbus-banner">
<span class="t-blue">   _  __ __ _   __ ___  _ __  ___</span>
<span class="t-bright-blue">  / |/ // // \,' // o.)/// /,' _/</span>
<span class="t-cyan"> / || // // \,' // o \/ U /_\ \`. </span>
<span class="t-bright-cyan">/_/|_//_//_/ /_//___,'\_,'/___,'</span>
<span class="t-dim">            C L O U D</span>
<span class="t-dim">  Network:</span>  <span class="t-bold">MyNetwork</span>
<span class="t-dim">  Version:</span>  <span class="t-cyan">v0.2.0</span>
<span class="t-dim">──────────────────────────────</span>
<span class="t-dim">[12:00:01]</span> <span class="t-yellow">▲ STARTING</span>  <span class="t-bold">Proxy-1</span> <span class="t-dim">(port=25565)</span>
<span class="t-dim">[12:00:04]</span> <span class="t-green">● READY</span>     <span class="t-bold">Proxy-1</span>
<span class="t-dim">[12:00:04]</span> <span class="t-yellow">▲ STARTING</span>  <span class="t-bold">Lobby-1</span> <span class="t-dim">(port=30000)</span>
<span class="t-dim">[12:00:08]</span> <span class="t-green">● READY</span>     <span class="t-bold">Lobby-1</span>
<span class="t-dim">[12:00:15]</span> <span class="t-green">↑ SCALE UP</span>  <span class="t-bold">BedWars</span> 1 → 2
<span class="t-prompt">nimbus</span> <span class="t-cyan">»</span> <span class="t-white">_</span>
</pre>
</div>
