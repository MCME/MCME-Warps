# MCME-Warps
A velocity & paper plugin using yaml storage with dynmap integration.

* The velocity plugin provides a config.yml file - see the default [here](src/main/resources/default-config.yml)
* The paper plugin loads all `*.yml` files from the `plugins/MCME-Warps-Paper/warps` directory into dynmap

## Deployment
To share warp data between the velocity proxy and a paper backend without duplicating files, symlink
the backend's warp directory to the corresponding server folder inside the proxy's warp storage:

```
plugins/mcme-warps-velocity/warps/<server-name>/  <--  plugins/MCME-Warps-Paper/warps/
```

## Warp Types
| Type    | Who can use it                                           | Who can modify it                                   |
|---------|----------------------------------------------------------|-----------------------------------------------------|
| Public  | Anyone with `mcmewarps.cmd.warp`                         | Players with `mcmewarps.override.modify`            |
| Private | Creator, members & players with `mcmewarps.override.use` | Creator or players with `mcmewarps.override.modify` |

Private warps have an enforced name prefix of `zzz-<playerName>-`, which keeps them sorted to the
bottom of warp lists so public warps remain easy to browse.

## Commands
All management commands are subcommands of `/warpmanager` (alias: `/wmanage`).

| Command                                           | Permission                      | Description                                                  |
|---------------------------------------------------|---------------------------------|--------------------------------------------------------------|
| `/warp <name>`                                    | `mcmewarps.cmd.warp`            | Teleport to a warp                                           |
| `/warp random`                                    | `mcmewarps.cmd.random`          | Teleport to a random public warp                             |
| `/localwarp <name>`                               | `mcmewarps.cmd.local-warp`      | Teleport to a warp without changing server or world          |
| `/warpmanager create <name>`                      | `mcmewarps.cmd.create-public`   | Create a public warp at your location                        |
| `/warpmanager privateCreate <name>`               | `mcmewarps.cmd.create-private`  | Create a private warp at your location                       |
| `/warpmanager delete <warp>`                      | `mcmewarps.cmd.delete`          | Delete a warp                                                |
| `/warpmanager rename <warp> <new-name>`           | `mcmewarps.cmd.rename`          | Rename a warp                                                |
| `/warpmanager move <warp>`                        | `mcmewarps.cmd.move`            | Move a warp to your current location                         |
| `/warpmanager members <warp> add/remove <player>` | `mcmewarps.cmd.manage-members`  | Add or remove a warp member                                  |
| `/warpmanager setPublic <warp>`                   | `mcmewarps.cmd.set-public`      | Convert a private warp to public                             |
| `/warpmanager setPrivate <warp>`                  | `mcmewarps.cmd.set-private`     | Convert a public warp to private                             |
| `/warpmanager setIcon <warp> <icon>`              | `mcmewarps.cmd.set-icon`        | Set the dynmap icon for a warp                               |
| `/warpmanager setLayer <warp> <layer>`            | `mcmewarps.cmd.set-layer`       | Set the dynmap layer for a warp                              |
| `/warpmanager setWelcome <warp> <message>`        | `mcmewarps.cmd.welcome-message` | Set a welcome message shown on teleport (`default` to clear) |
| `/warpmanager list`                               | `mcmewarps.cmd.list`            | List all public warps                                        |
| `/warpmanager privateList`                        | `mcmewarps.cmd.list`            | List your private warps                                      |
| `/warpmanager assets`                             | `mcmewarps.cmd.list`            | List your private warps in the current server                |
| `/warpmanager reload`                             | `mcmewarps.cmd.reload`          | Reload config & warps from disk                              |

## Other Permissions
```sh
mcmewarps.limits.ignore.private

# Allows using/modifying any warp, not just ones the player owns or is a member of
mcmewarps.override.use
mcmewarps.override.modify

# Grants access to a specific paper backend world/server.
# Since this permission covers all backend servers, give each world a unique name to configure access individually.
mcmewarps.world-access.[WORLDNAME]
```

## Warp Names
All public and private warps share a single namespace, so names must be unique. Names are
normalised so players don't need to worry about case, accents, or apostrophes when searching.

### Normalisation rules
* Lowercase
* Strip accents
* Remove apostrophes

## Visit Counts
Visit counts are **not** written to disk on every teleport — this is to avoid
constant disk I/O. Instead, they are saved to YAML on:
* Proxy shutdown
* Proxy reload
* `/warpmanager reload`

## Developing Locally
### Setting up a velocity proxy
1. In your IDE start a velocity server with the `run velocity` task in the gradle toolbar
2. Inside /run, open `velocity.toml`
   1. player-info-forwarding-mode: "modern"
   2. Delete the forced hosts section (optional)
3. Open `forward.secret` and copy it

### Setting up a paper backend
1. Open `server.properties`
   1. Disable `online-mode`
      > This prevents the server from authenticating players, which the proxy handles instead
   2. Ensure the server port matches what's in `velocity.toml`
2. Open `config/paper-global.yml`
   1. Enable the velocity section
   2. Set `online-mode: true`
   3. Paste the forwarding secret

Once everything is running, connect to the proxy with `localhost:<port>`
