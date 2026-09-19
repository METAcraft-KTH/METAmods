package nu.metacraft.rivals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.rivals.gun.Ink;
import nu.metacraft.rivals.gun.InkOnScreen;
import nu.metacraft.rivals.gun.PaintWeapon;
import nu.metacraft.rivals.gun.Roll;
import nu.metacraft.rivals.gun.WeaponTuning;
import nu.metacraft.rivals.gun.WeaponTuning.Param;
import nu.metacraft.rivals.paint.Paint;
import nu.metacraft.rivals.paint.PaintDisplays;
import nu.metacraft.rivals.paint.Painter;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player paint effects, every tick.
 *
 * <p>Sneaking in own-colour paint is squid form: small, quick, invisible, refilling, and unable to
 * shoot. It holds over the paint as well as in it — own-colour ink anywhere in the four cells under the
 * feet counts, and a short grace period after that carries a leap between two painted surfaces — so a
 * jump never costs the form (and the invisibility with it). Squid form also holds — it does not require paint under the feet — while the player is
 * beside a wall inked in their own colour: pushing into that wall climbs it, and easing off clings
 * to it instead of sliding back down, so a climb off the floor paint never drops the player mid-wall.
 * The cling is gravity switched off by attribute rather than a velocity packet, and the climb's packet
 * carries the movement the player was measured making, so a squid that jumps keeps its momentum
 * instead of having it overwritten by the server's stale idea of where it was going.
 * The size and speed come from {@link SquidState}'s attribute modifiers rather than potion effects,
 * so they are exact and do not show up in the client's effect list; only invisibility is still a
 * potion effect, because there is no attribute for it — and because it has a duration where the
 * modifiers do not, it is taken off explicitly on the tick the form ends, so the player reappears with
 * the Pirkko rather than up to three quarters of a second after it. Entering squid form from a stand is a dive: a
 * horizontal shove along the player's look direction and a quiet splash, gated by a short per-player
 * cooldown so it fires once per dive rather than every tick spent in the paint. A swimming squid
 * leaves a wake — a burst of ink at its feet every tick it is actually moving, a soft swim
 * note every six — and the dive throws a ring of them; a squid that has stopped leaves nothing, so
 * the trail reads as movement rather than as a permanent marker saying "someone is here".
 *
 * <p>Standing in another colour is a trap rather than an inconvenience: Slowness II, no jump at all,
 * and a point of damage every second (never the last one — enemy ink wears you down, it does not kill
 * you on its own). Potion effects here are short and topped back up to their full duration only once
 * they run low, so leaving the paint lets them run out within a second with no bookkeeping, and
 * vanilla isn't resyncing a fresh effect packet to the client every tick.
 */
public final class PlayerTick {
	private static final int EFFECT_TICKS = 15;
	/**
	 * Invisibility's own, much shorter, duration.
	 *
	 * <p>It is the one effect whose lifetime anybody can see: the squid disappears the tick the form
	 * ends, so any invisibility outliving the form is a player who is simply not there. {@link #exitForm}
	 * takes it off on that tick, which is the fix; this is the belt to that braces, for the tick the
	 * player is never given — a disconnect mid-jump, a crash, a tick the loop does not reach. Six ticks
	 * refreshed at three is the shortest pair that still keeps {@link #keep} from re-adding the effect
	 * (and vanilla from resending its packet) every single tick.
	 */
	private static final int INVISIBILITY_TICKS = 6;
	/**
	 * Standing in your own ink refills the tank, at Splatoon 1's own rates on a 100-unit tank: ten
	 * seconds on your feet, three as a squid ({@code InkTankItem.inventoryTick} gives 0.5 and 1.667 a
	 * tick). Whole ink on a whole tick, so each is the nearest fraction with a small period — one every
	 * two ticks is a half, five every three is 1.667 exactly.
	 */
	private static final int TOPUP_EVERY = 2;
	private static final int TOPUP = 1;
	private static final int SQUID_TOPUP_EVERY = 3;
	private static final int SQUID_TOPUP = 5;
	/** Ticks between two drips of enemy-ink damage. */
	private static final int DRIP_EVERY = 20;
	private static final float DRIP_DAMAGE = 1.0f;
	/** Upward speed while swimming up an inked wall, blocks per tick. */
	private static final double WALL_SWIM_SPEED = 0.42;
	/** How far the player's box may sit off a wall's plane and still count as pressed against it. */
	private static final double WALL_REACH = 0.15;
	/**
	 * How much faster than the climb a rise has to be before it is read as the player's own jump rather
	 * than as the climb the server itself asked for. A steady climb measures almost exactly
	 * {@link #WALL_SWIM_SPEED} every tick — with the odd floating-point crumb either side of it — so a
	 * bare {@code >=} would drop every other climb packet; a squid's jump leaves the ground at about
	 * 0.75, which clears this comfortably.
	 */
	private static final double JUMP_MARGIN = 0.1;
	/** Horizontal nudge over the lip on the tick the climbed wall runs out above the player's head. */
	private static final double LEDGE_HOP = 0.25;
	/** Horizontal push, along the look direction, on the tick squid form is entered. */
	private static final double DIVE_SURGE_SPEED = 0.45;
	/** No repeat surge for a re-entry (e.g. a brief unshift) within this many ticks of the last one. */
	private static final int DIVE_SURGE_COOLDOWN = 10;
	/** How far below the feet own-colour paint still holds squid form: a ledge, a hop, a short drop. */
	private static final int INK_BELOW_DEPTH = 4;
	/** Ticks squid form survives after the last tick any ink held it — a jump, a gap, a leap off a roof. */
	private static final int SQUID_GRACE = 10;
	/** How often a player's ovve is checked against their team. Once a second is plenty for getting dressed. */
	private static final int OVVE_EVERY = 20;

	/** Below this many blocks per tick of horizontal movement a squid is holding still, not swimming. */
	private static final double RIPPLE_SPEED = 0.05;
	/** Ink pillars per swimming tick, plus the one crumb that goes with them. */
	private static final int RIPPLE_PARTICLES = 2;
	private static final int RIPPLE_CRUMBS = 1;
	/** Ticks between two swim notes. */
	private static final int SWIM_SOUND_EVERY = 6;
	/** Pillars thrown in a ring on the dive, and how far out they land. */
	/**
	 * How far behind itself a squid gets its own wake. Far enough that {@link Painter#NEAR_EYES} plus the
	 * crumb's spread clears the eyes of a swimming squid, which sit barely half a block over its feet.
	 */
	private static final double WAKE_BEHIND = 1.5;
	private static final int DIVE_RING_PARTICLES = 6;
	private static final double DIVE_RING_RADIUS = 0.5;

	/** Server tick of each player's last dive surge, so a flicker in and out of squid form does not
	 * re-trigger it every tick. Cleared alongside the squid bookkeeping on disconnect and server stop. */
	private static final Map<UUID, Long> LAST_DIVE = new HashMap<>();
	/** The last tick each player's ink held their squid form, for {@link #SQUID_GRACE}. */
	private static final Map<UUID, Long> LAST_INK = new HashMap<>();
	/** Where each squid was last tick, because a real player's server-side delta is not its speed. */
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

	/**
	 * How many velocity packets the squid loop has asked for, ever. Only a test reads it: a packet is
	 * queued by setting {@code syncVelocity} and sent by vanilla later in the tick, so there is nothing
	 * else a server-side test can count.
	 */
	private static int velocitySyncs = 0;

	/** How many velocity packets the squid loop has asked for. Read by the tests; see the field. */
	public static int velocitySyncs() {
		return velocitySyncs;
	}

	private PlayerTick() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player, now);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SquidState.clearAll();
			SquidDisplay.clearAll();
			LAST_DIVE.clear();
			LAST_POS.clear();
			LAST_INK.clear();
		});
		// A player who logs out mid-squid (or standing in enemy ink) is never ticked again, so nothing
		// would ever take the state off them: the UUID would stay in the squid set, and a rejoin would
		// report a squid whose attributes died with the old entity. Same tidy-up the spectator branch does.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// Including the invisibility: a potion effect is saved with the player, so a logout mid-swim
			// that left it on would have them rejoin invisible for the rest of its duration.
			exitForm(handler.getPlayer());
			SquidState.clearEnemyInk(handler.getPlayer());
			LAST_DIVE.remove(handler.getPlayer().getUUID());
			LAST_POS.remove(handler.getPlayer().getUUID());
			LAST_INK.remove(handler.getPlayer().getUUID());
			PaintWeapon.forget(handler.getPlayer());
			Roll.forget(handler.getPlayer());
		});
	}

	public static boolean isSquid(Player player) {
		return SquidState.isSquid(player);
	}

	/**
	 * The colour of the paint in the cell the player stands in, checked at two heights. Block paint and
	 * a wall/fence's display quads are keyed at the player's own feet cell, so that is checked first. A
	 * stair tread or bottom slab's quads are keyed one cell higher — {@link PaintDisplays#paint} stores
	 * them at {@code surface.relative(face)}, i.e. the cell above the tread — even though a player
	 * standing on that tread has {@code blockPosition()} equal to the tread's own cell, not the cell
	 * above it. So when the feet cell has nothing, the cell above is checked for quads only (a full
	 * paint block can't occupy the space a player's feet are standing in one cell below it).
	 *
	 * <p>That fallback only applies when the block at the feet cell is not a full cube. A player
	 * standing on a full block has their feet in the cell above it, and the cell above <em>that</em> is
	 * head height: paint on the wall beside a player's head is not paint they are standing in.
	 *
	 * <p>Paint under the feet means the cell's down face, the one lying on the floor the player stands
	 * on. A cell whose only paint is on a wall face is paint beside them, not under them — that is
	 * {@link #paintedWallBeside}'s business, and it is what holds squid form on during a wall climb.
	 * Display quads obey the same rule from the other side: their cell records the face of the
	 * <em>surface</em> they cover, so floor quads are the ones painted on a surface's {@link
	 * Direction#UP} face — quads on the side of a pane are a wall, not a floor, and must not count as
	 * paint underfoot.
	 */
	public static @Nullable PaintColor paintUnder(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return null;
		BlockPos feet = player.blockPosition();
		BlockState state = level.getBlockState(feet);
		if (state.getBlock() instanceof Paint paint && (paint.faceMask(state) & 1 << Direction.DOWN.ordinal()) != 0) return paint.color();
		PaintDisplays displays = PaintDisplays.of(level);
		PaintColor quads = floorQuads(displays, feet);
		if (quads != null) return quads;
		if (state.isCollisionShapeFullBlock(level, feet)) return null;
		return floorQuads(displays, feet.above());
	}

	/**
	 * The colour of the display quads lying face-up in {@code cell}, or null. {@link PaintDisplays}
	 * keys a cell by the surface face its quads cover, so {@link Direction#UP} is the floor case: the
	 * top of a slab, a stair tread or a fence post. Any other face is paint on a wall or a ceiling.
	 */
	private static @Nullable PaintColor floorQuads(PaintDisplays displays, BlockPos cell) {
		return displays.faceAt(cell) == Direction.UP ? displays.colorAt(cell) : null;
	}

	/**
	 * Is there own-colour paint below the player, within {@link #INK_BELOW_DEPTH} cells of their feet?
	 *
	 * <p>The same two kinds of floor paint {@link #paintUnder} knows about — a paint block carrying its
	 * {@link Direction#DOWN} face, or display quads covering a surface's {@link Direction#UP} face — but
	 * looked for all the way down rather than only in the cell the feet are in, because this answers
	 * "is the squid over its own ink" rather than "is it standing in it".
	 */
	public static boolean inkBelow(Player player, PaintColor own) {
		if (!(player.level() instanceof ServerLevel level)) return false;
		PaintDisplays displays = PaintDisplays.of(level);
		BlockPos feet = player.blockPosition();
		for (int drop = 0; drop <= INK_BELOW_DEPTH; drop++) {
			BlockPos cell = feet.below(drop);
			BlockState state = level.getBlockState(cell);
			if (state.getBlock() instanceof Paint paint && paint.color() == own
					&& (paint.faceMask(state) & 1 << Direction.DOWN.ordinal()) != 0) {
				return true;
			}
			if (floorQuads(displays, cell) == own) return true;
		}
		return false;
	}

	/** Is the player inside the {@link #SQUID_GRACE} ticks after the last tick their ink held them? */
	private static boolean inGrace(Player player, long now) {
		Long last = LAST_INK.get(player.getUUID());
		return last != null && now - last <= SQUID_GRACE;
	}

	/**
	 * Refresh {@code effect} to {@code ticks} only when it is missing, weaker, or run below half of them;
	 * re-adding it every tick regardless would make vanilla resend the effect packet every tick.
	 *
	 * <p>Ambient and invisible, which is also how {@link #exitForm} recognises the effects as ours: a
	 * player who drank an invisibility potion and then went for a swim keeps their potion when the swim
	 * ends, because a brewed one is not ambient.
	 */
	private static void keep(Player player, Holder<MobEffect> effect, int amplifier, int ticks) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() < ticks / 2) {
			player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, false));
		}
	}

	/**
	 * Stop being a squid: the attribute modifiers, the Pirkko everyone else was watching, and the
	 * invisibility that only the form ever asked for — all on the one tick.
	 *
	 * <p>The invisibility is the point. Squid form's size and speed are attribute modifiers, taken off
	 * the instant the form ends, but invisibility has no attribute and so it is a potion effect with a
	 * duration; {@link SquidState#exit} never touched it, and {@link #keep} had left it at up to fifteen
	 * ticks. So the figure vanished and the player stayed invisible for up to three quarters of a second
	 * after — not a squid and not a player, just a hole in the floor, which is the one thing squid form
	 * must not leave behind. Now the effect ends with the form.
	 *
	 * <p>Only if it is ours: {@link #keep} adds it ambient, and nothing brewed is. Safe to call for a
	 * player who is not a squid, which is what every path here does — the {@code wasSquid} check is only
	 * so a player who is not one is not made to pay for an effect lookup every tick.
	 */
	private static void exitForm(Player player) {
		boolean wasSquid = SquidState.isSquid(player);
		SquidState.exit(player);
		SquidDisplay.hide(player);
		if (!wasSquid) return;
		MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
		if (invisibility != null && invisibility.isAmbient()) player.removeEffect(MobEffects.INVISIBILITY);
	}

	public static void tick(Player player, long now) {
		// A spectator flies through the paint blocks they are standing in; giving them squid form,
		// slowness or damage for it is noise, and their gun (if any) is not usable anyway.
		if (player.isSpectator()) {
			exitForm(player);
			SquidState.clearEnemyInk(player);
			InkOnScreen.clear(player);
			LAST_POS.remove(player.getUUID());
			LAST_INK.remove(player.getUUID());
			return;
		}
		if (now % OVVE_EVERY == 0) wearYourColours(player);
		PaintColor under = paintUnder(player);
		Optional<PaintColor> own = PaintColor.byTeam(player.getTeam());
		boolean inOwn = under != null && own.isPresent() && under == own.get();
		boolean inEnemy = under != null && own.isPresent() && under != own.get();
		// A climb off the floor paint must not drop squid form mid-wall: without this, the moment the
		// player is lifted off the ground `inOwn` goes false, squid form ends, and the wall swim only
		// ever lasts the one tick that started it.
		boolean wallBeside = own.isPresent() && paintedWallBeside(player, own.get());
		// Ink under the feet, not only ink they are standing in: a jump, a ledge or a step off a kerb
		// takes the paint out from under a squid for a few ticks, and dropping the form (and with it the
		// invisibility) for that is the bug the user reported as "you go out of invisibility because you
		// were away from ink too long".
		boolean inkBelow = own.isPresent() && inkBelow(player, own.get());
		boolean held = inOwn || wallBeside || inkBelow;
		if (held) {
			LAST_INK.put(player.getUUID(), now);
		} else if (!inGrace(player, now)) {
			LAST_INK.remove(player.getUUID());
		}
		// And a grace period on top, so a leap between two painted roofs is one swim rather than two.
		boolean squid = (held || inGrace(player, now)) && player.isShiftKeyDown();
		boolean wasSquid = SquidState.isSquid(player);
		if (squid) {
			SquidState.enter(player);
			if (!wasSquid) diveSurge(player, now);
			keep(player, MobEffects.INVISIBILITY, 0, INVISIBILITY_TICKS);
			// How far the player actually moved since last tick. Measured, not read off
			// getDeltaMovement(): for a real player the server's delta is not the client's motion, and
			// both the wake and the climb packet are built from this.
			Vec3 moved = measure(player);
			wake(player, own.get(), moved, now);
			SquidDisplay.show(player, own.get(), moved, inOwn);
			if (wallBeside) {
				Direction climbing = paintedWallToward(player, own.get(), moveIntent(player));
				if (climbing != null) {
					SquidState.clearCling(player);
					// Not on the tick squid form was entered: the dive surge has already sent this tick's
					// velocity packet, and a climb packet on top of it would overwrite the surge.
					if (wasSquid) climb(player, climbing, moved);
				} else if (moved.y > 0.0) {
					// On the way up — a jump off the wall. Clinging here would switch gravity off at the
					// top of the impulse and leave the squid rising forever; let the arc finish.
					SquidState.clearCling(player);
				} else {
					boolean wasClinging = SquidState.isClinging(player);
					SquidState.applyCling(player);
					// Zero gravity stops a squid falling further, but it does not take away the speed it
					// already had: a squid that reaches the wall mid-fall would keep sinking at whatever
					// it was doing. One packet on the tick the cling goes on arrests that, and none after
					// — this is the one place the cling touches the client's motion at all.
					if (!wasClinging && wasSquid) arrest(player, moved);
				}
				player.resetFallDistance();
			} else {
				SquidState.clearCling(player);
			}
		} else {
			exitForm(player);
			LAST_POS.remove(player.getUUID());
		}
		if (inEnemy) {
			keep(player, MobEffects.SLOWNESS, 1, EFFECT_TICKS);
			SquidState.applyEnemyInk(player);
			// Wading through it splashes it up the visor: not more ink — how much there is is how much
			// health is gone — but this team's ink, which is what the visor is coloured with.
			InkOnScreen.standing(player, under);
			// Never the killing blow: enemy ink leaves you at one heart for someone else to finish.
			if (now % DRIP_EVERY == 0 && !player.isCreative() && player.getHealth() - DRIP_DAMAGE >= 1.0f
					&& player.level() instanceof ServerLevel level) {
				player.hurtServer(level, level.damageSources().magic(), DRIP_DAMAGE);
			}
		} else {
			SquidState.clearEnemyInk(player);
		}
		// Nothing refills while the trigger is down. Splatoon recovers no ink at all while a weapon is in
		// use, and without that rule the roller was a perpetual motion machine: rolling spends one ink
		// every five ticks and standing in your own paint pays one every two, so rolling through your own
		// ink filled the tank faster than rolling emptied it — "the roller loses less ink than you get
		// from walking on the ink". Written generally, for every held weapon rather than for the roller:
		// the shooter firing and the charger scoping are the same bargain. In practice it is only the
		// standing rate this can reach, since squid form and using an item cannot coexist.
		boolean firing = player.isUsingItem() && player.getUseItem().getItem() instanceof PaintWeapon;
		if ((inOwn || wallBeside) && !firing) {
			int gain = squid
					? (now % SQUID_TOPUP_EVERY == 0 ? SQUID_TOPUP : 0)
					: (now % TOPUP_EVERY == 0 ? TOPUP : 0);
			if (gain > 0) {
				for (InteractionHand hand : InteractionHand.values()) {
					ItemStack stack = player.getItemInHand(hand);
					// A weapon that has just fired is still recovering and takes nothing: Splatcraft's
					// ink_recovery_cooldown, which is why holding a shooter down over your own paint is
					// not free. How long the wait is was decided by whoever fired — a splat bomb waits
					// its own, not the weapon it was thrown from — so all this has to ask is whether it
					// is over.
					if (!(stack.getItem() instanceof PaintWeapon)) continue;
					if (Ink.recovering(stack, now)) continue;
					Ink.add(stack, gain);
				}
			}
		}
		// Last, because this tick's damage and healing have to be in before the LED is published: the
		// ink on the screen is the health that is missing, and nothing else.
		InkOnScreen.tick(player);
	}

	/**
	 * The ovve picks the team. Metacraft's ovvar mod dresses players in chapter overalls, and the arena
	 * should read the same way the campus does: put on the DATA ovve and you are on DATA, with no
	 * command to run. Matched on the item's registry id by {@link OvveTeams}, so nothing here depends on
	 * ovvar being installed; a player wearing no ovve keeps whatever team they had, because taking
	 * someone off their team for changing trousers would be worse than leaving them on it.
	 *
	 * <p>{@link RivalsCommands#setupTeams} is idempotent and creates both teams, so joining works on a
	 * server where nobody has run {@code /rivals setup} yet.
	 */
	private static void wearYourColours(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return;
		Optional<PaintColor> ovve = OvveTeams.worn(player);
		if (ovve.isEmpty()) return;
		PaintColor color = ovve.get();
		if (PaintColor.byTeam(player.getTeam()).orElse(null) == color) return;
		RivalsCommands.setupTeams(level.getServer());
		ServerScoreboard board = level.getScoreboard();
		PlayerTeam team = board.getPlayerTeam(TeamNames.nameOf(color));
		if (team == null) return;
		board.addPlayerToTeam(player.getScoreboardName(), team);
		if (player instanceof ServerPlayer server && server.connection != null) {
			server.sendSystemMessage(Component.literal("Your ovve puts you on " + color.displayName), true);
		}
	}

	/**
	 * How far the player moved since the last tick, and remember where they are now.
	 *
	 * <p>The one honest number a server-side tick has about a real player's motion: the server's own
	 * {@code getDeltaMovement()} is a guess reconstructed from move packets, while two consecutive
	 * positions are what the client did. The first tick of a swim has no previous position and so
	 * measures nothing, which is a tick, not a problem.
	 */
	private static Vec3 measure(Player player) {
		Vec3 at = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), at);
		return last == null ? Vec3.ZERO : at.subtract(last);
	}

	/**
	 * One tick of a wall climb: up at {@link #WALL_SWIM_SPEED}, keeping the horizontal momentum the
	 * player already had.
	 *
	 * <p>This is the module's only velocity packet in the squid loop, and the horizontal part of it is
	 * the <em>measured</em> movement rather than {@code getDeltaMovement()}: a packet built from the
	 * server's stale guess overwrites whatever the client was really doing, which is how a jump used to
	 * lose all its momentum. A player whose measured rise already matches the climb has jumped, and gets
	 * no packet at all ({@link #JUMP_MARGIN}) — their own impulse is faster than anything this would
	 * send them.
	 */
	private static void climb(Player player, Direction climbing, Vec3 moved) {
		if (moved.y > WALL_SWIM_SPEED + JUMP_MARGIN) return;
		// Nothing left to press into above the head means the wall has run out: a small push over the
		// lip, or the squid hangs at the top of the climb instead of topping out.
		double lip = topsOut(player, climbing) ? LEDGE_HOP : 0.0;
		player.setDeltaMovement(moved.x + climbing.getStepX() * lip, WALL_SWIM_SPEED,
				moved.z + climbing.getStepZ() * lip);
		player.syncVelocity = true;
		velocitySyncs++;
	}

	/**
	 * The tick a cling begins: keep the horizontal movement, drop the vertical to nothing. Built from the
	 * measured movement for the same reason {@link #climb}'s packet is.
	 */
	private static void arrest(Player player, Vec3 moved) {
		player.setDeltaMovement(moved.x, 0.0, moved.z);
		player.syncVelocity = true;
		velocitySyncs++;
	}

	/**
	 * The swimming squid's wake, once a tick, from the movement {@link #measure} took this tick.
	 */
	private static void wake(Player player, PaintColor own, Vec3 moved, long now) {
		if (!(player.level() instanceof ServerLevel level)) return;
		Vec3 at = player.position();
		if (ripples(level, player, own, moved).total() > 0 && now % SWIM_SOUND_EVERY == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SWIM, SoundSource.PLAYERS, 0.25f, 1.6f);
		}
	}

	/** What one tick of wake put out: the particles at the feet for everyone else, the crumbs behind it for the squid. */
	public record Wake(int others, int self) {
		static final Wake NONE = new Wake(0, 0);

		/** Everything the tick sent, which is what {@link #wake} listens for. */
		public int total() {
			return others + self;
		}
	}

	/**
	 * Ink specks at the squid's feet, if it is moving horizontally at all. Returns what it sent — the only
	 * thing a server-side test can see, since particles leave no trace in the level.
	 *
	 * <p>Mostly dust pillars — vanilla's mace-smash particle, a chunky column that rises and falls, so the
	 * wake reads as displaced ink rather than as grit — with one block crumb under them for texture. The
	 * shots and the splashes keep the crumb on its own; this is the one place ink is supposed to look big.
	 *
	 * <p>The squid does <em>not</em> get that wake. It comes out at foot level, and a half-height squid's eyes
	 * are only about half a block above its feet, so a rising pillar walks straight through its own camera and
	 * fills the screen with a translucent team-coloured square ({@link Painter#NEAR_EYES}). Leaving your own
	 * wake out entirely is what made squid form feel like nothing was happening, so the squid gets a wake of
	 * its own instead: crumbs only, {@link #WAKE_BEHIND} behind it, which is where a wake trails anyway.
	 */
	public static Wake ripples(ServerLevel level, Player player, PaintColor color, Vec3 velocity) {
		if (velocity.horizontalDistance() <= RIPPLE_SPEED) return Wake.NONE;
		Vec3 feet = new Vec3(player.getX(), player.getY() + 0.05, player.getZ());
		List<ServerPlayer> others = level.players().stream().filter(viewer -> viewer != player).toList();
		// A little upward speed so the ink hops out of the pool and falls back rather than sitting on it.
		Painter.burst(level, others, Painter.pillar(color), feet, RIPPLE_PARTICLES, 0.35, 0.02, 0.35, 0.05);
		Painter.burst(level, others, Painter.crumbs(color), feet, RIPPLE_CRUMBS, 0.3, 0.02, 0.3, 0.05);
		int self = 0;
		if (player instanceof ServerPlayer squid) {
			self = Painter.burst(level, List.of(squid), Painter.crumbs(color), wakeBehind(player, velocity),
					RIPPLE_CRUMBS, 0.2, 0.02, 0.2, 0.05);
		}
		return new Wake(RIPPLE_PARTICLES + RIPPLE_CRUMBS, self);
	}

	/**
	 * Where the squid's own wake goes: {@link #WAKE_BEHIND} back along the way it came, at foot level. A still
	 * squid never gets here — {@link #ripples} has already answered nothing — so the horizontal velocity always
	 * has a direction to reverse.
	 */
	public static Vec3 wakeBehind(Player player, Vec3 velocity) {
		Vec3 back = new Vec3(-velocity.x, 0, -velocity.z).normalize().scale(WAKE_BEHIND);
		return new Vec3(player.getX(), player.getY() + 0.05, player.getZ()).add(back);
	}

	/**
	 * A snappy dive: the tick squid form is entered from not-squid, push the player along their look
	 * direction and play a quiet splash. Cooldown-gated on {@link #LAST_DIVE} so a flicker in and out
	 * of squid form (e.g. a one-tick unshift at the edge of the paint) does not surge every re-entry.
	 */
	private static void diveSurge(Player player, long now) {
		Long last = LAST_DIVE.get(player.getUUID());
		if (last != null && now - last < DIVE_SURGE_COOLDOWN) return;
		LAST_DIVE.put(player.getUUID(), now);
		Vec3 look = player.getLookAngle();
		player.push(look.x * DIVE_SURGE_SPEED, 0, look.z * DIVE_SURGE_SPEED);
		player.syncVelocity = true;
		velocitySyncs++;
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.4f, 1.5f);
			// A ring of ink thrown outwards, so the dive lands with a splat rather than a shove. Through
			// Painter.burst, which at a radius of half a block drops the whole ring for the diver: pillars
			// rising a hand's width from their own eyes are a screenful of colour, not a splat.
			PaintColor.byTeam(player.getTeam()).ifPresent(color -> {
				for (int i = 0; i < DIVE_RING_PARTICLES; i++) {
					double angle = i * 2.0 * Math.PI / DIVE_RING_PARTICLES;
					Painter.burst(level, Painter.pillar(color),
							new Vec3(player.getX() + Math.cos(angle) * DIVE_RING_RADIUS, player.getY() + 0.05,
									player.getZ() + Math.sin(angle) * DIVE_RING_RADIUS),
							1, 0.0, 0.0, 0.0, 0.0);
				}
			});
		}
	}

	/**
	 * Is there own-colour paint on a wall face beside the player? Any of the four horizontal neighbours
	 * counts — this is the cling and the reason squid form holds off the floor paint, so which wall it
	 * is does not matter here; {@link #paintedWallToward} is the directed version that climbs.
	 *
	 * <p>Paint on a wall face lives in the cell in front of that face, which is the player's own cell
	 * (feet or head): as a paint block with the face flag pointing back at the wall, or as display
	 * quads keyed at that same cell when the wall is not a full cube. A quad-painted floor slab's
	 * quads are keyed at that same feet cell too (one cell above the tread), so the colour match alone
	 * is not enough — the quads must also carry the horizontal face pointing from the wall back at the
	 * player, i.e. {@code side.getOpposite()}, or a squid standing on its own paint would climb any
	 * paintable neighbour regardless of whether that neighbour is inked.
	 *
	 * <p>The head-height paint is matched against the neighbour at head height rather than the one beside
	 * the ankles: paint a cell up belongs to whatever wall is a cell up, and a shoulder-high ledge with
	 * air below it is still a wall to climb.
	 */
	static boolean paintedWallBeside(Player player, PaintColor own) {
		return paintedWall(player, own, Vec3.ZERO) != null;
	}

	/**
	 * The inked wall the player is actually climbing: one they are both asking to move into and pressed
	 * up against, or null. {@code move} is the intended direction from {@link #moveIntent}; an empty
	 * one is a cling rather than a climb, so it never picks a wall.
	 */
	static @Nullable Direction paintedWallToward(Player player, PaintColor own, Vec3 move) {
		// Not a guard the shared scan can make: an empty move there means the undirected cling scan,
		// which answers with whatever wall is beside the player.
		return move.lengthSqr() < 1.0E-4 ? null : paintedWall(player, own, move);
	}

	/**
	 * Which way the player is asking to move, as a unit horizontal vector, or {@link Vec3#ZERO} when
	 * they are asking for nothing.
	 *
	 * <p>This reads the client's own key state rather than the movement the server saw. A player
	 * walking into a wall has their delta clipped client-side and sends one of about zero, so the
	 * server's own {@code move()} never reports a horizontal collision on that player: testing
	 * {@code horizontalCollision} here only ever climbed the single block squid form's taller step
	 * height carried the player over. Only a {@link ServerPlayer} has client input; any other player
	 * (a fake one) is treated as asking for nothing and can still cling.
	 */
	static Vec3 moveIntent(Player player) {
		if (!(player instanceof ServerPlayer server)) return Vec3.ZERO;
		Input input = server.getLastClientInput();
		double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
		double strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) return Vec3.ZERO;
		// The rotation vanilla's Entity.getInputVector does: yaw 0 faces +Z, and the strafe axis is
		// positive to the left.
		float yaw = player.getYRot() * ((float) Math.PI / 180f);
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		return new Vec3(strafe * cos - forward * sin, 0, forward * cos + strafe * sin).normalize();
	}

	/**
	 * The shared scan. Either way the player has to be hugging the wall ({@link #pressedAgainst}). With
	 * an empty {@code move} any horizontal neighbour they are up against counts — that is the cling, and
	 * the test that keeps squid form on. With a direction, only a wall they are also pushing into
	 * (within 60° of it, i.e. {@code dot > 0.5}) counts.
	 */
	private static @Nullable Direction paintedWall(Player player, PaintColor own, Vec3 move) {
		if (!(player.level() instanceof ServerLevel level)) return null;
		boolean directed = move.lengthSqr() >= 1.0E-4;
		BlockPos feet = player.blockPosition();
		BlockPos head = feet.above();
		PaintDisplays displays = PaintDisplays.of(level);
		// None of these four vary with the direction, and three are chunk lookups: read them once.
		BlockState atFeet = level.getBlockState(feet);
		BlockState atHead = level.getBlockState(head);
		PaintColor quadColor = displays.colorAt(feet);
		Direction quadFace = displays.faceAt(feet);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			// Pushing into it is what separates a climb from a cling, so only the climb asks about the
			// direction — but both ask about the hug. A cling that reached any painted neighbour at any
			// distance held squid form (and switched gravity off) for a squid floating in mid-air beside a
			// pane, which vanilla's own flying check would then kick them for.
			if (directed && move.x * side.getStepX() + move.z * side.getStepZ() <= 0.5) continue;
			if (!pressedAgainst(player, feet.relative(side), side)) continue;
			if (Painter.paintable(level.getBlockState(feet.relative(side)))) {
				if (facing(atFeet, side, own)) return side;
				if (quadColor == own && quadFace == side.getOpposite()) return side;
			}
			if (facing(atHead, side, own) && Painter.paintable(level.getBlockState(head.relative(side)))) return side;
		}
		return null;
	}

	/** Is the player's box within {@link #WALL_REACH} of {@code wall}'s near plane, or already inside it? */
	private static boolean pressedAgainst(Player player, BlockPos wall, Direction side) {
		AABB box = player.getBoundingBox();
		double gap = switch (side) {
			case EAST -> wall.getX() - box.maxX;
			case WEST -> box.minX - (wall.getX() + 1);
			case SOUTH -> wall.getZ() - box.maxZ;
			case NORTH -> box.minZ - (wall.getZ() + 1);
			default -> Double.POSITIVE_INFINITY;
		};
		return gap <= WALL_REACH;
	}

	/**
	 * Has the climbed wall run out? The cell two above the player's feet is still beside their head, so
	 * the question is about the one above <em>that</em>: no collision there means the next tick of the
	 * climb has nothing left to press into. Asking a block lower fires the hop while the wall is still
	 * there and shoves the squid off it. A pane or fence still counts as wall, so a climb up one is not
	 * cut short either.
	 */
	private static boolean topsOut(Player player, Direction side) {
		if (!(player.level() instanceof ServerLevel level)) return false;
		BlockPos above = player.blockPosition().above(3).relative(side);
		return level.getBlockState(above).getCollisionShape(level, above).isEmpty();
	}

	/** Is {@code cell} a paint block of {@code own} carrying a face that looks towards {@code side}? */
	private static boolean facing(BlockState cell, Direction side, PaintColor own) {
		return cell.getBlock() instanceof Paint paint && paint.color() == own
				&& (paint.faceMask(cell) & 1 << side.ordinal()) != 0;
	}
}
