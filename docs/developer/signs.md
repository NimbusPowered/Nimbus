# Signs Plugin

The Nimbus Signs plugin adds clickable server signs to lobby and hub servers. Players can click a sign to join a specific server or be routed to the best available server in a group.

## Installation

The signs plugin is bundled with Nimbus but **not auto-deployed** to servers. It's extracted to the `plugins/` directory at the Nimbus root for convenience:

```
plugins/
  nimbus-sdk.jar        # Auto-deployed (you don't need this copy)
  nimbus-signs.jar      # Copy this to your lobby server's plugins/
```

To install, copy `nimbus-signs.jar` into the `plugins/` folder of the template for your lobby group:

```
templates/
  Lobby/
    plugins/
      nimbus-signs.jar
```

::: tip
Once placed in a template directory, Nimbus will auto-update the signs plugin JAR on every boot, keeping it in sync with your Nimbus version.
:::

The signs plugin **requires the SDK plugin** (`nimbus-sdk.jar`) to be present. The SDK is auto-deployed to all backend servers, so this is already handled.

## Commands

The command is `/nsign`. All subcommands require the player to be looking at a sign block within 5 blocks.

| Command | Permission | Description |
|---|---|---|
| `/nsign set <target> [strategy]` | `nimbus.signs.create` | Create a Nimbus sign |
| `/nsign remove` | `nimbus.signs.remove` | Remove the sign you're looking at |
| `/nsign list` | `nimbus.signs.create` | List all configured signs |
| `/nsign reload` | `nimbus.signs.reload` | Reload sign configuration |

### Creating signs

```
/nsign set BedWars
/nsign set BedWars least
/nsign set BedWars fill
/nsign set BedWars random
/nsign set BedWars-1
```

The `target` can be either a **group name** or a **service name**:

| Target | Behavior |
|---|---|
| `BedWars` | Routes to the best available server in the group using the specified strategy |
| `BedWars-1` | Connects directly to the specific service |

Service targets are detected automatically when the name matches the `*-N` pattern.

### Routing strategies

| Strategy | Keyword | Description |
|---|---|---|
| Least players | `least` (default) | Send to the server with the fewest players |
| Fill first | `fill` | Send to the server with the most players |
| Random | `random` | Send to a random server |

## Sign display

Each sign shows 4 lines with dynamic content. The display is customizable through the [Display system](/config/display).

### Default layout

```
+---------------------+
|  * BedWars *        |  Line 1: Name
|  24 playing         |  Line 2: Player count
|  3 server(s)        |  Line 3: Server count or state
|  > Click to join!   |  Line 4: Status (online/offline)
+---------------------+
```

When the target is offline:

```
+---------------------+
|  * BedWars *        |
|  0 playing          |
|  0 server(s)        |
|  x Offline          |  Line 4 changes
+---------------------+
```

### Placeholders

| Placeholder | Description |
|---|---|
| `{name}` / `{target}` | Target name (group or service) |
| `{players}` | Current player count |
| `{max_players}` | Maximum players |
| `{servers}` | Number of running services (group targets) |
| `{state}` | Current state (resolved through display config) |

### Display config integration

Signs pull their line templates and state labels from the [Display system](/config/display). Each group can have custom sign lines defined in `displays/<group>.toml`:

```toml
[sign]
line1 = "&1&l* {name} *"
line2 = "&8{players} playing"
line3 = "&8{servers} server(s)"
line4_online = "&2> Click to join!"
line4_offline = "&4x Offline"

[states]
READY = "&aOnline"
INGAME = "&6In Game"
ENDING = "&cEnding"
STOPPED = "&4Offline"
```

State labels are resolved through `NimbusDisplay.resolveState()` -- the raw state (e.g., `"INGAME"`) is mapped to a display label (e.g., `"In Game"`).

## Click behavior

When a player clicks a Nimbus sign:

**Group target** (e.g., `BedWars`):
1. Find all routable services in the group (READY + no custom state)
2. Apply the routing strategy to select the best server
3. Send the player via the Nimbus API

**Service target** (e.g., `BedWars-1`):
1. Check if the service is online and READY
2. Send the player directly to that service

## Sign updates

Signs are refreshed periodically by the `SignManager`. On each update cycle:

1. Query the `ServiceCache` for current service data
2. Fetch display configs from the Nimbus API
3. Replace placeholders with live values
4. Update the sign block text using Bukkit's Adventure API

Updates run on the main server thread (via `Scheduler.runTask()`) to ensure thread safety with Bukkit's block API.

## Data storage

Sign locations and configurations are stored in the plugin's `config.yml`:

```yaml
signs:
  bedwars-100-64-200:
    target: BedWars
    service: false
    strategy: LEAST_PLAYERS
    world: world
    x: 100
    y: 64
    z: 200
    line1: "&1&l* BedWars *"
    line2: "&8{players} playing"
    line3: "&8{servers} server(s)"
    line4_online: "&2> Click to join!"
    line4_offline: "&4x Offline"
```

The sign ID is generated from the target name and block coordinates.

## Permissions

| Permission | Description |
|---|---|
| `nimbus.signs.create` | Create and list Nimbus signs |
| `nimbus.signs.remove` | Remove Nimbus signs |
| `nimbus.signs.reload` | Reload sign configuration |

## Next steps

- [SDK](/developer/sdk) -- Backend server plugin API
- [Display Config](/config/display) -- Customize sign appearance
- [Server Groups](/guide/server-groups) -- Group configuration
