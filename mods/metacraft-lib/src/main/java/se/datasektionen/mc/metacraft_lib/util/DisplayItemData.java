package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.dynamic.Codecs;

import java.util.List;
import java.util.function.Supplier;

public record DisplayItemData(Either<List<ItemStack>, LootTable> items) {
	public static final DisplayItemData EMPTY = new DisplayItemData(Either.left(List.of()));

	public static final Codec<List<ItemStack>> ITEM_LIST_CODEC = Codec.withAlternative(
			Codecs.nonEmptyList(ItemStack.CODEC.listOf()), ItemStack.CODEC, List::of
	);

	private static final Codec<Either<List<ItemStack>, LootTable>> ICON_CODEC = Codec.either(
			ITEM_LIST_CODEC, LootTable.CODEC
	);

	public static final Codec<DisplayItemData> CODEC = ICON_CODEC.xmap(DisplayItemData::new, DisplayItemData::items);

	public List<ItemStack> getItems(Entity entity) {
		if (entity.getWorld().isClient()) return List.of();
		float luck = entity instanceof LivingEntity living ? (float) living.getAttributeValue(EntityAttributes.LUCK) : 0;
		Supplier<LootWorldContext> ctx = () -> new LootWorldContext.Builder((ServerWorld) entity.getWorld())
				.add(LootContextParameters.ORIGIN, entity.getPos())
				.luck(luck)
				.add(LootContextParameters.THIS_ENTITY, entity)
				.build(LootContextTypes.CHEST);
		var icon = items().map(
				items -> items,
				lootTable -> lootTable.generateLoot(ctx.get())
		);
		if (icon.isEmpty()) {
			icon = List.of(new ItemStack(Items.BARRIER));
		}
		return icon;
	}
}
