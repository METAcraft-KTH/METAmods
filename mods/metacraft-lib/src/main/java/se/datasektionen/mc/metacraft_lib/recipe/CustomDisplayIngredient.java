package se.datasektionen.mc.metacraft_lib.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.util.DisplayItemData;

import java.util.List;
import java.util.stream.Stream;

public record CustomDisplayIngredient(Ingredient base, List<ItemStack> display) implements CustomIngredient {

	public static final Serializer SERIALIZER = new Serializer();

	@Override
	public boolean test(ItemStack stack) {
		return base.test(stack);
	}

	@Override
	public Stream<RegistryEntry<Item>> getMatchingItems() {
		return base.getMatchingItems();
	}

	@Override
	public boolean requiresTesting() {
		return base.requiresTesting();
	}

	@Override
	public CustomIngredientSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public SlotDisplay toDisplay() {
		if (display.size() == 1) {
			return new SlotDisplay.StackSlotDisplay(display.getFirst());
		}
		return new SlotDisplay.CompositeSlotDisplay(display.stream().map(
				s -> (SlotDisplay) new SlotDisplay.StackSlotDisplay(s)
		).toList());
	}

	public static class Serializer implements CustomIngredientSerializer<CustomDisplayIngredient> {

		public static final MapCodec<CustomDisplayIngredient> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Ingredient.CODEC.fieldOf("base").forGetter(CustomDisplayIngredient::base),
						DisplayItemData.ITEM_LIST_CODEC.fieldOf("display").forGetter(CustomDisplayIngredient::display)
				).apply(instance, CustomDisplayIngredient::new)
		);

		public static final PacketCodec<RegistryByteBuf, CustomDisplayIngredient> PACKET_CODEC = PacketCodec.tuple(
				Ingredient.PACKET_CODEC, CustomDisplayIngredient::base,
				ItemStack.OPTIONAL_LIST_PACKET_CODEC, CustomDisplayIngredient::display,
				CustomDisplayIngredient::new
		);

		@Override
		public Identifier getIdentifier() {
			return METAcraftLib.getID("custom_display");
		}

		@Override
		public MapCodec<CustomDisplayIngredient> getCodec() {
			return CODEC;
		}

		@Override
		public PacketCodec<RegistryByteBuf, CustomDisplayIngredient> getPacketCodec() {
			return PACKET_CODEC;
		}
	}
}
