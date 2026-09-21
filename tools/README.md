# tools

Operational scripts that ship with the plugin. Python 3, standard library only — no install step.

## `convert_warps.py` — private-warp name migration

One-shot migration that retires the historical `zzz-<player>-` prefix on private warps, which the
namespaced `(server, world, name)` warp key makes unnecessary. **Run this against the proxy warp
store before releasing the version that introduces the new key**, or private warps will not resolve
under their new names.

### What it does

**Public warps are untouched.** They already live at `server/world/name`, so the new key only
formalises where they are. No file changes are needed for them.

Private warps (`private-warps/<uuid>/*.yml`) are either dropped or converted:

| Outcome | When |
| --- | --- |
| **Dropped** | 0 visits (abandoned) |
| **Dropped** | name contains `/` or `\` (path-unsafe once the prefix is gone) |
| **Dropped** | empty after the prefix is stripped |
| **Dropped** | two warps of one creator collide on the same stripped name |
| **Converted** | everything else — prefix stripped, `name:` rewritten, file renamed to the normalised name |

Because every collision is dropped rather than suffixed, the conversion is a pure prefix-strip:
no per-warp exceptions and no suffix logic, and every surviving private warp is unique per creator.

The prefix was applied under several conventions over the years, so `strip_prefix` tries them in
order: creator-as-prefix, creator-as-suffix, a literal `-zzz` suffix, drop-first-segment (for warps
prefixed with a since-renamed creator), then a bare `zzz-` / `zzz ` strip.

### Usage

```bash
# Dry run (the default). Writes a plan, changes nothing.
python tools/convert_warps.py --warps /path/to/plugins/mcme-warps-velocity/warps

# Apply. Archives the entire tree first, then converts, then verifies.
python tools/convert_warps.py --warps /path/to/warps --apply --stamp 2026-09-21
```

| Flag | Meaning |
| --- | --- |
| `--warps` | the proxy `warps/` directory (required) |
| `--apply` | actually perform the migration; without it the script only plans |
| `--out` | where to write the plan and archive (default: alongside `warps/`) |
| `--stamp` | archive suffix, so repeated runs cannot silently overwrite one another |

### Output

Three files, written on both dry and applied runs:

- `migration-plan.md` — human-readable summary and the names that needed sanitising
- `migration-deletes.txt` — every path that will be or was removed
- `migration-renames.tsv` — `old_path → old_name → new_name → new_file`

### Safety

- **Dry-run by default.** `--apply` is required to change anything.
- **Reversible.** `--apply` copies the whole warp tree to `warps-archive-<stamp>` before touching
  it. Restoring is a directory copy back.
- **Refuses to proceed on residual collisions.** If any creator would still end up with two warps
  of the same key, it aborts rather than guessing.
- **Two-phase writes.** All content is read into memory first, then every migrating file is removed
  before any new one is written, because a warp's new filename can collide with another warp's
  not-yet-renamed old filename.
- **Verifies afterwards.** Re-reads the tree and checks the public count is unchanged and the
  private count matches the conversion plan; exits non-zero and points at the archive if not.

### Before you run it

Roughly **1.5% of private warps cannot be migrated automatically** and need a human decision —
mostly per-creator collisions after stripping (one player cannot keep two private warps of the same
name), plus a couple that normalise to an empty name. The script drops those by design. Run the dry
run first, read `migration-plan.md`, and decide whether any of them are worth rescuing by renaming
them by hand beforehand.

Re-run the dry run against a **current** copy of the production store rather than relying on an
earlier analysis — the numbers drift as players create and abandon warps.
