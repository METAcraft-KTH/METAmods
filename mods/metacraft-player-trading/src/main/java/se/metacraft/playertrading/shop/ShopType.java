package se.metacraft.playertrading.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.OrderedPMap;
import org.pcollections.PMap;
import se.metacraft.playertrading.PlayerTrading;
import se.metacraft.playertrading.block.entities.ShopBlockEntity;
import se.metacraft.playertrading.component.TradingComponents;
import se.metacraft.playertrading.util.helper.ItemStackTemplateHelper;

import java.util.*;

public interface ShopType {

	Codec<ShopType> CODEC = ShopTypeRegistry.REGISTRY.byNameCodec().dispatch(
		ShopType::codec, c -> c
	);

	Identifier CAN_EDIT_ADMIN_SHOP = PlayerTrading.getID("admin_shop.edit");

	boolean canEditShop(ShopBlockEntity shop, LivingEntity entity);

	default ShopType withNewOwner(LivingEntity user) {
		return this;
	}

	default ShopType removeSlots(IntCollection slots) {
		return this;
	}

	default Optional<UUID> getOwner() {
		return Optional.empty();
	}

	int remainingUses(ShopBlockEntity shop, int slot);

	UseResult use(ShopBlockEntity shop, int slot);

	default void handleTakenItems(ShopBlockEntity shop, List<ItemStack> items) {}

	MapCodec<? extends ShopType> codec();

	record Player(Optional<UUID> owner) implements ShopType {

		public static final MapCodec<Player> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Player::owner)
			).apply(instance, Player::new)
		);

		@Override
		public boolean canEditShop(ShopBlockEntity shop, LivingEntity entity) {
			return owner.map(uuid -> {
				if (uuid.equals(entity.getUUID())) return true;
				return shop.getShopKey().map(
					key -> key.equals(entity.getMainHandItem().get(TradingComponents.SHOP_KEY)) ||
							key.equals(entity.getOffhandItem().get(TradingComponents.SHOP_KEY))
				).orElse(false);
			}).orElse(true);
		}

		@Override
		public Optional<UUID> getOwner() {
			return owner;
		}

		@Override
		public MapCodec<? extends ShopType> codec() {
			return CODEC;
		}

		private static long countMatchingItems(ItemStackTemplate item, Storage<ItemVariant> storage) {
			long count = 0;
			for (var view : storage.nonEmptyViews()) {
				if (ItemStackTemplateHelper.matches(view.getResource(), item)) {
					count += view.getAmount();
				}
			}
			return count;
		}

		private static int countPossiblePurchases(ItemStackTemplate item, Storage<ItemVariant> storage) {
			return (int) (countMatchingItems(item, storage) / item.count());
		}

		private static int getPossibleInsertions(List<ItemStackTemplate> toInsert, Storage<ItemVariant> targetStorage) {
			int insertions = 0;
			// We use a transaction to simulate how many items we can insert before it fills up completely so we can send that info to the client.
			try (var transaction = Transaction.openOuter()) {
				outerLoop: while (true) {
					for (var item : toInsert) {
						var variant = ItemStackTemplateHelper.variantOf(item);
						if (variant.isBlank()) continue;
						long inserted = targetStorage.insert(variant, item.count(), transaction);
						if (inserted != item.count()) {
							break outerLoop;
						} else {
							insertions++;
						}
					}
				}
				transaction.abort(); // If we abort the transaction then nothing will happen.
			}
			return insertions;
		}

		@Override
		public int remainingUses(ShopBlockEntity shop, int slot) {
			return shop.getSourceStorage().filter(Storage::supportsExtraction).flatMap(
				source -> shop.getResultStorage().filter(Storage::supportsInsertion).flatMap(
					resultStorage -> shop.getShop().map(s -> {
						var offer = s.offers().get(slot);
						int possibleResults = countPossiblePurchases(offer.result(), source);
						List<ItemStackTemplate> toInsert = new ArrayList<>();
						toInsert.add(offer.price().left());
						offer.price().right().ifPresent(toInsert::add);
						int possibleInsertions = getPossibleInsertions(toInsert, resultStorage);
						return Math.min(possibleResults, possibleInsertions);
					})
				)
			).orElse(0);
		}

		@Override
		public void handleTakenItems(ShopBlockEntity shop, List<ItemStack> items) {
			shop.getResultStorage().ifPresent(storage -> {
				try (var transaction = Transaction.openOuter()) {
					for (var item : items) {
						storage.insert(ItemVariant.of(item), item.count(), transaction);
					}
					transaction.commit();
				}
			});
		}

		@Override
		public UseResult use(ShopBlockEntity shop, int slot) {
			shop.getSourceStorage().filter(Storage::supportsExtraction).ifPresent(storage -> {
				shop.getShop().ifPresent(s -> {
					var offer = s.offers().get(slot);
					try (var transaction = Transaction.openOuter()) {
						storage.extract(ItemStackTemplateHelper.variantOf(offer.result()), offer.result().count(), transaction);
						transaction.commit();
					}
				});
			});
			return UseResult.PASS;
		}

		@Override
		public ShopType withNewOwner(LivingEntity user) {
			if (owner.isPresent() && owner.get().equals(user.getUUID())) return this;
			return new Player(Optional.of(user.getUUID()));
		}
	}

	class Admin implements ShopType {

		private static final Admin INSTANCE = new Admin();
		public static Admin getInstance() {
			return INSTANCE;
		}
		public static final MapCodec<Admin> CODEC = MapCodec.unit(INSTANCE);

		private Admin() {}

		@Override
		public boolean canEditShop(ShopBlockEntity shop, LivingEntity entity) {
			return entity.checkPermission(CAN_EDIT_ADMIN_SHOP, PermissionLevel.GAMEMASTERS);
		}

		@Override
		public int remainingUses(ShopBlockEntity shop, int slot) {
			return Integer.MAX_VALUE;
		}

		@Override
		public UseResult use(ShopBlockEntity shop, int slot) {
			return UseResult.PASS;
		}

		@Override
		public MapCodec<? extends ShopType> codec() {
			return CODEC;
		}

	}

	record LimitedUses(SlotMap<Integer> usesRemaining) implements ShopType {

		public static final MapCodec<LimitedUses> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				SlotMap.codec(
					METACodecs.createPMapCodec(
						ShopSlotRanges.CODEC, Codec.INT,
						OrderedPMap.empty()
					)
				).optionalFieldOf("uses_remaining", SlotMap.of(OrderedPMap.empty())).forGetter(LimitedUses::usesRemaining)
			).apply(instance, LimitedUses::new)
		);

		public static final ShopType SINGLE_USE_SHOP = new LimitedUses(SlotMap.of(OrderedPMap.singleton(
			ShopSlotRanges.WILDCARD, 1
		)));

		@Override
		public int remainingUses(ShopBlockEntity shop, int slot) {
			return usesRemaining.get(slot).reduce(Integer::sum).orElse(Integer.MAX_VALUE);
		}

		public LimitedUses withRemainder(PMap<SlotRange, Integer> usesRemaining) {
			return new LimitedUses(SlotMap.of(usesRemaining));
		}

		@Override
		public ShopType removeSlots(IntCollection slots) {
			var newUsesRemaining = PCollectionsHelper.collectToMap(
				usesRemaining.slots().entrySet().stream(),
				e -> ShopSlotRanges.extractSlots(e.getKey(), slots), Map.Entry::getValue,
				OrderedPMap.empty()
			);
			return withRemainder(newUsesRemaining);
		}

		@Override
		public UseResult use(ShopBlockEntity shop, int slot) {
			var first = usesRemaining().getSlotRanges(slot).stream().min(Comparator.comparing(SlotRange::size));
			if (first.isPresent()) {
				int newAmount = usesRemaining.slots().get(first.get())-1;
				if (newAmount <= 0) {
					var newRemainder = usesRemaining.slots().minus(first.get());
					IntSet slotsRemoved = new IntOpenHashSet();
					slotsRemoved.addAll(first.get().slots());
					for (var r : newRemainder.keySet()) {
						slotsRemoved.removeAll(r.slots());
					}
					return UseResult.removeTrade(withRemainder(newRemainder), slotsRemoved);
				} else {
					return UseResult.updated(withRemainder(usesRemaining.slots().plus(first.get(), newAmount)));
				}
			}
			return UseResult.removeTrade(this, IntSet.of(slot));
		}

		@Override
		public boolean canEditShop(ShopBlockEntity shop, LivingEntity entity) {
			return entity.checkPermission(CAN_EDIT_ADMIN_SHOP, PermissionLevel.GAMEMASTERS);
		}

		@Override
		public MapCodec<? extends ShopType> codec() {
			return CODEC;
		}
	}

	interface UseResult {
		Pass PASS = new Pass();
		static Updated updated(ShopType shopType) {
			return new Updated(shopType);
		}
		static RemoveTrade removeTrade(ShopType shopType, IntCollection removedSlots) {
			return new RemoveTrade(shopType, removedSlots);
		}

		class Pass implements UseResult {
			private Pass() {}
		}
		record Updated(ShopType type) implements UseResult {}
		record RemoveTrade(ShopType type, IntCollection removedSlots) implements UseResult {}
	}
}
