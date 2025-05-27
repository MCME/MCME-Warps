
# Questions
* Which commands should staff have? -> & what perms string
  * See *all* warps?
  * Rename & delete any warps?
  * Make any warp private/public?
* Welcome/Title/Subtitle needed?

# TODO
## Commands
  * random
  * private create
    * public/private & invitations
      * class.Warp & suggestions
    * user limit -> msg user how many remaining
    * automatically prefix with zzz-name-...
    * storage location
  * delete, rename
    * creator (+ staff/mods?)
    * *Should perms override creator?* Eg. ex staff member deleting Minas Tirith
  * welcome, title, subtitle
    * default values, teleportAsync success
  * priority, info, stats, list, plist
  * public, private, invite, uninvite
  * permission
  * favourite
* Reloading warps - /warp update?
* Automatically set warp region based on nearest warp?
  * Inherit the region from the average of the 5 nearest?
* Hide delete/rename/update commands until a player has made a private warp
  * player.updateCommands() after pcreate

## General
* Improved errors, e.g. if server/world don't exist in /warp <name>
* Permissions for individual warps
* Warp prefixes [pr, pl, fr, th, fav?]
* Warp autocomplete - choose top suggestion if warpName invalid
  * Just call the suggestions method with a fake context? or extract the internals?
* Warp name aliases?
  * Have an alias array, only show 1 alias/primary at a time in the suggestions
* Warp name length limit?

# /warp suggestions
* <WarpName> until user types something? Like "/teleport"?
* sorting & priority
  * fuzzy match
  * same server (+ world)
* Ignore special characters e.g. '
* Account for permissions and invitations
  * Display warp if:
    * public
    * player is creator
    * player has perms
    * player is invited
    * player is staff/ a mod???

# asdf
* Only store warp counts onPluginDisable?
* Migrate DB warps to .yml warps
* Multi-project/module repository -> might work now that I'm creating a fat JAR
* Better formatting
* Dynmap integration
  * Custom warp symbol for WIP locations/warps
    * https://www.mcmiddleearth.com/community/threads/change-dynmap-icons-for-warps.7068/
    * Also a discord suggestions
> Dynmap markers need to be created by the new Warp plugin. But that's quite simple as Dynmap plugin provides and API for that. Would be good though to have in mind that dynmap might be replaced by another map plugin.

