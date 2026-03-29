# Event Reference

All events in Nimbus are subclasses of `NimbusEvent`. Events are emitted through the `EventBus` and delivered to:

- WebSocket clients connected to `/api/events`
- Internal subscribers (scaling engine, console logger, proxy sync)

Every event includes a `timestamp` field (ISO 8601 format).

## WebSocket Message Format

Events are serialized as JSON with this structure:

```json
{
  "type": "EVENT_TYPE",
  "timestamp": "2025-01-15T10:30:15.123Z",
  "data": {
    "key": "value"
  }
}
```

---

## Service Events

### SERVICE_STARTING

Emitted when a service process is launched.

| Field | Type | Description |
|-------|------|-------------|
| `service` | string | Service name (e.g., `Lobby-1`) |
| `group` | string | Group name (e.g., `Lobby`) |
| `port` | string | Assigned port number |

```json
{
  "type": "SERVICE_STARTING",
  "timestamp": "2025-01-15T10:30:00.000Z",
  "data": {
    "service": "Lobby-1",
    "group": "Lobby",
    "port": "30001"
  }
}
```

---

### SERVICE_READY

Emitted when the service's stdout matches the ready pattern (e.g., Minecraft's `Done` message).

| Field | Type | Description |
|-------|------|-------------|
| `service` | string | Service name |
| `group` | string | Group name |

```json
{
  "type": "SERVICE_READY",
  "timestamp": "2025-01-15T10:30:15.000Z",
  "data": {
    "service": "Lobby-1",
    "group": "Lobby"
  }
}
```

---

### SERVICE_STOPPING

Emitted when a graceful shutdown is initiated for a service.

| Field | Type | Description |
|-------|------|-------------|
| `service` | string | Service name |

```json
{
  "type": "SERVICE_STOPPING",
  "timestamp": "2025-01-15T12:00:00.000Z",
  "data": {
    "service": "Lobby-1"
  }
}
```

---

### SERVICE_STOPPED

Emitted when a service process exits cleanly.

| Field | Type | Description |
|-------|------|-------------|
| `service` | string | Service name |

```json
{
  "type": "SERVICE_STOPPED",
  "timestamp": "2025-01-15T12:00:05.000Z",
  "data": {
    "service": "Lobby-1"
  }
}
```

---

### SERVICE_CRASHED

Emitted when a service process exits unexpectedly.

| Field | Type | Description |
|-------|------|-------------|
| `service` | string | Service name |
| `exitCode` | string | Process exit code |
| `restartAttempt` | string | Current restart attempt number |

```json
{
  "type": "SERVICE_CRASHED",
  "timestamp": "2025-01-15T11:45:00.000Z",
  "data": {
    "service": "BedWars-2",
    "exitCode": "1",
    "restartAttempt": "2"
  }
}
```

::: tip
If `restartOnCrash` is enabled for the group and `restartAttempt` is below `maxRestarts`, Nimbus will automatically restart the service.
:::

---

### SERVICE_CUSTOM_STATE_CHANGED

Emitted when a service's custom state is changed via the API (typically by game plugins).

| Field | Type | Description |
|-------|------|-------------|
| `service` | string | Service name |
| `group` | string | Group name |
| `oldState` | string? | Previous custom state (absent if null) |
| `newState` | string? | New custom state (absent if null) |

```json
{
  "type": "SERVICE_CUSTOM_STATE_CHANGED",
  "timestamp": "2025-01-15T11:00:00.000Z",
  "data": {
    "service": "BedWars-1",
    "group": "BedWars",
    "oldState": "WAITING",
    "newState": "INGAME"
  }
}
```

---

### SERVICE_MESSAGE

Emitted for service-to-service messaging. The `data` map includes the sender, recipient, channel, and any custom data fields.

| Field | Type | Description |
|-------|------|-------------|
| `from` | string | Sender service name |
| `to` | string | Recipient service name |
| `channel` | string | Message channel |
| *(custom)* | string | Additional data fields from the message payload |

```json
{
  "type": "SERVICE_MESSAGE",
  "timestamp": "2025-01-15T11:30:00.000Z",
  "data": {
    "from": "BedWars-1",
    "to": "Lobby-1",
    "channel": "game_end",
    "winner": "Red",
    "duration": "320"
  }
}
```

---

## Scaling Events

### SCALE_UP

Emitted when the scaling engine starts a new instance to handle load.

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Group name |
| `from` | string | Instance count before scaling |
| `to` | string | Target instance count |
| `reason` | string | Human-readable scaling reason |

```json
{
  "type": "SCALE_UP",
  "timestamp": "2025-01-15T14:00:00.000Z",
  "data": {
    "group": "BedWars",
    "from": "2",
    "to": "3",
    "reason": "Player threshold exceeded (85% of 16 per instance)"
  }
}
```

---

### SCALE_DOWN

Emitted when the scaling engine stops an idle instance.

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Group name |
| `service` | string | Service being stopped |
| `reason` | string | Human-readable reason |

```json
{
  "type": "SCALE_DOWN",
  "timestamp": "2025-01-15T15:00:00.000Z",
  "data": {
    "group": "BedWars",
    "service": "BedWars-3",
    "reason": "Idle timeout reached (300s with 0 players)"
  }
}
```

---

## Player Events

### PLAYER_CONNECTED

Emitted when a player connects to a service.

| Field | Type | Description |
|-------|------|-------------|
| `player` | string | Player name |
| `service` | string | Service name |

```json
{
  "type": "PLAYER_CONNECTED",
  "timestamp": "2025-01-15T11:15:00.000Z",
  "data": {
    "player": "Steve",
    "service": "Lobby-1"
  }
}
```

---

### PLAYER_DISCONNECTED

Emitted when a player disconnects from a service.

| Field | Type | Description |
|-------|------|-------------|
| `player` | string | Player name |
| `service` | string | Service name |

```json
{
  "type": "PLAYER_DISCONNECTED",
  "timestamp": "2025-01-15T11:45:00.000Z",
  "data": {
    "player": "Steve",
    "service": "Lobby-1"
  }
}
```

---

## Group Events

### GROUP_CREATED

Emitted when a new server group is created (via console `create` command or API).

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Group name |

```json
{
  "type": "GROUP_CREATED",
  "timestamp": "2025-01-15T09:00:00.000Z",
  "data": {
    "group": "SkyWars"
  }
}
```

---

### GROUP_UPDATED

Emitted when a group's configuration is modified.

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Group name |

```json
{
  "type": "GROUP_UPDATED",
  "timestamp": "2025-01-15T09:30:00.000Z",
  "data": {
    "group": "SkyWars"
  }
}
```

---

### GROUP_DELETED

Emitted when a group is deleted.

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Group name |

```json
{
  "type": "GROUP_DELETED",
  "timestamp": "2025-01-15T10:00:00.000Z",
  "data": {
    "group": "SkyWars"
  }
}
```

---

## Permission Events

### PERMISSION_GROUP_CREATED

Emitted when a new permission group is created.

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Permission group name |

```json
{
  "type": "PERMISSION_GROUP_CREATED",
  "timestamp": "2025-01-15T09:00:00.000Z",
  "data": {
    "group": "vip"
  }
}
```

---

### PERMISSION_GROUP_UPDATED

Emitted when a permission group is modified (permissions added/removed, default changed, display updated, parents changed).

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Permission group name |

```json
{
  "type": "PERMISSION_GROUP_UPDATED",
  "timestamp": "2025-01-15T09:15:00.000Z",
  "data": {
    "group": "vip"
  }
}
```

---

### PERMISSION_GROUP_DELETED

Emitted when a permission group is deleted.

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Permission group name |

```json
{
  "type": "PERMISSION_GROUP_DELETED",
  "timestamp": "2025-01-15T09:30:00.000Z",
  "data": {
    "group": "vip"
  }
}
```

---

### PLAYER_PERMISSIONS_UPDATED

Emitted when a player's group assignments change.

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | string | Player UUID |
| `player` | string | Player name |

```json
{
  "type": "PLAYER_PERMISSIONS_UPDATED",
  "timestamp": "2025-01-15T11:00:00.000Z",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "player": "Steve"
  }
}
```

---

## Proxy Events

### PROXY_UPDATE_AVAILABLE

Emitted when a newer version of the Velocity proxy is detected.

| Field | Type | Description |
|-------|------|-------------|
| `currentVersion` | string | Currently installed version |
| `newVersion` | string | Available version |

```json
{
  "type": "PROXY_UPDATE_AVAILABLE",
  "timestamp": "2025-01-15T08:00:00.000Z",
  "data": {
    "currentVersion": "3.3.0",
    "newVersion": "3.4.0"
  }
}
```

---

### PROXY_UPDATE_APPLIED

Emitted after a proxy update has been applied.

| Field | Type | Description |
|-------|------|-------------|
| `oldVersion` | string | Previous version |
| `newVersion` | string | New version |

```json
{
  "type": "PROXY_UPDATE_APPLIED",
  "timestamp": "2025-01-15T08:05:00.000Z",
  "data": {
    "oldVersion": "3.3.0",
    "newVersion": "3.4.0"
  }
}
```

---

### TABLIST_UPDATED

Emitted when the tab list configuration is changed.

| Field | Type | Description |
|-------|------|-------------|
| `header` | string | Tab list header |
| `footer` | string | Tab list footer |
| `playerFormat` | string | Player display format |
| `updateInterval` | string | Update interval in seconds |

```json
{
  "type": "TABLIST_UPDATED",
  "timestamp": "2025-01-15T09:00:00.000Z",
  "data": {
    "header": "&b&lMyNetwork\n&7Online: %online%",
    "footer": "&7play.mynetwork.com",
    "playerFormat": "%prefix%%player%%suffix%",
    "updateInterval": "5"
  }
}
```

---

### MOTD_UPDATED

Emitted when the MOTD configuration is changed.

| Field | Type | Description |
|-------|------|-------------|
| `line1` | string | First MOTD line |
| `line2` | string | Second MOTD line |
| `maxPlayers` | string | Max player count shown |
| `playerCountOffset` | string | Offset added to player count |

```json
{
  "type": "MOTD_UPDATED",
  "timestamp": "2025-01-15T09:00:00.000Z",
  "data": {
    "line1": "&b&lMyNetwork",
    "line2": "&aSeason 3 is live!",
    "maxPlayers": "500",
    "playerCountOffset": "10"
  }
}
```

---

### PLAYER_TAB_UPDATED

Emitted when a player's individual tab list format is set or cleared.

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | string | Player UUID |
| `format` | string? | New format (absent when cleared) |

```json
{
  "type": "PLAYER_TAB_UPDATED",
  "timestamp": "2025-01-15T11:00:00.000Z",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "format": "&c[Owner] &f%player%"
  }
}
```

---

### CHAT_FORMAT_UPDATED

Emitted when the chat format configuration is changed.

| Field | Type | Description |
|-------|------|-------------|
| `format` | string | Chat message format |
| `enabled` | string | Whether chat formatting is enabled (`true`/`false`) |

```json
{
  "type": "CHAT_FORMAT_UPDATED",
  "timestamp": "2025-01-15T09:00:00.000Z",
  "data": {
    "format": "%prefix%%player%%suffix% &8>> &f%message%",
    "enabled": "true"
  }
}
```

---

## System Events

### CONFIG_RELOADED

Emitted when group configurations are reloaded (via console `reload` or API).

| Field | Type | Description |
|-------|------|-------------|
| `groupsLoaded` | string | Number of group configs loaded |

```json
{
  "type": "CONFIG_RELOADED",
  "timestamp": "2025-01-15T10:00:00.000Z",
  "data": {
    "groupsLoaded": "3"
  }
}
```

---

### API_STARTED

Emitted when the REST API server starts.

| Field | Type | Description |
|-------|------|-------------|
| `bind` | string | Bind address |
| `port` | string | Port number |

```json
{
  "type": "API_STARTED",
  "timestamp": "2025-01-15T08:00:00.000Z",
  "data": {
    "bind": "0.0.0.0",
    "port": "8080"
  }
}
```

---

### API_STOPPED

Emitted when the REST API server stops.

| Field | Type | Description |
|-------|------|-------------|
| `reason` | string | Reason for stopping |

```json
{
  "type": "API_STOPPED",
  "timestamp": "2025-01-15T20:00:00.000Z",
  "data": {
    "reason": "manual stop"
  }
}
```

---

### API_WARNING

Emitted for API-related warnings (e.g., no auth token configured).

| Field | Type | Description |
|-------|------|-------------|
| `message` | string | Warning message |

```json
{
  "type": "API_WARNING",
  "timestamp": "2025-01-15T08:00:00.000Z",
  "data": {
    "message": "No auth token set — API is open! Set [api] token in nimbus.toml"
  }
}
```

---

### API_ERROR

Emitted when the API encounters a startup or runtime error.

| Field | Type | Description |
|-------|------|-------------|
| `error` | string | Error message |

```json
{
  "type": "API_ERROR",
  "timestamp": "2025-01-15T08:00:00.000Z",
  "data": {
    "error": "Failed to start: Address already in use"
  }
}
```
