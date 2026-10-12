package metacraft.pirkko;

import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps genuine empty pots visible while Pirkko uses an invisible pot for targeting. */
final class FlowerPotCarrier implements BlockWithElementHolder {
	private static final Identifier VANILLA_MODEL = Identifier.fromNamespaceAndPath("pirkko", "vanilla_flower_pot");

	static void register() {
		// The pack blanks only the empty-pot blockstate, leaving vanilla's models,
		// items and all filled pots intact. Polymer owns the display's chunk lifecycle.
		if (!BlockWithElementHolder.registerOverlay(Blocks.FLOWER_POT, new FlowerPotCarrier())) {
			throw new IllegalStateException("Pirkko cannot restore vanilla flower pots: another mod owns their display overlay");
		}
	}

	@Override
	public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
		var stack = new ItemStack(Items.FLOWER_POT);
		stack.set(DataComponents.ITEM_MODEL, VANILLA_MODEL);
		var display = new ItemDisplayElement(stack);
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setDisplaySize(1, 1);
		var holder = new ElementHolder();
		holder.addElement(display);
		return holder;
	}
}
