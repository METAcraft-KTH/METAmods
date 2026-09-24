package se.metacraft.playertrading.shop;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.helper.PCollectionsHelper;
import org.pcollections.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Shop(
	ShopType shopType,
	PVector<SimpleOffer> offers,
	SlotMap<ResourceKey<LootItemCondition>> conditions
) {

	public static final String OFFERS = "offers";

	public static final Codec<Shop> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			ShopType.CODEC.fieldOf("shop_type").forGetter(Shop::shopType),
			METACodecs.createPCollectionCodec(SimpleOffer.CODEC, (PVector<SimpleOffer>) TreePVector.<SimpleOffer>empty()).fieldOf(OFFERS).forGetter(Shop::offers),
			SlotMap.codec(
				METACodecs.createPMapCodec(
					ShopSlotRanges.CODEC, ResourceKey.codec(Registries.PREDICATE),
					OrderedPMap.empty()
				)
			).fieldOf("conditions").forGetter(Shop::conditions)
		).apply(instance, Shop::new)
	);

	public static Shop create(ShopType shopType) {
		return new Shop(shopType, TreePVector.empty(), SlotMap.of(OrderedPMap.empty()));
	}

	public Shop withShopType(ShopType shopType) {
		if (shopType == this.shopType) return this;
		return new Shop(shopType, offers, conditions);
	}

	private static <T> PVector<T> minusAll(PVector<T> v, IntCollection indices) {
		IntList sortedList = new IntArrayList(indices);
		sortedList.sort(IntComparator.comparing(i -> i).reversed()); // Indices change whenever we remove. If we remove from the back (i.e. largest index first) we won't have this issue.
		for (int index : sortedList) {
			v = v.minus(index);
		}
		return v;
	}

	public Shop removeSlots(IntCollection slots) {
		var newOffers = minusAll(offers, slots);
		if (newOffers == offers) return this;
		var newConditions = PCollectionsHelper.collectToMap(
			conditions.slots().entrySet().stream(),
			e -> ShopSlotRanges.extractSlots(e.getKey(), slots), Map.Entry::getValue,
			OrderedPMap.empty()
		);
		return new Shop(shopType.removeSlots(slots), newOffers, SlotMap.of(newConditions));
	}

	public Shop removeSlot(int slot) {
		return removeSlots(IntList.of(slot));
	}

	public Shop withNewOwner(LivingEntity user) {
		return withShopType(shopType.withNewOwner(user));
	}

	public Shop setOffers(PVector<SimpleOffer> offers) {
		return new Shop(shopType, offers, conditions);
	}

	public Shop withCondition(SlotRange range, ResourceKey<LootItemCondition> condition) {
		return withConditions(conditions.slots().plus(range, condition));
	}

	public Shop withoutCondition(SlotRange range) {
		return withConditions(conditions.slots().minus(range));
	}

	public Shop withConditions(PMap<SlotRange, ResourceKey<LootItemCondition>> conditions) {
		return new Shop(shopType, offers, SlotMap.of(conditions));
	}

	public record SimpleOffer(
		Price price, ItemStackTemplate result
	) {
		public static final String PRICE = "price";
		public static final String RESULT = "result";

		public static final Codec<SimpleOffer> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				Price.CODEC.fieldOf(PRICE).forGetter(SimpleOffer::price),
				ItemStackTemplate.CODEC.fieldOf(RESULT).forGetter(SimpleOffer::result)
			).apply(instance, SimpleOffer::new)
		);

		public record Price(ItemStackTemplate left, Optional<ItemStackTemplate> right) {
			public static final Codec<Price> CODEC = Codec.either(
				ItemStackTemplate.CODEC.listOf(), ItemStackTemplate.CODEC
			).comapFlatMap(
				either -> either.map(
					list -> list.size() == 2 ? DataResult.success(new Price(list.getFirst(), Optional.of(list.get(1)))) : DataResult.error(() -> "Expected 2 item stacks, but found " + list.size()),
					single -> DataResult.success(new Price(single, Optional.empty()))
				),
				price -> price.right().isEmpty() ? Either.right(price.left()) : Either.left(List.of(price.left(), price.right().get()))
			);
		}

		public static ItemCost costOf(ItemStackTemplate item) {
			return new ItemCost(
				item.item(), item.count(),
				DataComponentExactPredicate.allOf(item.components().split().added())
			);
		}
	}

}
