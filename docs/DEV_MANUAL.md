# MCME-Warps — Developer Manual

Architecture and contribution guide. For the operator/player-facing reference, see the
[User Manual](USER_MANUAL.md).

## Contents

- [Mental model](#mental-model)
- [Project layout](#project-layout)
- [Building & testing](#building--testing)
- [Running locally](#running-locally)
- [Data model & storage](#data-model--storage)
- [The store seam](#the-store-seam)
- [The plugin-message protocol](#the-plugin-message-protocol)
- [Commands](#commands)
- [Dynmap integration](#dynmap-integration)
- [Security model](#security-model)
- [Legacy & migrations](#legacy--migrations)
- [Conventions](#conventions)

---

## Mental model

One Gradle project builds **one fat jar containing two plugins**:

- **`WarpVelocity`** (`velocity/`) runs on the proxy and is the **source of truth**. It owns the warp
  store, the config, every command, permissions, and teleport routing.
- **`WarpPaper`** (`paper/`) runs on each backend. It executes teleports and renders Dynmap markers. It
  holds no authoritative state — it reads warp files through a symlink and reacts to messages from the
  proxy.

Everything shared by the two sits in **`core/`** (records, the message protocol, small utilities). The
guiding principle: **the proxy decides, the backend acts.** A backend never trusts a message's authority
on its own, and the proxy never assumes a backend's response is well-formed.

## Project layout

```
src/main/java/com/mcmiddleearth/warps/
├── core/                     # shared by both plugins
│   ├── BaseWarp, Warp fields, SimpleLocation, WarpIcon, Utils
│   ├── WarpLoader            # builds the Configurate YAML loader
│   ├── SafeEnumSerializer    # tolerant enum (de)serialisation for YAML
│   ├── Channels              # the three plugin-message channel names
│   └── messageprotocols/     # the wire protocol (see below)
├── velocity/                 # proxy plugin
│   ├── WarpVelocity          # @Plugin entry point, command registration, lifecycle
│   ├── Permission            # permission-node enum
│   ├── ChannelIdentifiers    # Velocity channel identifiers
│   ├── commands/             # one class per Brigadier command
│   ├── config/               # Config record + ConfigManager
│   ├── listener/             # MessageListener (backend → proxy)
│   └── warps/                # the store: Warp, WarpManager, WarpStore, WarpFileIo,
│                             #   ConfigurateWarpIo, WarpPaths, PlayerNameResolver, MyWarpDBConnector
└── paper/                    # backend plugin
    ├── WarpPaper              # JavaPlugin entry point
    ├── WarpWatcher            # watches the warp dir, updates Dynmap markers
    ├── DynmapAPI / MapAPI     # Dynmap marker rendering
    ├── Layer / LayerParser    # layer config parsing
    └── listener/              # MessageListener (proxy → backend)
```

The live `Warp` and `WarpManager` are the ones under `velocity/warps/` — the proxy owns the data, so both
belong to the Velocity plugin.

## Building & testing

```bash
./gradlew shadowJar   # build the fat jar -> build/libs/MCME-Warps-<version>.jar
./gradlew test        # run the unit tests
./gradlew build       # both
```

**Toolchain: JDK 25 is required.** `build.gradle` sets `targetJavaVersion = 25`. This is not optional:
Paper 26.x's `paper-api` declares a Java-25 minimum in its Gradle Module Metadata, and Gradle refuses to
put it on a Java-21 compile classpath. (A Maven build would ignore that metadata, but this is a Gradle
project.) The shaded output is Java-25 bytecode, so the servers that run it must also be on Java 25 —
which Paper 26.x requires anyway.

Key build facts:

- Fat jar via the **Shadow** plugin (`com.gradleup.shadow`). Runtime deps (Guava, Configurate, SquirrelID)
  are bundled and relocated.
- `paper-api` and `velocity-api` are `compileOnly`; the server provides them at runtime.
- The version string is injected into both `plugin.yml` (via resource filtering) and the Velocity
  `@Plugin` annotation (via a generated `BuildConstants`), so bump the version in **one** place —
  `build.gradle`'s `version`.

> **OneDrive note:** this repo often lives under OneDrive, which intermittently locks `build/` and makes
> Gradle fail with "Unable to delete directory `build\…`". Clear `build/classes` and `build/test-results`
> and retry — it's an environment quirk, not a build error. Also **never pipe `gradlew` through `tail`** in
> a script that checks `$?`: you'll read the pager's exit code, not the build's. Redirect to a file.

### Testing approach

Tests are JUnit 5 (`junit-bom`), TDD-first. The design deliberately keeps the testable logic free of the
platform so it can be unit-tested without a running server or mocks:

| Test | Covers |
| --- | --- |
| `WarpStoreTest` | The whole store: crash-safe add/update/delete, namespacing, visit flush, atomic reload — using an in-memory `FakeIo` that can inject write/delete failures. |
| `MessageProtocolTest` | The wire protocol: round-trips, stable opcodes, and rejection of bad-version / bad-opcode / truncated / trailing messages. |
| `WarpManagerPathTest` | Path containment (`WarpPaths`) — legit paths resolve, traversal is rejected. |
| `LayerParserTest` | Backend layer-config parsing, including the malformed-section case. |
| `WarpTest` | `Warp` invariants (e.g. the copy constructor isolates the members set). |

What is **not** unit-tested, by design: the Brigadier command classes and the two `MessageListener`s.
They're thin glue over the tested core and are heavily coupled to Velocity/Bukkit types; mocking them
costs more than it proves. They're exercised by running the dev servers (below). When you add logic worth
testing, extract it into a pure helper (as `WarpPaths`, `WarpStore`, and `LayerParser` were) and test that.

## Running locally

The `run-velocity` and `run-paper` Gradle tasks (jpenilla) spin up real servers with the jar installed.

```bash
./gradlew runVelocity   # a Velocity 3.4.0 proxy
./gradlew runServer     # a Paper 26.x backend
```

First-time wiring so the proxy and backend trust each other:

1. **Proxy** (`run/velocity.toml`): `player-info-forwarding-mode = "modern"`; copy `forwarding.secret`.
2. **Backend** (`run/server.properties`): `online-mode=false`, and set the port the proxy points at.
3. **Backend** (`run/config/paper-global.yml`): enable the `velocity` section, `online-mode: true`, and
   paste the forwarding secret.
4. Connect to the proxy at `localhost:<port>`.

Seed a valid `config.yml` in the proxy run dir before starting — the shipped default has every private
limit commented out, which is a `@Required` field and will fail to load on a truly fresh install (a known
rough edge; production always has a populated config).

## Data model & storage

A **`Warp`** (`velocity/warps/Warp.java`) is a Configurate-serialisable record of: creator id + name,
display name, server, `SimpleLocation` (world + x/y/z/yaw/pitch), type (`PUBLIC`/`PRIVATE`), members,
visit count, welcome message, icon, layer, and creation timestamp.

On disk, under the proxy's `plugins/mcme-warps-velocity/warps/`:

```
warps/<server>/<world>/<name>.yml          # a public warp
warps/private-warps/<creatorId>/<name>.yml # a private warp
```

- **Public** warps are keyed by normalised name in one global namespace.
- **Private** warps are keyed by `(creatorId, normalised name)` — so two players can each own `home`, and
  a player's private warp shadows a public one of the same name on lookup.

`WarpPaths` (pure, unit-tested) turns a `Warp` into its path and its normalised key, and **guarantees path
containment**: it `.normalize()`s the result and throws if it escapes the warps directory. `Utils.normaliseString`
defines the key form (strip accents, drop apostrophes, collapse spaces, lowercase). `CommandUtils.BLACKLIST`
rejects `/ \ < > : " | * ? !` in names.

## The store seam

All in-memory state and persistence live in **`WarpStore`**. `WarpManager` is a thin **static facade**
over a single `WarpStore` instance — it preserves the API the commands/listeners call and owns the
proxy-coupled concerns (data folder, logging, the file-walk load). Disk I/O is behind the **`WarpFileIo`**
interface (`ConfigurateWarpIo` in production, `FakeIo` in tests), which is what makes the store testable.

The store holds four invariants — treat them as load-bearing:

- **Crash-safe add/update (CORR-A):** the new file is written **before** the change is visible in memory.
  `update` applies the change to a *copy*, writes it, then swaps the map entry, then deletes the old file.
  A failed write leaves the live warp and its file untouched — no loss, no duplicate.
- **Crash-safe delete (CORR-B):** the file is deleted **before** the warp is forgotten. A missing or
  undeletable file keeps the warp in memory, so a stale file can never resurrect a "deleted" warp on the
  next load.
- **Thread safety (CONC-1):** the maps are `ConcurrentHashMap`s and compound operations use atomic
  primitives (`putIfAbsent`, etc.). Commands, suggestions, and message events all touch the store
  concurrently.
- **Atomic reload:** both maps live behind one `volatile` snapshot. A reload builds a fresh snapshot off to
  the side and swaps it in with a single reference write — lookups always see the whole old set or the
  whole new set, never a half-loaded map, and a reload that fails leaves the previous warps live.

**Visit counts** are dirty-tracked: `recordVisit` bumps the count in memory and marks the warp; `flushVisits`
writes only the warps that changed, through the same crash-safe `save` seam. Every write in the codebase
goes through the store — never load-modify-save a warp file directly.

### Adding a store operation

Add the method to `WarpStore` (keep it crash-safe: write/delete on disk before mutating memory), expose a
delegating method on `WarpManager` if commands need it, and add a `WarpStoreTest` case using `FakeIo` with
`failWrite`/`failDelete` to prove the failure path leaves memory and disk consistent.

## The plugin-message protocol

The proxy and backend communicate over three channels (`core/Channels.java`): `mcme:warp`,
`mcme:player-location`, `mcme:misc`. Because the two halves ship in one jar but deploy to separate
processes, **a version skew between them is a normal operational state**, so the protocol is explicit and
fail-loud rather than best-effort.

### Frame format

Every message is built and read through **`MessageFrame`**:

```
byte 0      protocol version (MessageFrame.PROTOCOL_VERSION)
byte 1      opcode (a subchannel enum's stable numeric code())
byte 2..n   the message body
```

- **Bump `PROTOCOL_VERSION` on any change to any message's layout.** A receiver on a different version
  rejects the whole message (`MalformedMessageException`) with a "redeploy both jars" log, instead of
  mis-reading a changed field layout (which previously risked teleporting a player to garbage coordinates).
- **Opcodes are stable numbers**, not enum names. Each subchannel enum has an explicit `code()` and a
  `fromCode()` that rejects unknown codes. Never renumber or reuse a code.
- Bodies decode with strict bounds (JDK `DataInputStream`, checked EOF) and a **trailing-byte check** —
  anything short or over-long is a `MalformedMessageException`.
- Both `MessageListener`s catch that exception and reject cleanly (log + honest player feedback).

### Message types

| Channel | Proxy → backend | Backend → proxy |
| --- | --- | --- |
| `mcme:warp` | `TeleportMessage` (SAME_SERVER / DIFF_SERVER / LOCAL_WARP) | `TeleportResult` (SUCCESS) |
| `mcme:player-location` | `RequestLocationMessage` (CREATE_PUBLIC / CREATE_PRIVATE / MOVE) | `PlayerLocationMessage` (the requested location) |
| `mcme:misc` | `MiscMessage` (UPDATE_COMMANDS) | — |

Example: creating a warp is a round trip — the proxy asks the backend "where is this player?"
(`RequestLocationMessage`), the backend replies with the coordinates (`PlayerLocationMessage`), and the
proxy writes the warp. Teleporting is the reverse: the proxy sends a `TeleportMessage`, the backend
teleports and replies `TeleportResult`.

### Adding a message or field

1. Add/extend the message class in `core/messageprotocols/`, serialising via `MessageFrame.write(opcode, …)`
   and reading via `MessageFrame.read(bytes, (opcode, in) -> …)`.
2. If it's a new subchannel, give the enum constant an explicit unused `code()`.
3. **Bump `PROTOCOL_VERSION`** — any layout change is a wire break; proxy and backend must be redeployed
   together.
4. Add a `MessageProtocolTest` round-trip and a rejection case.

> Request/response correlation (a request id) is intentionally **not** implemented — responses correlate
> by `(subchannel, warpName)`, and adding a correlation id would mean a stateful pending-request table. If a
> concrete need appears, it's a clean additive version bump (an `int` after the opcode).

## Commands

Commands are Brigadier, one class per command under `velocity/commands/`, registered in
`WarpVelocity.onProxyInitialization`. Command *literals are case-sensitive* (`setPublic`, `pcreate`).

Shared helpers:

- **`CommandUtils.getWarp(context, arg[, predicate])`** resolves a warp argument for the sender
  (private-first), enforces an access predicate, and throws a friendly `CommandSyntaxException` otherwise.
- **`WarpPredicates.usableBy` / `modifiableBy`** wrap `Warp.isUsable` / `isModifiable`.
- **`Permission`** enum holds every node; requirements are built from it in `WarpVelocity`.

To add a command: create a `register(Predicate<CommandSource> requirement)` returning a
`LiteralArgumentBuilder`, add a `Permission` node, wire it into the relevant command tree in
`WarpVelocity`, and route any warp mutation through `WarpManager` (never write files directly). Re-check
permission/ownership **inside** the executor if the action can also be reached via a plugin message — the
command requirement alone is not a security boundary (see below).

## Dynmap integration

On the backend, `WarpPaper` wires up `DynmapAPI` (a `softdepend` — absent Dynmap degrades gracefully to a
log line). `WarpWatcher` watches the backend's `warps/` directory and updates markers as files change.
`LayerParser` turns the backend `config.yml` `layers` block into `Layer` records (label / min-zoom /
priority); layer names must be mirrored in the proxy's `layer-keys` for `/wmanage setLayer` validation.
Private warps are never rendered — they don't live under a per-server folder, so a correctly-scoped
symlink never exposes them.

## Security model

Validation and authorization live at **both** the command layer and the message boundary, because a
modified client can forge plugin messages to the backend. The invariants:

- **SEC-1 — path containment.** `WarpPaths.resolveWarpPath` normalises and rejects any path escaping the
  warps directory, so a crafted warp name can't write outside the store.
- **SEC-2 — no message forwarding.** The proxy's `MessageListener` consumes every warp-channel message
  *unconditionally* before checking the source, so Velocity never forwards a client-sent message on to a
  backend (which would let a client impersonate the proxy). All three channels are registered on the proxy
  for this reason.
- **SEC-3 — re-auth at the message boundary.** The create/move handlers re-check permission and ownership
  when a `PlayerLocationMessage` arrives; they don't trust that the originating command was authorised.
- **SEC-4 — ownership on type changes.** `setPublic`/`setPrivate` and the edit commands resolve the warp
  through `modifiableBy`, so a player can't privatise a public landmark or edit a warp they don't own.

**Rule of thumb:** any code path that can be reached by a plugin message must not assume the sender was
authorised. Re-check inside the handler.

## Legacy & migrations

- **MyWarp importer.** `MyWarpDBConnector` + the `sql` config block are a one-shot importer from an old
  MyWarp MySQL install, used only when the warps directory doesn't exist yet. It's dormant in normal
  operation. It still reconstructs the old `zzz-` name style, so if it were ever re-enabled its output
  would need conversion — treat it as legacy.
- **The namespacing migration.** Moving from the old single-namespace / `zzz-<player>-` prefix scheme to
  per-creator private keys required a one-time data conversion of the existing store (strip prefixes, drop
  unconvertable entries). That conversion must ship **together** with the keying code — old-format private
  warps only load correctly under the new `(creatorId, name)` keys after conversion.

## Conventions

- **Match the surrounding code.** Comment density, naming, and structure should look like the file you're
  editing.
- **Route every warp write through the store.** No direct file I/O for warp data outside `WarpFileIo`.
- **Keep the testable logic platform-free.** If it's worth testing, it should be extractable into a pure
  helper and covered by a unit test with `FakeIo` or a plain input — not left inside a listener/command.
- **Treat the wire format as a contract.** Any change to a message means bumping `PROTOCOL_VERSION` and
  redeploying both plugins together.
- **Do not commit internal planning/design notes to this repo.** Keep the repo to code, README, and these
  manuals.
