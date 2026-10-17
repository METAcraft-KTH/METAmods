# METAcraft Booklet

The server's guidebook: [Booklet](https://github.com/Patbox/booklet) (Patbox's server-side, data-driven
guidebook, a dialog on a vanilla client) with a page for each thing METAcraft adds. `/guide` (anyone)
opens it on "Welcome to METAcraft": the METAcraft logo (`assets/metacraft/textures/booklet/image/logo.png`),
the server's idea, a link to our Discord, and the sections. The client test (below) photographs every page.

## The structure

```
Welcome to METAcraft            booklet:index (the welcome pack replaces Booklet's own index)
  Student Culture Features      metacraft:student_culture
    Chapter Drinks              metacraft:brewing (Cooking, Distilling, Barrels, Drinking, Drinks, IT's drinks)
    Banner Patterns             metacraft:banner_patterns
    (Pirkkos, order 30)         kept free for Ripsquid's pirkko mod, see below
    Ovvar                       metacraft:ovvar (The ovve, Patches, Sewing, Wardrobe and stash)
    Colors                      metacraft:moredyes
  Vanilla+ Features             metacraft:vanilla_plus
    Void Anchor                 metacraft:qol/void_anchor
    Silencing mobs              metacraft:qol/silence_mobs
    Concrete Crafting           metacraft:qol/concrete_cauldron
    New Decor                   metacraft:decorating (Canvas, Mailboxes, Rope, Sign posts, Lanterns and leads, Hammer and trowel)
  Nether Changes                metacraft:nether
    New Mobs                    metacraft:nether/mobs (Chained Ghast, Piglin Drummer, Piglin Grenadier)
    Unstable Portals            metacraft:rifts (Lighting a rift, Stabilising)
    Piglin Raid                 metacraft:nether/piglin_raid (sizes, the raid, the Piglin General, spoils)
    Amplified Nether            metacraft:nether/amplified
  End Changes                   metacraft:end
    Void Anchor                 the same page as under Vanilla+
```

A section is a page in `category=booklet:main_page` (`order=` 10, 20, 30, 40) that lists its own category
with `### Category Entries:`; each entry is a page in that category with an `order=` of its own, and the
same again one level down. A page can be in more than one category (`category=a,b`): the Void Anchor is
in Vanilla+ and End.

Two page ids are opened by other mods and must stay: `metacraft:ovvar` (ovvar's `/ovvar guide`) and
`metacraft:rifts` (the rift warning in chat, which lands on Unstable Portals).

## Packs are hooks

Each mod's pages are a built-in datapack in `src/main/resources/resourcepacks/<name>/`, registered only
when its mod is loaded (`MetacraftBooklet.HOOKS`). No chapter's mod is a dependency: a server without
ovvar has no ovvar pages, and no ovvar item icons to resolve. A section's own page is in a pack that is on
when *any* of its mods is (`Hook.anyOf`), so it lists only the pages that are there, and a server with
none of them has no such section.

| hook | on with | what it adds |
| --- | --- | --- |
| `welcome` | always | "Welcome to METAcraft", in place of Booklet's index "The Encyclopedia": what METAcraft is about, then the sections |
| `student_culture` | ovvar, brewery, metacraft-kultur or moredyes | the Student Culture Features section page |
| `ovvar` | ovvar | "Ovvar": the ovve, patches, sewing, wardrobe and stash |
| `brewing` | brewery | "Chapter Drinks": Brewery's own book, rewritten (its recipe is switched off here): cooking, distilling, barrels, drinking, Brewery's drinks |
| `brewing_kultur` | metacraft-kultur and brewery | "IT's drinks": kultur's drinks, under Chapter Drinks |
| `kultur` | metacraft-kultur | "Banner Patterns": ITK, QMISK, TMEIT, and Pirkko behind its pattern item |
| `moredyes` | moredyes | "Colors": Cerise and Laserviolet |
| `vanilla_plus` | metacraft-qol or polydecorations | the Vanilla+ Features section page |
| `qol` | metacraft-qol | "Void Anchor", "Silencing mobs" (the muffler), "Concrete Crafting" (the concrete cauldron), and the End Changes section page |
| `decorating` | polydecorations | "New Decor": canvas, mailboxes, rope, sign posts, lanterns and leads, hammer and trowel |
| `polydecorations_s6` | polydecorations | turns off every PolyDecorations recipe except those of the things above |
| `rifts` | nether-rifts | the Nether Changes section page and everything in it: New Mobs, Unstable Portals, Piglin Raid, Amplified Nether |

Amplified Nether describes a datapack that is not built or deployed yet (an amplified nether, more
biomes, no way above the roof), as what the nether will be like when it opens.

A new mod's pages: a folder under `resourcepacks/` with a `pack.mcmeta` (data format 121 for 26.3) and its
pages in `data/metacraft/booklet/pages/en_us/`, then one line in `HOOKS`. Its main page goes in its
section's category with an `order=`, and its mod id in that section's `Hook.anyOf`
(`everyPageStaysReachableWithModsMissing` fails otherwise). See Booklet's
[USAGE.md](https://github.com/Patbox/booklet/blob/master/USAGE.md) for the page format. Two gotchas: `-`
lines are lists and numbered lines are not, and `<anything>` in angle brackets is parsed as a tag.

**Pirkkos** is the spot left for Ripsquid's pirkko mod (not in this repo yet): its chapter is a pack of
its own with its main page in `category=metacraft:student_culture` and `order=30` (between Banner
Patterns and Ovvar), a `Hook` for its mod id, and that mod id added to the `student_culture`
`Hook.anyOf`. The Pirkko *banner pattern* is kultur's and is on Banner Patterns.

## Page images, beside the text

Every picture sits to the right of the text it goes with. Dialog bodies stack, so there is no such
layout in Booklet; `Beside` makes one. Booklet draws an image as rows of 9 px glyph lines, the height
of a line of text, so a page that names an image under `beside/`

    ### Image: metacraft:beside/ovvar/hero The text that goes to the left of the picture.<nl2>More.

has its text laid beside the picture (`ImageBodyMixin` swaps Booklet's own layout for `Beside`'s).
The picture is a font of the mod's own, `assets/metacraft/font/beside.json`: cut into 16×9 UI px tiles
(the height of a line of text), four image pixels to a UI pixel, the detail a 1080p screen at GUI scale 4
shows. Each line of the body is a line of text wrapped to the room on the left and padded to exactly
that width, a gap, and one row of tiles: only ordinary positive advances, so nothing depends on another
font's spacing on the client.

Two things the client is strict about. A bitmap glyph is as wide as its rightmost drawn column, so fully
clear pixels are written with alpha 1 (invisible, but drawn) or clear-edged tiles would come out narrow.
And the dialog's text widget wraps at the body width less 2 × 4 px and breaks the moment a line's running
width passes that, so the −1 spacer goes *before* each tile (after it, the last tile would overshoot by
one) and every line keeps a few pixels in hand; `Beside.SLACK`.

The isometric renders are made the way Patbox makes PolyFactory's, with his client-only
[Simple Image Renderer](https://github.com/Patbox/SimpleImageRenderer), driven by a client test instead of
by hand. `BookletRenders` builds each scene in a flat world (mannequins in patched ovvar, the Tester at a
sewing stand with a patch in hand and ovvar's washed-out preview on the stand, a painted wall of canvases, a mailbox with mail, rope, sign posts, a wall lantern, a lead between fences, a trowelled path) and renders it; it also gives the Tester a design and a stash, opens
`/ovvar stash` at GUI scale 1 and cuts the menu out of a screenshot at its own pixels. The script cuts
the renders into that font (`textures/font/beside/`, `font/beside.json`, and `beside/index.json`, which
`Beside` reads for each picture's width and rows):

    METACRAFT_BOOKLET_RENDER=1 ./gradlew :mods:metacraft-booklet:runClientGameTest
    python3 mods/metacraft-booklet/tools/booklet_images.py

`GuideTests` fails if a page names an image that is not there. A new scene is a line in
`BookletRenders.SCENES` and a few in its `build`.

## PolyDecorations: only what we keep

METAcraft keeps the PolyDecorations things that look like vanilla: the canvas, mailboxes, rope, sign
posts, the hammer and the trowel, and its changes to vanilla blocks (lanterns on walls, leads from fence
to fence, lanterns and hanging signs under rope). The furniture, statues and the rest are out.

The server runs our PolyDecorations fork
([Froosty11/PolyDecorations](https://github.com/Froosty11/PolyDecorations/releases), 0.13.1+26.3-rc-1.metacraft.1),
which can switch features off completely: no blocks, items, Polymer block states or resource-pack assets.
Which features are on is a METAcraft config: `config/polydecorations.json`, editable in game with
`/meta-config-screen polydecorations` (applies at the next restart). Its defaults are Season 6's
(`src/main/resources/metacraft-booklet/polydecorations-s6.json`): on a server's first start the
booklet writes them there before PolyDecorations reads the file (from the booklet's own mixin
plugin, which loads before PolyDecorations' plugin reads it), so no file has to be copied by hand. The dev runs
copy the same file into theirs before starting.

With upstream PolyDecorations, which has no such config, `polydecorations_s6` still hides the rest: it
overrides every other recipe and recipe advancement with a file whose only content is a `fabric:false`
load condition, which removes it. The files were generated from PolyDecorations 0.13.1+26.3-rc-1;
`polyDecorationsHasOnlyTheRecipesWeKeep` fails, naming them, if a newer version adds recipes. The pack
is enabled by default and can be turned off with `/datapack disable`.

## The question mark

Vanilla puts a warning button beside the title of every dialog a server sends. The resource pack
replaces its three sprites (`assets/minecraft/textures/gui/sprites/dialog/warning_button*.png`) with a
question mark in the same style. It applies to every server dialog, not only the book.

## Testing

    JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-booklet:runServer -PrunDir=/tmp/mcb
    ./gradlew :mods:metacraft-booklet:runClientGameTest

The dev runtime loads ovvar (run its `runDatagen` first) and the PolyDecorations fork with Season 6's
features, so every chapter is on.
`GuideTests` checks every page loads with a title, description and icon, is reachable from the index
(and stays reachable whichever of the mods are missing), names only pages and items that exist, that
`/guide` opens on the welcome page (with the logo, the Discord link and every section) and every section
lists at least one page, and that PolyDecorations is down to the recipes we keep.
`GuideClientTests` opens every page on a vanilla client in a 1080p window and photographs it, and again
scrolled down (`*_more.png`), into `build/run/clientGameTest/screenshots/`.
