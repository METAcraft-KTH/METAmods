package nu.metacraft.lib.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.DisplayItemData;
import nu.metacraft.lib.util.METACodecs;

import java.util.List;
import java.util.stream.Stream;

public record CustomDisplayIngredient(Ingredient base, List<ItemStackTemplate> displayItems) implements CustomIngredient {

	public static final Serializer SERIALIZER = new Serializer();

	@Override
	public boolean test(ItemStack stack) {
		return base.test(stack);
	}

	@Override
	public Stream<Holder<Item>> items() {
		if (base.requiresTesting() && base.items().findAny().isEmpty()) {
			return displayItems.stream().map(ItemStackTemplate::item).distinct();
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
	public SlotDisplay display() {
		if (displayItems.size() == 1) {
			return new SlotDisplay.ItemStackSlotDisplay(displayItems.getFirst());
		}
		return new SlotDisplay.Composite(displayItems.stream().map(
				s -> (SlotDisplay) new SlotDisplay.ItemStackSlotDisplay(s)
		).toList());
	}

	public static class Serializer implements CustomIngredientSerializer<CustomDisplayIngredient> {

		public static final MapCodec<CustomDisplayIngredient> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Ingredient.CODEC.fieldOf("base").forGetter(CustomDisplayIngredient::base),
						METACodecs.collectionOrSingleCodec(
								ItemStackTemplate.CODEC,
								ItemStackTemplate.CODEC.listOf(),
								List::of
						).fieldOf("displayItems").forGetter(CustomDisplayIngredient::displayItems)
				).apply(instance, CustomDisplayIngredient::new)
		);

		public static final StreamCodec<RegistryFriendlyByteBuf, CustomDisplayIngredient> PACKET_CODEC = StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC, CustomDisplayIngredient::base,
				ItemStackTemplate.STREAM_CODEC.apply(
						ByteBufCodecs.collection(NonNullList::createWithCapacity)
				), CustomDisplayIngredient::displayItems,
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
		public StreamCodec<RegistryFriendlyByteBuf, CustomDisplayIngredient> getStreamCodec() {
			return PACKET_CODEC;
		}
	}
}
