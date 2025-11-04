package nu.metacraft.relay;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import nu.metacraft.relay.blocks.block.RelayBlock;
import nu.metacraft.relay.recipe.RelayProgramRecipe;
import nu.metacraft.lib.event.RecipeLoad;

public class Events {

	public static void init() {
		RecipeLoad.EVENT.register((id, json, recipe, registryLookup) -> {
			if (id == RelayDatagen.RELAY_PROGRAM && recipe instanceof ShapelessRecipe crafting) {
				return new RelayProgramRecipe(crafting);
			}
			return recipe;
		});
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, packetContext) -> {
			if (original.getItem() instanceof BlockItem b && b.getBlock() instanceof RelayBlock) {
				if (
						original.getOrDefault(
								DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
						).shows(DataComponents.LODESTONE_TRACKER)
				) {
					var tracker = original.get(DataComponents.LODESTONE_TRACKER);
					if (tracker != null && tracker.target().isPresent()) {
						var targetPos = tracker.target().get().pos().toShortString();
						var targetDim = tracker.target().get().dimension().location().toString();
						client.update(
								DataComponents.LORE, ItemLore.EMPTY,
								lore -> lore.withLineAdded(
										RelayBlock.getTargetText(targetPos, targetDim).setStyle(PolymerItemUtils.CLEAN_STYLE)
								)
						);
					} else {
						client.update(
								DataComponents.LORE, ItemLore.EMPTY,
								lore -> lore.withLineAdded(
										Component.translatableWithFallback(
												"block.metacraft.relay.no_target", "No Target"
										).setStyle(PolymerItemUtils.CLEAN_STYLE).withStyle(
												style -> style.applyFormat(ChatFormatting.RED)
										)
								).withLineAdded(
										Component.translatableWithFallback(
												"block.metacraft.relay.no_target.1", "Please combine me with a lodestone"
										).setStyle(PolymerItemUtils.CLEAN_STYLE).withStyle(
												style -> style.applyFormat(ChatFormatting.YELLOW)
										)
								).withLineAdded(
										Component.translatableWithFallback(
												"block.metacraft.relay.no_target.2", "compass in a crafting grid"
										).setStyle(PolymerItemUtils.CLEAN_STYLE).withStyle(
												style -> style.applyFormat(ChatFormatting.YELLOW)
										)
								)
						);
					}
				}
			}
			return client;
		});
	}

}
