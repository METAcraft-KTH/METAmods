package nu.metacraft.lib.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.DisplayItemData;

import java.util.List;
import java.util.stream.Stream;

public record CustomDisplayIngredient(Ingredient base, List<ItemStack> display) implements CustomIngredient {

	public static final Serializer SERIALIZER = new Serializer();

	@Override
	public boolean test(ItemStack stack) {
		return base.test(stack);
	}

	@Override
	public Stream<Holder<Item>> getMatchingItems() {
		if (base.requiresTesting() && base.items().findAny().isEmpty()) {
			return display.stream().map(s -> (Holder<Item>) s.getItem().builtInRegistryHolder()).distinct();
		}
		return base.items();
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
			return new SlotDisplay.ItemStackSlotDisplay(display.getFirst());
		}
		return new SlotDisplay.Composite(display.stream().map(
				s -> (SlotDisplay) new SlotDisplay.ItemStackSlotDisplay(s)
		).toList());
	}

	public static class Serializer implements CustomIngredientSerializer<CustomDisplayIngredient> {

		public static final MapCodec<CustomDisplayIngredient> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Ingredient.CODEC.fieldOf("base").forGetter(CustomDisplayIngredient::base),
						DisplayItemData.ITEM_LIST_CODEC.fieldOf("display").forGetter(CustomDisplayIngredient::display)
				).apply(instance, CustomDisplayIngredient::new)
		);

		public static final StreamCodec<RegistryFriendlyByteBuf, CustomDisplayIngredient> PACKET_CODEC = StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC, CustomDisplayIngredient::base,
				ItemStack.OPTIONAL_LIST_STREAM_CODEC, CustomDisplayIngredient::display,
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
		public StreamCodec<RegistryFriendlyByteBuf, CustomDisplayIngredient> getPacketCodec() {
			return PACKET_CODEC;
		}
	}
}
