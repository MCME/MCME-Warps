# MCME-Warps

A warp system for a **Velocity + Paper** Minecraft network, with YAML storage and optional
[Dynmap](https://github.com/webbukkit/dynmap) markers. Built for [MC Middle Earth](https://www.mcmiddleearth.com).

Players teleport to named locations (`/warp rivendell`); staff curate public landmarks and players keep
their own private warps. Warps can span servers — a warp on one backend can be reached from anywhere on
the network, with the proxy handling the server switch.

---

## How it works

MCME-Warps ships as **one jar that contains two plugins**:

| Plugin | Runs on | Responsibility |
| --- | --- | --- |
| `MCME-Warps-Velocity` | the Velocity **proxy** | The source of truth. Owns the warp store, all commands, permissions, teleport routing. |
| `MCME-Warps-Paper` | each Paper **backend** | Executes teleports and renders warp markers on Dynmap. Holds no authoritative state. |

The two halves talk over Minecraft plugin-message channels. The proxy stores every warp as a small YAML
file; a backend only ever sees the warps for the world(s) it hosts (via a symlink — see
[Installation](#installation)).

```
Player ──/warp rivendell──▶ Velocity proxy ──(look up warp, switch server if needed)──▶ Paper backend ──teleport──▶ Player
```

## Features

- **Public and private warps.** Public warps are curated, network-unique landmarks; private warps are
  per-player (two players can each have a `home`), with an optional member list.
- **Cross-server teleports.** A warp knows which server and world it lives on; the proxy moves the player
  there transparently.
- **Dynmap integration.** Warps appear as configurable markers grouped into layers, with per-warp icons.
  Entirely optional — the plugin runs fine without Dynmap.
- **Per-group private-warp limits**, per-world access control, and custom welcome messages.
- **Crash-safe storage.** Every change is written to disk before it takes effect in memory, so a crash
  mid-write can never corrupt or lose the store.

## Requirements

- A **Velocity 3.4.x** proxy and one or more **Paper 26.x** backend servers.
- **Java 25** (required by Paper 26.x and by this plugin's build).
- **Dynmap** on the backends — optional, only needed for map markers.

## Installation

1. Drop the same `MCME-Warps-<version>.jar` into **both** `plugins/` folders: the Velocity proxy and
   every Paper backend that should render or execute warps.
2. Start the proxy once to generate `plugins/mcme-warps-velocity/config.yml`, then configure your
   private-warp limits and Dynmap layers (see the [User Manual](docs/USER_MANUAL.md#configuration)).
3. **Share each backend's warps with the proxy by symlink.** The proxy is the source of truth; a backend
   reads its slice of the store through a link, so there is only ever one copy of the data:

   ```
   plugins/MCME-Warps-Paper/warps/   ->   plugins/mcme-warps-velocity/warps/<server-name>/
   ```

   Link the **per-server** folder (`warps/<server-name>/`), **not** the whole `warps/` directory —
   linking the whole thing would publish players' private-warp coordinates on the public map.
4. Grant permissions (see [Permissions](docs/USER_MANUAL.md#permissions)). At minimum, players need
   `mcmewarps.cmd.warp` and `mcmewarps.world-access.<world>` for each world they may warp into.

## Commands at a glance

`/warp <name>` (alias `/to`) teleports; `/localwarp <name>` (alias `/lwarp`) teleports within the current
server; everything else lives under `/warpmanager` (alias `/wmanage`).

| Command | Permission | Description |
| --- | --- | --- |
| `/warp <name>` | `mcmewarps.cmd.warp` | Teleport to a warp |
| `/warp random` | `mcmewarps.cmd.random` | Teleport to a random public warp |
| `/localwarp <name>` | `mcmewarps.cmd.local-warp` | Teleport without changing server |
| `/wmanage create <name>` | `mcmewarps.cmd.create-public` | Create a public warp here |
| `/wmanage pcreate <name>` | `mcmewarps.cmd.create-private` | Create a private warp here |
| `/wmanage list [filters]` | `mcmewarps.cmd.list` | List warps |
| `/wmanage move <warp>` | `mcmewarps.cmd.move` | Move a warp to your location |
| `/wmanage rename <warp> <new>` | `mcmewarps.cmd.rename` | Rename a warp |
| `/wmanage delete <warp>` | `mcmewarps.cmd.delete` | Delete a warp |
| … | | |

See the **[User Manual](docs/USER_MANUAL.md)** for the complete command, permission, and configuration
reference.

## Documentation

- **[User Manual](docs/USER_MANUAL.md)** — for server operators and staff: every command, permission,
  config option, Dynmap setup, and common workflows.
- **[Developer Manual](docs/DEV_MANUAL.md)** — for contributors: architecture, the storage seam, the
  plugin-message protocol, how to add a command or message, building, and testing.

## Building from source

```bash
./gradlew shadowJar
```

Produces the fat jar at `build/libs/MCME-Warps-<version>.jar` (both plugins, dependencies shaded in).
Requires a **JDK 25** toolchain. Run the tests with `./gradlew test`. See the
[Developer Manual](docs/DEV_MANUAL.md#building--testing) for local proxy/backend setup.

## License

Released under the [GNU General Public License v3.0](LICENSE).

## Credits

Created and maintained by **_Drayz_** for MC Middle Earth.
