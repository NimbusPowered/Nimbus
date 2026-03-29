# Display System

The display system controls how server groups appear on signs and NPCs in your lobby. Each non-proxy group gets an auto-generated display config in the `displays/` directory that you can customize.

## Overview

- Display configs are stored as TOML files in `displays/` (e.g., `displays/BedWars.toml`)
- Configs are auto-generated for new groups (proxy groups are excluded)
- Placeholders like `{name}`, `{players}`, and `{state}` are replaced at runtime
- State labels map internal states (e.g., `READY`) to display-friendly names (e.g., `ONLINE`)

---

## Configuration File

Each display config has three sections: sign layout, NPC appearance, and state labels.

### Complete Example

```toml
[display]
name = "BedWars"

[display.sign]
line1 = "&1&l★ BedWars ★"
line2 = "&8{players}/{max_players} online"
line3 = "&7{state}"
line4_online = "&2▶ Click to join!"
line4_offline = "&4✖ Offline"

[display.npc]
display_name = "&b&lBedWars"
item = "RED_BED"
subtitle = "&7{players}/{max_players} online &8| &7{state}"
subtitle_offline = "&c✖ Offline"

[display.states]
PREPARING = "STARTING"
STARTING = "STARTING"
READY = "ONLINE"
STOPPING = "STOPPING"
STOPPED = "OFFLINE"
CRASHED = "OFFLINE"
WAITING = "WAITING"
INGAME = "INGAME"
ENDING = "ENDING"
```

---

## `[display.sign]`

Controls the four lines of a Minecraft sign.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `line1` | String | `"&1&l★ {name} ★"` | First line of the sign. Typically the group name. |
| `line2` | String | `"&8{players}/{max_players} online"` | Second line. Usually player count. |
| `line3` | String | `"&7{state}"` | Third line. Usually the server state. |
| `line4_online` | String | `"&2▶ Click to join!"` | Fourth line when the group has at least one READY instance. |
| `line4_offline` | String | `"&4✖ Offline"` | Fourth line when no instances are available. |

---

## `[display.npc]`

Controls NPC appearance in the lobby.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `display_name` | String | `"&b&l{name}"` | NPC name displayed above its head. |
| `item` | String | `"GRASS_BLOCK"` | Minecraft material name for the item the NPC holds. |
| `subtitle` | String | `"&7{players}/{max_players} online &8\| &7{state}"` | Subtitle shown below the NPC when online. |
| `subtitle_offline` | String | `"&c✖ Offline"` | Subtitle shown when no instances are available. |

### Auto-Detected Items

When Nimbus generates a display config, it guesses a fitting NPC item based on the group name:

| Group Name Contains | Item |
|--------------------|------|
| `bedwar` | `RED_BED` |
| `skywar` | `EYE_OF_ENDER` |
| `skyblock` | `GRASS_BLOCK` |
| `survival` | `DIAMOND_PICKAXE` |
| `creative` | `PAINTING` |
| `lobby` | `NETHER_STAR` |
| `practice` | `IRON_SWORD` |
| `pvp` | `DIAMOND_SWORD` |
| `build` | `BRICKS` |
| `prison` | `IRON_BARS` |
| `faction` | `TNT` |
| `kitpvp` | `GOLDEN_APPLE` |
| `duels` | `BOW` |
| `murder` | `IRON_SWORD` |
| `tnt` | `TNT` |
| `party` | `CAKE` |
| Fabric server | `CRAFTING_TABLE` |
| Forge server | `ANVIL` |
| *default* | `GRASS_BLOCK` |

---

## Placeholders

The following placeholders can be used in any sign or NPC text field:

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{name}` | Group name | `BedWars` |
| `{players}` | Total online players across all instances | `47` |
| `{max_players}` | Max players per instance (from group config) | `16` |
| `{servers}` | Number of running instances | `3` |
| `{state}` | Resolved state label (see below) | `ONLINE` |

---

## `[display.states]`

Maps internal service states to display-friendly labels. You can customize these to match your network's branding.

### Default State Labels

| Internal State | Default Label | Description |
|---------------|---------------|-------------|
| `PREPARING` | `STARTING` | Service directory being prepared from template |
| `STARTING` | `STARTING` | Server process launched, waiting for ready signal |
| `READY` | `ONLINE` | Server is ready and accepting players |
| `STOPPING` | `STOPPING` | Server is shutting down gracefully |
| `STOPPED` | `OFFLINE` | Server process has exited |
| `CRASHED` | `OFFLINE` | Server process exited unexpectedly |

### Custom States

Plugins on your backend servers can set custom states via the Nimbus SDK (e.g., `WAITING`, `INGAME`, `ENDING`). These are also resolved through the state label map:

| Custom State | Default Label | Typical Use |
|-------------|---------------|-------------|
| `WAITING` | `WAITING` | Game lobby, waiting for players |
| `INGAME` | `INGAME` | Match in progress |
| `ENDING` | `ENDING` | Match ending, showing results |

You can add your own custom states:

```toml
[display.states]
READY = "ONLINE"
WAITING = "Waiting for players..."
INGAME = "In Progress"
ENDING = "Finishing Up"
RESTARTING = "Restarting Soon"
MAINTENANCE = "Under Maintenance"
```

::: info
Services with custom states like `INGAME` or `ENDING` are excluded from the scaling engine's capacity calculations. They won't accept new players and don't count toward the fill rate.
:::

---

## Auto-Generation

When groups are loaded, Nimbus checks the `displays/` directory and creates a default display config for any group that doesn't have one. Proxy groups (Velocity) are skipped since they don't appear on signs or NPCs.

The auto-generated config uses:
- The group name for all display text
- `max_players` from the group's resource config
- An auto-detected NPC item based on the group name
- Default state labels

To regenerate a display config, delete its file from `displays/` and run the `reload` command.

---

## API Endpoints

Display configs are accessible via the REST API. All endpoints require bearer token authentication.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/displays` | List all display configs |
| `GET` | `/api/displays/{name}` | Get display config for a specific group |
| `GET` | `/api/displays/{name}/state/{state}` | Resolve a state label for a group |

### Example Response

```json
GET /api/displays/BedWars
```

```json
{
  "name": "BedWars",
  "sign": {
    "line1": "&1&l★ BedWars ★",
    "line2": "&8{players}/16 online",
    "line3": "&7{state}",
    "line4Online": "&2▶ Click to join!",
    "line4Offline": "&4✖ Offline"
  },
  "npc": {
    "displayName": "&b&lBedWars",
    "item": "RED_BED",
    "subtitle": "&7{players}/16 online &8| &7{state}",
    "subtitleOffline": "&c✖ Offline"
  },
  "states": {
    "PREPARING": "STARTING",
    "STARTING": "STARTING",
    "READY": "ONLINE",
    "STOPPING": "STOPPING",
    "STOPPED": "OFFLINE",
    "CRASHED": "OFFLINE",
    "WAITING": "WAITING",
    "INGAME": "INGAME",
    "ENDING": "ENDING"
  }
}
```

---

## Signs Plugin

To use sign displays in your lobby, install the `nimbus-signs` plugin on your Paper/Purpur lobby servers. The plugin:

1. Connects to the Nimbus API to fetch display configs and live service data
2. Updates signs at regular intervals with current player counts and states
3. Handles player clicks to send them to the appropriate server group
4. Switches between `line4_online` and `line4_offline` based on group availability

::: tip
Place signs in your lobby world and use the setup command provided by nimbus-signs to link them to a group. The sign text is automatically formatted using the display config.
:::
