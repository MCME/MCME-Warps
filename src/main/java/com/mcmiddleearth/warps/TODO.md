
# Questions
* What is warp priority?
* Should a warp's name change when its type changes

# TODO
* addMember/removeMember -> members add/remove
* Simplify the update warp logic?

## Commands
* private warps
  * zzz naming
    * what happens when making a public warp private (auto prefix?)
    * and the opposite (auto strip the prefix?)
    * What happens when 'rename' is used!!!

# Post launch
* Suggestions
  * BUG: Quoted suggestions are invalid if the warp name has >1 word
  * Fuzzy suggestions for word 2+
  * Hide suggestions if it matches the user's search term?
  * Consistent use of greedy & word args for warp names
* Server prefixes
* Add/Remove offline members
  * Cache player names and UUIDs on PlayerJoin
    * Use these to allow adding & removing offline players to private warps
  * Luckperms
    * Remove any offline player from a warp (not just those who logged within the last 24hrs)
      * On plugin load lookup the player names OR do it as an async suggestion
  * Or store the UUIDs of all players that have been invited in a yaml??? And load into memory???
* Warp region
  * Only for moria & mainworld warps?
  * Upon create, default to that of the nearest warp
  * Will be used by the new warp book
  * /warp region command
* Favourite warps
  * Display favourites in the /warp suggestions?
## Maybe?
* More warp tags
  * INCOMPLETE (purple flag), CAPITAL? FARM? TOWER?
  * Custom icons?
  * Different scaled icons for importance/population size
* Warp title & subtitle (v. similar to /warp welcome)
* warp permissions??? (e.g. public warp only for staff/commoners etc.)
  * Add the permissions check to Warp.isUsable() 
  * OR add permissions to private warps -> /warp invite asdf g:group-name
* priority(?), info, stats, list, plist???

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
* Hide delete/rename/update commands until a player has made a private warp
    * After pcreate() in MessageListener, send a updateCommand plugin message to paper
      * iff modifiableWarps.size() === 1
    * Also invite/uninvite/makePublic
    * Implementation -> .requires( player has >= 1 modifiable warp )
      * Or - player is staff || player is creator of >=1 warp
      * if (warpType.equals(Warp.Type.PRIVATE) && WarpManager.getWarpNames(warp -> warp.isCreator(creator)).size() == 1)
* Add an onHover tooltip to warp name suggestions? Server/word?, creator?, region?
* Automatically set warp region based on nearest warp?
    * or average of the 3/5 nearest?
* Warp autocomplete - if there's only 1 suggestion then no need to tab complete
  * If <destination> doesn't exist, then re-build suggestions and if there's only 1 - use it
* Warp name aliases?
  * Have an alias array, only show 1 alias/primary at a time in the suggestions
* /warp player - for clicking on a sign???

# Code cleanup
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