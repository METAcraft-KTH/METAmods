package nu.metacraft.rivals.gun;

import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.PlayerTick;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.gun.WeaponTuning.Param;
import nu.metacraft.rivals.paint.Painter;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Every paint weapon, in one item class parameterised by a {@link Weapon}. Right click fires: it throws
 * paint in the colour of the holder's vanilla team, and no team means no shot. Left click is the roller's
 * flick, the one second gesture that belongs to a weapon whose right click is a hold; the charger fires
 * when the scope is <em>let go</em>, because a vanilla client refuses to attack while it is using an item,
 * so a scoped charger has no left click at all. <b>F</b>, the swap-hands key, is the special: the splat bomb. Clients
 * see a stand-in vanilla item wearing our 3D model; the model's ink is dye-tinted, and each inventory
 * tick writes the holder's team colour into the server-side stack as that dye, so every viewer sees the
 * weapon in its holder's colour.
 *
 * <p>The three buttons, in one table:
 *
 * <table border="1">
 * <caption>controls</caption>
 * <tr><th>weapon</th><th>right click</th><th>left click</th><th>F</th></tr>
 * <tr><td>shooter</td><td>hold to fire</td><td>—</td><td>splat bomb</td></tr>
 * <tr><td>charger</td><td>hold to scope/charge, let go to fire</td><td>— (blocked by the scope)</td><td>— (no bomb)</td></tr>
 * <tr><td>slosher</td><td>slosh</td><td>—</td><td>splat bomb</td></tr>
 * <tr><td>roller</td><td>hold to roll</td><td>flick</td><td>splat bomb</td></tr>
 * </table>
 *
 * <p>How the trigger is read depends on the weapon. A vanilla client repeats a <em>held</em> right
 * click only every four ticks, which is a ceiling of five shots a second, so the shooter and the roller
 * are {@link #isHeld held} instead: the press starts using the item and {@link #onUseTick} does the
 * work every tick until the button is let go. The slosher stays one slosh per click — its own cadence
 * is twelve ticks, so the repeat fits inside it — and the charger has always been held, for its charge.
 * Either way the item cooldown is the fire rate, and it is checked on the server rather than trusted to
 * the client. Holding takes both sides, though: a vanilla client decides for itself whether it is using
 * an item, and it is the side that sends the release — so the client stack carries a consumable
 * component that makes it agree. See {@link #HELD_USE}, which is where the roller's dead flick was.
 *
 * <p>Only the slosher swings the arm: it is a bucket, and the throw reads as one. The others return
 * {@link InteractionResult#CONSUME}, which takes the click without animating the hand — a swing loop on
 * a three-tick shooter looks like a stutter, not like firing.
 */
public final class PaintWeapon extends Item implements PolymerItem {
	private static final Map<Weapon, PaintWeapon> ITEMS = new EnumMap<>(Weapon.class);
	/** World up, for the barrel offset: right is look × up. */
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private final Weapon weapon;

	public PaintWeapon(Weapon weapon, Properties properties) {
		super(properties);
		this.weapon = weapon;
	}

	/**
	 * What a client does to a player who is holding an item in use. Vanilla's default is a bow's: no
	 * sprinting and a fifth of the walking speed, applied in {@code LocalPlayer} off the
	 * {@code minecraft:use_effects} component — and since round 7 the shooter and the roller are held
	 * items, so without this holding the trigger would leave a player crawling. The component is a
	 * default on the item, so it reaches the client on every stack.
	 *
	 * <p>The record is {@code (canSprint, interactVibrations, speedMultiplier)}. The numbers are
	 * Splatoon's own idea rather than vanilla's: firing a shooter there slows you to about seven tenths,
	 * not one fifth, so the shooter keeps its sprint and takes 0.72. The roller keeps the full multiplier
	 * — a roller at full tilt is the fastest thing on the map, and its own {@code roll_speed} attribute is
	 * what decides how fast, so two speed rules would be one too many — but it may <b>not sprint</b>: you
	 * are pushing a drum along the floor, and sprinting with it was the thing that made the roll read as
	 * free. The charger is deliberately left on vanilla's default: a charger that could run while scoped
	 * would be a sniper rifle with no cost at all, and being pinned in place is what the weapon trades its
	 * damage for.
	 */
	private static final UseEffects SHOOTER_USE = new UseEffects(true, false, 0.72f);
	private static final UseEffects ROLLER_USE = new UseEffects(false, false, 1.0f);

	/**
	 * What makes a <em>vanilla</em> client hold the trigger. This is the other half of held use, and
	 * without it the server half above was talking to itself.
	 *
	 * <p>The server starts using the item in {@link #use}, but the client never did, and almost
	 * everything about holding a button is the client's decision:
	 *
	 * <ul>
	 * <li>{@code Minecraft.handleKeybinds} only sends {@code RELEASE_USE_ITEM} — the packet that reaches
	 *     {@link #releaseUsing} — while {@code player.isUsingItem()}. With the client not using, the
	 *     roller's release never arrived, so <b>the flick never fired</b> and the {@code held} time the
	 *     tap is told from the roll by was meaningless.</li>
	 * <li>A client that is not using an item repeats the <em>use</em> packet every four ticks instead
	 *     (its {@code rightClickDelay}), so the server was re-{@code startUsingItem}ing on a loop.</li>
	 * <li>{@code minecraft:use_effects} — the no-sprint and the speed multiplier — is applied in
	 *     {@code LocalPlayer} only while the client is using, so the shooter's 0.72 and the roller's
	 *     no-sprint never happened at all.</li>
	 * </ul>
	 *
	 * <p>26.3's {@code Item.use} starts using an item that carries a {@code minecraft:consumable}
	 * component: it reads CONSUMABLE, calls {@code Consumable.startConsuming}, and that calls
	 * {@code startUsingItem} whenever {@code consumeTicks() > 0}. {@code canConsume} only consults the
	 * FOOD component, and there is none here, so it is always true. That is the whole trick: put a
	 * consumable on the <em>client</em> stack, and a vanilla client holds the button down for us. (The
	 * charger needs none of this — it is disguised as a spyglass, and {@code SpyglassItem.use} starts
	 * using by itself, which is why the charger was the one weapon that always worked.)
	 *
	 * <p>The disguise had to change with it. {@link #getPolymerItem} used to hand out
	 * {@code warped_fungus_on_a_stick}, and {@code FoodOnAStickItem.use} returns PASS on the client
	 * before it ever looks at a component, so no component could have helped; a plain {@link Item} —
	 * a stick — runs the base {@code Item.use} that reads CONSUMABLE. The client only ever sees our own
	 * model anyway ({@link #getPolymerItemModel}), so which vanilla item is underneath is invisible.
	 *
	 * <p>An hour of consume time, matching {@link #getUseDuration}, the NONE animation so the hand is
	 * not raised to a mouth, no consume particles, and the intentionally-empty sound. The last two are
	 * belt and braces: {@code Consumable.shouldEmitParticlesAndSounds} only starts emitting after 21.875%
	 * of the consume time has passed — thirteen minutes here — and then only every fourth tick, so a
	 * realistic hold never reaches the first burp. Nothing ever completes, either: the server ends the
	 * use long before the hour is up, and the server is the one that decides what the item does.
	 */
	private static final Consumable HELD_USE = Consumable.builder()
			.consumeSeconds(Weapon.CHARGE_MAX_TICKS / 20.0f)
			.animation(ItemUseAnimation.NONE)
			.sound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY))
			.hasConsumeParticles(false)
			.build();

	public static void register() {
		for (Weapon weapon : Weapon.values()) {
			Identifier id = Rivals.id(weapon.id);
			Item.Properties properties = new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, id));
			if (weapon == Weapon.SHOOTER) properties.component(DataComponents.USE_EFFECTS, SHOOTER_USE);
			if (weapon == Weapon.ROLLER) properties.component(DataComponents.USE_EFFECTS, ROLLER_USE);
			ITEMS.put(weapon, Registry.register(BuiltInRegistries.ITEM, id, new PaintWeapon(weapon, properties)));
		}
	}

	/** The registered item for a weapon; null until {@link #register()} has run. */
	public static PaintWeapon of(Weapon weapon) {
		return ITEMS.get(weapon);
	}

	public Weapon weapon() {
		return weapon;
	}

	/**
	 * One of every weapon, dropped at the player's feet if the inventory is full. Returns how many went
	 * into the inventory — not how many were made — so a full inventory reports what it actually took.
	 */
	public static int giveKit(Player player) {
		int given = 0;
		for (Weapon weapon : Weapon.values()) {
			ItemStack stack = new ItemStack(of(weapon));
			if (player.getInventory().add(stack)) {
				given++;
			} else {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		return given;
	}

	/**
	 * The tick each player's splat bomb is ready again, by UUID. The bomb is one special rather than one
	 * per weapon, so switching guns does not hand out a second one, and it is deliberately not the item
	 * cooldown: that is the fire rate, and a special that stopped the trigger for four seconds would be
	 * a punishment rather than a choice. Absolute server ticks, so the map is cleared when the server
	 * stops — a deadline further ahead than the cooldown itself cannot have been set this session and is
	 * treated as spent, the same rule {@link Ink} uses for a stale refill.
	 */
	private static final Map<UUID, Long> SPECIAL_READY = new HashMap<>();
	/** The tick each player's left click was last answered, so one click is one special. */
	private static final Map<UUID, Long> LAST_LEFT_CLICK = new HashMap<>();

	/**
	 * Left click, from every path it can arrive on. A left click on a block or an entity reaches the
	 * server as an attack packet and then a swing packet in the same tick, and a click at thin air as
	 * the swing alone; the two callbacks here answer the first pair and the {@code handlePunch} mixin the
	 * swing, all of them through {@link #leftClick}, which takes the first of the tick and ignores the
	 * rest. Both callbacks refuse the vanilla action: a paint weapon must not break the arena, and a
	 * flick thrown at someone must not also be a punch.
	 */
	public static void init() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				holdsWeapon(player) ? answerLeftClick(player) : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				holdsWeapon(player) ? answerLeftClick(player) : InteractionResult.PASS);
		// Both maps are absolute server ticks, and the tick count starts again at 0 every boot.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SPECIAL_READY.clear();
			LAST_LEFT_CLICK.clear();
		});
	}

	/**
	 * Forget a player. Both maps are absolute server ticks keyed by UUID; a player who leaves would
	 * otherwise keep their entries until the server stopped. Called from {@link
	 * nu.metacraft.rivals.PlayerTick}'s disconnect hook, alongside the squid bookkeeping.
	 */
	public static void forget(Player player) {
		SPECIAL_READY.remove(player.getUUID());
		LAST_LEFT_CLICK.remove(player.getUUID());
	}

	private static boolean holdsWeapon(Player player) {
		return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PaintWeapon;
	}

	private static InteractionResult answerLeftClick(Player player) {
		leftClick(player);
		return InteractionResult.FAIL;
	}

	/**
	 * The swap-hands key, which is the special. A vanilla client sends a player action of
	 * {@code SWAP_ITEM_WITH_OFFHAND} for F, and the {@code handlePlayerAction} mixin hands it here before
	 * vanilla has swapped anything: a paint weapon answers with its {@link #special}, and the answer is
	 * the whole of it — nothing moves between the hands, whether the bomb went or was refused for its ink
	 * or its wait. Returns whether this was ours, which is what the mixin cancels the packet on, so F
	 * with anything else in hand is still vanilla's swap.
	 *
	 * <p>F rather than the left button because a left click has to stay the second trigger: the roller's
	 * flick and the charger's shot are what a player reaches for mid-fight, and a bomb is a decision.
	 */
	public static boolean swapHands(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return false;
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!(held.getItem() instanceof PaintWeapon weapon)) return false;
		weapon.special(level, player, held);
		return true;
	}

	/**
	 * The second trigger, once per tick per player, for whoever is holding a paint weapon in their main
	 * hand. Returns whether anything happened, which is what the tests read.
	 */
	public static boolean leftClick(Player player) {
		if (!(player.level() instanceof ServerLevel level)) return false;
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!(held.getItem() instanceof PaintWeapon weapon)) return false;
		long now = level.getServer().getTickCount();
		Long last = LAST_LEFT_CLICK.put(player.getUUID(), now);
		if (last != null && last == now) return false;
		return weapon.leftClick(level, player, held);
	}

	/**
	 * What a left click does with this weapon in hand: the second gesture of the two weapons that have
	 * one, and nothing at all for the two that do not.
	 *
	 * <p>The roller <b>flicks</b>. It is the button the gesture always wanted: the flick used to be a
	 * <em>tap</em> of the right button, told from a roll by how long the release came after the press,
	 * which meant a player who wanted one had to let go of the roll to ask for it and a player who wanted
	 * neither got one by accident. On the left button the two are independent — flick while rolling, and
	 * the roll is stopped for the throw and starts again on the next tick if the right button is still
	 * down — and the flick pays its own ink and its own recovery whether a roll was running or not.
	 *
	 * <p>The charger <b>fires</b>: the shot it has been charging under the scope if the player is scoped,
	 * and a snap shot at no charge if they are not, because the one weapon whose right click is already a
	 * hold needs its own button to pull the trigger with.
	 *
	 * <p>The shooter and the slosher do nothing. Their right click is the whole weapon, and their
	 * {@link #special} is on F with everybody else's.
	 */
	public boolean leftClick(ServerLevel level, Player player, ItemStack gun) {
		return switch (weapon) {
			case ROLLER -> {
				// The roll is stopped before the throw rather than after it: the head is being lifted off the
				// floor to swing, and a roll that went on painting through the flick would paint from a drum
				// that is over the player's shoulder.
				Roll.stop(player);
				yield flick(level, player, gun);
			}
			case CHARGER -> fireCharge(level, player, gun);
			case SHOOTER, SLOSHER -> false;
		};
	}

	/**
	 * The charger's trigger. The charge is whatever the scope has built, or none at all when the player is
	 * not scoped, and the scope is let go by the shot — the ink is checked first, so a shot refused for an
	 * empty tank leaves the player still aiming rather than dropping them out of the scope for nothing.
	 */
	private boolean fireCharge(ServerLevel level, Player player, ItemStack gun) {
		Optional<PaintColor> ready = ready(level, player, gun); // team, refill, squid
		if (ready.isEmpty()) return false;
		float charge = chargeOf(player);
		if (Ink.get(gun) < chargeCost(WeaponTuning.get(weapon), Math.max(charge, 0.0f))) {
			outOfInk(level, player, gun);
			return false;
		}
		if (charge >= 0) player.stopUsingItem();
		return chargerShot(level, player, gun, Math.max(charge, 0.0f), ready.get());
	}

	/**
	 * The special, on F: whichever of the three {@link Special}s the thrower picked, for that special's
	 * own ink and its own wait. Three of the four weapons throw one, and all three throw the same one —
	 * the special belongs to the player rather than to the gun, which is why it is picked in a screen of
	 * its own and remembered in {@link SpecialChoice}.
	 *
	 * <p>The charger does not, and says so rather than doing nothing: its charge <em>is</em> its special,
	 * and somebody pressing F with one in hand has asked a fair question.
	 */
	public boolean special(ServerLevel level, Player player, ItemStack gun) {
		return special(level, player, gun, SpecialChoice.of(level.getServer()).orDefault(player));
	}

	/**
	 * The same, told which special to throw. Split out so the pick is one lookup in one place and
	 * everything below it is about the throw — and so a test can name the special instead of arranging
	 * for a player to have picked it.
	 */
	public boolean special(ServerLevel level, Player player, ItemStack gun, Special chosen) {
		Optional<PaintColor> ready = ready(level, player, gun); // team, refill, squid
		if (ready.isEmpty()) return false;
		PaintColor color = ready.get();
		if (weapon == Weapon.CHARGER) {
			actionBar(player, Component.literal("The charger carries no bomb — hold right click, let go to fire")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		SpecialTuning tuning = SpecialTuning.get(chosen);
		long now = level.getServer().getTickCount();
		int wait = tuning.intValue(SpecialTuning.Param.COOLDOWN);
		Long readyAt = SPECIAL_READY.get(player.getUUID());
		if (readyAt != null && now < readyAt && readyAt - now <= wait) {
			// Named, because the wait is the special's own: a player who has picked the burst bomb should
			// be told about a burst bomb, and what it costs them to have picked it is what they read here.
			actionBar(player, Component.literal(chosen.displayName + " in " + ((readyAt - now + 19) / 20) + "s")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		int cost = tuning.intValue(SpecialTuning.Param.INK);
		if (Ink.get(gun) < cost) {
			outOfInk(level, player, gun);
			return false;
		}
		throwSpecial(level, player, color, chosen, tuning);
		// The special's own wait, not the weapon's: most of a tank out at once is not a shooter's shot,
		// and Splatcraft gives every sub an ink_recovery_cooldown of its own for exactly that reason.
		spend(level, gun, cost, tuning.intValue(SpecialTuning.Param.REFILL_DELAY));
		SPECIAL_READY.put(player.getUUID(), now + wait);
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
		return true;
	}

	/** Ticks until this player's splat bomb is ready, or 0 when it is. What the tests and the hint read. */
	public static long specialWait(Player player, long now) {
		Long readyAt = SPECIAL_READY.get(player.getUUID());
		if (readyAt == null || now >= readyAt) return 0;
		return readyAt - now;
	}

	/**
	 * Throw the bomb: one blob carrying the special's blast, its splat radius and its own flight, and no
	 * bounce — what a bomb does when it meets something is its {@link Special.Mode}'s business, not a
	 * bounce's. The three differ only in these numbers and in that mode: a slow high lob that lands and
	 * waits, a fast flat one that bursts on contact, or a low one thrown to reach the floor and slide.
	 */
	private void throwSpecial(ServerLevel level, Player player, PaintColor color, Special chosen, SpecialTuning tuning) {
		PaintBall bomb = new PaintBall(level, player, color, 0, tuning.intValue(SpecialTuning.Param.LIFETIME));
		bomb.setWeapon(weapon);
		bomb.setSpecial(chosen);
		bomb.setSplatRadius(tuning.intValue(SpecialTuning.Param.RADIUS));
		bomb.setDamage(tuning.floatValue(SpecialTuning.Param.DAMAGE));
		bomb.setGravity(tuning.value(SpecialTuning.Param.GRAVITY));
		bomb.setBlast(tuning.value(SpecialTuning.Param.BLAST), tuning.floatValue(SpecialTuning.Param.EDGE_DAMAGE),
				tuning.value(SpecialTuning.Param.CORE));
		bomb.setBlobScale(tuning.floatValue(SpecialTuning.Param.SCALE));
		bomb.shootFromRotation(player, player.getXRot() + tuning.floatValue(SpecialTuning.Param.PITCH), player.getYRot(),
				0.0f, tuning.floatValue(SpecialTuning.Param.VELOCITY), 0.0f);
		level.addFreshEntity(bomb);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
				SoundSource.PLAYERS, 0.9f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE,
				SoundSource.PLAYERS, 0.8f, 0.6f);
	}

	/**
	 * How charged the scoped charger in this player's hands is, from 0 to 1, or −1 when they are not
	 * scoped with one at all. Read-only — the scope is let go by whoever fires — and shared with
	 * {@link InkHud}, which is where the number is actually shown to the player.
	 */
	public static float chargeOf(Player player) {
		ItemStack using = player.getUseItem();
		if (!player.isUsingItem() || !(using.getItem() instanceof PaintWeapon gun) || gun.weapon != Weapon.CHARGER) {
			return -1.0f;
		}
		int held = gun.getUseDuration(using, player) - player.getUseItemRemainingTicks();
		// A full charge in no ticks at all would divide by zero; one tick is the shortest charge there is.
		return Math.min(1.0f, held / (float) Math.max(1, WeaponTuning.get(Weapon.CHARGER).intValue(Param.CHARGE_FULL)));
	}

	/**
	 * What a charger shot at this charge is worth, which is Splatoon's curve and has a step in it: a
	 * partial charge runs {@code charge_damage_min} up to {@code charge_damage_partial} in proportion to
	 * how long it was held, and a <em>full</em> one jumps to {@code charge_damage_full}, which is more
	 * than a player has.
	 *
	 * <p>The step is the weapon. A charger held to the top splats and one let go a moment early does not,
	 * and that is the whole of what makes charging a decision — a straight line from 8 to 32 would make
	 * every fraction of a charge worth its fraction of a kill, which is a different and much duller gun.
	 */
	public static float chargeDamage(WeaponTuning tuning, float charge) {
		if (charge >= 1.0f) return tuning.floatValue(Param.CHARGE_DAMAGE_FULL);
		float min = tuning.floatValue(Param.CHARGE_DAMAGE_MIN);
		return min + (tuning.floatValue(Param.CHARGE_DAMAGE_PARTIAL) - min) * Math.max(0.0f, charge);
	}

	/** What a charger shot at this charge costs: {@code charge_ink_min} to {@code charge_ink_full}. */
	private static int chargeCost(WeaponTuning tuning, float charge) {
		double inkMin = tuning.value(Param.CHARGE_INK_MIN);
		return (int) Math.round(inkMin + (tuning.value(Param.CHARGE_INK_FULL) - inkMin) * charge);
	}

	/**
	 * Does holding this weapon's right click keep it working? A vanilla client repeats a held right click
	 * only every four ticks, which is a ceiling of five shots a second and simply not a fire rate a
	 * shooter can have; and the roller's roll is not a click at all. Both therefore become <em>held-use</em>
	 * items: the press starts using, and {@link #onUseTick} does the work every tick until the button is
	 * let go. The slosher stays one slosh per click, because its own cadence is twelve ticks and the
	 * four-tick repeat fits inside it; the charger is held for its charge and fires on the left click.
	 *
	 * <p>Held on the server is only half of it. A vanilla client decides for itself whether it is using
	 * an item, and it is the side that sends the release — so the client stack carries a
	 * {@link #HELD_USE} consumable that makes it agree.
	 */
	public boolean isHeld() {
		return weapon == Weapon.SHOOTER || weapon == Weapon.ROLLER || weapon == Weapon.CHARGER;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
		ItemStack gun = player.getItemInHand(hand);
		Optional<PaintColor> ready = ready(serverLevel, player, gun); // team, refill, squid
		if (ready.isEmpty()) return InteractionResult.FAIL;
		PaintColor color = ready.get();
		WeaponTuning tuning = WeaponTuning.get(weapon);
		// A tank that cannot cover the shot is as good as empty: one ink must not buy a seven-ink slosh.
		// The roller is exempt: it may be picked up on an empty tank to roll, which costs almost nothing,
		// and only the flick has to be paid for.
		if (weapon != Weapon.ROLLER && Ink.get(gun) < tuning.intValue(Param.INK)) {
			outOfInk(serverLevel, player, gun);
			return InteractionResult.FAIL;
		}
		if (isHeld()) {
			// The charger spends nothing on the press: right click is the scope, and its shot, ink and
			// cooldown all wait for the left click that fires it. The shooter fires at once and then keeps
			// firing from the use tick — waiting for the first tick would put a frame of nothing between
			// the click and the shot — and the roller's first roll needs a position to measure from, so it
			// starts on the next tick by construction.
			player.startUsingItem(hand);
			if (weapon == Weapon.SHOOTER) fireIfReady(serverLevel, player, gun, color);
			return InteractionResult.CONSUME;
		}
		// The cadence is the cooldown for the clicked weapons too. A real client refuses to send a use
		// packet for an item on cooldown, so this only ever fires on one that is not a real client — but
		// the fire rate is a rule of the game, not a courtesy of the client, and the held weapons have
		// been checking it since fireIfReady.
		if (player.getCooldowns().isOnCooldown(gun)) return InteractionResult.FAIL;
		fire(serverLevel, player, color);
		feel(serverLevel, player, color);
		spend(serverLevel, gun, tuning.intValue(Param.INK));
		player.getCooldowns().addCooldown(gun, tuning.intValue(Param.COOLDOWN));
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
		return weapon == Weapon.SLOSHER ? InteractionResult.SUCCESS_SERVER : InteractionResult.CONSUME;
	}

	/**
	 * A tick of holding the button down, on the server. 26.3 calls this every tick a living entity is
	 * using an item, with the ticks <em>left</em> of {@link #getUseDuration}; ours is vanilla's cap for
	 * "as long as you like", so the number counts down from 72000 and what matters is only that this ran.
	 *
	 * <p>The shooter fires whenever its cooldown is up, which is what makes {@code cooldown} the fire rate
	 * rather than a floor under the client's four-tick repeat. The roller rolls. The charger does nothing
	 * here: its holding is the charge, and {@link InkHud} is what shows it.
	 */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) return;
		if (weapon != Weapon.SHOOTER && weapon != Weapon.ROLLER) return;
		Optional<PaintColor> ready = ready(serverLevel, player, stack); // team, refill, squid
		if (ready.isEmpty()) {
			// Squid form, no team or a refill in progress: stop rolling rather than roll invisibly.
			if (weapon == Weapon.ROLLER) Roll.stop(player);
			return;
		}
		if (weapon == Weapon.ROLLER) {
			Roll.tick(serverLevel, player, stack, ready.get());
		} else {
			fireIfReady(serverLevel, player, stack, ready.get());
		}
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
	}

	/**
	 * One shot, if the weapon's own cooldown has run out and the tank can cover it. The item cooldown is
	 * the rate limiter and the thing the player can see; an empty tank starts the refill, exactly as a
	 * click on an empty tank does. Returns whether a shot left the barrel.
	 */
	private boolean fireIfReady(ServerLevel level, Player player, ItemStack gun, PaintColor color) {
		if (player.getCooldowns().isOnCooldown(gun)) return false;
		WeaponTuning tuning = WeaponTuning.get(weapon);
		int cost = tuning.intValue(Param.INK);
		if (Ink.get(gun) < cost) {
			outOfInk(level, player, gun);
			return false;
		}
		fire(level, player, color);
		feel(level, player, color);
		spend(level, gun, cost);
		player.getCooldowns().addCooldown(gun, tuning.intValue(Param.COOLDOWN));
		return true;
	}

	/**
	 * Throw this weapon's paint from the shooter's eyes along their view. Three of the four weapons are
	 * the same shot with different numbers — {@code count} balls, spread {@code fan_yaw} degrees apart
	 * around the view and pitched by {@code fan_pitch}, each carrying the weapon's gravity, bounces,
	 * lifetime, splat radius and damage — so there is one loop rather than an arm apiece: a shooter's
	 * single flat ball is that fan with one ball in it, a sprayer's cone is three of them at nine
	 * degrees of inaccuracy, a slosher's four at ten degrees of deliberate yaw. Every number is read
	 * from {@link WeaponTuning} here, at the shot, so {@code /rivals tune} lands on the next click.
	 */
	public void fire(ServerLevel level, Player player, PaintColor color) {
		if (weapon == Weapon.CHARGER) return; // the charger throws no ball; see chargerShot
		WeaponTuning tuning = WeaponTuning.get(weapon);
		int count = tuning.intValue(Param.COUNT);
		float fanYaw = tuning.floatValue(Param.FAN_YAW);
		float pitch = player.getXRot() + tuning.floatValue(Param.FAN_PITCH);
		// Splatoon doubles a shooter's spread for a player in the air; ours is one parameter rather than a
		// multiplier, so a weapon that should not care can say so by leaving the two equal.
		float spread = tuning.floatValue(player.onGround() ? Param.SPREAD : Param.SPREAD_AIR);
		for (int i = 0; i < count; i++) {
			// Centred on the view: an odd count puts one ball down the crosshair, an even one straddles it.
			float offset = (i - (count - 1) / 2.0f) * fanYaw;
			PaintBall ball = new PaintBall(level, player, color,
					tuning.intValue(Param.BOUNCES), tuning.intValue(Param.LIFETIME));
			ball.setWeapon(weapon);
			ball.setSplatRadius(tuning.intValue(Param.SPLAT_RADIUS));
			ball.setDamage(tuning.floatValue(Param.DAMAGE));
			ball.setDecay(tuning.intValue(Param.DECAY_START), tuning.floatValue(Param.DECAY_PER_TICK),
					tuning.floatValue(Param.DECAYED_DAMAGE));
			ball.setGravity(tuning.value(Param.GRAVITY));
			ball.setFlight(tuning.value(Param.STRAIGHT_BLOCKS), tuning.value(Param.DECAYED_SPEED));
			ball.shootFromRotation(player, pitch, player.getYRot() + offset, 0.0f,
					tuning.floatValue(Param.VELOCITY), spread);
			level.addFreshEntity(ball);
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		// Vanilla's cap for "as long as you like": every held weapon keeps going until the button is let
		// go, and what each of them does with the time is onUseTick's business.
		return isHeld() ? Weapon.CHARGE_MAX_TICKS : 0;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		// The client item is a spyglass, so the spyglass animation is also the scope: holding zooms. NONE
		// for the other two: the standing rule is no arm swing, and every other animation in the enum
		// moves the hand somewhere the weapon should not be — and would take the LED with it, which is
		// solved for the plain first-person transform.
		return weapon == Weapon.CHARGER ? ItemUseAnimation.SPYGLASS : ItemUseAnimation.NONE;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (!(entity instanceof Player player)) return false;
		int held = getUseDuration(stack, entity) - timeLeft;
		if (weapon == Weapon.ROLLER) {
			// Letting go of the roller is the end of the roll and nothing else. It used to be the flick as
			// well — a release under flick_tap ticks was read as a tap — and that is now the left button,
			// which is a button of its own rather than a stopwatch on this one.
			Roll.stop(player);
			return false;
		}
		// Letting go of the scope is the shot. It was the left click for a while — aim and fire as two
		// buttons — but a vanilla client will not attack while it is using an item, and the scope is a
		// spyglass in use, so a scoped charger had no left click at all and could never fire. The charge
		// is what the hold built, read off the ticks here rather than off chargeOf, which asks the
		// player whether they are using the item and gets a different answer depending on which side of
		// stopUsingItem this is called from.
		if (weapon != Weapon.CHARGER || !(level instanceof ServerLevel serverLevel)) return false;
		WeaponTuning tuning = WeaponTuning.get(weapon);
		float charge = Math.min(1.0f, held / (float) Math.max(1, tuning.intValue(Param.CHARGE_FULL)));
		Optional<PaintColor> ready = ready(serverLevel, player, stack); // team, refill, squid
		if (ready.isEmpty()) return false;
		if (Ink.get(stack) < chargeCost(tuning, charge)) {
			outOfInk(serverLevel, player, stack);
			return false;
		}
		chargerShot(serverLevel, player, stack, charge, ready.get());
		return false;
	}

	/**
	 * The roller's tap: a bucketful thrown in an arc that lands a few blocks ahead, for the weapon's own
	 * ink and its own recovery. Returns whether it left the head, which is what the tests read.
	 */
	public boolean flick(ServerLevel level, Player player, ItemStack gun) {
		if (weapon != Weapon.ROLLER) return false;
		Optional<PaintColor> ready = ready(level, player, gun);
		if (ready.isEmpty()) return false;
		WeaponTuning tuning = WeaponTuning.get(weapon);
		int cost = tuning.intValue(Param.INK);
		if (Ink.get(gun) < cost) {
			outOfInk(level, player, gun);
			return false;
		}
		if (player.getCooldowns().isOnCooldown(gun)) return false;
		fire(level, player, ready.get());
		feel(level, player, ready.get());
		spend(level, gun, cost);
		player.getCooldowns().addCooldown(gun, tuning.intValue(Param.COOLDOWN));
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
		return true;
	}

	/**
	 * The charger's shot, at {@code charge} from 0 (a snap shot) to 1 (a full one). The charge is the
	 * whole weapon: it scales range, ink, damage and kick together, each of them from its {@code *_min}
	 * at no charge to its {@code *_full} at a full one. The shot itself is hitscan: one clip along the
	 * view, a line of paint on the floor under it, and a splash where it stops — under the feet of
	 * whoever was standing in the way, if anyone was, and otherwise on the block face it ran into.
	 * Whoever stopped it also takes {@link #chargeDamage}, unless they are on the shooter's own team.
	 * Every one of those numbers is read off {@link WeaponTuning} here, at the shot, so
	 * {@code /rivals tune} lands on the next one.
	 */
	public boolean chargerShot(ServerLevel serverLevel, Player player, ItemStack stack, float charge) {
		if (weapon != Weapon.CHARGER) return false;
		Optional<PaintColor> ready = ready(serverLevel, player, stack);
		return ready.isPresent() && chargerShot(serverLevel, player, stack, charge, ready.get());
	}

	/** The shot itself, for a caller that has already asked {@link #ready} what colour it is firing. */
	private boolean chargerShot(ServerLevel serverLevel, Player player, ItemStack stack, float charge, PaintColor color) {
		WeaponTuning tuning = WeaponTuning.get(weapon);
		int cost = chargeCost(tuning, charge);
		if (Ink.get(stack) < cost) {
			outOfInk(serverLevel, player, stack);
			return false;
		}
		double rangeMin = tuning.value(Param.RANGE_MIN);
		double range = rangeMin + (tuning.value(Param.RANGE_FULL) - rangeMin) * charge;
		Vec3 from = player.getEyePosition();
		Vec3 reach = player.getLookAngle().scale(range);
		Vec3 to = from.add(reach);
		BlockHitResult hit = serverLevel.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean struck = hit.getType() == HitResult.Type.BLOCK;
		Vec3 end = struck ? hit.getLocation() : to;
		// Anyone standing in the way stops the line where they are: scanned only as far as the block hit,
		// so a hit that comes back is by construction the nearer of the two. Paint goes under their feet,
		// the same shape a ball's entity hit makes — a charger line that passed through a player and
		// painted the wall behind them read as a miss.
		AABB along = player.getBoundingBox().expandTowards(reach).inflate(1.0);
		EntityHitResult inTheWay = ProjectileUtil.getEntityHitResult(serverLevel, player, from, end, along,
				// isPickable, so a dropped item, an XP orb or someone else's paint ball in flight does not
				// stop the line: those are not what a charger shot is aimed at.
				candidate -> candidate != player && candidate.isAlive() && candidate.isPickable() && !candidate.isSpectator(), 0.0f);
		if (inTheWay != null) end = inTheWay.getLocation();
		int painted = Painter.line(serverLevel, from, end, color, player);
		if (inTheWay != null) {
			BlockPos below = inTheWay.getEntity().blockPosition().below();
			painted += Painter.splash(serverLevel, end, below, Direction.UP, color, serverLevel.getRandom(), player);
			// The one weapon whose damage rides the charge: a full-charge line is the hardest hit in the
			// game, a barely-held one is a poke. Attributed to the player, so a kill goes on their name.
			if (PaintBall.hostile(color, inTheWay.getEntity())) {
				float hurt = chargeDamage(tuning, charge);
				if (PaintDamage.hurt(serverLevel, inTheWay.getEntity(),
						serverLevel.damageSources().indirectMagic(player, player), hurt)) {
					InkOnScreen.hit(inTheWay.getEntity(), color, hurt);
				}
			}
		} else if (struck) {
			painted += Painter.splash(serverLevel, end, hit.getBlockPos(), hit.getDirection(), color, serverLevel.getRandom(), player);
		}
		Rivals.LOGGER.debug("charger: charge {}, range {}, {} cells painted", charge, range, painted);
		spend(serverLevel, stack, cost);
		player.getCooldowns().addCooldown(stack, tuning.intValue(Param.COOLDOWN));
		// A half charge should not buck like a full one, so the kick rides the charge.
		Recoil.kick(player, tuning.floatValue(Param.KICK) * charge);
		muzzle(serverLevel, player, color);
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
		return true;
	}

	/**
	 * The checks every shot shares: a team to paint for, a tank that is not mid-refill, and hands rather
	 * than fins. Says why when it refuses, and hands back the colour it found: a shot that is allowed is
	 * exactly a shot that has one, so looking the team up again afterwards was a second call that could
	 * only have failed if this one had.
	 */
	private Optional<PaintColor> ready(ServerLevel level, Player player, ItemStack gun) {
		Optional<PaintColor> color = PaintColor.byTeam(player.getTeam());
		if (color.isEmpty()) {
			actionBar(player, Component.literal("Join a team first: /team join " + PaintColor.values()[0].id)
					.withStyle(ChatFormatting.RED));
			return Optional.empty();
		}
		long now = level.getServer().getTickCount();
		Ink.finishIfDue(gun, now);
		if (Ink.isRefilling(gun, now)) return Optional.empty();
		if (isSquid(player)) {
			actionBar(player, Component.literal("Can't shoot in squid form").withStyle(ChatFormatting.RED));
			return Optional.empty();
		}
		return color;
	}

	/**
	 * Pay for a shot: the ink, and the stamp that holds the own-paint top-up off for the weapon's
	 * {@code refill_delay}. Every path that fires goes through here, so there is one place the two are
	 * kept together and no way to spend ink without starting the wait.
	 */
	private static void spend(ServerLevel level, ItemStack gun, int cost, int refillDelay) {
		Ink.add(gun, -cost);
		Ink.noteShot(gun, level.getServer().getTickCount(), refillDelay);
	}

	/** The same, for a shot that waits this weapon's own {@code refill_delay}, which is most of them. */
	private void spend(ServerLevel level, ItemStack gun, int cost) {
		spend(level, gun, cost, WeaponTuning.get(weapon).intValue(Param.REFILL_DELAY));
	}

	/** An empty tank: start the refill, and hold the gun on cooldown until it is done. */
	static void outOfInk(ServerLevel level, Player player, ItemStack gun) {
		Ink.startRefill(gun, level.getServer().getTickCount());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 0.9f);
		player.getCooldowns().addCooldown(gun, Ink.REFILL_TICKS);
		if (player instanceof ServerPlayer serverPlayer) InkHud.show(serverPlayer);
	}

	static void actionBar(Player player, Component text) {
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) serverPlayer.sendSystemMessage(text, true);
	}

	/** Is this player holding a roller's button down right now? The roll runs for exactly as long. */
	public static boolean isRolling(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof PaintWeapon gun
				&& gun.weapon == Weapon.ROLLER;
	}

	/** Squid form (sneaking on own paint) can't shoot. */
	public static boolean isSquid(Player player) {
		return PlayerTick.isSquid(player);
	}

	/** The chunk of a shot: camera kick, a nudge back, a muzzle burst in the team colour, layered sounds. */
	void feel(ServerLevel level, Player shooter, PaintColor color) {
		Recoil.kick(shooter, WeaponTuning.get(weapon).floatValue(Param.KICK));
		muzzle(level, shooter, color);
	}

	/** The burst everyone but the shooter sees, at eye + look × this. */
	private static final double MUZZLE_REACH = 0.9;
	/** The shooter's own, smaller burst: at the barrel tip, off to the right of the view and below it. */
	private static final double BARREL_REACH = 1.4;
	private static final double BARREL_RIGHT = 0.3;
	private static final double BARREL_DROP = 0.25;

	/** Everything about a shot but the camera kick: the nudge back, the burst of colour, the layered sounds. */
	void muzzle(ServerLevel level, Player shooter, PaintColor color) {
		Vec3 look = shooter.getLookAngle();
		shooter.push(-look.x * 0.06, 0, -look.z * 0.06);
		shooter.syncVelocity = true;
		// The full burst is for everyone else. Ink crumbs at the shooter's own eyes hang in front of their
		// camera for the whole of a held trigger and clog the first-person view, so the shooter gets a
		// couple at the barrel tip instead — off the centre of the screen, where a muzzle is.
		Vec3 muzzle = shooter.getEyePosition().add(look.scale(MUZZLE_REACH));
		BlockParticleOption dust = Painter.crumbs(color);
		List<ServerPlayer> others = level.players().stream().filter(viewer -> viewer != shooter).toList();
		// Painter.burst keeps the burst off the eyes of whoever the shooter is standing on top of, and holds
		// vanilla's own thirty-two-block cut-off for an unforced particle packet.
		Painter.burst(level, others, dust, muzzle, 5, 0.1, 0.1, 0.1, 0.02);
		if (shooter instanceof ServerPlayer self) {
			Vec3 across = look.cross(UP);
			Vec3 right = across.lengthSqr() < 1.0e-6 ? Vec3.ZERO : across.normalize(); // straight up or down: no side
			Vec3 barrel = shooter.getEyePosition()
					.add(look.scale(BARREL_REACH))
					.add(right.scale(BARREL_RIGHT))
					.subtract(UP.scale(BARREL_DROP));
			Painter.burst(level, List.of(self), dust, barrel, 2, 0.05, 0.05, 0.05, 0.0);
		}
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7f, 0.7f);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
		// A bucketful wants weight under the snowball throw; a low slime step is that weight.
		if (weapon == Weapon.SLOSHER) {
			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 0.9f, 0.6f);
		}
	}

	/**
	 * The tank rule for a server-side weapon stack, in one place: dyed with the team's paint colour (the
	 * model's tank reads that dye), undyed for no team or a team that is none of ours. Polymer copies
	 * the dye onto the client stack, so every viewer sees the holder's colour.
	 */
	public static ItemStack withTankColor(ItemStack stack, @Nullable PlayerTeam team) {
		Optional<PaintColor> color = PaintColor.byTeam(team);
		if (color.isPresent()) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.get().rgb));
		} else {
			stack.remove(DataComponents.DYED_COLOR);
		}
		return stack;
	}

	/**
	 * Keep the tank dye in step with the holder's team. Compared before writing, because setting a
	 * component re-syncs the stack to everyone who can see it and this runs every tick.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		Ink.finishIfDue(stack, level.getServer().getTickCount());
		if (!(entity instanceof LivingEntity holder)) return; // a dropped weapon keeps the dye it had
		// The ink-on-screen meter rides the weapon's data LED, and this is the one place it is written, so a
		// weapon stowed with a full screen cannot come back out still carrying a live number. The holder is
		// noted with it: only their own client is ever told the value.
		if (holder instanceof Player carrier) {
			InkOnScreen.put(stack, InkOnScreen.ledFor(carrier));
			InkOnScreen.owner(stack, carrier.getUUID());
			// releaseUsing ends a roll, but not every way of stopping goes through it — dying with the
			// button down, or having the weapon taken out of the hand. The roll's speed bonus is a
			// transient modifier and must not outlive the holding, so the tick that keeps the dye honest
			// keeps this honest too. Asked per player rather than per stack, so a second roller in the
			// backpack does not stop the one in the hand.
			if (weapon == Weapon.ROLLER && !isRolling(carrier)) Roll.stop(carrier);
		}
		PlayerTeam team = holder.getTeam();
		DyedItemColor wanted = PaintColor.byTeam(team).map(color -> new DyedItemColor(color.rgb)).orElse(null);
		if (Objects.equals(stack.get(DataComponents.DYED_COLOR), wanted)) return;
		withTankColor(stack, team);
	}

	/**
	 * The LED value this stack may show to {@code viewer}: the real one for the player whose meter it is,
	 * {@link InkOnScreen#IDLE} for everybody else. A lit LED on someone else's gun would be a tell — and
	 * worse, the ink post effect hunts the whole lower half of the frame for that signature, so another
	 * player's third-person weapon walking past would splatter the finder's own screen.
	 */
	public static int ledForViewer(ItemStack stack, @Nullable UUID viewer) {
		UUID owner = InkOnScreen.ownerOf(stack);
		return viewer != null && viewer.equals(owner) ? InkOnScreen.ledOf(stack) : InkOnScreen.IDLE;
	}

	/**
	 * The client's copy of a weapon: Polymer's own (the mapped item, the dye, the model) with the data LED
	 * masked to whoever is being sent it. {@link PacketContext#GAME_PROFILE} is the receiving player.
	 *
	 * <p>The masking happens on a copy of the <em>server</em> stack, before Polymer builds anything.
	 * Polymer's {@code createItemStack} embeds the stack it was handed under {@code $polymer:stack} in the
	 * client stack's custom data so it can map the item back; masking the result afterwards left the real
	 * value, and the LED's owner UUID with it, inside that copy, on every other player's client. Masking
	 * first means the number on the wire is the number that viewer is allowed to see, whichever field of
	 * the packet they read it out of.
	 */
	@Override
	public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context, HolderLookup.Provider lookup) {
		// No context at all is "nobody in particular is being sent this" — a test, or a stack built off a
		// packet — and the safe reading of that is the same as a stranger's: the idle LED.
		GameProfile viewer = context == null ? null : context.get(PacketContext.GAME_PROFILE);
		ItemStack masked = stack.copy();
		InkOnScreen.put(masked, ledForViewer(stack, viewer == null ? null : viewer.id()));
		ItemStack client = PolymerItem.super.getPolymerItemStack(masked, flag, context, lookup);
		// The two held weapons that are not the charger have to be held by the CLIENT as well as by the
		// server: see HELD_USE. Both components go on the stack Polymer built rather than on the one it
		// was handed, because what reaches the client is a rebuilt stack carrying the components Polymer
		// chooses to copy, and neither of these is one a server item is expected to need on the wire.
		if (isHeld() && weapon != Weapon.CHARGER) {
			client.set(DataComponents.CONSUMABLE, HELD_USE);
			client.set(DataComponents.USE_EFFECTS, weapon == Weapon.SHOOTER ? SHOOTER_USE : ROLLER_USE);
		}
		return client;
	}

	/**
	 * The vanilla item the client is handed. The charger is a spyglass, for the scope: its client-side
	 * {@code use} starts using the item by itself and the zoom is the aim.
	 *
	 * <p>Everything else is a plain stick, and plain is the point. It was
	 * {@code warped_fungus_on_a_stick}, whose {@code FoodOnAStickItem.use} returns PASS on the client
	 * before looking at a single component — so the client never held the button, and the roller's
	 * release packet, which is the only thing that can tell a tap from a roll, was never sent. A stick
	 * is a bare {@link Item}, so the base {@code Item.use} runs and starts using on the CONSUMABLE
	 * component this class puts on the client stack. What the item is underneath is invisible: the
	 * client draws {@link #getPolymerItemModel}'s model, not the stick's.
	 */
	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return weapon == Weapon.CHARGER ? Items.SPYGLASS : Items.STICK;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return Rivals.id(weapon.id);
	}

	@Override
	public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
		tooltip.add(Component.literal(switch (weapon) {
			case SHOOTER -> "Hold right click to fire, F for the bomb";
			case CHARGER -> "Hold right click to aim, left click to fire the line";
			case SLOSHER -> "Right click to slosh, F for the bomb";
			case ROLLER -> "Hold right click to roll, left click to flick, F for the bomb";
		}).withStyle(ChatFormatting.GRAY));
	}
}
