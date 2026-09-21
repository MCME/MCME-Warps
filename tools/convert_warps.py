#!/usr/bin/env python3
"""
MCME-Warps private-warp migration: drop abandoned/broken warps, retire the zzz- prefix.

One-shot, reversible, stdlib-only. Operates on a proxy warp store directory
(plugins/mcme-warps-velocity/warps). PUBLIC warps are left untouched (already
namespaced by server/world). PRIVATE warps (private-warps/<uuid>/*.yml) are:

  1. DROPPED if abandoned or non-convertible:
        - 0 visits            (abandoned)
        - name contains / or \\  (path-unsafe once the prefix is gone)
        - empty after prefix strip
        - same-name collision within a creator (dropped so the rest need no suffix logic)
  2. Otherwise CONVERTED: strip the zzz- prefix -> clean name; rewrite the `name:`
     field and rename the file to <normalised-name>.yml.

Because every collision is dropped, the conversion is a pure prefix-strip: no
suffixes, no per-warp exceptions. Every surviving private warp gets a unique
per-creator name.

Usage:
    python convert_warps.py --warps <dir>                # dry-run (default): write a plan, change nothing
    python convert_warps.py --warps <dir> --apply        # archive the whole tree, then apply + verify
    python convert_warps.py --warps <dir> --out <dir>    # where to write the plan/archive (default: alongside)
"""
import argparse, re, sys, shutil, unicodedata, collections
from pathlib import Path

# --- name handling (single source of truth) -------------------------------

def normalise(s: str) -> str:
    """Mirror the plugin's Utils.normaliseString + trim (the storage key / filename)."""
    s = unicodedata.normalize('NFKD', s).encode('ascii', 'ignore').decode()
    s = s.replace("'", "")
    s = re.sub(r'\s+', ' ', s)
    return s.lower().strip()

def strip_prefix(name: str, creator: str) -> str:
    """Recover a warp's natural name from the historical zzz- conventions.
    Rules are tried in order, most specific first: creator-as-prefix (zzz-<creator>-<name>),
    creator-as-suffix (zzz-<name>-<creator>), literal -zzz suffix, drop-first-segment (covers
    warps whose prefix is a since-renamed creator), then a bare zzz- / 'zzz ' strip."""
    n = name.strip().strip("'\"")
    c = re.escape(creator) if creator else None
    if c:
        m = re.match(r'(?i)^zzz[- ]' + c + r'[- ](.+)$', n)
        if m: return m.group(1)
        m = re.match(r'(?i)^zzz[- ](.+?)[- ]' + c + r'$', n)
        if m: return m.group(1)
    m = re.match(r'(?i)^zzz[- ](.+?)[- ]zzz$', n)
    if m: return m.group(1)
    m = re.match(r'(?i)^zzz[- ][^- ]+[- ](.+)$', n)
    if m: return m.group(1)
    m = re.match(r'(?i)^zzz[- ]?(.+)$', n)
    if m: return m.group(1)
    return n

PATH_UNSAFE = re.compile(r'[/\\]')
BLACKLIST = set('<>:"|*?!')   # the plugin's current validateWarpName blacklist (minus / \ handled above)
MAXLEN = 64

def sanitize(s: str) -> str:
    """Make a stripped name a valid warp name: drop the plugin's blacklisted chars
    (< > : " | * ? !), collapse the resulting whitespace, trim, cap at MAXLEN.
    (/ and \\ are not sanitised here - those names are dropped instead, per decision.)"""
    s = ''.join(ch for ch in s if ch not in BLACKLIST)
    s = re.sub(r'\s+', ' ', s).strip()
    return s[:MAXLEN].strip()

def yaml_field(text: str, key: str):
    m = re.search(r'^' + re.escape(key) + r':[ \t]*(.*?)[ \t]*$', text, re.M)
    return m.group(1).strip() if m else None

def set_name_line(text: str, new_name: str) -> str:
    """Replace the `name:` value, single-quoted (YAML-safe for any content)."""
    quoted = "'" + new_name.replace("'", "''") + "'"
    return re.sub(r'^(name:)[ \t]*.*$', lambda m: m.group(1) + ' ' + quoted, text, count=1, flags=re.M)

# --- load & classify -------------------------------------------------------

def load(warps: Path):
    pub, priv = [], []
    for p in warps.rglob('*.yml'):
        parts = p.relative_to(warps).parts
        text = p.read_text(encoding='utf-8', errors='replace')
        try: visits = int(yaml_field(text, 'visits') or '0')
        except ValueError: visits = 0
        rec = dict(path=p, parts=parts, text=text,
                   name=yaml_field(text, 'name') or '',
                   creator=yaml_field(text, 'creator-name') or '',
                   cid=yaml_field(text, 'creator-id') or '', visits=visits)
        (priv if parts and parts[0] == 'private-warps' else pub).append(rec)
    return pub, priv

def plan(priv):
    """Return (delete[list of (rec,reasons)], convert[list of (rec,newname)])."""
    for r in priv:
        r['strip'] = strip_prefix(r['name'], r['creator'])
        r['clean'] = sanitize(r['strip'])          # final, valid warp name
        r['nkey'] = normalise(r['clean'])          # storage key / filename stem
        reasons = []
        if r['visits'] == 0: reasons.append('0-visits')
        if PATH_UNSAFE.search(r['name']): reasons.append('path-unsafe')
        if r['nkey'] == '': reasons.append('empty-name')
        r['reasons'] = reasons
    # collisions among the not-yet-dropped, per creator
    survivors = [r for r in priv if not r['reasons']]
    by = collections.defaultdict(list)
    for r in survivors:
        by[r['cid']].append(r)
    for cid, lst in by.items():
        seen = collections.Counter(r['nkey'] for r in lst)
        for r in lst:
            if seen[r['nkey']] > 1:
                r['reasons'].append('collision')
    delete = [r for r in priv if r['reasons']]
    convert = [r for r in priv if not r['reasons']]
    return delete, convert

# --- run -------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="MCME-Warps private-warp migration (dry-run by default).")
    ap.add_argument('--warps', required=True, type=Path, help="the proxy warps/ directory")
    ap.add_argument('--apply', action='store_true', help="actually apply (default is dry-run)")
    ap.add_argument('--out', type=Path, default=None, help="dir for plan + archive (default: alongside warps)")
    ap.add_argument('--stamp', default=None, help="archive timestamp (pass one in; scripts must not read the clock in some envs)")
    args = ap.parse_args()

    warps = args.warps.resolve()
    if not warps.is_dir():
        sys.exit("not a directory: %s" % warps)
    out = (args.out or warps.parent).resolve()
    out.mkdir(parents=True, exist_ok=True)

    pub, priv = load(warps)
    delete, convert = plan(priv)

    # verify survivors are collision-free per creator
    by = collections.defaultdict(collections.Counter)
    for r in convert:
        by[r['cid']][r['nkey']] += 1
    residual = [(cid, k, n) for cid, c in by.items() for k, n in c.items() if n > 1]

    # warnings: survivors whose converted name still has blacklisted chars or is > 64
    warn = [r for r in convert if r['clean'] != r['strip']]  # names altered by sanitize/truncate

    # ---- write the plan ----
    lines = []
    lines.append("# Warp migration plan (%s)\n" % ("APPLIED" if args.apply else "DRY-RUN"))
    lines.append("warps dir: %s\n" % warps)
    lines.append("## Summary\n")
    lines.append("| | count |\n|---|---:|")
    lines.append("| public (untouched) | %d |" % len(pub))
    lines.append("| private before | %d |" % len(priv))
    lines.append("| private deleted | %d |" % len(delete))
    for reason in ('0-visits', 'path-unsafe', 'empty-name', 'collision'):
        lines.append("|   – %s | %d |" % (reason, sum(1 for r in delete if reason in r['reasons'])))
    lines.append("| private converted | %d |" % len(convert))
    lines.append("| **total after** | **%d** |" % (len(pub) + len(convert)))
    lines.append("| residual collisions (must be 0) | %d |" % len(residual))
    lines.append("| converted names still needing manual attention | %d |\n" % len(warn))
    if warn:
        lines.append("### Names altered by sanitize (illegal chars removed / truncated to 64)\n")
        for r in sorted(warn, key=lambda r: -r['visits'])[:60]:
            lines.append("- `%s` -> `%s`  (creator %s, %d visits)" % (r['name'], r['clean'], r['creator'], r['visits']))
        lines.append("")
    Path(out, 'migration-plan.md').write_text("\n".join(lines) + "\n", encoding='utf-8')

    # full machine-readable maps
    Path(out, 'migration-deletes.txt').write_text(
        "\n".join(sorted('/'.join(r['parts']) for r in delete)), encoding='utf-8')
    Path(out, 'migration-renames.tsv').write_text(
        "old_path\told_name\tnew_name\tnew_file\n" + "\n".join(
            "%s\t%s\t%s\t%s.yml" % ('/'.join(r['parts']), r['name'], r['clean'], normalise(r['clean']))
            for r in convert), encoding='utf-8')

    print("public=%d  private=%d  ->  delete=%d  convert=%d  after=%d  residual_collisions=%d  warnings=%d"
          % (len(pub), len(priv), len(delete), len(convert), len(pub) + len(convert), len(residual), len(warn)))
    print("plan written to:", out)

    if residual:
        sys.exit("ABORT: %d residual collisions - refusing to proceed (fix the plan logic)." % len(residual))

    if not args.apply:
        print("DRY-RUN only. Re-run with --apply to archive + execute.")
        return

    # ---- apply: archive whole tree first, then delete + convert, then verify ----
    stamp = args.stamp or "manual"
    archive = Path(out, "warps-archive-%s" % stamp)
    if archive.exists():
        sys.exit("archive already exists: %s (remove or pass a fresh --stamp)" % archive)
    print("archiving %s -> %s ..." % (warps, archive))
    shutil.copytree(warps, archive)

    # Two-phase to avoid in-place rename conflicts (a warp's new filename can equal another warp's
    # not-yet-renamed old filename). All content is already in memory (r['text']), so remove every
    # migrating private file first, then write the converts fresh at their new paths.
    for r in delete:
        r['path'].unlink()
    for r in convert:
        r['path'].unlink()
    for r in convert:
        new_file = r['path'].with_name(normalise(r['clean']) + '.yml')
        if new_file.exists():
            sys.exit("ABORT: target exists after clearing (should be impossible): %s" % new_file)
        new_file.write_text(set_name_line(r['text'], r['clean']), encoding='utf-8')

    # verify
    pub2, priv2 = load(warps)
    ok = (len(pub2) == len(pub) and len(priv2) == len(convert))
    d2, c2 = plan(priv2)
    still = len(d2)  # after conversion, ideally nothing is droppable except leftover 0-visit (converted keep visits)
    print("VERIFY: public %d==%d, private %d (expected %d).  post-check droppable=%d"
          % (len(pub2), len(pub), len(priv2), len(convert), still))
    if not ok:
        sys.exit("VERIFY FAILED - counts mismatch. Archive at %s" % archive)
    print("APPLIED OK. Reversible: restore from %s" % archive)

if __name__ == '__main__':
    main()
