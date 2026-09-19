# Ovvar wardrobe screen (design)

User: the stash chest UI should look "swag-i-skogen"; chosen: the full wardrobe screen, one font-drawn 9×6
chest, stitched-cloth art direction, preview on the right (look-only, hover tooltips), a "show on
mannequin" action for a 3D look.

## 1. Screen
`WardrobeGui` replaces `StashGui`: one sgui `SimpleGui` over `MenuType.GENERIC_9x6`. The container title is
a component in a custom font (`ovvar:wardrobe`) whose glyphs draw a full 176×166 background over the vanilla
chest (the negative-space + bitmap-glyph technique better-pets and metacraft-bundles use in this repo:
`space` provider with negative advances to move the cursor, one `bitmap` glyph per background, then the
stats text in the vanilla font). One background per chapter (7), generated from a single hand-drawn
template tinted to the chapter's cloth colour (DATA `#BD3754`, IT `#8A57BD`, IT kisel `#769BB0`,
MEDIA dark `#1B1B1B`, polymiter variants reuse their chapter): the ovve fabric fills the frame, panels are
outlined with white overlock stitching, the patch panel is a cream canvas pocket, the garment silhouette
stands in the right four columns.

Grid (slot index = row × 9 + col):
- Row 0: tabs — one per chapter the player owns an ovve of (cols 0..), then `top` and `feet` toggles;
  the stats strip ("earned N · sewn N · stash N") is drawn as text in the background, not slots.
- Rows 1–4, cols 0–4: the patch collection (20 slots) from the wardrobe stash, patch item + count +
  today's lore and hints.
- Rows 1–4, cols 5–8: the preview. Garment art in the background; each sewn patch is a hover-only item
  (no click handler) at the slot nearest its spot with a tooltip "<patch> on <spot>".
- Row 5: actions — take out (col 0), deposit (1), sew on stand (2), show on mannequin (3), help (7),
  close (8). Mode text in the middle drawn as background text ("survival · sewing on" / "minigame ·
  stash only").

## 2. Behaviour
Every action keeps today's semantics and callbacks (`Stash.withdraw`, deposit, `StashSession.start`,
`OwnedSewing.editingRefusal`, `StashConfig` gating: take out and sew only where allowed; minigame server →
look only; refusals in red chat as today). "Show on mannequin" spawns the existing showcase stand wearing
the player's current ovve two blocks in front of them (reusing the mannequin/showcase code) and closes the
screen. Tabs re-render the same GUI for another chapter/piece; a chapter the player owns no ovve of is
not shown. Stats: earned = patches ever granted (stash + sewn, from the wardrobe record), sewn = placements
on the current chapter/piece, stash = total unsewn.

## 3. Pack
`pack/WardrobeArt` generates: the 7 background PNGs (from `art/ovvar/wardrobe_template.png`, a greyscale
template with the stitching drawn in; tint = template luminance × chapter colour, stitching kept white),
`assets/ovvar/font/wardrobe.json` (space provider + 7 bitmap glyphs, ascent/height chosen so the glyph's
top-left lands on the chest's top-left when the title is drawn), and registers them through the existing
`RESOURCE_PACK_CREATION_EVENT`. The title component: `[space to −8][glyph for chapter][space back][stats
text]`.

## 4. Tests (game tests)
- `wardrobeTitleCarriesTheChapterGlyph`: the title component for each chapter contains that chapter's
  glyph char and the wardrobe font.
- `wardrobeSlotsFollowTheWardrobe`: a wardrobe with 3 stash kinds and 2 sewn placements → 3 collection
  slots, 2 preview items at the expected slots, actions per mode (survival vs minigame).
- `wardrobeActionsRespectTheMode`: on a minigame config, take out/sew are absent, look-only hint present.
- `foreignOvveHasNoWardrobeTab`: the tabs list only owned chapters.
- Pack: font JSON and 7 PNGs present, PNG size 176×166.

## 5. Out of scope
Animated backgrounds, per-patch custom icons, a book view. The old `StashGui` is deleted.
