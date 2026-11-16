package se.metacraft.ovvar.content.entity;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;

import java.util.Optional;

/**
 * Punkphöz: a wandering trader in his own BIL model, the vanilla villager rig in Dante's look ({@code /model/ovvar/punkphoz.bbmodel}).
 * He wanders, trades and leaves like the vanilla one, but drinks no invisibility potion (or milk) at dusk: his model has no
 * invisible look to show. He walks and shakes his head the way a vanilla villager does ({@link PunkphozAnimations}), and shakes it
 * when asked to trade with nothing left to sell, as a villager without offers does (vanilla's wandering trader never does).
 */
public class Punkphoz extends WanderingTrader implements AnimatedEntity {

	private final LivingEntityHolder<Punkphoz> holder;

	public Punkphoz(EntityType<? extends Punkphoz> type, Level level) {
		super(type, level);
		this.holder = new LivingEntityHolder<>(this, PunkphozEntities.model());
		EntityAttachment.ofTicking(this.holder, this);
	}

	@Override
	public LivingEntityHolder<Punkphoz> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.removeAllGoals(g -> g instanceof UseItemGoal);
	}

	/** The villager's unhappy shake: how many ticks it lasts ({@code Villager.setUnhappy}). */
	public static final int UNHAPPY_TICKS = 40;

	/** Asked to trade while sold out (no offers, or every one out of stock), he shakes his head and says no, then trades as before. */
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && !level().isClientSide() && !player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG)
				&& isAlive() && !isTrading() && !isBaby() && soldOut()) {
			setUnhappyCounter(UNHAPPY_TICKS);
			makeSound(SoundEvents.WANDERING_TRADER_NO);
		}
		return super.mobInteract(player, hand);
	}

	private boolean soldOut() {
		return getOffers().stream().allMatch(MerchantOffer::isOutOfStock);
	}

	/**
	 * Counts the shake down, as {@code Villager.tick} does. While he shakes his look is level, so the shake's own head pitch is
	 * the whole of it, as vanilla's {@code head.xRot = 0.4} overrides the look's pitch; then the animations follow his state.
	 */
	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		int unhappy = getUnhappyCounter();
		if (unhappy > 0) {
			setUnhappyCounter(unhappy - 1);
			setXRot(0);
			xRotO = 0;
		}
		PunkphozAnimations.tick(this);
	}

	/** His stock for this visit ({@link PunkphozStock#roll}), counted in the world's {@link PunkphozHistory}. */
	@Override
	protected void updateTrades(ServerLevel level) {
		PunkphozHistory history = PunkphozHistory.get(level.getServer());
		MerchantOffers offers = getOffers();
		for (PunkphozStock.Stocked stocked : PunkphozStock.roll(OvvarConfig.get().punkphoz(), PunkphozConfig.poolOnOffer(),
				PunkphozStock.extras(level.registryAccess(), OvvarConfig.get().enchantments(), OvveEnchantments.BREWERY), random, history)) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, stocked.emeralds()), Optional.empty(), stocked.result(), 0,
					stocked.maxUses(), 1, 0.05f));
		}
		history.setDirty();
	}
}
