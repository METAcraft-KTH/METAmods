package se.metacraft.kultur.drinks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What drinking does: the alcohol goes on the meter, then the drink's own effects, or, if that took
 * the meter to {@value Alcohol#BLACKOUT_AT}, the blackout instead (as in Kultur's Brewery drinks, where
 * the drink reads the level after it is added). Bread and milk take a little off, as Brewery's
 * {@code alcohol_reduction_items}. Only drinking counts: a splash or lingering bottle never gets here.
 */
public final class DrinkEffects {
	/** Brewery's {@code 60 + 14 × quality} seconds at quality 10. */
	static final int OWN_EFFECT_TICKS = 200 * 20;
	static final int BLINDNESS_TICKS = 10 * 20;
	static final float BLACKOUT_DAMAGE = 4;
	/** Brewery's {@code teleport_random} with distance 500: up to 250 blocks each way. */
	static final int TELEPORT_REACH = 250;
	static final int TELEPORT_TRIES = 16;
	public static final String HEADACHE = "You wake up with a splitting headache...";
	public static final int HEADACHE_COLOUR = 0xFF0F0F;
	static final double BREAD = 3;
	static final double MILK = 10;

	private DrinkEffects() {}

	/** Called when a player finishes consuming a stack, before the stack's own effects (see {@code ConsumableMixin}). */
	public static void consumed(ServerPlayer player, ItemStack stack) {
		if (!KulturDrinks.registered()) return;
		DrinkKind kind = stack.is(Items.POTION) || stack.is(KulturDrinks.NYCKELN) ? DrinkPotions.kindOf(stack) : null;
		if (kind != null) {
			onDrink(player, kind);
		} else if (stack.is(Items.BREAD)) {
			sober(player, BREAD);
		} else if (stack.is(Items.MILK_BUCKET)) {
			sober(player, MILK);
		}
	}

	public static void onDrink(ServerPlayer player, DrinkKind kind) {
		if (kind == DrinkKind.NYCKELN) return;   // the sober one
		Drunkenness.drink(player, kind.alcohol());
		double alcohol = Drunkenness.get(player);
		if (Alcohol.blacksOut(alcohol)) {
			blackout(player);
		} else {
			ownEffects(player, kind, alcohol);
		}
		DrunkTicker.refreshInebriated(player);
	}

	/**
	 * Alcoholic Beverage and Spiken send you fast or slow, even odds. Släggan sends you slow three times
	 * in four, and strong with it once you are drunk (60); otherwise fast, and weak once you are drunk.
	 */
	private static void ownEffects(ServerPlayer player, DrinkKind kind, double alcohol) {
		RandomSource random = player.getRandom();
		int amplifier = Alcohol.drinkAmplifier(alcohol);
		boolean drunk = alcohol >= 60;
		if (kind == DrinkKind.SLAGGAN) {
			if (random.nextFloat() < 0.75f) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, OWN_EFFECT_TICKS, amplifier));
				if (drunk) player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, OWN_EFFECT_TICKS, 0));
			} else {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, OWN_EFFECT_TICKS, amplifier));
				if (drunk) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, OWN_EFFECT_TICKS, 0));
			}
		} else {
			player.addEffect(new MobEffectInstance(random.nextBoolean() ? MobEffects.SLOWNESS : MobEffects.SPEED, OWN_EFFECT_TICKS, amplifier));
		}
	}

	/**
	 * PolymITer's fourth drink: you come to somewhere within 250 blocks, with no effects but blindness,
	 * a headache (four points of alcohol poisoning, which can kill) and 30 alcohol. With nowhere to go
	 * you come to where you were and keep your effects: stripping water breathing or fire resistance
	 * from someone who stays at sea or in lava would kill them.
	 */
	private static void blackout(ServerPlayer player) {
		if (teleport(player)) player.removeAllEffects();
		ServerLevel level = player.level();
		player.hurtServer(level, KulturDamageTypes.alcoholPoisoning(level), BLACKOUT_DAMAGE);
		ToClient.send(player, new ClientboundSetActionBarTextPacket(Component.literal(HEADACHE)
				.withStyle(style -> style.withBold(true).withColor(HEADACHE_COLOUR))));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0));
		Drunkenness.set(player, Alcohol.AFTER_BLACKOUT);
	}

	/**
	 * Up to {@value #TELEPORT_TRIES} random spots within {@value #TELEPORT_REACH} blocks in the same
	 * dimension, in chunks already loaded; the first safe one wins. If none is, the player stays where
	 * they are (PolymITer's version tried forever). Returns whether the player moved.
	 */
	static boolean teleport(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		for (int i = 0; i < TELEPORT_TRIES; i++) {
			int x = Mth.floor(player.getX() + (random.nextDouble() - 0.5) * 2 * TELEPORT_REACH);
			int z = Mth.floor(player.getZ() + (random.nextDouble() - 0.5) * 2 * TELEPORT_REACH);
			BlockPos spot = safeSpot(level, x, z);
			if (spot == null) continue;
			if (player.isPassenger()) player.stopRiding();
			Vec3 from = player.position();
			player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
			player.resetFallDistance();
			level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(player));
			return true;
		}
		return false;
	}

	/**
	 * Where a player could stand in this column: two free blocks over a solid top, nothing that burns
	 * or pricks, inside the world border. Open-sky dimensions use the surface (over the End's void there
	 * is none); one with a roof, the Nether, is searched from just under the roof down, so no spot is in
	 * or on top of the roof. Only a column whose chunk is already loaded counts: loading or generating
	 * one would freeze the server, sixteen times over at sea.
	 */
	public static @Nullable BlockPos safeSpot(ServerLevel level, int x, int z) {
		if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) return null;
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
		int bottom = level.getMinY() + 1;
		if (!level.dimensionType().hasCeiling()) {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, y, z);
			return y >= bottom && standable(level, feet) ? feet : null;
		}
		int roof = level.getMinY() + level.getLogicalHeight();
		for (int y = roof - 8; y >= bottom; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			if (standable(level, feet)) return feet;
		}
		return null;
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		BlockState ground = level.getBlockState(below);
		return free(level, feet) && free(level, feet.above())
				&& ground.isFaceSturdy(level, below, Direction.UP)
				&& !ground.is(BlockTags.DANGEROUS_FOR_TELEPORTATION);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()
				&& !state.is(BlockTags.DANGEROUS_FOR_TELEPORTATION);
	}

	/** Bread and milk, Brewery's sobering: only while there is alcohol, and never below zero. */
	private static void sober(ServerPlayer player, double by) {
		double alcohol = Drunkenness.get(player);
		if (alcohol <= 0) return;
		Drunkenness.set(player, Alcohol.sobered(alcohol, by));
		DrunkTicker.refreshInebriated(player);
	}
}
