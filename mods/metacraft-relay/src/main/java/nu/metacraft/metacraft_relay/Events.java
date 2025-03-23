package nu.metacraft.metacraft_relay;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import nu.metacraft.metacraft_relay.blocks.block.RelayBlock;
import nu.metacraft.metacraft_relay.recipe.RelayProgramRecipe;
import se.datasektionen.mc.metacraft_lib.event.RecipeLoad;

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
								DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT
						).shouldDisplay(DataComponentTypes.LODESTONE_TRACKER)
				) {
					var tracker = original.get(DataComponentTypes.LODESTONE_TRACKER);
					if (tracker != null && tracker.target().isPresent()) {
						var targetPos = tracker.target().get().pos().toShortString();
						var targetDim = tracker.target().get().dimension().getValue().toString();
						client.apply(
								DataComponentTypes.LORE, LoreComponent.DEFAULT,
								lore -> lore.with(
										RelayBlock.getTargetText(targetPos, targetDim).setStyle(PolymerItemUtils.CLEAN_STYLE)
								)
						);
					} else {
						client.apply(
								DataComponentTypes.LORE, LoreComponent.DEFAULT,
								lore -> lore.with(
										Text.translatableWithFallback(
												"block.metacraft.relay.no_target", "No Target"
										).setStyle(PolymerItemUtils.CLEAN_STYLE).styled(
												style -> style.withFormatting(Formatting.RED)
										)
								).with(
										Text.translatableWithFallback(
												"block.metacraft.relay.no_target.1", "Please combine me with a lodestone"
										).setStyle(PolymerItemUtils.CLEAN_STYLE).styled(
												style -> style.withFormatting(Formatting.YELLOW)
										)
								).with(
										Text.translatableWithFallback(
												"block.metacraft.relay.no_target.2", "compass in a crafting grid"
										).setStyle(PolymerItemUtils.CLEAN_STYLE).styled(
												style -> style.withFormatting(Formatting.YELLOW)
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
