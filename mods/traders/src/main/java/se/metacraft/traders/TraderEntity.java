package se.metacraft.traders;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A shopkeeper that stands where it was put and looks at passing players. Clients see a display model (BIL) with an
 * interaction box over it; a click on that opens a vanilla trade screen for its {@link TraderKind}'s offers in {@link TradersConfig}. It
 * is a {@link Merchant} of its own rather than a villager: it needs no profession, levels, restocking or breeding, and
 * a villager's client-side type would be wrong for the model anyway.
 */
public class TraderEntity extends PathfinderMob implements AnimatedEntity, Merchant {

	/** Ticks between the "yes" and "no" sounds as items are moved about the trade screen (vanilla's throttle is similar). */
	private static final int SOUND_GAP = 20;

	private final LivingEntityHolder<TraderEntity> holder;
	@Nullable
	private Player tradingPlayer;
	private MerchantOffers offers = new MerchantOffers();
	/** The trade each of {@link #offers} is, by index. */
	private List<TradersConfig.Trade> offeredTrades = List.of();
	private long lastSoundTick = Long.MIN_VALUE / 2;

	private final TraderKind kind;

	public TraderEntity(EntityType<? extends TraderEntity> type, Level level, TraderKind kind) {
		super(type, level);
		this.kind = kind;
		this.holder = new LivingEntityHolder<>(this, TraderModels.get(kind));
		EntityAttachment.ofTicking(this.holder, this);
		setPermanentlyInvulnerable(true);
		setPersistenceRequired();
	}

	/**
	 * No knockback: with no gravity a trader is never on the ground, so nothing would stop it, and a creative player's
	 * hit would slide it about 4 blocks from where it was put.
	 */
	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	public LivingEntityHolder<TraderEntity> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tradingPlayer != null) {
			getLookControl().setLookAt(tradingPlayer, 30.0f, 30.0f);
		}
	}


	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isAlive() || tradingPlayer != null) {
			return InteractionResult.PASS;
		}
		if (level() instanceof ServerLevel serverLevel) {
			// Each offer starts at the times this player has already made it, so a used-up one shows crossed out.
			var purchases = TraderPurchases.get(serverLevel.getServer());
			offeredTrades = TradersConfig.tradesOnOffer(kind);
			offers = new MerchantOffers();
			for (var trade : offeredTrades) {
				offers.add(trade.toOffer(purchases.count(player.getUUID(), kind, trade.key())));
			}
			if (offers.isEmpty()) {
				return InteractionResult.PASS;
			}
			setTradingPlayer(player);
			openTradingScreen(player, getDisplayName(), 0);
			// The screen didn't open (the player has another open): nobody is trading.
			if (!(player.containerMenu instanceof MerchantMenu)) {
				setTradingPlayer(null);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getDisplayName() {
		return hasCustomName() ? super.getDisplayName() : Component.translatable(getType().getDescriptionId());
	}

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		this.tradingPlayer = player;
	}

	@Nullable
	@Override
	public Player getTradingPlayer() {
		return tradingPlayer;
	}

	@Override
	public MerchantOffers getOffers() {
		return offers;
	}

	@Override
	public void overrideOffers(MerchantOffers offers) {
		// Offers come from the config and the player's purchases, every time the screen opens.
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		offer.increaseUses();
		int index = offers.indexOf(offer);
		if (index >= 0 && tradingPlayer != null && level() instanceof ServerLevel serverLevel) {
			var key = offeredTrades.get(index).key();
			TraderPurchases.get(serverLevel.getServer()).record(tradingPlayer.getUUID(), kind, key);
			// The trader's other trades counted under the same key are used up with it; the client is told.
			boolean shared = false;
			for (int i = 0; i < offers.size(); i++) {
				if (i != index && offeredTrades.get(i).key().equals(key)) {
					offers.get(i).increaseUses();
					shared = true;
				}
			}
			if (shared) {
				tradingPlayer.sendMerchantOffers(tradingPlayer.containerMenu.containerId, offers, 0, getVillagerXp(), showProgressBar(), canRestock());
			}
		}
		playSound(getNotifyTradeSound());
	}

	@Override
	public void notifyTradeUpdated(ItemStack stack) {
		if (!level().isClientSide() && level().getGameTime() - lastSoundTick > SOUND_GAP) {
			lastSoundTick = level().getGameTime();
			playSound(stack.isEmpty() ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_YES);
		}
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.VILLAGER_YES;
	}

	@Override
	public boolean isClientSide() {
		return level().isClientSide();
	}

	@Override
	public boolean stillValid(Player player) {
		return tradingPlayer == player && isAlive() && player.isWithinEntityInteractionRange(this, 4.0);
	}

	/** A trader hangs where it was put, in the air too: nothing pulls it down, and so nothing carries it into the void. */
	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}
}
