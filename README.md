# MCME-Warps
A velocity & paper plugin using yaml storage with dynmap integration

* The velocity plugin provides a config.yml file - see the default [here](src/main/resources/default-config.yml)
* The paper plugin loads all `*.yml` (from the `plugins/MCME-Warps-Paper/warps` directory) into the 
dynmap
  * If symlinking: symlink the `/warps` directory in the paper backend to it's corresponding 
    server folder inside the `/plugins/mcme-warps-velocity/warps` folder of the proxy

## Permissions
```
mcmewarps.cmd.warp
mcmewarps.cmd.random
mcmewarps.cmd.create-private
mcmewarps.cmd.create-public
mcmewarps.cmd.delete
mcmewarps.cmd.rename
mcmewarps.cmd.move
mcmewarps.cmd.add-member
mcmewarps.cmd.remove-member
mcmewarps.cmd.set-public
mcmewarps.cmd.set-private
mcmewarps.cmd.reload
mcmewarps.cmd.welcome-message
mcmewarps.cmd.set-icon

mcmewarps.limits.ignore.private

// If a player has these permissions they can use/modify any warp (not just their own private warps)
mcmewarps.override.use
mcmewarps.override.modify

// This permission covers every paper backend server, so to configure
// them individually, they need to have different names
mcmewarps.world-access.[WORLDNAME]
```

# Warp Names
All public and private warps are collated into the same set - this means that warps must have 
unique names. The warp names are normalised to prevent annoying duplicates

This also means players don't have to worry about case, accents or apostrophes when searching 
for warps

Private warps have an enforced prefix `zzz-<playerName>-`

### Warp name normalisation
* Lowercase
* Strip accents
* Remove apostrophes

## Developing Locally
### Setting up a velocity proxy
1. In your IDE start a velocity server with the `run velocity` task in the gradle toolbar
2. Inside /run, open `velocity.toml`
   1. player-info-forwarding-mode: "modern"
   2. Delete the forced hosts section (optional)
3. Open forward.secret and copy it

In your paper backend(s)
1. Open server.properites
   1. disable online-mode 
      > This prevents the server from authenticating players, which the proxy will do instead
   2. Ensure the server-port matches up with what's in velocity.toml
2. Open config > paper-global.yml
   1. Enable the velocity section
   2. online-mode: true
   3. paste the forwarding secret

Once everything is running connect to the proxy server with `localhost:<port>`
