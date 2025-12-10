package nu.metacraft.lib.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record DisplayItemData(Either<List<ItemStack>, LootTable> items) {
	public static final DisplayItemData EMPTY = new DisplayItemData(Either.left(List.of()));

	public static final Codec<List<ItemStack>> ITEM_LIST_CODEC = Codec.withAlternative(
			ExtraCodecs.nonEmptyList(ItemStack.CODEC.listOf()), ItemStack.CODEC, List::of
	);

	private static final Codec<Either<List<ItemStack>, LootTable>> ICON_CODEC = Codec.either(
			ITEM_LIST_CODEC, LootTable.DIRECT_CODEC
	);

	public static final Codec<DisplayItemData> CODEC = ICON_CODEC.xmap(DisplayItemData::new, DisplayItemData::items);

	public List<ItemStack> getItems(Entity entity) {
		if (entity.level().isClientSide()) return List.of();
		float luck = entity instanceof LivingEntity living ? (float) living.getAttributeValue(Attributes.LUCK) : 0;
		Supplier<LootParams> ctx = () -> new LootParams.Builder((ServerLevel) entity.level())
				.withParameter(LootContextParams.ORIGIN, entity.position())
				.withLuck(luck)
				.withParameter(LootContextParams.THIS_ENTITY, entity)
				.create(LootContextParamSets.CHEST);
		var icon = items().map(
				items -> items,
				lootTable -> lootTable.getRandomItems(ctx.get())
		);
		if (icon.isEmpty()) {
			icon = List.of(new ItemStack(Items.BARRIER));
		}
		return icon;
	}
}
