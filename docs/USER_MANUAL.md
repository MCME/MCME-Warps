# MCME-Warps — User Manual

For server operators and staff. This covers every command, permission, and configuration option, plus
Dynmap setup and common workflows. For architecture and contribution notes, see the
[Developer Manual](DEV_MANUAL.md).

## Contents

- [Concepts](#concepts)
- [Warp types & access](#warp-types--access)
- [Command reference](#command-reference)
- [Listing & searching](#listing--searching)
- [Permissions](#permissions)
- [Private-warp limits](#private-warp-limits)
- [Warp names](#warp-names)
- [Welcome messages](#welcome-messages)
- [Dynmap: icons & layers](#dynmap-icons--layers)
- [Configuration](#configuration)
- [Visit counts](#visit-counts)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

---

## Concepts

- **The proxy owns the data.** All warps live on the Velocity proxy as YAML files. Backends only render
  and execute; they never hold the authoritative copy. Edit warps through in-game commands, not by hand.
- **Warps are network-wide.** Each warp records the server and world it points at. `/warp <name>` moves
  the player to the right backend automatically, even from a different server.
- **Public vs private.** Public warps form a single, network-wide namespace of curated landmarks. Private
  warps belong to individual players and are keyed per-player — two players can each have a warp called
  `home`, and neither collides with a public warp of the same name.

## Warp types & access

| | Public warp | Private warp |
| --- | --- | --- |
| **Namespace** | One shared, network-wide (names are unique) | Per-creator (each player has their own) |
| **Who can teleport to it** | Anyone with `mcmewarps.cmd.warp` **and** `world-access` for its world | The creator, its members, and staff with `override.use` |
| **Who can edit/move/delete it** | Only staff with `mcmewarps.override.modify` | Only the creator (or staff with `override.modify`) |
| **On the map** | Rendered on Dynmap | Not rendered (kept out of the public store) |

Two rules are worth committing to memory:

1. **To use *any* warp in a world, a player needs `mcmewarps.world-access.<world>`** (unless they have
   `override.use`). This is how you scope who can warp into which worlds.
2. **A public warp cannot be modified by a normal player** — even the player who created it. Editing an
   existing public warp requires `override.modify`, so public warps are effectively staff-managed once
   created. Private warps are always editable by their creator.

When a player runs `/warp <name>`, their **own private warp of that name takes priority** over a public
one. So a player with a private `home` always warps to their own; a player without one falls through to a
public `home` if it exists.

## Command reference

Two top-level commands handle teleporting; everything else is a subcommand of `/warpmanager`
(alias `/wmanage`). Command names are **case-sensitive** — type them exactly as shown (e.g. `setPublic`,
not `setpublic`).

### Teleporting

| Command | Alias | Permission | What it does |
| --- | --- | --- | --- |
| `/warp <name>` | `/to <name>` | `mcmewarps.cmd.warp` | Teleport to a warp (your private one shadows a public of the same name). |
| `/warp random` | | `mcmewarps.cmd.random` | Teleport to a random public warp on the main build servers. |
| `/localwarp <name>` | `/lwarp <name>` | `mcmewarps.cmd.local-warp` | Teleport to a warp's coordinates **in your current world**, without switching server or world. Handy for jumping to the same spot across parallel copies of a world. |

### Creating warps

| Command | Permission | What it does |
| --- | --- | --- |
| `/wmanage create <name>` | `mcmewarps.cmd.create-public` | Create a **public** warp at your current location. |
| `/wmanage pcreate <name>` | `mcmewarps.cmd.create-private` | Create a **private** warp at your current location. Counts against your [private-warp limit](#private-warp-limits). |

### Editing warps

You can only target warps you're allowed to modify (your own private warps, or any warp with
`override.modify`).

| Command | Permission | What it does |
| --- | --- | --- |
| `/wmanage move <warp>` | `mcmewarps.cmd.move` | Move the warp to your current location. |
| `/wmanage rename <current> <new>` | `mcmewarps.cmd.rename` | Rename the warp. |
| `/wmanage delete <warp>` | `mcmewarps.cmd.delete` | Delete the warp. |
| `/wmanage setPublic <warp>` | `mcmewarps.cmd.set-public` | Convert a private warp to public. |
| `/wmanage setPrivate <warp>` | `mcmewarps.cmd.set-private` | Convert a public warp to private. |
| `/wmanage setWelcome <warp> <message>` | `mcmewarps.cmd.welcome-message` | Set a message shown on arrival. Pass `default` to clear it. Supports [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting. |
| `/wmanage setIcon <warp> <icon>` | `mcmewarps.cmd.set-icon` | Set the warp's Dynmap icon (see [Icons](#icons)). |
| `/wmanage setLayer <warp> <layer>` | `mcmewarps.cmd.set-layer` | Set the warp's Dynmap layer (see [Layers](#layers)). |

### Private-warp members

Members can *teleport to* a private warp but cannot edit it. Only the warp's creator can manage its
members, and the target player must be **online** at the time.

| Command | Permission | What it does |
| --- | --- | --- |
| `/wmanage members add <warp> <player>` | `mcmewarps.cmd.manage-members` | Add a player as a member. |
| `/wmanage members remove <warp> <player>` | `mcmewarps.cmd.manage-members` | Remove a member. |

### Listing & administration

| Command | Permission | What it does |
| --- | --- | --- |
| `/wmanage list [filters] [page]` | `mcmewarps.cmd.list` | List warps, with optional filters (see below). |
| `/wmanage plist [filters] [page]` | `mcmewarps.cmd.list` | List the private warps you created or are a member of. |
| `/wmanage assets [filters] [page]` | `mcmewarps.cmd.list` | List **your** private warps on your **current** server (a scoped shortcut for `plist`). |
| `/wmanage reload` | `mcmewarps.cmd.reload` | Reload the config and re-read all warps from disk. Also flushes visit counts first. |

## Listing & searching

`/wmanage list` accepts space-separated flags, each `-<flag> <value>`, and an optional page number at the
end (10 warps per page).

| Flag | Meaning | Values |
| --- | --- | --- |
| `-v` | Visibility | `public` (default), `private`, `all` |
| `-c` | Creator | a player name |
| `-n` | Name contains | any text (case/accent-insensitive) |
| `-s` | Server | a server name |
| `-w` | World (optionally `world/server`) | a world name — **cannot** be combined with `-s` |
| `-o` | Ordering | `alphabetical` (default), `createdAt`, `visits` |

Examples:

```text
/wmanage list                        # all public warps, alphabetical
/wmanage list -v all -o visits       # every warp, most-visited first
/wmanage list -c Gandalf -v private  # Gandalf's private warps
/wmanage list -n rivend              # warps whose name contains "rivend"
/wmanage list -w moria 2             # warps in the "moria" world, page 2
```

`plist` always filters to your own private warps, so it rejects the `-v` and `-c` flags. `assets` behaves
like `plist` but also pins the server to the one you're on; it accepts only `-n` and `-o`.

## Permissions

Two things gate every action: the **command permission** (can you run the command at all) and, for
commands that target a warp, **whether you may act on that warp**.

### Command permissions

| Node | Grants |
| --- | --- |
| `mcmewarps.cmd.warp` | `/warp` |
| `mcmewarps.cmd.random` | `/warp random` |
| `mcmewarps.cmd.local-warp` | `/localwarp` |
| `mcmewarps.cmd.create-public` | `/wmanage create` |
| `mcmewarps.cmd.create-private` | `/wmanage pcreate` |
| `mcmewarps.cmd.move` | `/wmanage move` |
| `mcmewarps.cmd.rename` | `/wmanage rename` |
| `mcmewarps.cmd.delete` | `/wmanage delete` |
| `mcmewarps.cmd.set-public` | `/wmanage setPublic` |
| `mcmewarps.cmd.set-private` | `/wmanage setPrivate` |
| `mcmewarps.cmd.set-icon` | `/wmanage setIcon` |
| `mcmewarps.cmd.set-layer` | `/wmanage setLayer` |
| `mcmewarps.cmd.welcome-message` | `/wmanage setWelcome` |
| `mcmewarps.cmd.manage-members` | `/wmanage members` |
| `mcmewarps.cmd.list` | `/wmanage list`, `plist`, `assets` |
| `mcmewarps.cmd.reload` | `/wmanage reload` |

### Access permissions

| Node | Grants |
| --- | --- |
| `mcmewarps.world-access.<world>` | Permission to **teleport to** warps whose location is in `<world>` (lowercase). Required for every world a player may warp into. |
| `mcmewarps.override.use` | Use any warp regardless of world-access, ownership, or membership. |
| `mcmewarps.override.modify` | Edit/move/delete **any** warp, including public ones. The staff "manage warps" permission. |
| `mcmewarps.limits.ignore.private` | Exempt from the private-warp limit. |

> **`world-access` is per world, and world names are the ones the warps record.** Because a single
> permission covers a world across all backends, give each world a unique name so access can be granted
> individually.

### Example permission setups

```text
# A regular builder who may warp around the main worlds and keep private warps
mcmewarps.cmd.warp
mcmewarps.cmd.local-warp
mcmewarps.cmd.create-private
mcmewarps.cmd.list
mcmewarps.world-access.world
mcmewarps.world-access.moria

# Staff who curate public landmarks
mcmewarps.cmd.*
mcmewarps.override.use
mcmewarps.override.modify
mcmewarps.limits.ignore.private
```

## Private-warp limits

Each player may keep a limited number of private warps, set in the proxy config:

```yaml
private-warp-limits:
  default-limit: 2
  configured:
    adventurer: 10   # players with mcmewarps.limits.<key> use the highest matching limit
    commoner: 50
```

- `default-limit` applies to everyone without a more specific entry.
- Each key under `configured` is matched against the player; the **highest** limit they qualify for wins.
- `mcmewarps.limits.ignore.private` removes the limit entirely.

The limit is checked when a player runs `/wmanage pcreate`; converting a public warp to private with
`setPrivate` does not re-check it.

## Warp names

- Names may be up to `warp-name-max-length` characters (default **64**).
- These characters are **not allowed** and will be rejected: `/ \ < > : " | * ? !`
- Names are **normalised** for lookup and matching, so players don't have to be exact:
  - accents are stripped — `Amon Dîn` matches `amon din`
  - apostrophes are ignored — `Helm's Deep` matches `helms deep`
  - runs of spaces collapse to one, and case is ignored
- The stored display name keeps its original spelling; only searching and uniqueness use the normalised
  form.

## Welcome messages

`/wmanage setWelcome <warp> <message>` sets text shown to a player when they arrive. It supports
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags, e.g.:

```text
/wmanage setWelcome rivendell <gold>Welcome to Rivendell, last homely house east of the sea.
```

Pass `default` as the message to remove a custom welcome and fall back to the default.

## Dynmap: icons & layers

If [Dynmap](https://github.com/webbukkit/dynmap) is installed on a backend, public warps for that server
appear as markers. Without Dynmap the plugin still works — markers are simply skipped.

### Icons

Set with `/wmanage setIcon <warp> <icon>`. The built-in choices map to Dynmap marker icons:

| Icon | Dynmap marker |
| --- | --- |
| `DEFAULT` | `greenflag` |
| `WIP` | `construction` |
| `ON_HOLD` | `yellowflag` |
| `NOT_STARTED` | `redflag` |
| `PIRATE` | `pirateflag` |

### Layers

Layers group markers into toggleable sets in the Dynmap sidebar. They're defined in the **backend**
`config.yml` and must be mirrored in the proxy's `layer-keys` so `/wmanage setLayer` knows which names are
valid. See [Configuration](#configuration) below.

## Configuration

### Proxy — `plugins/mcme-warps-velocity/config.yml`

```yaml
# Longest a warp name may be
warp-name-max-length: 64

# How many private warps a player may keep (see "Private-warp limits")
private-warp-limits:
  default-limit: 2
  configured:
#    adventurer: 10
#    commoner: 50

# Valid layer names for /wmanage setLayer. Must match the backend config.yml layers.
layer-keys:
  - major

# Legacy MyWarp database importer — leave commented out. Only used to bulk-import from an old
# MyWarp install when no warp files exist yet; not part of normal operation.
#sql:
#  user:
#  password:
#  db-name:
#  ip:
#  port:
```

### Backend — `plugins/MCME-Warps-Paper/config.yml`

```yaml
debug: false

layers:
  # label   - the name shown in the Dynmap layer control
  # min-zoom - the layer is hidden below this zoom (0 = fully zoomed out); -1 = always shown
  # priority - higher priority appears lower in the layer control list
  default:
    label: "Other warps"
    min-zoom: 2
    priority: 9
  custom:
    major:
      label: "Major warps"
      min-zoom: -1
      priority: 8
```

Every key under `custom` is a layer name that must also appear in the proxy's `layer-keys`. The `default`
layer catches any warp not assigned to a custom layer.

## Visit counts

Each warp tracks how many times it's been used (visible via `/wmanage list -o visits`). To avoid a disk
write on every teleport, counts are held in memory and flushed to disk on:

- proxy shutdown,
- proxy reload,
- `/wmanage reload`.

A hard crash can therefore lose the visit counts accumulated since the last flush. Warp data itself is
never at risk — only the visit tally.

## Deployment

The proxy is the single source of truth. Give each backend access to **its** warps with a symlink from
the backend's warp folder to the matching per-server folder in the proxy store:

```
plugins/MCME-Warps-Paper/warps/   ->   plugins/mcme-warps-velocity/warps/<server-name>/
```

> **Only link the per-server folder.** The proxy store also contains `warps/private-warps/`; linking the
> whole `warps/` directory into a backend would expose players' private-warp coordinates on the public
> Dynmap.

## Troubleshooting

**"You do not have access to warp …"** — the player lacks `mcmewarps.world-access.<world>` for the warp's
world, or it's a private warp they don't own / aren't a member of.

**A player can't edit a public warp they made.** Expected: editing existing public warps requires
`mcmewarps.override.modify`. Public warps are staff-managed after creation.

**`/wmanage setpublic` says unknown command.** Command names are case-sensitive — it's `setPublic`.

**A warp doesn't appear on the map.** Check that Dynmap is installed on that backend, that the backend's
`warps/` symlink points at the right per-server folder, and that the warp's layer exists in the backend
`config.yml`. Private warps never appear on the map by design.

**Two players both have `home` and it "works differently" for each.** Correct — private warps are
per-player. `/warp home` resolves to the caller's own private `home` first, then a public one.

**Changed the config and nothing happened.** Run `/wmanage reload` (or restart the proxy) to re-read it.
