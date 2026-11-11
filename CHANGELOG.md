# Changelog

Notable changes to MCME-Warps. The release workflow uses a version's section as its release notes.

## [1.0.8]

### Fixed

- **Other players' private warps can be used again.** Since 1.0.7 a private warp could only be looked up
  by its owner. Members of a private warp, and staff with `mcmewarps.override.use`, were offered it in
  tab-completion but got "No warp found". Staff with `mcmewarps.override.modify` could not delete, move
  or rename another player's private warp either.
- **Lists show every warp.** A list kept one warp per name, so of several players' private warps called
  `home` only one was listed.
- **Public warps no longer drop out of tab-completion.** Suggestions were cut at 50 in no particular
  order. Now the player's own warps and the most visited ones are kept.
- **A renamed player's warps carry the new name after a restart.** The new name was only applied in
  memory; it is now written to the warp files, on every login.
- The `-s`, `-w`, `-c` and `-n` suggestions of `/warpmanager list` no longer miss values: they looked at
  the first 50 warps only.

### Added

- **`owner:name`** addresses another player's private warp, for example `/warp Alfonios:castle`. It is
  needed when a warp of your own or a public warp has the same name, or when several players have a
  private warp of that name; the plugin then names the owners. Lists show such warps as `owner:name`.

### Changed

- **Tab-completion no longer offers other players' private warps for a plain name**, also not to staff
  who may use all of them: they outnumber public warps several times and buried them. Type `owner:` to
  see that player's private warps. Private warps shared with you are still offered, as `owner:name`.
- A name reaches, in this order: your own private warp, a public warp, another player's private warp you
  may use. Another player's warp never takes over a name you already use.

Deploy the same jar to the proxy and every backend, as always. No data migration and no config change.

## [1.0.7]

The Paper 26.2 / Velocity port, the security and crash-safety fixes, per-player private warp names
(retiring the `zzz-<player>-` prefix) and the versioned plugin-message protocol. See the
[1.0.7 release](https://github.com/MCME/MCME-Warps/releases/tag/v1.0.7).
