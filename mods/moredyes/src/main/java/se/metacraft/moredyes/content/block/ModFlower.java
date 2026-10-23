package se.metacraft.moredyes.content.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import se.metacraft.moredyes.content.ModContent;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * A colour's flower (Cerose, Laser Violet, RGBouquet; colors.json names it): what a sniffer digs up
 * beside vanilla's torchflower seeds and pitcher pods, and what our dye is crafted from. Vanilla's
 * torchflower in every way but its look, which is hand-drawn (a cross model on a Polymer plant state).
 */
public final class ModFlower extends FlowerBlock implements PolymerTexturedBlock {
	private final BlockState client;

	public ModFlower(Properties properties, Identifier id) {
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
			table.modifyPools(pool -> ModContent.flowers().forEach(item -> pool.add(LootItem.lootTableItem(item))));
		});
	}

	/**
	 * Our flower in a flower pot, potted and taken out as vanilla's are (registering it is what lets
	 * the empty pot take our flower). Polymer has no flower pot donor, so the client is told it is
	 * vanilla's empty flower pot, and an item display stands our flower in it: the plant half of
	 * vanilla's potted model, as stairs and slabs are drawn over vanilla's
	 * ({@link ShapedBlocks.DisplayHolder}). The pot under it is the real one: its outline, collision,
	 * sounds and break particles.
	 */
	public static final class Potted extends FlowerPotBlock implements PolymerTexturedBlock, BlockWithElementHolder, ShapedBlocks.DisplayProvider {
		private final Identifier id;

		public Potted(Block flower, Properties properties, Identifier id) {
			super(flower, properties);
			this.id = id;
		}

		@Override
		public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
			return Blocks.FLOWER_POT.defaultBlockState();
		}

		@Override
		public ItemStack displayStack(BlockState state) {
			return ShapedBlocks.displayStack(id);
		}

		@Override
		public Quaternionf displayRotation(BlockState state) {
			return ShapedBlocks.FIXED_FLIP;
		}

		@Override
		public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
			return new ShapedBlocks.DisplayHolder(this, initialBlockState);
		}
	}
}
