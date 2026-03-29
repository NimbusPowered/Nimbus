# nimbus.toml Reference

The main configuration file for Nimbus. Located at the root of your Nimbus installation directory. If this file does not exist on first launch, Nimbus uses built-in defaults for all options.

::: tip
After editing `nimbus.toml`, restart Nimbus for changes to take effect. Group configs can be hot-reloaded with the `reload` command, but main config changes require a restart.
:::

## Complete Example

```toml
[network]
name = "Nimbus"
bind = "0.0.0.0"

[controller]
max_memory = "10G"
max_services = 20
heartbeat_interval = 5000

[console]
colored = true
log_events = true
history_file = ".nimbus_history"

[paths]
templates = "templates"
services = "services"
logs = "logs"

[api]
enabled = true
bind = "127.0.0.1"
port = 8080
token = ""
allowed_origins = []

[java]
java_8 = ""
java_11 = ""
java_16 = ""
java_17 = ""
java_21 = ""
```

---

## `[network]`

Network identity and bind settings.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `name` | String | `"Nimbus"` | Display name for the network. Used in logging and API responses. |
| `bind` | String | `"0.0.0.0"` | Default bind address for all managed servers. Use `0.0.0.0` to listen on all interfaces, or a specific IP to restrict. |

```toml
[network]
name = "MyNetwork"
bind = "0.0.0.0"
```

---

## `[controller]`

Resource limits and scaling engine settings.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `max_memory` | String | `"10G"` | Total memory budget for all running services. Format: number + `M` or `G` (e.g., `"512M"`, `"8G"`). Nimbus will not start new services if the total allocated memory would exceed this limit. |
| `max_services` | Int | `20` | Maximum number of concurrent services across all groups. |
| `heartbeat_interval` | Long | `5000` | Interval in milliseconds between scaling engine evaluation cycles. Each cycle checks player counts and applies scaling rules. |

```toml
[controller]
max_memory = "16G"
max_services = 50
heartbeat_interval = 3000
```

::: warning
Setting `heartbeat_interval` too low (below 1000ms) may cause excessive server list pings. The default of 5000ms is suitable for most setups.
:::

---

## `[console]`

Interactive console appearance and behavior.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `colored` | Boolean | `true` | Enable ANSI colored output in the console. Disable if your terminal doesn't support color codes. |
| `log_events` | Boolean | `true` | Log internal events (service start/stop, scaling decisions) to the console. |
| `history_file` | String | `".nimbus_history"` | File path for command history persistence. Supports up-arrow recall between sessions. Relative to the Nimbus root directory. |

```toml
[console]
colored = true
log_events = true
history_file = ".nimbus_history"
```

---

## `[paths]`

Directory paths for templates, running services, and logs. All paths are relative to the Nimbus root directory unless an absolute path is specified.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `templates` | String | `"templates"` | Directory containing server templates. Each group references a template by name (subdirectory). |
| `services` | String | `"services"` | Directory where running service instances are created. Each service gets its own subdirectory (e.g., `services/Lobby-1/`). |
| `logs` | String | `"logs"` | Directory for Nimbus log files. |

```toml
[paths]
templates = "templates"
services = "services"
logs = "logs"
```

::: info
Dynamic services have their working directories recreated from the template on every start. Static services preserve their directories across restarts — see [Templates](/config/templates) for details.
:::

---

## `[api]`

REST API and WebSocket server configuration.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | `true` | Enable the HTTP API server. Required for the bridge plugin and SDK to communicate with Nimbus. |
| `bind` | String | `"127.0.0.1"` | Bind address for the API server. Use `127.0.0.1` for local-only access (recommended) or `0.0.0.0` for remote access. |
| `port` | Int | `8080` | Port for the API server. |
| `token` | String | `""` | Bearer token for API authentication. If empty, Nimbus auto-generates a secure token on first launch and prints it to the console. |
| `allowed_origins` | List\<String\> | `[]` | CORS allowed origins. Only needed if accessing the API from a web browser. Empty list disables CORS headers. |

```toml
[api]
enabled = true
bind = "127.0.0.1"
port = 8080
token = "my-secret-token"
allowed_origins = ["http://localhost:3000"]
```

::: warning
The API token grants full control over your Nimbus instance. Keep it secret. If you expose the API beyond localhost, always use a strong token and consider placing it behind a reverse proxy with TLS.
:::

::: tip
The `/api/health` endpoint is always public (no authentication required) and can be used for external health checks.
:::

---

## `[java]`

Java installation paths for different versions. Nimbus uses these to launch servers that require specific Java versions.

**If a path is empty, Nimbus will auto-detect installed JDKs** by scanning environment variables (`JAVA_8_HOME`, `JAVA_17_HOME`, etc.), `JAVA_HOME`, and common directories (`/usr/lib/jvm`, `~/.sdkman/candidates/java`, `~/.jdks`). **If no compatible JDK is found, Nimbus downloads one from [Adoptium](https://adoptium.net/) automatically** and caches it in the `jdks/` directory.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `java_8` | String | `""` | Path to Java 8 binary. For legacy servers (1.8.x - 1.16.x). |
| `java_11` | String | `""` | Path to Java 11 binary. For servers 1.8.x - 1.12.x (upper limit). |
| `java_16` | String | `""` | Path to Java 16 binary. For Minecraft 1.17.x. |
| `java_17` | String | `""` | Path to Java 17 binary. For Minecraft 1.18.x - 1.20.4. |
| `java_21` | String | `""` | Path to Java 21 binary. For Minecraft 1.20.5+ and Velocity. Auto-detected if available on PATH. |

```toml
[java]
java_8 = "/usr/lib/jvm/java-8-openjdk/bin/java"
java_17 = "/usr/lib/jvm/java-17-openjdk/bin/java"
java_21 = "/usr/lib/jvm/java-21-openjdk/bin/java"
```

### Resolution order

When starting a server, Nimbus resolves the Java executable in this order:

1. **Per-group** `java_path` override (from the [group config](/config/groups))
2. **Configured paths** from this `[java]` section
3. **Auto-detected** installations on the system
4. **Auto-downloaded** JDK from Adoptium (cached in `jdks/`)

::: tip
You can leave all `[java]` paths empty. Nimbus will find or download what it needs. Only set explicit paths if you want to pin a specific JDK distribution (e.g., GraalVM instead of Temurin).
:::

::: info
For a detailed explanation of Java version requirements per Minecraft version, see [Automatic JDK Management](/guide/concepts#automatic-jdk-management).
:::
