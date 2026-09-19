# Ovve Design Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player's sewn patches live in one shared, versioned "design" record keyed by player UUID and chapter, so an ovve looks the same on every METAcraft server and a recrafted ovve just picks the design up, while a patch can never be duplicated by owning two ovves.

**Architecture:** The ovve item carries an `ovvar:owner` UUID and a read-only snapshot of the owner's patches for drawing. Every sew and unpick is a compare-and-set write against the design store (file directory or JDBC), done off-thread and confirmed on the server thread before a patch item is consumed or handed back. Items refresh their snapshot from the cached design every tick.

**Tech Stack:** Fabric 26.3-rc-1, Polymer, plain JDBC (MariaDB and PostgreSQL drivers bundled jar-in-jar), H2 in-memory for the JDBC game test, Fabric game tests.

**Spec:** the conversation of 2026-09-12 (design keyed by UUID, visuals only, one patch lives in exactly one place, CAS versions, refuse writes when the store is unreachable, everything configurable).

## Global Constraints

- The item snapshot (`ovvar:patches`) is never a source for an unpick on an owned ovve; only the store is.
- A patch item is consumed at click time and refunded on any failure; it is handed back on unpick only after the store confirmed the write.
- All store I/O runs on a single-thread executor; results are applied via `server.execute` on the server thread.
- Everything is in `config/ovvar.json` under `designs`: backend, file directory, JDBC url/user/password/password env var/table/driver/timeouts, bind-on-pickup, sew/unpick when unreachable, retry seconds, query logging.
- Game tests run with `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:ovvar:runServer --offline -q` after `runDatagen`.

---

## File structure

- Create `store/Design.java` — record `(SpotPlacements patches | null, long version)` + JSON codec. Version 0 means "not in the store".
- Create `store/DesignKey.java` — record `(UUID owner, Chapter chapter)`.
- Create `store/DesignBackend.java` — interface: `Map<Chapter, Design> loadAll(UUID)`, `boolean store(DesignKey, Design next, long expectedVersion)`, `close()`.
- Create `store/FileBackend.java` — `<dir>/<uuid>/<chapter>.json`, atomic temp-file rename, CAS by re-reading the version.
- Create `store/JdbcBackend.java` — one table, CREATE IF NOT EXISTS, INSERT for version 0 else UPDATE ... WHERE version = ?, single reconnecting connection.
- Create `store/Designs.java` — lifecycle, cache per owner, async fetch, `update` with outcomes OK / CONFLICT / UNREACHABLE / NOT_LOADED, retry queue for writes allowed while unreachable, `/ovvar store` helpers.
- Modify `OvvarConfig.java` — add `DesignStoreConfig designs`.
- Modify `content/ModComponents.java` — `OWNER` (UUID, persistent) and `PENDING_SEW` (Placement, persistent).
- Modify `content/OvveItem.java` — bind on pickup, refresh snapshot from the design, run pending smithing sews.
- Modify `sewing/StandSewing.java` — `finish` and the shears unpick go through `Designs`.
- Modify `recipe/SewRecipe.java` — result marked `PENDING_SEW` for the tick to commit.
- Modify `ModCommands.java` — `give` binds and writes the design, `patches` writes the design, new `/ovvar store status|show|reload`.
- Modify `build.gradle` — bundle the drivers, H2 at dev runtime.
- Modify `gametest/OvvarGameTests.java` — tests below. Modify `README.md`.

---

### Task 1: Config

**Files:** Modify `src/main/java/metacraft/ovvar/OvvarConfig.java`; create `src/main/java/metacraft/ovvar/store/DesignStoreConfig.java`.

**Produces:** `OvvarConfig.designs()` returning `DesignStoreConfig` with `backend()`, `fileDirectory()`, `jdbc()` (`url, user, password, passwordEnv, table, driverClass, connectTimeoutSeconds, queryTimeoutSeconds`), `bindOnPickup()`, `sewWhenUnreachable()`, `unpickWhenUnreachable()`, `retrySeconds()`, `logQueries()`.

- [ ] Add the record with a `MapCodec` whose every field has a default (`optionalFieldOf(name, default)`), so an existing `config/ovvar.json` without `designs` still parses. Defaults: `FILE`, `""` (= `<world>/ovvar/designs`), refuse when unreachable, bind on pickup, retry 15 s.
- [ ] Compile: `./gradlew :mods:ovvar:compileJava --offline -q`.
- [ ] Commit `ovvar: design store config`.

### Task 2: Design records and the file backend

**Files:** create `store/Design.java`, `store/DesignKey.java`, `store/DesignBackend.java`, `store/FileBackend.java`; test in `gametest/OvvarGameTests.java`.

- [ ] Game test `fileBackendStoresWithVersions`: temp dir; `loadAll` empty; `store(key, design v1, 0)` true; `store(key, v1', 0)` false (already exists); `store(key, v2, 1)` true; `store(key, v2', 1)` false; `loadAll` returns v2.
- [ ] Implement; run tests; commit `ovvar: design records and file backend`.

### Task 3: JDBC backend

**Files:** create `store/JdbcBackend.java`; `build.gradle`; test.

- [ ] `build.gradle`: `include`+`implementation` `org.mariadb.jdbc:mariadb-java-client:3.5.10` and `org.postgresql:postgresql:42.7.13`; `runtimeOnly "com.h2database:h2:2.5.250"` for dev/test only.
- [ ] Game test `jdbcBackendStoresWithVersions`: same sequence against `jdbc:h2:mem:ovvar_<random>;DB_CLOSE_DELAY=-1`.
- [ ] Implement with standard SQL only (`CHAR(36)`, `VARCHAR(32)`, `BIGINT`, `TEXT`), `Class.forName(driverClass)` when set, `DriverManager.setLoginTimeout`, `Statement.setQueryTimeout`, reconnect on `SQLException`.
- [ ] Commit `ovvar: jdbc design backend`.

### Task 4: Designs service

**Files:** create `store/Designs.java`; register in `Ovvar.onInitialize`; test.

**Produces:**
```java
public static boolean loaded(UUID owner);
public static Optional<Design> cached(DesignKey key);       // empty when not loaded or absent
public static void fetch(UUID owner);                         // async, no-op while in flight
public static void update(DesignKey key, UnaryOperator<Design> change, Consumer<Outcome> done);
public enum Outcome { OK, CONFLICT, UNREACHABLE, NOT_LOADED }
public static boolean pending(DesignKey key);                 // a queued write not yet in the store
```
- [ ] Game test `designsUpdateIsCompareAndSet`: with the file backend, `fetch` then poll until `loaded`; `update` sew → OK and cached version 1; corrupt the file's version behind its back (write v5) → next `update` → CONFLICT and the cache re-fetches to v5.
- [ ] Implement: `SERVER_STARTING` opens the backend from config, `SERVER_STOPPING` closes and clears; `JOIN` fetches; retry queue ticks every `retrySeconds`.
- [ ] Commit `ovvar: designs service`.

### Task 5: Owner and snapshot on the item

**Files:** `ModComponents.java`, `OvveItem.java`; test.

- [ ] Game test `twoOvvesShareOneDesign`: player mock with two ovves of one owner in the inventory; `update` sews a patch; after ticks both snapshots show it; `update` removes it; both lose it.
- [ ] `inventoryTick`: (a) no owner and holder is a player and `bindOnPickup` → set `OWNER`; if the owner's design is loaded and absent, insert the snapshot as version 1 (first design); (b) owner set and design loaded and no pending write → copy design patches into the snapshot when different; (c) `PENDING_SEW` present → commit it via `Designs.update`, refund on failure.
- [ ] Commit `ovvar: ovves are owned; the snapshot follows the design`.

### Task 6: Sewing and unpicking through the store

**Files:** `StandSewing.java`, `SewRecipe.java`; test.

- [ ] `finish`: consume the patch first; unowned → local sew as before; owned → `Designs.update`; OK → celebrate, CONFLICT / NOT_LOADED → refund and message, UNREACHABLE → refund unless `sewWhenUnreachable`, in which case the local sew stands and the write is queued.
- [ ] Unpick: owned → `Designs.update(remove)`; the patch item is given only on OK; UNREACHABLE gives it only when `unpickWhenUnreachable`.
- [ ] `SewRecipe.assemble`: sew on the copy and set `PENDING_SEW`.
- [ ] Game test `unpickGivesThePatchOnceAcrossOvves`: owner with two ovves and a sewn patch; unpick via the same path the stand uses on ovve A → one patch item; the same unpick on ovve B → nothing (spot empty after refresh).
- [ ] Commit `ovvar: sew and unpick against the design store`.

### Task 7: Commands, README, config docs

- [ ] `give` sets the owner to the target and replaces their design; `patches` replaces the design of the held ovve's owner; `/ovvar store status`, `show <player>`, `reload <player>`.
- [ ] README: new section "Designs" (what is shared, config keys, backends, dupe rules), command list.
- [ ] Commit `ovvar: store commands and docs`.
