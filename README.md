<div align="center">

<img src="docs/public/banner.svg" alt="Nimbus Cloud" width="460" />

**Lightweight Minecraft Cloud System**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-21+-ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A.svg?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)
[![Docs](https://img.shields.io/badge/Docs-Read%20the%20Docs-0ea5e9.svg?style=for-the-badge&logo=readthedocs&logoColor=white)](https://jonax1337.github.io/Nimbus/)

Dynamic server management from a single JAR — auto-scaling, modpack support, and a powerful API without the bloat.

[Documentation](https://jonax1337.github.io/Nimbus/) &#183; [Quick Start](#quick-start) &#183; [API Reference](https://jonax1337.github.io/Nimbus/reference/api.html)

</div>

---

## Features

- **Single JAR** — `java -jar nimbus.jar` starts everything, no external dependencies
- **TOML Config** — one file per server group, human-readable and hot-reloadable
- **Auto-Scaling** — spin up/down instances based on real-time player count
- **Modpack Import** — import any Modrinth modpack with a single command (Fabric, Forge, NeoForge)
- **Velocity-First** — auto-manages proxy server list, forwarding, tab list, MOTD, and chat sync
- **Version Compatibility** — supports 1.8.8 to latest via adaptive forwarding + auto-deployed ViaVersion
- **Auto-Download** — fetches Paper, Purpur, Velocity, Fabric, Forge, and NeoForge JARs automatically
- **REST API + WebSocket** — full remote management, live events, and bidirectional console access
- **Interactive Console** — JLine3-powered REPL with tab completion, screen sessions, and live events
- **Crash Recovery** — auto-restarts crashed servers with configurable attempt limits

## Requirements

- **Java 21+** — everything else is bundled in the Shadow JAR

## Quick Start

```bash
# Download or build the JAR
java -jar nimbus.jar

# The setup wizard guides you through:
#   - Network name, server software, Minecraft versions
#   - Group configuration (Lobby, Game servers, etc.)

# Nimbus starts all services automatically
```

## Build from Source

```bash
git clone https://github.com/jonax1337/Nimbus.git
cd Nimbus
./gradlew shadowJar

# Output: nimbus-core/build/libs/nimbus-core-0.1.0-all.jar
java -jar nimbus-core/build/libs/nimbus-core-0.1.0-all.jar
```

## Architecture

```
nimbus-core/src/main/kotlin/dev/nimbus/
├── Nimbus.kt              # Entry point, bootstrap
├── api/                   # Ktor REST API + WebSocket
├── config/                # TOML config loading
├── console/               # JLine3 REPL, 24 commands
├── event/                 # Coroutine-based EventBus
├── group/                 # ServerGroup runtime, GroupManager
├── scaling/               # ScalingEngine + ScalingRule
├── service/               # Lifecycle, ProcessHandle, PortAllocator
├── setup/                 # First-run SetupWizard
├── template/              # TemplateManager, SoftwareResolver
└── velocity/              # VelocityConfigGen (auto proxy config)
```

## Configuration

<details>
<summary><b>nimbus.toml</b> — Main Config</summary>

```toml
[network]
name = "MyNetwork"
bind = "0.0.0.0"

[controller]
max_memory = "10G"
max_services = 20
heartbeat_interval = 5000

[console]
colored = true
log_events = true

[paths]
templates = "templates"
running = "running"
logs = "logs"
```

</details>

<details>
<summary><b>groups/lobby.toml</b> — Group Config</summary>

```toml
[group]
name = "Lobby"
type = "DYNAMIC"
template = "lobby"
software = "PAPER"
version = "1.21.4"

[group.resources]
memory = "1G"
max_players = 50

[group.scaling]
min_instances = 1
max_instances = 4
players_per_instance = 40
scale_threshold = 0.8
idle_timeout = 0

[group.lifecycle]
stop_on_empty = false
restart_on_crash = true
max_restarts = 5

[group.jvm]
args = ["-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"]
```

</details>

## Console Commands

| Category | Command | Description |
|----------|---------|-------------|
| **Service** | `list` | Show all running services with status, port, players |
| | `start <group>` | Start a new instance of a group |
| | `stop <service>` | Gracefully stop a service |
| | `restart <service>` | Stop and restart a service |
| | `screen <service>` | Attach to service console |
| | `exec <service> <cmd>` | Execute a command on a service |
| **Group** | `groups` | List all groups with instance counts |
| | `info <group>` | Show group config and scaling rules |
| | `create` | Interactive group creation wizard |
| **Network** | `status` | Cluster overview: groups, resources, uptime |
| | `players` | List all connected players |
| | `send <player> <srv>` | Transfer a player to another service |
| **System** | `reload` | Hot-reload group configs |
| | `shutdown` | Ordered shutdown: games → lobbies → proxy |

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Kotlin 2.1, Java 21 |
| Build | Gradle + Shadow plugin |
| Async | kotlinx-coroutines |
| Config | ktoml (TOML) |
| Console | JLine 3 |
| HTTP Client | Ktor Client (CIO) |
| API Server | Ktor Server (CIO) |
| Downloads | Paper, Purpur, Velocity, Modrinth APIs |

## License

MIT — see [LICENSE](LICENSE)

---

<br>

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=jonax1337/Nimbus&type=Date)](https://star-history.com/#jonax1337/Nimbus&Date)

</div>
