# Permission System

Nimbus includes a built-in permission system that manages groups, player assignments, and permission inheritance. Permissions are stored as TOML files in the `permissions/` directory and are automatically synced to the Velocity proxy and backend servers via the API.

## Overview

- **Permission groups** define sets of permissions with optional inheritance
- **Players** are assigned to one or more groups by UUID
- **Wildcards** and **negation** are supported
- A **default group** applies to all players automatically
- **Display properties** (prefix, suffix, priority) control chat formatting

---

## Storage Format

### Group Files

Each permission group is stored as a separate TOML file in `permissions/` (e.g., `permissions/admin.toml`):

```toml
[group]
name = "Admin"
default = false
prefix = "&c[Admin] "
suffix = ""
priority = 100

[group.permissions]
list = [
    "*",
]

[group.inheritance]
parents = ["Moderator"]
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | String | *required* | Group name. Must not be blank. |
| `default` | Boolean | `false` | Whether this group applies to all players automatically. Only one group should be default. |
| `prefix` | String | `""` | Chat prefix. Supports `&` color codes. |
| `suffix` | String | `""` | Chat suffix. Supports `&` color codes. |
| `priority` | Int | `0` | Display priority. Higher values take precedence when a player is in multiple groups. |
| `list` | List\<String\> | `[]` | Permission nodes granted by this group. Prefix with `-` to negate. |
| `parents` | List\<String\> | `[]` | Parent groups to inherit permissions from. |

### Player Assignments

All player-to-group assignments are stored in `permissions/players.toml`:

```toml
["550e8400-e29b-41d4-a716-446655440000"]
name = "Steve"
groups = ["Admin"]

["6ba7b810-9dad-11d1-80b4-00c04fd430c8"]
name = "Alex"
groups = ["Moderator", "Builder"]
```

Each entry is keyed by the player's UUID and contains:

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Player's display name (auto-updated on join). |
| `groups` | List\<String\> | Groups the player is explicitly assigned to. |

---

## Permission Resolution

When checking a player's permissions, Nimbus resolves them in this order:

1. **Default group** permissions (applies to everyone)
2. **Parent groups** are resolved recursively (depth-first, parent before child)
3. **Player's assigned groups** are applied on top
4. **Negated permissions** (prefixed with `-`) are removed from the final set

### Inheritance Chain

```
Default
  +-- Moderator (inherits Default)
        +-- Admin (inherits Moderator)
```

If a player is in the `Admin` group:
- Default permissions are loaded first
- Moderator permissions are layered on top
- Admin permissions are layered last (child overrides parent)
- Any `-permission.node` entries remove that node from the final set

::: info
Cycle detection is built in. If group A inherits from B which inherits from A, the cycle is broken and each group is only processed once.
:::

### Wildcard Matching

Permissions support wildcard patterns at any level:

| Pattern | Matches |
|---------|---------|
| `*` | Everything |
| `nimbus.*` | `nimbus.cloud.list`, `nimbus.cloud.start`, etc. |
| `nimbus.cloud.*` | `nimbus.cloud.list`, `nimbus.cloud.start` |

### Negation

Prefix a permission with `-` to explicitly deny it:

```toml
[group.permissions]
list = [
    "nimbus.*",
    "-nimbus.cloud.shutdown",
]
```

This grants all `nimbus.*` permissions except `nimbus.cloud.shutdown`.

---

## Console Commands

All permission management is available through the Nimbus console.

### Group Commands

| Command | Description |
|---------|-------------|
| `perms group list` | List all permission groups |
| `perms group info <name>` | Show group details, permissions, and parents |
| `perms group create <name>` | Create a new permission group |
| `perms group delete <name>` | Delete a group (removes from all players) |
| `perms group addperm <group> <permission>` | Add a permission node to a group |
| `perms group removeperm <group> <permission>` | Remove a permission node from a group |
| `perms group setdefault <group> [true/false]` | Set a group as the default (clears default from other groups) |
| `perms group addparent <group> <parent>` | Add a parent group for inheritance |
| `perms group removeparent <group> <parent>` | Remove a parent group |
| `perms group setprefix <group> <prefix...>` | Set the display prefix (supports spaces) |
| `perms group setsuffix <group> <suffix...>` | Set the display suffix (supports spaces) |
| `perms group setpriority <group> <number>` | Set the display priority |

### User Commands

| Command | Description |
|---------|-------------|
| `perms user list` | List all player assignments |
| `perms user info <name\|uuid>` | Show player's groups, effective permissions, and display info |
| `perms user addgroup <name\|uuid> <group>` | Assign a group to a player |
| `perms user removegroup <name\|uuid> <group>` | Remove a group from a player |

### Other

| Command | Description |
|---------|-------------|
| `perms reload` | Reload all permission data from files |

::: tip
Players can be identified by name (if they've joined before) or UUID. For first-time assignments to players who haven't connected yet, use their UUID.
:::

---

## API Endpoints

The permission system is fully accessible via the REST API. All endpoints require bearer token authentication.

### Groups

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/permissions/groups` | List all permission groups |
| `GET` | `/api/permissions/groups/{name}` | Get a specific group |
| `POST` | `/api/permissions/groups` | Create a new group |
| `PUT` | `/api/permissions/groups/{name}` | Update a group (permissions, parents, display) |
| `DELETE` | `/api/permissions/groups/{name}` | Delete a group |
| `POST` | `/api/permissions/groups/{name}/permissions` | Add a permission to a group |
| `DELETE` | `/api/permissions/groups/{name}/permissions` | Remove a permission from a group |

### Players

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/permissions/players/{uuid}` | Get player permissions and display info |
| `PUT` | `/api/permissions/players/{uuid}` | Register/update a player (called on join) |
| `POST` | `/api/permissions/players/{uuid}/groups` | Add a group to a player |
| `DELETE` | `/api/permissions/players/{uuid}/groups` | Remove a group from a player |

### Permission Check

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/permissions/check/{uuid}/{permission}` | Check if a player has a specific permission |

---

## Velocity Integration

The `nimbus-bridge` Velocity plugin includes `NimbusPermissionProvider`, which loads permissions from the Nimbus API and integrates with Velocity's permission system.

### How It Works

1. When a player connects, the bridge calls `PUT /api/permissions/players/{uuid}` to register them and fetch effective permissions
2. Permissions are cached in memory for fast `hasPermission()` lookups
3. Wildcard matching works identically to the core system
4. Permissions can be refreshed at any time via `invalidate()` or `refresh()`

The provider is automatically registered with Velocity and handles all permission checks transparently.

---

## Backend Integration (SDK)

The `nimbus-sdk` Bukkit plugin provides `NimbusPermissible`, which injects custom permission handling into Paper/Purpur servers.

### How It Works

1. On player join, the SDK fetches effective permissions from the Nimbus API
2. A custom `NimbusPermissible` replaces Bukkit's default `PermissibleBase` via reflection
3. All `hasPermission()` checks go through Nimbus first, falling back to Bukkit's default system
4. Wildcard matching is supported at all levels

This means plugins that use standard Bukkit permission checks (`player.hasPermission("...")`) work with Nimbus permissions without any modifications.

---

## Example Setup

### Step 1: Create Groups

```
perms group create Admin
perms group create Moderator
perms group create Default
perms group setdefault Default
```

### Step 2: Set Up Inheritance

```
perms group addparent Moderator Default
perms group addparent Admin Moderator
```

### Step 3: Add Permissions

```
perms group addperm Default nimbus.hub
perms group addperm Default nimbus.play

perms group addperm Moderator nimbus.cloud.list
perms group addperm Moderator nimbus.cloud.info
perms group addperm Moderator essentials.kick
perms group addperm Moderator essentials.mute

perms group addperm Admin *
```

### Step 4: Configure Display

```
perms group setprefix Default "&7"
perms group setprefix Moderator "&a[Mod] "
perms group setprefix Admin "&c[Admin] "
perms group setpriority Default 0
perms group setpriority Moderator 50
perms group setpriority Admin 100
```

### Step 5: Assign Players

```
perms user addgroup Steve Admin
perms user addgroup Alex Moderator
```

### Result

- **Steve**: Has `*` (all permissions), prefix `&c[Admin] `
- **Alex**: Has Moderator + Default permissions, prefix `&a[Mod] `
- **New players**: Get Default group automatically, prefix `&7`
