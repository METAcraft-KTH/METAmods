# Deploying mods to the servers

Every server gets the mods on its list, each as its own jar. A push uploads only the jars that changed
and deletes mods that were taken off the list. Changes take effect at the server's next restart.

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

You never list `metacraft-lib`, `metacraft-core` or `metacraft-zones`: anything a listed mod depends on
is added for you. If a mod needs another of our mods that isn't on the list, the build fails and names
both. Add the missing one, or take the first one off.

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

## Changing one of our mods

Nothing to do beyond pushing the change: the deploy works out which jars differ from what the server
has and uploads only those. Bump the mod's version in `gradle.properties` (e.g.
`faster_minecarts_version`) so the server's mod list and bug reports show which build is running.

## What a deploy did

Every deploy run has a summary (the run's page on GitHub → Summary) with, per server:

- **uploaded**: new or changed jars, with versions;
- **removed**: mods deleted at the next restart;
- **unchanged**: everything else.

It also publishes a release, `deploy-<server>-<date>-<commit>`, with that server's exact jars. Releases
are kept forever.

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

Jars it didn't deploy stay as they are, for example Fabric API or Polymer put in `mods/` by hand. It
keeps track of what it deployed in `mods/metacraft-deploy.json` on each server. Don't edit or delete
that file; if it is lost, the next deploy uploads everything again, which is harmless.

The deploy owns `mods/update`: it is a staging folder, not a place to put jars by hand. Any jar found
there that isn't part of the deploy is deleted by the next deploy. Jars put in `mods/` itself by hand
are still left alone.
