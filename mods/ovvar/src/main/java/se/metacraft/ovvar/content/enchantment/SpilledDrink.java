package se.metacraft.ovvar.content.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.ModContent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Curse of the Spilled Drink: whatever the wearer drinks, a weaker second dose lands a little later. A
 * duration effect's second dose lands {@code gap_ticks} after the first would have ended (it would only
 * hide under it otherwise); an instant effect's, {@code gap_ticks} after drinking. Pending doses are a
 * persistent attachment, so they survive logout and are lost on death. The cursed ovve also drips now and then.
 * Needs Brewery ({@link OvveEnchantments#enabled}).
 *
 * <p>Punkphöz sells {@link #wandererBook} at {@link #WANDERER_PRICE} among his pool picks
 * ({@link se.metacraft.ovvar.content.entity.PunkphozStock#extras}), only with Brewery.
 */
public final class SpilledDrink {
	private SpilledDrink() {}

	/** What the wandering Punkphöz asks for the curse's book, in emeralds. */
	public static final int WANDERER_PRICE = 16;

	public record Dose(Holder<MobEffect> effect, int amplifier, int duration, float instantScale, long due) {
		public static final Codec<Dose> CODEC = RecordCodecBuilder.create(i -> i.group(
				BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(Dose::effect),
				Codec.INT.fieldOf("amplifier").forGetter(Dose::amplifier),
				Codec.INT.fieldOf("duration").forGetter(Dose::duration),
				Codec.FLOAT.fieldOf("instant_scale").forGetter(Dose::instantScale),
				Codec.LONG.fieldOf("due").forGetter(Dose::due)).apply(i, Dose::new));
	}

	public static final AttachmentType<List<Dose>> DOSES = AttachmentRegistry.create(ModContent.id("spilled_doses"),
			b -> b.persistent(Dose.CODEC.listOf()));

	/** The second doses for these drunk effects, as of game time {@code now}. Infinite effects get none. */
	public static List<Dose> secondDoses(List<MobEffectInstance> drunk, long now, EnchantmentsConfig.SpilledDrink cfg) {
		List<Dose> doses = new ArrayList<>();
		for (MobEffectInstance drank : drunk) {
			Holder<MobEffect> effect = drank.getEffect();
			if (effect.value().isInstantaneous()) {
				doses.add(new Dose(effect, drank.getAmplifier(), 1, cfg.instantScale(), now + cfg.gapTicks()));
			} else if (!drank.isInfiniteDuration()) {
				int d = drank.getDuration();
				doses.add(new Dose(effect, Math.max(0, drank.getAmplifier() - 1), Math.max(1, Math.round(d * cfg.durationScale())), 1f, now + d + cfg.gapTicks()));
			}
		}
		return doses;
	}

	/** Called after a potion is drunk: a cursed wearer gets the second doses scheduled. */
	public static void onDrink(LivingEntity drinker, PotionContents contents, ItemStack stack) {
		if (!(drinker instanceof ServerPlayer) || OvveEnchantments.level(drinker, OvveEnchantments.SPILLED_DRINK_CURSE) <= 0) return;
		List<MobEffectInstance> drunk = new ArrayList<>();
		contents.forEachEffect(drunk::add, stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1f));
		onDrink(drinker, drunk);
	}

	// A Brewery drink has no potion_contents: its drink type's effects go through Brewery's own
	// ConsumptionEffect$Potion. BreweryDrinkMixin collects them while a drink is being finished.
	private static final ThreadLocal<List<MobEffectInstance>> BREWERY_DRINK = new ThreadLocal<>();

	/** BreweryDrinkMixin, as Brewery's {@code DrinkItem.finishUsingItem} starts: collect the effects it gives. */
	public static void breweryDrinkStarts() {
		BREWERY_DRINK.set(new ArrayList<>());
	}

	/** BreweryDrinkMixin, as one of the drink's potion effects is given (outside a drink: nothing). */
	public static void breweryEffect(MobEffectInstance effect) {
		List<MobEffectInstance> drunk = BREWERY_DRINK.get();
		if (drunk != null) drunk.add(new MobEffectInstance(effect));
	}

	/** BreweryDrinkMixin, as the drink is finished: the effects it gave, as from a potion. */
	public static void breweryDrinkEnds(LivingEntity drinker) {
		List<MobEffectInstance> drunk = BREWERY_DRINK.get();
		BREWERY_DRINK.remove();
		if (drunk != null && !drunk.isEmpty()) onDrink(drinker, drunk);
	}

	/** A cursed wearer who just drank these effects gets their second doses scheduled. */
	public static void onDrink(LivingEntity drinker, List<MobEffectInstance> drunk) {
		if (!(drinker instanceof ServerPlayer player) || OvveEnchantments.level(drinker, OvveEnchantments.SPILLED_DRINK_CURSE) <= 0) return;
		long now = player.level().getServer().overworld().getGameTime();
		List<Dose> doses = secondDoses(drunk, now, OvvarConfig.get().enchantments().spilledDrink());
		if (doses.isEmpty()) return;
		List<Dose> all = new ArrayList<>(player.getAttachedOrElse(DOSES, List.of()));
		all.addAll(doses);
		player.setAttached(DOSES, all);
	}

	/**
	 * Lands every due dose, curse or no curse (a dose already scheduled still comes). A duration dose goes
	 * through {@code addEffect}, which never weakens or shortens a running stronger instance: a weaker or
	 * shorter one is ignored, a weaker but longer one is kept hidden under it.
	 */
	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<Dose> pending = player.getAttached(DOSES);
			if (pending == null || pending.isEmpty() || !player.isAlive()) continue;
			List<Dose> left = new ArrayList<>(pending);
			List<Dose> due = new ArrayList<>();
			for (Iterator<Dose> it = left.iterator(); it.hasNext();) {
				Dose dose = it.next();
				if (dose.due() <= now) {
					due.add(dose);
					it.remove();
				}
			}
			if (due.isEmpty()) continue;
			if (left.isEmpty()) player.removeAttached(DOSES);
			else player.setAttached(DOSES, left);
			ServerLevel level = player.level();
			for (Dose dose : due) {
				if (dose.effect().value().isInstantaneous()) {
					dose.effect().value().applyInstantaneousEffect(level, null, player, player, dose.amplifier(), dose.instantScale());
				} else {
					player.addEffect(new MobEffectInstance(dose.effect(), dose.duration(), dose.amplifier()));
				}
			}
		}
	}

	public static boolean nuisance(RandomSource random, int intervalTicks) {
		return random.nextInt(intervalTicks) == 0;
	}

	/** Every tick the cursed ovve is worn: now and then a drip and a squelch, heard by everyone near. */
	public static void tickWorn(ServerPlayer player, ItemStack garment) {
		if (OvveEnchantments.level(garment, OvveEnchantments.SPILLED_DRINK_CURSE) <= 0) return;
		RandomSource random = player.getRandom();
		if (!nuisance(random, OvvarConfig.get().enchantments().spilledDrink().dripIntervalTicks())) return;
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.DRIPPING_WATER, player.getX(), player.getY() + 0.6, player.getZ(), 6, 0.3, 0.25, 0.3, 0);
		level.playSound(null, player, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.4f, 1.3f + 0.3f * random.nextFloat());
	}

	/**
	 * The curse's book for the wandering trader, or empty when the curse is not offered (no Brewery, or
	 * switched off) or not in the registry. Silent: no log, no throw.
	 */
	public static Optional<ItemStack> wandererBook(HolderLookup.Provider registries, EnchantmentsConfig cfg, boolean brewery) {
		Optional<Holder.Reference<Enchantment>> holder = registries.lookup(Registries.ENCHANTMENT)
				.flatMap(lookup -> lookup.get(OvveEnchantments.SPILLED_DRINK_CURSE));
		if (holder.isEmpty() || !OvveEnchantments.offered(holder.get(), cfg, brewery)) return Optional.empty();
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(holder.get(), 1);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		return Optional.of(book);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SpilledDrink::tick);
	}
}
