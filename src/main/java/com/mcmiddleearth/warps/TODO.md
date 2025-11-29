
# Questions
* What is warp priority?
* Should a warp's name change when its type changes

# TODO
* Convert geoguesser to use symlinked warps instead of the MySQL
* Limit /localwarp to world warps???
* 
* Add aliases
  * /warp setAlias "<warp>" alias (only public warps)
  * When loading a warp.yml if it has aliases create a warp for every alias
  * In the backend server instead of creating a new warp add the aliases to the warp name?
* list & plist
  * > By default, filter out personal warps, add a flag/option to view only personal warps for Moderators. (Eg -m)
  * paginated
  * Clickable (for what??)
  * multi-line tooltip
  * filters (creator, world, server, name, order?, radius?)
    * How to generate optional filter suggestions? CommandAPI?
    * Creator search
    * /warp list w:<> c:<> (look at what dynmap does!)
  * plist -> Remove zzz-<name> prefix? (do the same for renames? So people never need know about zzz?)
* Info? (lists everything about a warp)
  * Is this needed if the tooltip is good enough???
  * Non relative date
  * Average visits per day
*
* Add multiple members at once to a warp (greedy string)
* Remove offline members
  * Async suggestions using PlayerNameResolver - use the Map to link selected name to UUID
* Prevent public warps at the same location
  * Velocity message listener (ignore yaw and pitch)
* Smart search - If a capital is used (or just at the start?) then case-sensitive search
* Only show `random` if no input has been entered?
* Put `random` white list in the config???
*
* Server Prefixes (no longer needed because of localwarp?)
* Warp Icons
  * Just use the file-names instead of enum mappings?
  * How to make it easier for admins to select custom icons?

## Commands
* private warps
  * zzz naming
    * what happens when making a public warp private (auto prefix?)
    * and the opposite (auto strip the prefix?)
    * What happens when 'rename' is used!!!

# Post launch
* Simplify the update warp logic? -> weird rollback messages atm
* Suggestions
  * Hide suggestions if it matches the user's search term?
  * Consistent use of greedy & word args for warp names
* Server prefixes
* Warp region
  * Only for moria & mainworld warps?
  * Upon create, default to that of the nearest warp
  * Will be used by the new warp book UI
  * /warp region command
* Favourite warps
  * Display favourites in the "/warp" suggestions?
## Maybe?
* More warp tags
  * INCOMPLETE (purple flag), CAPITAL? FARM? TOWER?
  * Custom icons?
  * Different scaled icons for importance/population size
* Warp title & subtitle (v. similar to /warp welcome)
* warp permissions??? (e.g. public warp only for staff/commoners etc.)
  * Add the permissions check to Warp.isUsable() 
  * OR add permissions to private warps -> /warp invite asdf g:group-name
* priority???

# Ideas
* Suggested warps
  * Set suggested warps in the config? OR an isFeatured flag in the yml warp files???
  * Randomly choose 3,5? warps to recommend each day (from the list of 'interesting' warps)
    * Utilise the daily server restart for this behaviour
    * Choose randomly from 3 categories of warps?
* Dynmap labels
  * Regions
  * Mountain ranges
* Prevent (public) warps at the exact same location?
* Add multiple players to a private warp at once
* /warp leave to leave a warp you were added to
* Warp autocomplete - if there's only 1 suggestion then no need to tab complete
  * If <destination> doesn't exist, then re-build suggestions and if there's only 1 - use it
* Warp name aliases?
  * Have an alias array, only show 1 alias/primary at a time in the suggestions
* /warp player - for clicking on a sign???

# Code cleanup
* executesPlayer helper OR look into the CommandAPI
* Do away with WarpManager.update?
* How to simplify saveWarp()? - RuntimeException?
* consistent command arg names
  * Store as static strings
  * Centralise???
* Shared brigadier warp arg + suggestions
  * Have a BiPredicate function for filtering warps?
    * isModifiable, isUsable, isPrivate & sender is creator
* DRY player requirement
  * .requires(MyCommand::isPlayer) to every command? Then assert getSource as Player?
  * WarpUtil helper?
  * Are there commands that could be run by a non player?
* p(create) shared code
* warp & random shared code

# Dev UX
* Multi-project/module repository
* Add a better IDE formatter

# Server prefixes
Prefixes for each server, defined in the config.yml -> [pl, fr, th, fav?]

Why?
* Handy filter for warps of a specific server
* Always know which server you are warping to
* If the main map is copied to another server, its warps can be copied over and still usable

Suggestions
* /warp pl:gon
* Nice to have loose matching on everything after the ':'

* Should private warps have prefixes?
  * pl:zzz-Drayz-gondor
  * Just use an onHover tooltip instead?

How
* Q: Adding the prefix to the normalised key of the hashmap??? Or the actual warpName???

These would be used as a prefix in the key of the Warps hashmap
* When putting a warp, get the prefix for its server

These would be used to prefix the name of each warp when loading each warp file and creating a warp in memory
* How to account for a warp being renamed/moved/made public or private???
  * rename - overwrite/add prefix after the new name is given (abc:warp -> pl:warp, warp -> pl:warp)
  * moved - prefix needs to change -> add this to WarpManager.update?

Instead of storing the 'server' in the yaml, use the name of the parent directory
  * Make 'server' a final field that is set in the constructor
  * This is so when a server and its warps are copied we don't have to manually change the server
    of each file!
  * This could be done easily post launch - warp.yml files with server would just ignore the server
  field (and it would be deleted on save?)

# Reloading warps
* To change a warp's name change both the file name and warp name