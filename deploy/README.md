# Deploying mods to the servers

Every server gets the mods on its list, each as its own jar, and the datapacks on its list, each as a
folder in the world's `datapacks`. A push uploads only what changed and deletes what was taken off the
list. Mod changes take effect at the server's next restart; datapack changes at `/reload` (see
[When changes apply](#when-changes-apply)).

## Which branch goes where

`deploy/servers.json` says which servers a branch deploys to:

| Branch     | Servers          |
|------------|------------------|
| `dev`      | test             |
| `prod`     | survival, test   |
| `minigame` | event            |

Pushing to any other branch, or opening a PR, builds and tests but deploys nothing. Every run keeps
the built sets as the `deploy-sets` artifact, and a PR's run shows (job **preview**) exactly what
merging it *would* upload to and remove from each server of the target branch.

## Adding one of our mods to a server

1. Open `deploy/<server>.txt` on the branch that deploys to it (for survival: `prod`).
2. Add the mod's folder name from `mods/`, on its own line:
   ```
   faster-minecarts
   ```
3. Push. The next restart of that server loads it.

You never list `metacraft-lib`, `metacraft-core`, `metacraft-zones` or `metacraft-config`: anything a
listed mod depends on is added for you. If a mod needs another of our mods that isn't on the list, the
build fails and names both. Add the missing one, or take the first one off.

A brand-new mod also needs its `include "mods:<name>"` line in `settings.gradle`, as today. Being in
`settings.gradle` does *not* put a mod on any server; being on a list does.

## Removing a mod from a server

Delete its line from `deploy/<server>.txt` and push. The deploy tells the server to delete it. At the
next restart it is gone, together with any other changes.

Taking a mod off a list deletes that mod id from the server even if its jar was put there by hand.

## Adding a mod from outside the repo

For jars built elsewhere (ovvar, metacraft-booklet, the PolyDecorations fork, a Modrinth mod):

```
external ovvar https://github.com/Froosty11/ovvar/releases/download/v1.4.0/ovvar-1.4.0.jar sha256:3f2a…
```

- `ovvar` is the jar's mod id (the `id` in its `fabric.mod.json`).
- Use a URL that never changes: a release download, not "latest".
- Get the hash with `shasum -a 256 ovvar-1.4.0.jar`. The build refuses a jar whose hash doesn't match,
  so a changed or tampered download never reaches a server.
- To update it, change the URL and hash in the same line.

## Adding a datapack

```
datapack planked_chests https://example.org/planked-chests-1.0.0.zip sha256:9c1e…
datapack vt_anti_enderman_grief vanillatweaks:26.3/gameplay%20changes/anti%20enderman%20grief sha256:0cbd…
```

- The deploy unpacks the zip into the folder `<world>/datapacks/planked_chests/` on the server, where
  you can open it in Pterodactyl's file manager. Minecraft sees it as the pack `file/planked_chests`, so
  `/datapack list`, `/datapack disable "file/planked_chests"` and `/reload` work as for any datapack.
  The folder keeps the pack exactly as its zip has it, overlays included: Minecraft applies them.
- `<world>` is `level-name` from the server's `server.properties` (`world` if it has none). If
  `level-name` changes, the next deploy puts the datapacks in the new world and leaves the old world's
  folders alone.
- The name is the folder's: lower-case letters, digits, `_` or `-`.
- The hash is the zip's (`shasum -a 256 pack.zip`), as for a jar.
- To update a pack, change the URL and hash in its line. Taking the line off deletes its folder.

The deploy only ever touches folders it put there itself (it keeps track in
`mods/metacraft-deploy-datapacks.json`). Other packs in `datapacks`, by hand or as zips, are left alone,
and a list line whose folder already exists without the deploy having put it there is skipped with a
warning. A pack you disabled with `/datapack disable` stays disabled through updates.

Vanilla Tweaks has no download URL that lasts, so `vanillatweaks:<version>/<category>/<pack>` fetches
one of their packs at build time, by the names their datapack picker shows: lower-case, `%20` for a
space, `%2F` for the slash in `decorative%2Fcosmetic`. Their terms don't allow sharing the packs
unchanged, so never commit their zips here. To get a pack's hash, download that pack from
vanillatweaks.net for the same version and run `shasum -a 256` on its zip (inside the `UNZIP_ME` zip).

### Textures

A datapack's `assets/` (textures and models for its items) don't work from a datapack folder, so the
build makes them a resource pack of their own, which `metacraft-resource-packs` sends to players. The
datapack folder gets everything but `assets/`.

- The server's list must have `metacraft-resource-packs`; without it, the build fails for a pack with
  `assets/` and says so.
- The deploy uploads the resource pack as
  `config/metacraft-resource-packs/resource-packs/update/<name>.zip`. The mod moves it into
  `resource-packs/` at its next start or `/metacraft-resource-pack reload`, and adds it to its
  `config.json5`. An update replaces the zip under the same name, so the mod keeps its entry.
- **A new resource pack reaches nobody until it is global.** The mod adds packs it finds as not global
  (sent only by `/metacraft-resource-pack send`). Once per pack: after the restart or reload that adds
  it, set `"global": true` on its entry (the one whose `file` ends in `<name>.zip`) in
  `config/metacraft-resource-packs/config.json5`, then run `/metacraft-resource-pack reload` straight
  away. The deploy summary reminds you. The deploy doesn't edit that file: the mod rewrites it, and
  picks each pack's id itself.
- Taking the datapack off the list deletes `<name>.zip` from `resource-packs/` and `update/`. Run
  `/metacraft-resource-pack reload` (or restart) soon after: until then, a player who joins can't
  download that pack.
- A pack with `assets/` inside one of its overlays fails the build: put the textures in a resource pack
  of their own.

### Edits made on the server

You can edit a datapack's folder on the server, but the deploy then leaves that pack alone: it neither
updates it nor removes it, so your edit is never overwritten. The deploy summary (and the run's
annotations) shows a warning naming the pack and every file that differs from what the deploy put
there. To let the deploy manage the pack again, either:

- delete the folder: the next deploy installs the pack again, as on its list; or
- undo your edit, so the folder holds exactly what the deploy put there.

To keep a change for good, change the pack's zip and its line instead.

## When changes apply

The summary of every deploy ends with what to do:

- **Needs a restart**, when a mod jar changed, or when a changed datapack has worldgen
  (`data/*/worldgen/`, `data/*/dimension/` or `data/*/dimension_type/`), which `/reload` doesn't apply.
- **Datapacks changed: apply with `/reload` or the next restart**, when only datapacks changed. `/reload`
  also enables a new pack's folder: it looks for packs again and enables every one that isn't
  disabled in the world (checked against 26.3's `ReloadCommand.discoverNewPacks`). A removed folder's
  pack is gone after `/reload`.
- **Resource packs changed: apply with `/metacraft-resource-pack reload` or the next restart**. Plain
  `/reload` doesn't reload resource packs.

Nothing is reloaded or restarted for you.

## Changing one of our mods

Nothing to do beyond pushing the change: the deploy works out which jars differ from what the server
has and uploads only those. Bump the mod's version in `gradle.properties` (e.g.
`faster_minecarts_version`) so the server's mod list and bug reports show which build is running.

## What a deploy did

Every deploy run has a summary (the run's page on GitHub → Summary) with, per server:

- **uploaded**: new or changed jars, with versions;
- **removed**: mods deleted at the next restart;
- **unchanged**: everything else;
- for datapacks: which were installed, updated, removed and unchanged, which resource packs were
  uploaded or removed, and a warning for each pack left alone;
- what to do for the changes to apply ([When changes apply](#when-changes-apply)).

It also publishes a release, `deploy-<server>-<date>-<commit>`, with that server's exact jars and
datapacks. Releases are kept forever.

Deploys to one server run one at a time. If several pushes queue up for the same server, the newest one
wins: a queued deploy or rollback that a newer one replaces is cancelled, and a push that is no longer
its branch's newest commit when its turn comes deploys nothing (its run says so). If the newest push
fails to build, nothing is deployed until a later push builds.

## Rolling back

To put a server back to an earlier state:

1. Find the release: Releases → `deploy-<server>-…` from before the problem.
2. Actions → the build workflow → **Run workflow**, on `dev` (rollback only runs from the default
   branch; you still choose any server there). Choose the `server` and paste the `release` tag.
3. It uploads what differs from the server's current state and removes what that release didn't have,
   datapack folders included (except folders edited on the server, as for any deploy). Restart the
   server, or `/reload` if only datapacks changed.

A rollback compiles no mods, so it works even when a mod doesn't compile. It still needs `dev`'s
Gradle build to configure.

## When a deploy refuses

- **"update autodeploy.jar on <server> first"**: that server's autodeploy is too old to delete mods.
  Put `autodeploy.jar` from METAcraft-KTH/FabricModsUpdate's releases (v1.1 or later) on the server, in
  place of the old one, and restart it once. The startup line stays the same. Nothing was uploaded, so
  push again (or re-run the job) afterwards.
- **"<mod> needs <other mod>, which is not on <server>'s list"**: add the other mod to the list, or take
  the first one off.
- **"unknown project <name>"**: a typo, or the mod isn't in `settings.gradle`.
- **"sha256 mismatch for <url>"**: the external jar isn't the one you pinned. Check the URL, and update
  the hash only if you meant to change the jar.
- **"a list may not contain metacraft"**: that's the old all-in-one bundle. Every deploy deletes it,
  and it is never listed.
- **"… has textures (assets/), which reach players through metacraft-resource-packs"**: add
  `metacraft-resource-packs` to the list, or use a pack without textures.
- **"level-name in <server>'s server.properties: … is not a safe relative path"**: the deploy only
  writes inside the server's folder; fix `level-name`.

## What the pipeline doesn't touch

Jars it didn't deploy stay as they are, for example Fabric API or Polymer put in `mods/` by hand. It
keeps track of what it deployed in `mods/metacraft-deploy.json` on each server. Don't edit or delete
that file; if it is lost, the next deploy uploads everything again, which is harmless.

The deploy owns `mods/update`: it is a staging folder, not a place to put jars by hand. Any jar found
there that isn't part of the deploy is deleted by the next deploy. Jars put in `mods/` itself by hand
are still left alone.

For datapacks it keeps track in `mods/metacraft-deploy-datapacks.json`; don't edit or delete that file
either. If it is lost, the deploy no longer knows its own folders and skips each one with a warning
(as for a folder it didn't put there) until you delete it. While it replaces a folder it uses
`.<name>.deploying` and `.<name>.replaced` next to it, which a later deploy cleans up if one was
interrupted; they never hold a whole pack.

## Testing the deploy tooling

CI runs the deploy tooling's unit tests (`./gradlew -p buildSrc test`), builds every server's set, and
checks that two clean builds give the same jars and datapacks (`.github/scripts/check-reproducible.sh`).
The unit tests deploy datapacks to a server in memory and to an SFTP server in the test itself.

The end-to-end test runs by hand, because it needs `autodeploy.jar` and METAcraft-KTH/FabricModsUpdate
is private. It deploys the real test set to an SFTP server in Docker and runs autodeploy as the
restart: the refusal with an old autodeploy, the switch from the bundle, a re-deploy, one changed mod,
a removal and a rollback. It has no datapacks, which autodeploy doesn't handle. Run it when you change the deploy tooling or autodeploy:

```
./gradlew deploySet -Pserver=test
.github/scripts/e2e.sh path/to/autodeploy.jar     # v1.1 or later
.github/scripts/e2e.sh path/to/FabricModsUpdate   # or a checkout, outside this one: builds autodeploy.jar
```

It needs Docker, Java, `jq`, `python3` and `shasum`, and touches no real server. To build from a
checkout, point `AUTODEPLOY_JAVA_HOME` at a JDK 17: FabricModsUpdate's Gradle doesn't run on Java 25.
