# Rivals Paint

METAmods module `mods/rivals-paint` (mod id `rivals-paint`). A Splatoon-style paint
prototype for vanilla clients: Fabric + [Polymer](https://polymer.pb4.eu), Minecraft 26.3-rc-1, Java 25.
(Loader 0.19.5, Fabric API 0.160.3+26.3, Polymer 0.18.0+26.3-rc-1 — all from the root
`gradle.properties`. The module is not shipped in `dist`; it runs on a server of its own.)
Players need only the auto-served resource pack. Design: `docs/superpowers/specs/2026-09-11-metacraft-rivals-paint-prototype-design.md`
(v1), `docs/superpowers/specs/2026-09-12-metacraft-rivals-v2-design.md` (v2: feel, art, any-block
paint, ink), `docs/superpowers/specs/2026-09-12-metacraft-rivals-v3-design.md` (v3: gloss that
actually renders, real squid form, blobby bouncing shots, three more weapons) and
`docs/superpowers/specs/2026-09-12-metacraft-rivals-v4-connected-paint-design.md` (v4: two teams,
connected paint, the shader-drawn border). v6 added the two visual features described below: paint on
non-full blocks as block displays of the real paint state, and ink on the screen from damage taken.
v7 is Julle's four weapon models, the roller that replaces the sprayer, held-use fire, Splatoon 1's
own numbers on a hundred-unit tank, an ink LED nobody can see, and screen ink drawn from four textures
an artist can paint over. v8 is the seam to the MAIN datapack — the running flag it starts and stops
rounds with, the per-side function that ends one, and the per-player board its outro reads — and the
floors off redstone wire, because the arena is built with redstone on it.

**Standalone.** This module is not bundled into the `dist` jar (root `build.gradle`, `standaloneMods`):
its pack retextures sculk vein, resin clump, pale moss carpet and five wooden buttons as
paint, which only a dedicated Rivals server wants.

## How it works

- Two sides, one colour each: **DATA** `#BD3754` and **IT** `#8A57BD`. A side *is* a plain vanilla
  scoreboard team, and which team is configurable — `config/rivals-paint/teams.json`,
  `{"teams": {"data": "main.data", "it": "main.it"}}`, where the keys are the two colour slots and each value
  is the scoreboard team name that slot uses. It defaults to `main.` plus the slot's id — the two teams the
  MAIN datapack runs on the minigame server, so that server needs no file; any other server points a slot at one
  instead of keeping a second pair. `PaintColor.byTeam` — the single question anything here asks about a
  player's side — resolves through those names, `/rivals setup` creates any that do not exist and says
  which it made, and `/rivals reload` re-reads the file. Players join with `/team join <name>`.
- A painted region renders as a continuous sheet, not a decal picked at random per cell. A
  single-face cell is `ConnectedPaintBlock`: server-side properties for its face and four in-plane
  connection bits — which of its floor/wall neighbours hold the same colour on the same face —
  recomputed from those neighbours every time one changes. The bits travel to vanilla clients as
  the low nibble of a texture's red channel, and the fragment shader turns them into a rounded,
  gently wobbling edge wherever the sheet actually ends, so there are no edge tiles to draw and the
  border is a smooth curve at any resolution (see the shader bullet below). A cell painted on more
  than one face (a corner: floor plus wall in the same air cell) falls back to `PaintBlock`, the
  plain multiface splat that carries no bits.
- Both block kinds are sent to vanilla clients as blockstates borrowed from **five donors**. Every
  state paint borrows has to be **inert** on a vanilla client — no collision, no light, no water, and
  no `animateTick` that emits anything — because a pack can repaint a borrowed state but it cannot
  make the client stop simulating it. `PaintStates.inert` is that rule in one place, and the table is
  checked against it state by state at class load, so a vanilla change that gives a donor collision or
  a particle of its own is a start-up failure rather than a report from a server.

  | donor | inert states | what is struck out, and why |
  |---|---|---|
  | sculk vein | 64 | its 64 waterlogged states: a waterlogged state carries a water `FluidState`, so the client draws a full block of water in the cell and predicts swimming in it |
  | resin clump | 64 | nothing — it has no `waterlogged` |
  | pale moss carpet | 81 | its 81 `base=true` states: `MossyCarpetBlock.getCollisionShape` returns a real box for those, so a client would stand a notch above the paint and disagree with the server about where the player is |
  | the crimson, warped, bamboo, pale oak and poplar buttons | 24 each | nothing — no `animateTick` (a lever has one), no collision, no `waterlogged`, and `ButtonBlock.entityInside` returns on `isClientSide` before it reads anything, so even the wooden ones an arrow can press are inert on a client |

  329 inert states against 306 in use (153 per colour: 96 connected face×bits combinations, then 57
  corner masks), so the table fits with 23 button states to spare.

  **Why not redstone wire.** It held the floors and ceilings until the arena was built: the pack
  override is the donor's *whole blockstate file*, so every block of that kind anybody places anywhere
  in the world draws as paint — and Paint Splat Town has redstone on it. The 64 unpowered wire states
  the floors used are now wooden buttons instead (crimson, warped, bamboo, pale oak, poplar: 24 each,
  120 for the 64 the floors need and the 33 the corner masks take after the carpet), picked for being
  the ones nobody builds with. The stone button, which held 24 of the corner masks, went the same way
  and for the same reason. A game test asserts wire is not a donor, so it cannot come back by accident.

  **Why not tripwire.** A donor has to be a block *nobody else* hands out, because the pack override
  is a whole file: `assets/minecraft/blockstates/<donor>.json`, every state of the block mapped to a
  paint model. Polymer's own block pool — `eu.pb4.polymer.blocks.api.BlockModelType`, the thing
  `PolymerBlockResourceUtils.requestBlock` serves — hands out vanilla states to any mod that asks, and
  writes that block's blockstate file itself. Tripwire is in that pool (`TRIPWIRE`, `TRIPWIRE_FLAT`),
  and moredyes' carpets sit in it: on the minigame server, which ships moredyes, Rivals and ovvar in
  one dist, the two overrides double-booked tripwire and **every floor cell drew nothing at all**
  while wall paint carried on drawing. So tripwire is gone as a donor. None of the eight is in any
  `BlockModelType` pool, which is pinned by a game test (`donorsAreOutsidePolymersBlockPools`) that
  reads Polymer's own pool table and asserts no donor is in it.

  **The rule for adding a donor.** It must (1) be absent from every Polymer `BlockModelType` pool —
  the test above will say so — (2) be inert in every state it lends, which `PaintStates.inert` decides
  and the start-up check enforces, (3) have no client-side behaviour a pack cannot silence (which
  rules out the climbables: a client predicts climbing from the block it *sees*, so a vine-backed cell
  would stick players to walls), (4) bring enough states that the cell kind it serves is covered
  outright, since `PaintStates` throws at class load rather than reuse a state or run a pool dry, and
  (5) **be a block the arena's builders will never place** — the redstone lesson above. The next one,
  if a cell kind grows, is another button: 24 states each and nine still unspent in vanilla — a wooden
  one, since the stone button is a block a map has.

  **Which donor state stands for which paint state** is chosen by donor, not by shape. Rivals is
  played in adventure mode, so the one thing a borrowed state's outline was ever good for — the
  targeted-block highlight, which no resource pack can change — never appears, and a donor's shape
  costs nothing:

  - **wall cells** take the colour's own multiface donor — 4 directions × 16 bit patterns is exactly
    the 64 inert states one has, with none to spare. The all-faces-false state is usable because the
    pack replaces the whole blockstate file, so the client draws our quad rather than vanilla's union
    of face slabs; only that state's outline is empty, and nobody in adventure mode draws one;
  - **floor and ceiling cells** take the five button donors — crimson, warped, bamboo, pale oak, poplar,
    in that order — 64 of the 120 they lend;
  - **corner masks** (the `PaintBlock` splat, for a cell painted on two or more faces: the join lines
    of an arena rather than its surfaces) take the pale moss carpet, then 33 of the 56 button states the
    floors did not need — 81 + 33 = 114. The carpet is spent outright, which is why it is pinned by a test.

  Glow lichen was considered and dropped — it lights every state that has a face, which would make
  paint glow; so was the lever, whose `animateTick` makes dust whenever it is powered. The v1 caveat
  still applies, now for eight blocks instead of one: real sculk veins, resin clumps, pale moss carpet
  and the five buttons a player places in an arena render as paint too — which is why the buttons are
  the ones they are.
- The roller sprays where its head touches: three crumbs at the contact point on every tick that paints,
  with a dust pillar every fourth for the ink pushed ahead of the drum. The point is the head's own — one
  `roll_reach` ahead of the feet along the flat look, on whatever floor the strip's own downward ray finds
  under it, so on a stair the spray is where the drum is rather than where the player's feet are. A roller
  sees its own: the head is a couple of blocks from its eyes, which is outside the radius `Painter.burst`
  keeps crumbs out of.
- Every ink burst — muzzle flash, impact splash, the rays off it, the charger's trail, a squid's wake
  — is made of vanilla's block-break crumbs carrying one of the paint client states, not redstone
  dust. Dust reads as a drifting grey-red haze; a crumb is a lump that arcs and falls, which is what
  thrown liquid does. The state is always a multiface one (sculk vein for DATA, resin clump for IT):
  the client takes the sprite from that state's model `particle` texture, which the pack points at the
  team's paint tile, and a redstone-wire-backed state would have been tinted dark red by vanilla's own
  colour provider instead. Crumbs are much bigger than grains, so every burst count is about half what
  the dust counts were.
- No paint particle is ever spawned on a camera. Vanilla textures a crumb with a random *quarter* of
  its state's particle sprite — four texels of our uniform paint tile, translucent — so one that spawns
  at a player's eyes is a team-coloured wash over their whole screen. Every burst goes out per viewer
  through `Painter.burst`, which drops any player whose eyes are within `NEAR_EYES` (0.9 blocks) plus
  the burst's own spread, and keeps vanilla's 32-block cut-off. The squid's wake is the one case the
  distance rule cannot fix on its own — a pillar at the feet rises into the camera after it spawns — so
  the squid gets crumbs only, a stride behind it, and everyone else gets the full wake at its feet.
- A swimming squid leaves a wake: a few crumbs of its own ink at its feet on every tick it is
  actually moving (measured between ticks, because a real player's server-side delta is zero most
  ticks), a soft swim note every sixth such tick, and a ring of specks thrown outwards on the dive. A
  squid holding still leaves nothing, so the trail reads as movement rather than as a marker saying
  where someone is hiding.
- Squid form also hides the held items from everyone else. Vanilla invisibility hides the body but not
  what it is carrying, so without this a squid reads to an enemy as a gun floating across the floor.
  Every squid tick sends the players tracking that squid — never the squid itself, which still wants
  to see its own gun — an equipment packet with empty hands and armour; the tick it stops being a
  squid sends the real one back.
- Shots hurt. A direct hit on someone from another team takes hearts off them as well as painting the
  ground under their feet, and since round 7 **every pellet lands its own damage**. Vanilla keeps a
  twenty-tick window after a hit and, for the first ten of it, applies only the *excess* over the last
  one — which is why a slosher's two pellets used to be worth one and a three-tick shooter lost two
  shots in three. `PaintDamage` stands in front of every paint hit and takes the window off before and
  after it (`LivingEntity.damageCooldownTime = 0`, which is also the branch that stops `lastHurt`
  gating anything), so a bucketful is a bucketful. Knockback is deliberately left vanilla, so two
  pellets do shove twice. Teammates take the paint and nothing else, no team at all counts as fair
  game, and a squid is an ordinary player here — squid form is cover, not armour. Kills are attributed
  to the shooter.
- Damage is Splatoon's, and it falls off. A shot is worth its full damage for the first few ticks of
  flight and then loses a slice a tick down to a floor, read at the hit rather than baked in at the
  throw: a shooter's ball is 8 up close and 4 across a courtyard. A weapon that says nothing about
  falloff — the slosher, every special — is flat.
- One colour per cell: a hit in another colour wipes the cell and starts it over as a single
  connected face in the new colour, even if the old cell held paint on more than one face.
- Four weapons, one item class (`PaintWeapon`) parameterised by a `Weapon` enum, given with
  `/rivals gun <shooter|charger|slosher|roller>` (default shooter) or all at once with
  `/rivals kit`. **Every number is Splatoon 1's**, read off Splatcraft (MIT), whose
  `data/splatcraft/weapon_settings/*.json` is that game's figures on the scale this module already
  uses: 20 hit points, a hundred-unit ink tank, twenty ticks to the second. Each default in `Weapon`
  names its source file beside it.

  | Weapon | Ink | Cadence | Damage | Shot | Splatcraft |
  |---|---|---|---|---|---|
  | shooter | 1 | 3 ticks (held) | 8, −0.34/tick from tick 3, floor 4 | one ball at 2.0 straight for 8 blocks, then 0.5 falling at 0.075; one bounce; 3×3 splat; spread 6° on the ground, 12° in the air | `splattershot.json` |
  | charger | 2 → 18 | 20 ticks | 8 → 16 over a partial charge, **32 at a full one** | hold right click to aim (the spyglass scope; a full charge is 20 ticks), let go to fire a hitscan line of 9 → 24 blocks, stopped by the first block or player in it | `splat_charger.json` |
  | slosher | 7 | 12 ticks (click) | 7, flat | 2 pellets 8° apart, lobbed 15° up at 1.1 under gravity 0.06, 5×5 splat, no bounce | `slosher.json` |
  | roller | 9 a flick, 1 per 5 ticks rolling | 15 ticks after a flick | flick 30, −3.45/tick from tick 8, floor 7; roll 25 | **hold** right click to roll a 3-wide strip where you walk, with 8% more speed, no sprinting, and a head that runs over anyone in front once per 10 ticks — both only while you are actually moving, so a roller parked in a doorway is not a wall of damage, and paint thrown up where the head touches the ground; **left click** to flick 3 drops in a high arc | `splat_roller.json` |

  The thing on **F** is not in this table any more, because it is not a weapon and it is not the
  weapon's: see **Specials**, below.

  Standing in your own ink refills the tank in ten seconds on your feet and three as a squid, and a
  weapon that has just fired waits its own `refill_delay` first (7 for a shooter, 15 for a roller —
  Splatcraft's `ink_recovery_cooldown`). **Nothing refills while the trigger is held**, as in Splatoon:
  without that the roller spent one ink every five ticks and took one back every two, so rolling through
  your own paint filled the tank faster than rolling emptied it.

  A roll costs one ink every five ticks of *moving*, so a full tank is about twenty-five seconds of solid
  rolling — near enough Splatoon 1's Splat Roller, which empties in about thirty-three. It was one every
  sixteen, which is a minute and a half, which is to say it never ran out. An empty roller is not thrown
  out of its roll: the head keeps rolling and keeps running people over, as Splatoon's does, it simply
  paints nothing until the tank has something in it — and it says so once when it happens, because a
  roller that has quietly stopped painting reads as a roller that is broken.

  **Controls.** Three buttons:

  | weapon | right click | left click | F (swap hands key) |
  |---|---|---|---|
  | shooter | hold to fire | — | your special |
  | charger | hold to scope/charge | fire the charge | — |
  | slosher | slosh | — | your special |
  | roller | hold to roll | **flick** | your special |

  The charger is the one weapon with nothing on F: its charge *is* its special, and pressing F with one
  in hand says so rather than doing nothing.

  ### Specials

  **F throws your special**, and there are three of them — Splatoon 1 sub weapons on the same
  hundred-unit tank. They differ in the one way a sub can: *when* it goes off. The splat bomb lands and
  waits, so throwing one is a question about where somebody will be in a second; the burst bomb goes off
  on contact, so it is a question about where they are now; the curling bomb goes off somewhere else
  entirely, because it slides there first painting the floor as it goes, and it is the only one worth
  throwing at nobody at all.

  | special | ink | wait | behaviour |
  |---|---|---|---|
  | splat bomb | 70 | 80 t | a slow lob 30° up at 0.75; bounces where it lands and goes off 20 ticks later; 7×7 splat, 36 at the centre → 6 at 3.25 blocks |
  | burst bomb | 40 | 40 t | thrown nearly flat at 1.4 and **bursts on impact**, block or body, no fuse; 5×5 splat on the face it struck, 25 → 5 at 2 blocks |
  | curling bomb | 55 | 70 t | thrown low, **slides along the floor for up to 40 ticks** painting a 1-wide line under itself and reflecting off walls, then bursts; 5×5 splat, 18 → 4 at 2.5 blocks |

  The special is the *player's*, not the gun's: a splat bomb out of a roller is the same splat bomb, so
  it is picked once and remembered ([`SpecialChoice`](src/main/java/nu/metacraft/rivals/gun/SpecialChoice.java),
  saved data by UUID, written as the special's own id). `/rivals special` opens the picker — three
  pictures of the blob the bomb actually flies as, in your team's colour and at the size it is thrown
  at — and `/rivals special pick <id>` is what its buttons run. The weapon picker's last button says
  what F throws now and opens it, taking a weapon opens it once for anybody who has never picked, and a
  match start asks whoever is still missing either half of a loadout.

  Every number above is tunable live, keyed by special rather than by weapon:
  `/rivals tune special <id> <param> <value>`. The falloff is linear in distance *squared* with no
  line-of-sight test, the same shape Splatcraft's own explosion uses, and every blast is at full damage
  inside its `core`, so it starts falling off at the edge of the bomb rather than at a point.

  **The pick is locked in its slot.** A weapon you have picked is *the* weapon you are carrying, and it
  stays in the slot it was put in (`WeaponPicks.GIVEN_SLOT`, the first): the hotbar selection is pinned
  there, the stack cannot be dropped, and it cannot be dragged out of the inventory screen — a player who
  could scroll off the gun would be standing in a firefight punching, and one who could drop it would have
  no way of getting it back. The selection has exactly **one** place to be, that slot, because the **weapon
  selector** is not in the hotbar at all: it sits in the top-right slot of the main inventory grid
  (`WeaponSelector.SLOT` = 17 — the hotbar is 0..8 and the grid 9..35) and is clicked *there*. Any click on
  it, any button: the click is not run, the screen is closed and the picker is sent after it, since a dialog
  is drawn over whatever screen the client has open. Swapping weapons is only ever through the selector, so
  it stays in the inventory during a match too — a lobby, an arm-up or a pick puts it back in that slot
  whenever it has wandered, displacing whatever was there into the first free slot, and hands one out to
  anybody without — and it cannot be dropped either. Operators are locked like everyone else; `/rivals
  gun` still hands out a weapon, which is the way round it for testing.

  Server-side the lock is three more packet handlers beside the buttons, all in the same mixin and all
  asking `WeaponLock`: `handleSetCarriedItem` (refused selections are set back and the client snapped with
  `ClientboundSetHeldSlotPacket`, because a vanilla client moves its own selection without waiting to be
  told), the two drop actions of `handlePlayerAction`, and `handleContainerClick` — a clicked slot that is
  the weapon's, a hotbar-swap key aimed at it from anywhere on the screen, or a paint weapon already on the
  cursor, any of which is dropped on the floor and the menu resent whole with
  `containerMenu.sendAllDataToRemote()`.

  Right click fires. The shooter and the roller are *held*: a vanilla client repeats a
  held right click only every four ticks, which is not a fire rate a shooter can have, so the press
  starts using the item and `Item#onUseTick` does the work every tick until the button is let go — the
  shooter fires whenever its cooldown is up, the roller rolls. The slosher stays a click, because
  twelve ticks is slower than the client's repeat anyway. Letting the button go is only ever the end of
  a roll: the flick used to be a *tap* of the same button, told from a roll by how soon the release came
  after the press, which meant a player who wanted a flick had to give up the roll to ask for it and a
  player who wanted neither got one by accident. It is the left button now, and the two are independent —
  flick mid-roll, and the roll is stopped for the throw and starts again on the next tick if the right
  button is still down. The charger is the exception on both counts: right click is its scope and letting
  go fires nothing, and left click is its trigger — the charge it has built if it is scoped, a snap shot
  at no charge if it is not.

  **How a vanilla client is made to hold.** Starting the use on the server is only half of it, and for
  one round it was the only half: `Minecraft.handleKeybinds` sends the `RELEASE_USE_ITEM` packet only
  while `player.isUsingItem()` *on the client*, so with the client not using, the roller's release never
  arrived and **its flick never fired**; the client repeated the use packet every four ticks instead,
  re-starting the use on a loop; and `use_effects` — the no-sprint and the speed multiplier, both read
  client-side — never applied at all. 26.3's `Item.use` starts using anything carrying a
  `minecraft:consumable` (it calls `Consumable.startConsuming`, which calls `startUsingItem` whenever
  `consumeTicks() > 0`; `canConsume` only asks about FOOD, and there is none), so the shooter's and the
  roller's **client** stacks carry one: an hour long, animation `none`, no consume particles, the
  intentionally-empty sound. Nothing ever completes it — the server ends the use — and vanilla only
  starts the eating sounds after 21.875% of the consume time, which is thirteen minutes in.

  It buys a second thing: the item definition can now switch on `minecraft:using_item`, which is true
  exactly while the client is using the item. `items/roller.json` is a `minecraft:condition` on it, so
  **holding the button swaps the roller's model** for `item/roller_rolling` — the same geometry as a
  child model, with the head pitched nose-down, pushed ahead and scaled up by a third, so it reads as
  pressed against the floor instead of carried in front of your face; third person turns the drum down and
  ahead — Julle's base pose carries it level out in front, so merely lowering it showed nothing — and pushes
  it a block and a bit ahead of the hand, onto the floor, so everyone else sees the head down. The transforms are in `tools/weapon_models.py`'s
  `ROLLING_DISPLAY` and were solved against the same first-person camera chain the LED used to be solved
  against: at 1080p the drum sits on the bottom edge, slightly clipped, with the grip rising above it.

  That is also why the disguise is a **stick**. It was `warped_fungus_on_a_stick`, and
  `FoodOnAStickItem.use` returns PASS on the client before it looks at a single component, so no
  component could have reached it; a bare `Item` runs the base `Item.use` that reads the consumable.
  What the item is underneath is invisible either way — the client draws the model Polymer points it
  at. The charger stays a spyglass: `SpyglassItem.use` starts using by itself, which is why the charger
  was the one weapon whose hold always worked.

  Holding an item in use costs a vanilla player their sprint and four fifths of their speed — a bow's
  behaviour, read client-side off the `minecraft:use_effects` component (`canSprint`,
  `interactVibrations`, `speedMultiplier`) — so the shooter and the roller carry their own: the shooter
  keeps sprinting and takes 72% (about what firing costs in Splatoon), and the roller takes no
  multiplier at all, since its `roll_speed` attribute is what decides how fast a roll is, **but may not
  sprint** — you are pushing a drum along the floor, and sprinting with it was what made rolling read as
  free. The charger is left on vanilla's, because being pinned in place is what its damage is paid for.
  None of this took effect at all until the client started using the item for real: `use_effects` is
  applied in `LocalPlayer`, and only while it is using.

  **F** — the swap-hands key — throws **your special** on everything but the charger: whichever of the
  three in **Specials** above you picked, for that special's own ink and its own wait, separate from the
  fire cooldown so the trigger is never held up by it. A player who never picked throws the splat bomb,
  which is what F has always thrown. The wait is named when it refuses, because a player who picked the
  burst bomb should be told about a burst bomb. F reaches
  the server as a `ServerboundPlayerActionPacket` carrying `SWAP_ITEM_WITH_OFFHAND`, which nothing in the
  Fabric API covers, so a mixin on `handlePlayerAction` turns it into `PaintWeapon.swapHands` and
  **cancels the packet**: vanilla never runs, so nothing moves between the hands, whether the bomb went or
  was refused for its ink or its wait. F with anything else in hand is still vanilla's swap.

  Server-side, a left click arrives as up to two packets in the same tick — an attack
  on the block or entity under the crosshair, then a swing — so Fabric's `AttackBlockCallback` and
  `AttackEntityCallback` (both returning `FAIL`, so a paint weapon never breaks the arena or
  punches anyone) and a mixin on `handlePunch`, 26.3's replacement for the swing packet, all go
  through one `PaintWeapon.leftClick` that answers the first of the tick and ignores the rest. The
  client sends the punch even while an item is in use, which is exactly what lets the charger aim
  with one button and fire with the other, and the roller flick out of a roll; it does not repeat it
  while the button is held, so a left click is one flick.

  Only the slosher swings the arm on use — it's a bucket, and the throw reads as one — so its
  `use` returns `SUCCESS_SERVER` (the server broadcasts the swing, including to the thrower); the
  other three return `CONSUME`, which takes the click without animating the hand, since a
  four-tick swing loop on a rapid-fire weapon looks like a stutter rather than firing.
- Every paint ball is a snowball entity hidden from clients, with a Polymer item display — a
  rounded, dyed blob model (a cube cut back to an octagon in all three planes by three 45° bands),
  not the vanilla firework-star particle — riding along on an attachment. It squashes and
  stretches as it flies: each tick the model's local up is turned onto the velocity and the blob
  is drawn out along it by its speed, losing across what it gains in length, so the volume reads
  constant. The shooter's ball keeps two bounces: on a block hit it splashes, reflects off the hit
  face at 62% of its speed, pancakes flat against that face for two ticks before easing back into
  its flying shape over three more, throws off two short-lived single-face droplets along the
  reflection (droplets never throw droplets of their own) with a wet slime step, and keeps flying
  until the impact after the last bounce spends it; slosher pellets and roller flicks do not bounce. On
  impact (or the final bounce) it splashes: the usual blob on the struck face (3×3 for the shooter, 5×5
  for the slosher), plus fourteen short rays from the impact point (six axis directions and eight
  diagonals) that paint whatever face they hit, so a floor shot next to a wall also paints the
  wall and fills in the corner. A coloured dust burst and a wet impact sound go with it; the burst
  is sized to the splat (4 grains for a single face, 10 for a 3×3, 16 for a 5×5, with ray dust only
  from radius 1 up), so a single-face droplet at point-blank range does not fill its own screen. Colour comes
  from the shooter's vanilla team, whose name is the colour id.
- Firing has a kick: the client's pitch is nudged up on the shot and eased back down two ticks
  later (scaled to the charger's charge), plus a small push, a muzzle particle burst and a
  layered sound (a low slime step added under the slosher's throw for weight). Recoil packets
  only reach real connected players; mock players (game tests) are unaffected. The muzzle burst
  goes to every viewer *but* the shooter (per-player particles, 32 blocks): in first person those
  grains hang in the middle of the camera. The shooter gets three small ones at the barrel tip
  instead, offset right and down out of the crosshair. The charger's trail starts its dust 1.5
  blocks along the shot for the same reason; the paint under the line still starts at the eyes.
- **The lobby.** Between matches — in LOBBY and after the whistle in ENDED — `Lobby` is what a player
  gets: the **whole Rivals kit off them** (`Match.disarm`: every paint weapon, and the weapon selector with
  it), adventure mode, a clean screen and no roll. Nothing in the lobby hands any of it out — `Match.arm`
  is the only thing that ever gives a selector, and `WeaponSelector.home` moves a stray one back without
  conjuring a new one, so a stopped match cannot leave a compass nobody can drop in somebody's inventory. Ops keep their
  own mode: the permission asked is the module's own `metacraft.rivals`, the same one the admin commands
  use, so a permissions plugin can grant it without granting op, and an operator in the lobby is usually
  building it. Adventure for everyone else because a lobby is not a place to mine the arena from, and a
  gun in the lobby is a gun used on the arena before the round starts.

  The same thing happens **on join**, through `ServerPlayConnectionEvents.JOIN` — unless a match is
  *playing*, in which case the arrival is a mid-match add instead (armed, on their team, with the three
  seconds of respawn grace), so nobody loads into a firefight. A **lobby death** puts a dressed player on
  their own team's spawn and everybody else at the level's own respawn point, rather than wherever they
  happened to die.
- **The round loop.** `Match` is one machine for the whole server, in memory:
  **LOBBY → COUNTDOWN (5 s) → PLAYING (n minutes) → ENDED (10 s) → LOBBY**.

  ```
  /rivals match start 3         three minutes, after the readiness check
  /rivals match start 3 force   start anyway, undressed players and all
  /rivals match stop            the whistle, early
  /rivals match status          the state and the time left
  ```

  `start` checks readiness (unless forced), makes sure both teams exist the way `/rivals setup` does, puts
  every player on a side into the match, clears the paint inside the arena, hands each player the weapon they
  picked (the shooter if they never picked), teleports them to their team's spawn in adventure (always adventure — an arena is painted, not mined), and
  freezes them for the countdown: titles 5…1, a note under each, then **GO!**. Frozen is a −100 %
  `MOVEMENT_SPEED` modifier plus a −100 % `JUMP_STRENGTH` one — transient attribute modifiers by id,
  exactly as the roller's speed bonus is, so they are exact, they do not appear in the client's effect
  list, and they come off by id. Playing adds a timer bossbar, `⏱ m:ss`, beside the score bars.

  At zero (or on `stop`) the round is taken back: every player's kit goes (the weapon in its slot, the
  selector in its own, and the weapon lock with them, since the lock is only ever "a paint weapon in slot
  0") and every Rivals boss bar — the timer and the score bars — comes off every screen, so a player is
  left with the inventory they walked in with. `/rivals kit` and `/rivals gun` still hand out items outside
  a match, for testing an arena. Everybody is put in spectator, the paint is counted, and the winner is titled in their
  own colour — "DATA wins!", "IT wins!" or "Draw", with both percentages under it and in chat — while ten
  team-coloured rockets go up over three seconds at the winner's spawn. Ten seconds later it is the lobby
  again: adventure back on, and everybody teleported to the level's own respawn point (`Lobby.sendHome`). Every transition clears `InkOnScreen` and stops any `Roll` for everybody: ink on the glass is
  health lost in a round that is over, and a roll that survived a teleport is a player rolling on a spawn
  platform.

  **Dying** during PLAYING puts a player back on their own team's spawn (Fabric's `AFTER_RESPAWN`, which
  hands over the new entity — overriding the respawn position itself would also have to answer for the
  bed, the anchor and the end portal), frozen and invulnerable for three seconds with a "Respawning"
  title and a clean screen, re-armed — and re-dressed: a round is played in the side's ovve, and a death may
  have dropped it. A player who **joins mid-match** gets the same treatment as a respawn, so nobody loads
  into a firefight.

  **The ovve.** `Ovves` puts the side's ovve on a player's legs at every way into a round (start, mid-round
  join, respawn): DATA wears the Data ovve; IT wears the silicon-blue IT ovve (PolymITer's kiselblå, which
  counts as IT) if they own one, else the plain IT one. One they own is taken from their inventory first, a
  fresh one owned by them is given otherwise, and whatever the legs held goes back in the inventory. Ovves
  are ovvar's, so the whole thing is behind `FabricLoader.isModLoaded("ovvar")` (`OvvarBridge` is the only
  class that names ovvar's types); without ovvar it is a no-op.

  **Changing weapon mid-round** is done at your own spawn: `WeaponPicks.pickRefusal` refuses a pick — and
  the selector refuses to open — during PLAYING further than `SWAP_RADIUS` (8 blocks) from the side's own
  spawn. The lobby and the countdown are anywhere.

  The clock and the roster are both handed in: `Match.tick` takes the tick count and `start` takes a
  supplier of the players, defaulting to the online list. Nothing in `Match` reads `getTickCount()` on its
  own, which is what lets the game tests walk the whole machine through inside a single tick — and they
  must, because the real `END_SERVER_TICK` hook drives the same singleton.
- **The MAIN datapack** owns the minigame on the event server; this mod owns the playing of it.
  `MainPack` is the whole seam, and it is three things:

  **The running flag.** MAIN keeps a fake player `?running` in the objective `splat.state` — 0 while the
  minigame is not active, 1 while it is, which is MAIN's own `?superstate main.state` being 3. Nothing
  here ever writes it. It is read every ten ticks and only the *edges* do anything: 0 → 1 starts a round
  and 1 → 0 stops whatever is running. Edges rather than levels, because a flag that is still 1 through
  the ten seconds of fireworks must not read as "start another round"; and the first read of a server's
  life only reads, so a restart with the flag already up neither starts nor stops anything — the mod has
  no idea how much of that round has been played. A round the flag starts runs for
  `config/rivals-paint/main.json`'s `minutes` (3 by default, re-read by `/rivals reload`) in the first
  level with a spawn set for both sides, and is forced: MAIN decides who is playing, so a player on
  neither side is left out rather than the whole round refused. An objective MAIN has not made reads as
  0, which is exactly right — nobody is running the minigame.

  **Ending the game.** MAIN never ends this minigame on its own, so the arena says how. Each side has a
  **win function** and the arena a **draw function**, saved with the arena and set by an operator —
  `/rivals win-function set data main:api/end_game_data`, `… set it main:api/end_game_it`,
  `/rivals draw-function set <function>` (`show` prints either) — and `Match` runs the winner's when the
  ten seconds of celebration are over and the lobby begins, so MAIN's outro starts after the fireworks.
  Equal paint is broken on kills first; only a round level on both is a real draw, which the titles say
  and the draw function answers. An operator's `/rivals match stop` ends the round the same way. The one
  ending that runs nothing is the flag dropping: then MAIN is already ending the game, and telling it so
  again would be MAIN answering itself. An arena with no function set simply runs none, which is what
  lets this module be played on its own.

  **The stats board.** `Stats` writes the two per-player numbers MAIN's outro sorts for its top five,
  both plain dummy objectives it creates if MAIN's pack has not:

  - `splat.stats.blocks` is *held* paint — how many faces of the final picture a player was the last to
    paint. The painter records an owner per (cell, face) as it paints ([`PaintTally`](src/main/java/nu/metacraft/rivals/paint/PaintTally.java)
    for blocks, the quad's own `owner` for display cells), an overpaint hands the face to whoever
    painted over it, and a recolour that wipes a cell forgets every face it held. Written once, at the
    whistle, off the same swept tally the percentages come from. So a player whose whole strip was
    rolled over ends on nothing, which is what "who painted this arena" means when the arena is the
    score;
  - `splat.stats.kills` is live: one per kill, on the board the tick it happens, so the number is there
    however the round ends. Only a player killing another player during a live match counts — the
    enemy-ink drip is never lethal and friendly fire is refused by the weapons.

  A round starts by emptying both objectives of *every* holder, so last round's top five cannot haunt
  this one, and then giving each player on a side a zero of their own, so MAIN's sort sees the whole
  roster rather than only whoever scored. Every way into a match goes through `Match.join`, which is
  where the board learns a UUID's scoreboard name — without it a player who paints and then logs out
  before the whistle has nothing to be written against.
- **Readiness.** `/rivals ready` prints one line per online non-spectator player — name, side (or "no
  team"), the weapon they picked (or "none yet") — grouped by side with whoever is on neither last, and
  **fails**, naming them, if anybody is on neither. A player on neither side has no colour, cannot paint
  and cannot score, so a match that starts with one has a passenger in it; the failure says
  `/team join <name>` with the configured names in it, and `/rivals match start` refuses on the same check
  unless the word `force` is added. Spectators are left out rather than counted as teamless — a spectator
  is deliberately not playing. Nobody at all is not ready either: there is no match without players.
- **Spawns and arena bounds.** `Arena` is saved data, one per level (id `rivals_arena`), and holds a
  spawn per team — position, yaw *and* pitch — plus an optional box. It is saved, unlike the tally and
  the display quads, because setting an arena up is work an operator does once.

  ```
  /rivals spawn set data                where DATA starts: your own position and look
  /rivals spawn set it
  /rivals spawn list                    both spawns and the bounds, or what is missing
  /rivals arena set 10 60 10 90 90 90   the two corners
  /rivals arena clear                   the whole level takes paint again
  /rivals arena show                    the twelve edges in end rods for 10 s, plus the corners in chat
  ```

  A spawn is set by standing where the team should appear and facing the way they should face, because
  that is the only way to pick a look direction that does not involve typing two numbers.

  While bounds are set, **paint outside them is refused** — `Painter.paintFace` and
  `PaintDisplays.paint`, so a paint block and a display quad are fenced in alike — and `/rivals reset`
  clears only what is inside them, leaving whatever is painted outside the arena where it is. Inside the
  bounds it **sweeps the arena itself** rather than reading the tracking, so a restart cannot hide old
  paint: it walks the chunk sections the box covers, skipping the ones whose palette holds no paint, and
  loads any chunk in the box that is not loaded. (The tracking is memory only; before the sweep, a reset
  after a restart answered "Nothing painted" and left thousands of paint blocks to be cleared by hand.) The test is
  on the **surface** block rather than on the cell the paint goes in: a wall standing on the box's own
  edge paints into the cell beyond it, and testing the cell would have left the arena's own boundary wall
  unpaintable from the inside. `show` is per viewer, the way every paint burst is, and its step grows with
  the box so a hundred-block arena does not ask a client for ten thousand particles.
- **Picking a weapon.** `/rivals weapons` (any player, no permission — the rest of the `/rivals` tree is
  game-master only, so the permission sits on each subcommand rather than on the root) opens a **26.3
  server-sent dialog**: a picture of each weapon, two to a row, with what it is for written *under* it,
  and a button per weapon. Each picture is the *real* weapon stack dyed in the viewer's team colour, so
  the screen is four paint guns drawn by their own models rather than four stand-in vanilla items, and
  each line says how far the weapon reaches and what it costs — read off the live tuning, so a retuned
  server describes the weapon its players are holding. The one they are already on is marked
  "(current)", and the way out is a "Keep the …" button that changes nothing.

  It was a one-row chest menu ([sgui](https://github.com/Patbox/sgui)) until v9. A chest row can only
  ever be nine icons and a hover; what a weapon is for is a sentence. The module no longer depends on
  sgui at all.

  A button carries a `run_command` click event, so clicking it is the player running
  `/rivals weapons pick <id>` themselves — the dialog, the compass and the command line are one path
  through `WeaponPicks.pick`. That needs no confirmation screen and no custom packet: a client only
  stops to ask when the command fails to parse against the command tree it was sent, needs a permission
  it was not given, or carries signable chat arguments, and this one is literals and an id. The command
  goes on the button **without** a leading slash, because the client parses the string straight with its
  own dispatcher. Picking takes every paint weapon out of the inventory and puts the chosen one in the
  first slot; anything that is not a paint weapon is left alone.

  A picture is an `ItemBody`, which holds an `ItemStackTemplate` rather than an `ItemStack` — and
  Polymer 0.18 patches that type's packet codec (`ItemStackTemplateMixin`), so a paint gun in a dialog
  reaches a vanilla client already translated into the stick or spyglass it is disguised as, exactly as
  one in an inventory slot does. Nothing here needs a resource pack the players do not already have.

  The pick is remembered in `WeaponChoice`, saved data on the server (not the level — a player carries
  their weapon between dimensions), keyed by UUID and stored as the weapon's *id* rather than its
  ordinal, so reordering the enum cannot hand anyone somebody else's gun. That is what a match start
  hands out, and it survives a relog and a restart; a player who never picked gets the shooter, and a
  match start puts the picker in front of them during the countdown so that they do not have to.

  The dialog also opens by right-clicking the **weapon selector**, a Polymer item the client is shown as
  a compass (with the lodestone tracker stripped, so the needle does not spin) named "Weapon selector".
  The lobby hands out exactly one. `/rivals gun` and `/rivals kit` stay, for admins.
- **Unpaintable blocks.** Ink falls through a grate rather than covering it, so some blocks never take
  paint at all — no paint block, no display quad, the shot and the splash simply skip them, and so do a
  roll and the charger's line, because all of it goes through one `Painter.paintable`. Two sources, and a
  block in either is out:

  - the block tag **`#rivals-paint:unpaintable`**, shipped at
    `data/rivals-paint/tags/block/unpaintable.json` and overridable by a data pack like any other
    tag. The defaults are `#minecraft:bars` (iron bars and the eight copper bars), `#minecraft:rails`,
    `#minecraft:trapdoors` (wooden, iron and copper — *doors* stay paintable), all eight copper grates,
    every glass pane including the sixteen stained ones, all nine chains, `ladder` and `scaffolding`.
    Note 26.3's names: plain `minecraft:chain` is gone — it is `iron_chain` plus the copper chain
    family — and there is no `#minecraft:copper_grates` or `#minecraft:glass_panes` tag to lean on, so
    those two families are listed block by block.
  - the list in **`config/rivals-paint/unpaintable.json`**, `{"_help": "…", "blocks":
    ["minecraft:copper_grate", …]}`, read on server start and again on `/rivals reload`. The file is
    written with its own `_help` (json has no comments) and an empty list the first time the server
    starts. An id no block answers to is a warning in the log and is ignored, so a typo does not take a
    start down.

  A tag for what the mod ships and a map maker overrides; a config file for what an arena builder
  changes between rounds without writing a data pack.
- **Paint on other shapes.** Paint blocks only ever sit on a full face — the same attach rule
  vanilla's own multiface blocks use, and exactly the rule paint wants. A face that isn't full
  (stairs, slabs, fences, walls) instead gets a set of flat quads that wrap the
  block's own outline shape (not its collision box, so paint on a fence sits on top of the post, not
  floating at collision height). Each quad is a Polymer **block display carrying the paint state
  itself** — `PaintStates.connected(colour, attach, bits)`, the very client state a painted cell
  carries — so a quad on a slab is the same material as the paint block beside it, borders against it,
  and is drawn by the same shader code (below). Placing one needs no rotation: the paint state's attach
  direction already puts the model's single quad against the right side of the display's unit cube, so
  it is the box's face plane on the face axis and the box's own extent on the other two. The
  connection nibble counts both kinds of neighbour — paint blocks through
  `ConnectedPaintBlock.neighbourBits`, and neighbouring quad cells of the same colour on the same face
  — and because nothing sends a block display a neighbour update, `Painter` re-states the quads around
  every cell it paints (and the sweep does it for every cell it drops), so a newly painted neighbour
  opens their border within the tick. It works both ways round: `ConnectedPaintBlock.neighbourBits` also
  counts quad cells, so a paint block beside a slab opens its own edge towards the quads rather than
  leaving a one-sided seam. At most three quads per cell, the largest boxes on the struck side, so a wall post
  with four arms doesn't put a dozen displays in one cell. These quads aren't blocks, so nothing tells
  them to fall on their own: they are dropped, and stop being counted, once their surface is destroyed,
  replaced, buried, or merely changes shape (a stair turned under them), or once its chunk unloads — a
  chunk unload takes the paint with it, and unlike a paint block it does not come back when the chunk
  reloads. Entity lighting is one sample with no ambient occlusion, so a quad is lit a little more
  flatly than the block paint around it. (Before v6 these were item displays of a white sprite tinted
  with the team colour: the right silhouette, but a different material from the block paint and no way
  to border against it.)
- Paint on a full block face is drawn by the pack's own art and the shader (below), not a Kenney
  silhouette. Per colour there are 16 uniform 16×16 textures — the paint colour, alpha 235 (the
  gloss shader's marker: the window 233..237 is the one band in 200..254 that no vanilla block texture
  has a texel in, with 232 on `nether_portal` and 238 on `frosted_ice` the nearest values that exist), and the four connection bits packed into the low
  nibble of the red channel (`r = (base & 0xF0) | bits`) — plus six shared one-quad models, one per
  attach direction, each 0.1/16 off the face like the multiface donors. A blockstate `variants` file
  per donor block maps every one of its states to either a wrapper model (a connected cell's face
  quad with its (colour, bits) texture) or a mask model (a corner cell's quad-per-face, all on the
  all-connected texture); states paint doesn't use point at an empty model. The override replaces the
  donor's whole vanilla blockstate file, so those unused states render *nothing at all* — a
  waterlogged sculk vein, a pale moss carpet with a base, one of the 23 button states nothing was dealt
  — is invisible under the pack.
  The display quads need no art of their own: they show a paint state, so they resolve to the same
  wrapper model and the same bit-carrying texture a painted cell does.
- The pack also overrides `assets/minecraft/shaders/core/terrain.vsh`/`terrain.fsh` — the pair that
  actually draws chunk geometry in 26.3 — inside a guard on that alpha marker. It reads the four
  bits back out of the texel's red channel, works out the face's two in-plane axes from
  `cross(dFdx(chunkPos), dFdy(chunkPos))`, and computes the signed distance to a rounded box in that
  plane: unconnected sides are inset (with a slow time-and-position wobble) and rounded at a corner
  only where both sides meeting there are unconnected, while connected sides run out past the cell
  so the seam to the next sheet is invisible; a fragment outside that box is discarded, which is what
  draws the border with no baked edge tile at any resolution. Everything that decision and the look
  depend on — the in-plane cell coordinate, the wobble's unwrapped coordinate and the position the
  waves and highlights are sampled at — is first snapped to the centre of its 1/16-block texel, so the
  paint reads as pixel art on the vanilla grid: stepped corners, a wobble that moves in whole texels
  (time stays continuous, so texels flip rather than slide) and blocky highlights. Inside the border it keeps v3's liquid
  pass — a meniscus rim lit toward the light on the shape's outer edge, a moving three-wave normal,
  and glint/sheen/fresnel mixed toward white — now computed from that same distance field instead of
  sampling neighbour texels; every other texel keeps vanilla's shading byte for byte. (v2 keyed this
  into `block.vsh`/`block.fsh`, which chunk terrain never runs through, so the gloss never rendered;
  those overrides are gone.) Known limit: a shader pack (e.g. Iris) replaces the core shaders
  wholesale and loses the gloss.
- The pack overrides `assets/minecraft/shaders/core/item.vsh`/`item.fsh` as well, with the same gloss
  block behind the same marker guard, because that is the pair that draws the display quads: both
  display kinds render block models through `Sheets.cutoutBlockItemSheet()`, which is
  `RenderPipelines.ITEM_CUTOUT`, which is `core/item` — not terrain, not entity. Two varyings carry it.
  `viewPos` is the view-space position, for the normal (from its derivatives) and the view vector;
  `rawColor` is the vertex tint before vanilla's directional light, which the chunks never get — without
  that, display paint came out visibly darker than the paint block beside it (the lightmap and the fog
  still apply, exactly as for terrain).

  The in-face coordinate comes off the **sprite**: `fract(texCoord0 * textureSize(Sampler0, 0) / 16)`.
  terrain.fsh can use `chunkPos` because chunk geometry is stored chunk-relative, but a display's
  vertices are baked by the render PoseStack with the camera rotation already in them — there is no world
  position in the item pair to take `fract()` of. (v6 first tried to rebuild one from the `Globals`
  camera; on a village path it drew borders across the middle of cells, each quad a random blob with
  holes.) The sprite works because the paint sprites are 16×16 and the stitcher lays equal-size sprites
  out on multiples of their own size; the one setting that breaks it is anisotropic texture filtering,
  which asks the stitcher for padding around every sprite and so shifts the pattern inside the cell.
  Which way round the sprite lies is decided by the `uv` array `PaintArt` writes per face — vanilla maps
  a face's u and v to world axes differently per face, so each of the six gets the flip that lines its
  sprite up with the paint's own in-plane axes. The wobble and the waves then run on the cell's own
  coordinate with the connection bits shifting their phase, so the pattern repeats from cell to cell;
  that shows only where a cell ends, and where a cell ends there is a border anyway.

  Held items, dropped items and the inventory come through untouched: no texture in either atlas an
  item pipeline draws — blocks and items — carries an alpha anywhere in the 233..237 the guard admits at
  mip 0 (a handful average to 236 at mip 3, which is noted in the shader).
- **Ink on your screen.** Enemy paint in the face throws ink over the player's view, and **how much
  of it there is is how much health they have lost**: `255 × (maxHealth − health) / maxHealth`, floored,
  0 at full health. The shader's four overlays are therefore quarters of your health gone — a quarter,
  a half, three quarters, nearly dead — so the ink is a health bar the player cannot help reading, and
  a bad fight ends with them squinting through a nearly full screen. There is no meter of its own: it
  does not decay on a timer and nothing tops it up, so regenerating clears the ink by itself, a heal
  wipes it on the next tick, and a respawn starts clean. What a hit decides is *whose* ink it is —
  `InkOnScreen.hit` and `.standing` keep the colour of the last enemy paint to touch the player, and a
  player no enemy has touched has no colour and so no ink, however far a fall took them. The ink is
  cleared outright on death, on spectating, on leaving the teams and on logging out.

  It is drawn by a post effect, which is where the interesting part is. A server-side mod cannot run
  client code, but 26.3's `GameRenderer.update` asks for the post effect `minecraft:end_of_frame`
  *every single frame* and drops the request silently when no pack defines it — so a pack that does
  define it gets one full-screen pass per frame, with nothing to trigger and nothing to switch on.

  It cannot set a uniform either, so the number has to be *in the frame* — and in the frame before the
  effect runs. `GameRenderer.render` calls `renderLevel()`, then `applyPostEffects()`, and only then
  `GuiRenderer.render()`, so nothing on the HUD (a title, the action bar) is on the target the effect
  samples; what is on it is the held item, drawn inside `renderLevel` by `renderItemInHand`. So the
  meter rides the weapon. Every gun model carries a **data LED**: one model pixel, six faces on a
  dedicated 16×16 texture whose alpha is 246, tinted by
  `custom_model_data` colour 0. 246 is in 245..247, one of only three three-wide alpha bands that no
  texel of any blocks- or items-atlas texture reaches at *any* mip level 0..4, so the item shader can
  recognise it: it takes the unlit vertex tint (`rawColor`, a varying our `item.vsh` adds) and writes it
  to the frame exactly, before lighting, fog and the paint gloss. `InkOnScreen` writes
  `(255, team, amount)` into that component — red at full with green under 16 is the signature, green's
  low nibble picks the ink colour, blue is the amount — and `PaintWeapon.inventoryTick` is the one place
  it reaches the stacks, in the same tick that keeps the tank dyed, so a weapon stowed with a full screen
  cannot come back out still carrying a live number. With no ink the value is `InkOnScreen.IDLE`, a dark
  grey, and the item shader **discards** it outright: an LED with nothing to say draws nothing at all.

  And the LED is not drawn where the model puts it. **`item.vsh` pins it in screen space**: a vertex whose
  *tint* carries the signature (red at full, green under 16 — the same bytes the probe reads back out of
  the frame, and nothing else in a frame is that) ignores `Position` outright and is emitted onto a fixed
  8×8-pixel quad at the bottom centre, one pixel up from the bottom edge — under the hotbar, which the
  GUI draws after the post effect has read the frame, so the probe reads it out of the frame and the
  player never sees it. The corner of the quad comes from `gl_VertexIndex & 3` (items are drawn as quads,
  four consecutive vertices a face) with the x mirrored on the faces pointing the other way, so one
  winding always survives the culling. An idle LED fails the signature and is not pinned at all, which is
  also every other player's weapon — they are all handed the idle value, and the fragment stage discards
  it.

  Two things about that were found the hard way, on a real client (macOS, the renderpearl backend). The
  first version recognised an LED vertex by reading the *atlas* — a vertex texture fetch for the sprite's
  246 marker alpha, the value the fragment stage keys on — and it never produced a quad at all; the tint
  is what works. And it must be spelled **`gl_VertexIndex`**: renderpearl parses Vulkan-flavoured GLSL,
  `gl_VertexID` does not compile there, and a pack with a shader that does not compile is a pack the
  client refuses outright — which took a client down on the way to finding that out. Under an orthographic projection
  (`ProjMat[2][3] == 0`) — the hotbar's own icons, the inventory — the quad is sent to a z outside the
  clip volume and thrown away instead, since there is no hotbar to hide behind there. All six faces of
  the LED box map onto the same quad; half are culled by their winding and at least one survives.

  The quad's **depth** is the part that cost a round. 26.3 does not clip to OpenGL's classic `[-1, 1]`:
  `Projection.setupPerspective` builds the matrix with JOML's `setPerspective(…, zZeroToOne = true)`, so
  the volume is `[0, 1]`, and the depth is *reversed* — `GameRenderer` clears the depth texture to `0.0`
  before `renderItemInHand` and tests GREATER, so 0 is the far plane and **1 is the near one**. The first
  version of this put the quad at `z = -0.999`, which is outside the volume: every LED vertex was
  clipped, the probe found nothing, the ink pass handed the frame through, and the screen stayed clean
  however much health was missing. It is `0.9999` now.

  It used to be solved into the model instead: each weapon's element placed so that its own
  `firstperson_righthand` transform landed it under the hotbar, by walking the client's chain in
  `tools/weapon_models.py` and inverting it. That worked and looked right in a screenshot, but the hand
  is not fixed on screen — `GameRenderer.bobView` moves the hand pose by up to ~0.1 units per walk cycle
  and the sprint FOV change moves it too, about a tenth of the screen height between them. The LED left
  the frame every other step, the probe found nothing, and **the ink blinked in walking rhythm**. Pinning
  it in the vertex shader is bob-proof, sprint-proof and the same pixels at every resolution and GUI
  scale, and it costs the model nothing: all a weapon owes the LED now is one element whose six faces are
  `#led` at tint index 1, anywhere inside its own box.

  The value only ever goes to the holder's own client: Polymer's per-viewer item hook hands every other
  player the idle colour, because a lit LED on someone else's gun is both a tell and a false reading — the
  probe would find it on their third-person weapon and splatter the finder's screen. The item definitions
  also turn `hand_animation_on_swap` off: a component change is a stack change, and without that the client
  replays the equip animation every time the meter (or the tank's dye) moves.

  The chain's first pass renders to a **one-by-one** target, so the read runs once a frame rather than
  once a pixel — and it is no longer a search at all. It swept 1 344 samples of the bottom of the frame
  while the LED could be anywhere in a box; now the quad has a fixed address, so the pass reads three
  pixels: the middle of the quad (`InSize.x × 0.5`, 5 px up) and two pixels to either side, all three of
  which have to carry the same signature and the same amount, so a red pixel of the world is never
  mistaken for it. The pass hands on the amount and the team, and nothing else: there is no longer
  anything for the ink pass to paint over, because the hotbar does it.

  **The ink itself is four textures**, not a field of procedural blobs — two flat tones and a signed
  distance function had no depth and did not read as pixel art. `textures/effect/ink_1.png` …
  `ink_4.png` are 320×180 RGBA overlays, one per quarter of the health you can lose — with the next
  quarter fading in, see below — bound to the ink pass as
  `PostChainConfig` texture inputs with `bilinear: false`. The directory and the bare names in the
  JSON go together: a texture input's `location` is resolved by `PostChain` as
  `textures/effect/<path>.png`, so the chain says `rivals-paint:ink_1` and the file sits at
  `textures/effect/ink_1.png`. Spelling the path out in the `location` instead asks for
  `textures/effect/textures/post/ink_1.png.png`, which is missing, and a missing texture input is four
  fat magenta-and-black quadrants over the whole screen — which is exactly what v7 shipped with. **Alpha is coverage** and is only ever 0 or
  255; **RGB is a greyscale shading map**, which the shader steps into four tones of the *team's*
  colour (under 0.30 → the colour at 55%, under 0.60 → the colour, under 0.85 → a quarter toward
  white, above → 60% toward white), so one drawing serves both teams. Sampled at texel centres with no
  filtering, so a texel is a fat block of screen pixels — the texture *is* the pixel grid. State 1 is a
  little ink around the edges, state 4 is nearly covered with the middle still clear, and ink always
  creeps in from the sides. They are ordinary resources: an artist paints over them and nothing else
  changes. The format is written out in `textures/effect/README.md`, and `tools/ink_overlays.py` (Pillow)
  drew the placeholders that are checked in. The overlays are **drawn the right way up**: row 0 of the
  PNG is the top of the screen, as every paint program writes it. The pass therefore samples them at
  `vec2(uv.x, 1.0 - uv.y)`, because a post effect's own `texCoord` has y = 0 at the *bottom* of the
  frame — v7 sampled it straight, and the ink was upside down with the drips running up.

  **The states crossfade.** The amount is continuous, not four steps: `s = amount × 4`, layer
  `n = floor(s)` is fully on the glass, and the texels the next state *adds* on top of it — the overlays
  are cumulative, so that is state `n+1`'s alpha minus state `n`'s — are blended over the frame at
  `smoothstep(0, 1, fract(s))`, the way powder snow's frost fades in. So being hurt grows the next round
  of blobs in over the quarter instead of popping it onto the screen whole, and at `n = 0` the whole of
  state 1 fades up from a clean screen. Only that opacity is continuous: the four tones stay quantised
  and the alpha edges stay hard, because that is the pixel-art look.

  The drawing is **paint splatter**, drawn from a reference of a wall twenty minutes after a paintball
  fight. A splat is a polar radius profile rather than a circle — short sharp teeth round the rim from
  high-frequency harmonics with high exponents, two or three long thin tongues from low-frequency ones,
  broad bumps, a slow wobble, and a notch harmonic biting back into the rim so it reads as torn rather
  than as a flower. Each throws a couple of dozen **satellite droplets** that thin out with distance, and
  long tapering **drips** straight down from its underside ending in a fatter bead; small independent
  splats of six to fourteen texels are scattered through the ring outside the clear middle. The states
  are **cumulative** — five big splats and two small ones in state 1, seven more arriving in each later
  state, every splat already on screen spreading by 22%, and every drip running 40% longer, which is what
  "twenty minutes after" looks like — so state N contains every texel of state N−1 and ink never flickers
  off a corner while the player is being hurt. A game test asserts that, and it is a requirement for a
  hand-drawn replacement too. Coverage runs about 15% / 27% / 41% / 56%.

  Known limits: no weapon in hand means no LED on the frame and so no ink, however much health is missing
  (the ink comes back with the weapon). Third person is no longer one of them — the held weapon is still
  drawn inside `renderLevel`, on the player model rather than in the hand, and its LED vertices land on
  the same pinned quad. The pass runs every frame whether there is ink or not (two full-screen passes'
  worth of work), and a shader pack that replaces the post chain loses the effect, exactly as it loses
  the gloss.
- The guns' 3D models live under `assets/rivals-paint/models/item/`; the ink faces are dye-tinted
  to the team colour. All four are Julle's hand-built Blockbench models, delivered under
  `tools/julle/` (two snapshots, each with its masters, previews and a ready resource pack) and
  copied in as `paint_gun.json` (their shooter — the registry id is v2's and stays), `charger.json`,
  `slosher.json` and `roller.json`. The only edits on the way in are the texture ids — one shared
  128×128 pair, `julle_body` and `julle_ink`, instead of `splat:item/shooter_*` — and the ink LED
  element. Their `display` transforms are kept verbatim: they were authored against in-game
  first- and third-person screenshots (see `tools/julle/*/previews/`), and the LED's placement is
  computed from the first-person one, so changing a transform moves the LED with it.
- Ink faces take `tintindex: 0`, which our `items/*.json` feeds from `minecraft:dye` — the same
  component `PaintWeapon.withTankColor` writes every inventory tick — and the LED takes
  `tintindex: 1` off `custom_model_data` colour 0. Fixed lime, ivory and dark parts stay untinted.
- Ink: every gun holds one shared tank of **100**, which is Splatoon's own scale — every cost in the
  table above is that game's percentage with no factor in front of it — tracked in the stack's own data
  so it survives item moves, and clamped on the way out so a stack written when the tank was 40 comes
  back part-full rather than wrong. Trying to fire on a tank that can't cover the shot starts a 30-tick
  refill (sound, cooldown) that fills the tank the moment the deadline passes. Standing in your own
  colour's paint tops the tank up at Splatoon 1's rates — ten seconds on your feet, three as a squid —
  but not while the weapon is still inside its own `refill_delay` after a shot. An action-bar ammo bar
  in the team colour refreshes every 10 ticks and after every shot, rounded to ten cells
  (`INK ██████░░░░ 61/100`), and reads
  `REFILLING…` or adds `SQUID` as appropriate. A scoped charger adds its charge to the same line and
  refreshes every tick instead of every ten (`INK ████░░ 26  CHARGE ▮▮▮▯▯▯ 48%`), in bold yellow at
  100%: the spyglass zoom says you are aiming and nothing else said how long you had been at it.
- Sneaking on your own colour's paint is squid form, and it's now a real mechanic rather than a
  cosmetic buff: half size, +80% movement speed with the vanilla sneak penalty lifted (net faster
  than sprinting), a big ~2.5-block hop with a floatier fall and no fall damage from it, and a
  taller step (seven attribute modifiers, not potion effects, so they're exact and don't show up
  in the client's effect list), plus vanilla invisibility — **which now ends on the same tick the
  form does**: the modifiers come off the instant it ends, but invisibility has no attribute, so it
  is a potion effect with a duration, and the old fifteen ticks left you invisible for up to three
  quarters of a second after Pirkko had already gone — neither squid nor player, just a hole in the
  floor. It is taken off explicitly on the way out now (only if it is ours: a brewed invisibility is
  not ambient and is left alone), and the duration is down to six ticks refreshed at three, so even a
  tick the loop never reaches can only leave a few ticks of it — and the gun refuses to fire while
  it's active. Diving into squid form from a stand gives a snappy horizontal surge and a quiet
  splash. Squid form also holds beside a wall face painted in your own colour even with no paint
  underfoot, so a climb off the floor paint doesn't drop you mid-wall: pushing into that wall
  swims you straight up it for as long as the ink goes, with a nudge over the lip at the top, and
  easing off clings in place instead of sliding back down. While the form is on, everyone else sees a
  team-coloured **Pirkko** riding your feet — Julle's model of the IT chapter's mascot, delivered in
  `tools/julle/pirkko_models/` and imported by `tools/pirkko_model.py`, lying flat on the paint at
  0.9 blocks long with her head leading the way you are going. She dives and jumps the way the
  Splatoon squid does: a lean nose-down into a swim that grows with your speed (up to ten degrees,
  with a hint of stretch along her length), nose up about 35° and a little off the floor on the way
  up out of a leap, nose down the same on the way back down, and a mild pancake for two ticks the
  tick she lands, because a hop has to read as having weight. There is no idle wobble, because the
  one time a squid holds still is the one time it must not be seen: lying still in your own ink is
  how a squid hides, so then she shows nothing at all and comes back the moment you move. You never
  see your own. The thrown paint ball keeps the old blob. "Pushing into" is your own movement keys
  rather than the server noticing a collision — walking into a wall is clipped client-side, so the
  server never sees one, which is why the climb used to stall a block up. Standing on an enemy
  colour's paint is a trap instead: Slowness II, no jumping at all, and half a heart of damage
  every second that never brings you below one health.
- Score: bossbars show each colour's share of painted faces across all levels — paint blocks and
  surviving display quads alike — counted once a second from the cells the painter has touched (in
  memory; a restart forgets them). They are on screen **only while a match is running**: the whistle takes
  them off everybody and the lobby never puts one back, because last round's "DATA 100 %" hanging over a
  lobby reads as this round's score. The counting keeps happening either way — it is also the prune. `/rivals score` counts only the level it is run in, and names
  that level in its reply — and **with arena bounds set it sweeps the arena itself** for paint blocks
  instead of trusting the tracking, so a score taken after a restart is honest about paint that was
  already standing. Without bounds there is nothing to sweep but the whole level, so the reply says
  that only paint placed since the server started is known.

## Play

```
/rivals setup            make the two teams (names from config/rivals-paint/teams.json)
/team join main.data @s
/rivals gun              shooter, the default
/rivals gun slosher      or charger / roller
/rivals kit              one of every weapon
/rivals score
/rivals reset
/rivals reload           re-read every file in config/rivals-paint/ (teams, unpaintable, main, weapons, specials)
/rivals config           what each config file is for and holds right now
/rivals help             the whole setup, in order
/rivals weapons          the weapon picker dialog (any player)
/rivals weapons pick roller   what its buttons run
/rivals spawn set data   where a team starts
/rivals arena set <from> <to>
/rivals ready            who is here, on which side, and armed
/rivals match start 3    three minutes
/rivals match stop
/rivals match status
```

### Running a match

The two sides are plain **vanilla scoreboard teams**. Which ones is set in
`config/rivals-paint/teams.json` (`{"teams": {"data": "main.data", "it": "main.it"}}` — the keys are the two
colour slots, the values are the team names they use), written with its own `_help` the first time the
server starts and re-read by `/rivals reload`. Point a slot at a team the server already runs, or leave the
defaults — MAIN's own `main.data` and `main.it` — and let `/rivals setup` make them if they are missing. Players
join a side with `/team join <name>`.

In order, once per arena:

```
/rivals setup                          make any of the two teams that do not exist yet
/rivals spawn set data                 stand where DATA starts, facing the way they should face
/rivals spawn set it
/rivals arena set 10 60 10 90 90 90    the bounds; paint outside them is refused
/rivals arena show                     check them
```

Then, once per round:

```
/team join main.data @s                     each player picks a side (or an operator assigns them)
/rivals weapons                        each player, or right-click the weapon selector
/rivals special                        and what F throws; the weapon picker's last button opens it too
/rivals ready                          fails and names anybody on neither team
/rivals match start 3                  ... or  /rivals match start 3 force
/rivals match status                   the state and the clock
/rivals match stop                     the whistle, early
```

`match start` does the rest: both teams made if need be, a clean arena, everybody's chosen weapon, a
teleport to their side's spawn, the countdown, the timer bar, the result and the fireworks, and the lobby
ten seconds later.

### Running it under MAIN

On the event server nobody types `match start`: the MAIN datapack does it with a scoreboard flag, and
the mod answers. Two objectives and two functions are the whole contract.

```
scoreboard objectives add splat.state dummy          MAIN's, and MAIN writes it
scoreboard players set ?running splat.state 1        the minigame is on  (main.state == 3)
scoreboard players set ?running splat.state 0        the minigame is off
```

The mod polls `?running` every ten ticks and acts on the edges: 1 starts a round, 0 stops one. It never
writes the flag. When the round ends, the arena's win function for the winning side runs and MAIN takes
it from there — so, once per arena:

```
/rivals win-function set data main:api/end_game_data
/rivals win-function set it   main:api/end_game_it
/rivals draw-function set     <whatever MAIN wants on a real draw>
```

A stop caused by the flag dropping runs nothing, because MAIN is already ending it. How long a
flag-started round runs is `config/rivals-paint/main.json`:

```json
{"minutes": 3}
```

and the arena is whichever level has a spawn set for both sides, so the setup above is still the setup.
For the outro, the mod writes **`splat.stats.blocks`** (how much of the final picture each player was the
last to paint) and **`splat.stats.kills`** (one per kill, live) — both dummy objectives, made here if
MAIN has not made them, emptied at the start of every round, with a zero for everybody playing so a top
five sorts the whole roster.

### Tuning

Every number a shot is made of is adjustable from inside the game, per weapon, and lands on the next
click — no restart, no reload:

```
/rivals tune                            everything that is off its default
/rivals tune shooter                    one weapon's whole sheet, defaults in [brackets]
/rivals tune shooter velocity           one number
/rivals tune shooter velocity 2.4       set it; the reply says what it was
/rivals tune shooter reset              that weapon back to its defaults
/rivals tune reset                      all four back to theirs
/rivals tune special                    the specials' sheet: what is off its default
/rivals tune special burst_bomb         one special's whole sheet
/rivals tune special burst_bomb ink 30  set it; the reply says what it was
/rivals tune special reset              all three back to theirs
```

Weapon ids, special ids and parameter names all tab-complete, and a name that does not exist answers
with the ones that do. `special` is a literal in the weapon's place, because a special is not a weapon:
what F throws belongs to the thrower rather than to the gun, so it has a sheet of its own keyed by
special.

| group | parameters | who reads them |
|---|---|---|
| the shot | `velocity`, `spread`, `spread_air`, `straight_blocks`, `decayed_speed`, `gravity`, `bounces`, `restitution`, `lifetime`, `splat_radius`, `count`, `fan_yaw`, `fan_pitch` | the three that throw a ball |
| the cost | `ink`, `cooldown`, `refill_delay`, `kick` | all four |
| the damage | `damage`, `decay_start`, `decay_per_tick`, `decayed_damage` | the three that throw a ball |
| a bounce | `spatter_count`, `spatter_lifetime`, `spatter_speed`, `spatter_scatter`, `spatter_damage` | the three that throw a ball |
| the roll | `roll_width`, `roll_damage`, `roll_hit_cooldown`, `roll_ink_every`, `roll_speed` | the roller alone |
| the charge | `charge_min`, `charge_full`, `range_min`, `range_full`, `charge_ink_min`, `charge_ink_full`, `charge_damage_min`, `charge_damage_partial`, `charge_damage_full` | the charger alone |

And the specials' own sheet, under `/rivals tune special <id>`:

| group | parameters | who reads them |
|---|---|---|
| the cost | `ink`, `cooldown`, `refill_delay` | all three |
| the throw | `velocity`, `gravity`, `pitch`, `lifetime`, `scale` | all three |
| the blast | `radius`, `damage`, `edge_damage`, `blast`, `core` | all three |
| the fuse | `fuse` | the splat bomb alone |
| the slide | `slide_ticks`, `friction` | the curling bomb alone |

`straight_blocks` and `decayed_speed` are the shot's shape — how far it flies straight and fast, and
what it drops to after that — and `decay_start` / `decay_per_tick` / `decayed_damage` are how the
damage falls off with time in the air; a `decay_per_tick` of 0 is a weapon that does not care how far
it has thrown. `refill_delay` belongs to all four, and a special waits its own instead — the
special's, not the weapon it was thrown from, because seventy ink of a hundred is not a shooter's shot.

The charger's `range_*` and `charge_ink_*` run from no charge to a full one with everything between
interpolated. Its **damage does not**, and that is deliberate: `charge_damage_min` → `charge_damage_partial`
is the line a partial charge climbs (8 → 16), and `charge_damage_full` (32) is a *step* taken the moment
the charge is full. Splatoon's charger is built on exactly that discontinuity — held to the top it
splats, let go a moment early it does not — and a straight 8 → 32 would make every fraction of a charge
worth its fraction of a kill, which is a duller weapon. `flick_tap` is gone with the tap it timed: the
roller's flick is the left button, so there is nothing to tell a tap from a hold, and a tuning file that
still names the key is accepted with a line in the log and the key dropped.

Every parameter has a range, which `/rivals tune <weapon>` prints beside it and a refusal states:
several of them are loop bounds and spawn counts, so a `splat_radius` of 500 (a million block writes
in one splat) or a `count` of 5000 is refused rather than clamped. A value out of range in the file is
clamped with a warning, and `NaN`/`Infinity` — which a hand edit can get past the json parser — is
ignored with one.

The tuning lives in `config/rivals-paint/weapons.json`, and the specials' in
`config/rivals-paint/specials.json`; both are written after every change. Only what differs from the
defaults is kept, so a fresh file is `{}` and a default changed in the code still reaches everyone who
never touched it. The defaults themselves are the constants in `Weapon.java`, `Special.java` and
`PaintBall.java`; a missing or unreadable file is a warning in the log and the defaults stand.

Or just get dressed: wearing an ovvar ovve puts you on that chapter's team within the second, creating
the teams if nobody has run `/rivals setup` yet. Matched on the item's registry id (`ovvar:data_*` →
DATA, `ovvar:it_*` → IT), so there is no dependency on ovvar and an arena without it plays exactly as
before; taking the ovve off leaves you on the team you were on.

## Build, run, test

```
./gradlew mods:rivals-paint:build -x mods:metacraft-lib:test  # lib unit tests fail on dev for unrelated reasons
./gradlew mods:rivals-paint:runServer      # needs two runs on a fresh clone, see below
./gradlew mods:rivals-paint:runGameTest    # server-side game tests (107 of ours, plus vanilla's always_pass: 108 in total)
```

`run/` is gitignored, and the `eula = true` in `build.gradle` applies only to the game-test run, so
on a fresh clone `runServer` takes two passes:

1. Run it once. That creates `run/` (with `eula.txt` and `server.properties`) and the server stops.
2. Set `eula=true` in `run/eula.txt` and `online-mode=false` in `run/server.properties` (a fresh
   `server.properties` has `online-mode=true`), then run it again.

Polymer's pack autohost is on by default in the dev environment, so the required pack is served
without any extra setup.

Join the dev server with a vanilla 26.3-rc-1 client (offline mode) and accept the pack.

To drive a running dev server without a client attached (`/rivals setup`, `/op <name>`, a score
check), `tools/rcon.py` sends one command over RCON and prints the reply — standard library only,
no dependency to install:

```
python3 mods/rivals-paint/tools/rcon.py 'rivals setup'
```

It defaults to `127.0.0.1:25576` with the password `rivals-dev` (override with `RCON_HOST`,
`RCON_PORT`, `RCON_PASSWORD`). RCON is off in a fresh `server.properties`, so add these three
lines to `run/server.properties` alongside `online-mode=false` in step 2 above, then restart:

```
enable-rcon=true
rcon.port=25576
rcon.password=rivals-dev
```

## Credits

All four weapon models — shooter, charger, slosher and roller — are Julle's, delivered in
`tools/julle/` with their Blockbench masters, their shared 128×128 `julle_body`/`julle_ink` atlas and
their display transforms, which ship verbatim. **Pirkko**, the figure a squid wears for everyone else,
is Julle's too: the IT chapter's mascot, a twelve-cube adaptation of the reference at
[Pirkko Power](https://pirkkopower.com/), delivered at `tools/julle/pirkko_models/` with its own
Blockbench masters, previews and 128×128 greyscale sheet, and imported by `tools/pirkko_model.py` as a
copy and a rename so a redelivery is a re-run. In their own words: *original fan-made geometry;
unofficial fan models inspired by Splatoon. Splatoon belongs to Nintendo; Minecraft belongs to
Mojang/Microsoft.* The paint art — block textures and the display quads' sprite alike — is generated
by the mod. Kenney's Blaster Kit supplied the weapons up to round 6 and Kenney's Splat Pack the
display quads up to round 5; neither is shipped any more.

`tools/splatcraft-rp/` is **SplatCraft RP by SculK3d**, from the "SplatCraft Map + RP" bundle, under
**LGPL-3.0** (its own `LICENSE.LGPL-3.0` and `ATTRIBUTION.md` are kept with it). It is source material,
not something the mod ships: it is where the squid item model, the splat bomb model and the ink tank
textures are being read from. Nothing in `src/main/resources` is copied out of it as things stand, and
anything that ever is inherits those terms.

## Not yet

Persisting display quads and the tally across a restart; damage on
enemy paint (beyond the enemy-ink drip); Iris-compatible gloss; respawn/death
handling for the drip.
