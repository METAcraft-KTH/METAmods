# Deploying mods to the servers

Every server gets the METAmods modules on its list, each as its own jar. A push uploads only the jars
that changed and deletes mods that were taken off the list. Changes take effect at the server's next
restart.

The lists are not the server's whole mod list. Fabric API, Polymer, datapacks and anything else the
server runs are added by hand through the Pterodactyl panel, as before, and the deploy never touches
them (see [What the pipeline doesn't touch](#what-the-pipeline-doesnt-touch)).

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

The deploy only removes mods it deployed itself. If someone replaced one of them by hand with a jar of
the same mod id, that jar is deleted too: the deploy tracks its mods by mod id, and Fabric loads only
one jar per mod id anyway.

## A mod from outside the repo that our modules depend on

Only for a mod that one of our modules directly depends on but doesn't embed (jar-in-jar), so that the
module and the mod it needs reach the server together. It is not a way to manage the server's other
mods: those are added through the Pterodactyl panel, and the lists don't name them.

```
external some-library https://github.com/someone/some-library/releases/download/v1.4.0/some-library-1.4.0.jar sha256:3f2a…
```

- `some-library` is the jar's mod id (the `id` in its `fabric.mod.json`).
- If that mod is already on the server by hand, the deploy's jar replaces it (same mod id), and from
  then on the deploy manages it: taking the line off deletes it.
- Use a URL that never changes: a release download, not "latest".
- Get the hash with `shasum -a 256 ovvar-1.4.0.jar`. The build refuses a jar whose hash doesn't match,
  so a changed or tampered download never reaches a server.
- To update it, change the URL and hash in the same line.
- If a listed module depends on it and it is neither embedded nor listed, the build doesn't fail on
  that: mods that aren't ours are assumed to be on the server by hand.

## Changing one of our mods

Nothing to do beyond pushing the change: the deploy works out which jars differ from what the server
has and uploads only those. Bump the mod's version in `gradle.properties` (e.g.
`faster_minecarts_version`) so the server's mod list and bug reports show which build is running.

## What a deploy did

Every deploy run has a summary (the run's page on GitHub → Summary) with, per server:

- **uploaded**: new or changed jars, with versions;
- **removed**: mods deleted at the next restart;
- **unchanged**: everything else.

A deploy to any server but test also publishes a release, `deploy-<server>-<date>-<commit>`, with that
server's exact jars. Releases are kept forever. The test server gets none: it is never rolled back.

Deploys to one server run one at a time. If several pushes queue up for the same server, the newest one
wins: a queued deploy or rollback that a newer one replaces is cancelled, and a push that is no longer
its branch's newest commit when its turn comes deploys nothing (its run says so). If the newest push
fails to build, nothing is deployed until a later push builds.

## Rolling back

To put a server back to an earlier state:

1. Find the release: Releases → `deploy-<server>-…` from before the problem.
2. Actions → the build workflow → **Run workflow**, on `dev` (rollback only runs from the default
   branch; you still choose any server there). Choose the `server` and paste the `release` tag.
3. It uploads what differs from the server's current state and removes what that release didn't have.
   Restart the server.

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

## What the pipeline doesn't touch

The deploy only adds, replaces and removes the mods it deployed itself. It keeps track of them, by mod
id, in `mods/metacraft-deploy.json` on each server. Every other mod stays as it is, for example Fabric
API or Polymer put in `mods/` by hand: the deploy never uploads over it, never lists it for removal and
never deletes it. The one exception is `metacraft`, the old all-in-one bundle of our modules, which the
separate jars replace: every deploy lists it for removal.

The deploy stages its uploads in `mods/update`, where autodeploy picks them up at the restart. A jar
there that isn't in the set being deployed is deleted only if it is one of the deploy's own mods (an
upload no restart has installed yet, or a jar of one of its mods put there by hand). A jar of any
other mod is left there for autodeploy to install, as before.

Don't edit or delete `mods/metacraft-deploy.json`. If it is lost, the next deploy uploads everything on
the list again, which is harmless, but it no longer knows mods it deployed before that have since been
taken off the list: delete those by hand.

## Testing the deploy tooling

CI runs the deploy tooling's unit tests (`./gradlew -p buildSrc test`), builds every server's set, and
checks that two clean builds give the same jars (`.github/scripts/check-reproducible.sh`).

The end-to-end test runs by hand, because it needs `autodeploy.jar` and METAcraft-KTH/FabricModsUpdate
is private. It deploys the real test set to an SFTP server in Docker and runs autodeploy as the
restart: the refusal with an old autodeploy, the switch from the bundle, a re-deploy, one changed mod,
a removal and a rollback. Run it when you change the deploy tooling or autodeploy:

```
./gradlew deploySet -Pserver=test
.github/scripts/e2e.sh path/to/autodeploy.jar     # v1.1 or later
.github/scripts/e2e.sh path/to/FabricModsUpdate   # or a checkout, outside this one: builds autodeploy.jar
```

It needs Docker, Java, `jq`, `python3` and `shasum`, and touches no real server. To build from a
checkout, point `AUTODEPLOY_JAVA_HOME` at a JDK 17: FabricModsUpdate's Gradle doesn't run on Java 25.
