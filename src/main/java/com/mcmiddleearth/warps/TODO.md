
# Questions
* Should staff have access to all warps
  * View/Use/Modify?
* What is warp priority?

# TODOs
* (p)create greedy string protection
* p(create) shared code
* warp & random shared code
* getWarp reusable helper
* Extract common command errors

# TODO
## Commands
* private warps
  * automatically prefix with zzz-name-...
  * makePublic, makePrivate
  * user limit -> upon create, msg user how many remaining
* warp permissions
* favourite warps
* reloadAll -> if yaml's manually updated
* region
* welcome, title, subtitle
    * default values, teleportAsync success
* priority(?), info, stats, list, plist

## General
* lucky perms permissions
* Adapt warp location if underground
* server specific permissions - e.g. freebuild warps only for commoner+
* Improved WarpManager errors, e.g. if server/world don't exist in /warp <name>
* Warp name length limit? -> config.yml?
* Warp counter
  * Only write to yml onPluginDisable?
* Migrate DB warps to .yml warps
* Dynmap integration
    * Custom warp symbol for WIP locations/warps
        * https://www.mcmiddleearth.com/community/threads/change-dynmap-icons-for-warps.7068/
        * Also a discord suggestion
> Dynmap markers need to be created by the new Warp plugin. But that's quite simple as Dynmap plugin provides and API for that. Would be good though to have in mind that dynmap might be replaced by another map plugin.

## Ideas
* Hide delete/rename/update commands until a player has made a private warp
    * player.updateCommands() after pcreate (iff they have 0 modifiable warps)
* suggestions onHover to show the server? (only with modifying commands?)
* Warp server prefix [pl, fr, th, fav?]
  * Easy server filtering
  * Visually see which server
* Automatically set warp region based on nearest warp?
    * or  average of the 3/5 nearest?
* Warp autocomplete - if there's only 1 suggestion then no need to tab complete
  * If <destination> doesn't exist, then re-build suggestions and if there's only 1 - use it
* Warp name aliases?
  * Have an alias array, only show 1 alias/primary at a time in the suggestions

# Dev UX
* Multi-project/module repository?
* Add a better IDE formatter