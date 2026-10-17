package metacraft.moredyes.content.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import metacraft.moredyes.content.ModContent;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import org.jspecify.annotations.Nullable;

/**
 * A torchflower in one of our colours: what a sniffer digs up beside vanilla's torchflower seeds and
 * pitcher pods, and what our dye is crafted from. Vanilla's torchflower in every way but its look,
 * which is the torchflower's flame recoloured (a cross model on a Polymer plant state).
 */
public final class ModTorchflower extends FlowerBlock implements PolymerTexturedBlock {
	private final BlockState client;

	public ModTorchflower(Properties properties, Identifier id) {
		super(MobEffects.NIGHT_VISION, 5.0F, properties);
		this.client = ClientStates.request(id.toString(), BlockModelType.PLANT,
				PolymerBlockModel.of(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath())));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return client;
	}

	/** A sniffer's dig can turn up one of ours, as often as each of its vanilla finds. */
	public static void addToSnifferDigging() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!key.equals(BuiltInLootTables.SNIFFER_DIGGING)) return;
			table.modifyPools(pool -> ModContent.torchflowers().forEach(item -> pool.add(LootItem.lootTableItem(item))));
		});
	}
}
