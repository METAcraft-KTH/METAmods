package se.metacraft.portable_jukebox.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import se.metacraft.portable_jukebox.Boombox;

import java.util.function.Function;

public class BoomboxBlocks {

	public static final BoomboxBlock BOOMBOX = register(
			"portable_jukebox", BoomboxBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.PLAYER_HEAD)
					.strength(0.8F)
					.sound(SoundType.WOOD)
	);

	public static void init() {
		BlockEntities.init();
	}

	private static <T extends Block> T register(String id, Function<BlockBehaviour.Properties, T> block, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, Boombox.getID(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, block.apply(settings.setId(key)));
	}

	public static class BlockEntities {

		public static final BlockEntityType<BoomboxBlockEntity> BOOMBOX = register(
				"portable_jukebox", FabricBlockEntityTypeBuilder.create(
						BoomboxBlockEntity::new, BoomboxBlocks.BOOMBOX
				).build()
		);

		public static void init() {

		}

		private static <T extends BlockEntityType<?>> T register(String id, T blockEntity) {
			PolymerBlockUtils.registerBlockEntity(blockEntity);
			return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Boombox.getID(id), blockEntity);
		}

	}

}
