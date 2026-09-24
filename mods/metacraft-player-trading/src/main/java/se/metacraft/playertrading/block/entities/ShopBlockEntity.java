package se.metacraft.playertrading.block.entities;

import com.google.common.base.Suppliers;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import nu.metacraft.lib.util.TaskScheduler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import se.metacraft.playertrading.block.TradingBlockEntities;
import se.metacraft.playertrading.block.blocks.BaseShopBlock;
import se.metacraft.playertrading.component.TradingComponents;
import se.metacraft.playertrading.component.components.ShopKey;
import se.metacraft.playertrading.criteria.ShopCriteriaTriggers;
import se.metacraft.playertrading.item.TradingItems;
import se.metacraft.playertrading.shop.gui.BuyFromShopGUI;
import se.metacraft.playertrading.shop.gui.ConfigureShopGUI;
import se.metacraft.playertrading.shop.Shop;

import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ShopBlockEntity extends BlockEntity {

	public static final String SHOP = "shop";
	private static final String SHOP_KEY = "shop_key";

	private PSet<UpdatableGUI> openGUIs = HashTreePSet.empty();

	@Nullable
	private Shop shop;

	@Nullable
	private ShopKey shopKey;

	protected ShopBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
		super(type, worldPosition, blockState);
	}

	public ShopBlockEntity(BlockPos worldPosition, BlockState blockState) {
		super(TradingBlockEntities.SHOP, worldPosition, blockState);
	}

	public void setPlacedBy(LivingEntity by) {
		if (by == null) return;
		if (shop != null) {
			shop = shop.withNewOwner(by);
		}
	}

	public InteractionResult onUse(Player player, BlockPos pos, InteractionHand hand) {
		if (shop != null && shop.shopType().getOwner().map(o -> o.equals(player.getUUID())).orElse(false)) {
			var item = player.getItemInHand(hand);
			if (item.is(Items.GOLD_INGOT) || item.is(TradingItems.SHOP_KEY)) {
				var shopKey = new ItemStack(TradingItems.SHOP_KEY);
				if (this.shopKey == null) {
					this.shopKey = ShopKey.generate();
					setChanged();
				}
				shopKey.set(TradingComponents.SHOP_KEY, this.shopKey);
				shopKey.set(DataComponents.ITEM_NAME, Component.translatable(TradingItems.SHOP_KEY.getDescriptionId()+".owned", player.getDisplayName()));
				item.consume(1, player);
				if (!player.getInventory().add(shopKey)) {
					player.drop(shopKey, false, Prediction.SERVER_ONLY);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	public InteractionResult onUse(Player player, BlockPos pos) {
		if (shop == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		if (shop.shopType().canEditShop(this, player) && !player.isCrouching()) {
			if (!pos.equals(getBlockPos())) {
				return InteractionResult.PASS;
			} else {
				var s = new ConfigureShopGUI(sp, this);
				openGUIs = openGUIs.plus(s);
				s.open();
				return InteractionResult.SUCCESS_SERVER;
			}
		} else {
			var s = new BuyFromShopGUI(sp, this);
			openGUIs = openGUIs.plus(s);
			s.open();
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	@Override
	protected void applyImplicitComponents(final @NonNull DataComponentGetter components) {
		shop = components.get(TradingComponents.SHOP);
		shopKey = components.get(TradingComponents.SHOP_KEY);
	}

	@Override
	protected void collectImplicitComponents(final DataComponentMap.@NonNull Builder components) {
		components.set(TradingComponents.SHOP, shop);
		components.set(TradingComponents.SHOP_KEY, shopKey);
	}

	@Override
	protected void loadAdditional(final @NonNull ValueInput input) {
		super.loadAdditional(input);
		shop = input.read(SHOP, Shop.CODEC).orElse(null);
		shopKey = input.read(SHOP_KEY, ShopKey.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(final @NonNull ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable(SHOP, Shop.CODEC, shop);
		output.storeNullable(SHOP_KEY, ShopKey.CODEC, shopKey);
	}

	public boolean isTradeAllowed(LivingEntity entity, int slot) {
		if (shop != null && entity.level() instanceof ServerLevel level) {
			Supplier<LootContext> ctx = Suppliers.memoize(() -> new LootContext.Builder(
				new LootParams.Builder(level)
					.withParameter(LootContextParams.ORIGIN, entity.position())
					.withParameter(LootContextParams.THIS_ENTITY, entity)
					.withParameter(LootContextParams.BLOCK_STATE, getBlockState())
					.withParameter(LootContextParams.BLOCK_ENTITY, this)
					.create(ShopCriteriaTriggers.SHOP_CONTEXT)
			).create(Optional.empty()));
			var predicates = level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.PREDICATE);
			return shop.conditions().get(slot).map(predicates::get).allMatch(
				condition -> condition.map(
					lootItemConditionReference -> lootItemConditionReference.value().test(ctx.get())
				).orElse(false)
			);
		}
		return false;
	}

	public int getRemainingUses(int slot) {
		if (shop != null) {
			return shop.shopType().remainingUses(this, slot);
		}
		return 0;
	}

	public Optional<ShopKey> getShopKey() {
		return Optional.ofNullable(shopKey);
	}

	public Optional<Shop> getShop() {
		return Optional.ofNullable(shop);
	}

	@Override
	public void setRemoved() {
		for (var gui : openGUIs) {
			gui.close();
		}
		super.setRemoved();
	}

	public void triggerUpdate() {
		if (level != null && level.getServer() != null) {
			TaskScheduler.scheduleImmediately(
				level.getServer(), () -> {
					for (var gui : openGUIs) {
						gui.update();
					}
				}
			);
		}
	}

	public void modifyShop(UnaryOperator<Shop> shopChanger) {
		if (shop == null) return;
		var prevShop = shop;
		shop = shopChanger.apply(prevShop);
		if (shop != prevShop) {
			setChanged();
			triggerUpdate();
		}
	}

	public void destroy() {
		shop = null;
		if (level != null) {
			level.setBlock(worldPosition, getBlockState().getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
		}
	}

	public void onClosed(UpdatableGUI gui) {
		openGUIs = openGUIs.minus(gui);
	}

	public interface UpdatableGUI {
		void update();
		void close();
	}

	public Direction getAttachedDirection() {
		var state = getBlockState();
		if (state.getBlock() instanceof BaseShopBlock s) {
			return s.getContainerDirection(state, level, getBlockPos());
		}
		return Direction.DOWN;
	}

	public BlockPos getAttachedPos() {
		var state = getBlockState();
		if (state.getBlock() instanceof BaseShopBlock s) {
			return s.getContainerPos(state, level, getBlockPos());
		}
		return getBlockPos().below();
	}

	public Optional<Storage<ItemVariant>> getShopStorage() {
		if (level == null) return Optional.empty();
		return Optional.ofNullable(ItemStorage.SIDED.find(level, getAttachedPos(), getAttachedDirection()));
	}

	public Optional<Storage<ItemVariant>> getSourceStorage() {
		if (level == null) return Optional.empty();
		List<Storage<ItemVariant>> storage = new ArrayList<>();
		getShopStorage().ifPresent(storage::add);
		var pos = getAttachedPos();
		for (var direction : Direction.values()) {
			var hopperPos = pos.relative(direction);
			if (level.getBlockEntity(hopperPos) instanceof HopperBlockEntity hopper) {
				if (hopper.getBlockState().getValue(HopperBlock.FACING) == direction.getOpposite()) {
					var hopperStorage = ItemStorage.SIDED.find(level, hopperPos, direction.getOpposite());
					if (hopperStorage != null) {
						storage.add(hopperStorage);
					}
				}
			}
		}
		if (storage.isEmpty()) return Optional.empty();
		if (storage.size() == 1) return Optional.of(storage.getFirst());
		return Optional.of(new CombinedStorage<>(storage));
	}

	public Optional<Storage<ItemVariant>> getResultStorage() {
		if (level == null) return Optional.empty();
		var hopperPos = getAttachedPos().below();
		if (level.getBlockEntity(hopperPos) instanceof HopperBlockEntity) {
			var hopperStorage = ItemStorage.SIDED.find(level, hopperPos, Direction.UP);
			if (hopperStorage != null) {
				return Optional.of(hopperStorage);
			}
		}
		return getShopStorage();
	}

	public static Optional<ShopBlockEntity> getConnectedTo(Level level, BlockPos pos) {
		if (BaseShopBlock.validShopContainer(level, pos)) {
			for (var direction : Direction.values()) {
				var entity = level.getBlockEntity(pos.relative(direction));
				if (entity instanceof ShopBlockEntity shop && shop.getAttachedPos().equals(pos)) {
					return Optional.of(shop);
				}
			}
		}
		return Optional.empty();
	}

	public static boolean hasShop(Level level, BlockPos pos) {
		return ShopBlockEntity.getConnectedTo(level, pos).map(ShopBlockEntity::getShop).isPresent();
	}

}
