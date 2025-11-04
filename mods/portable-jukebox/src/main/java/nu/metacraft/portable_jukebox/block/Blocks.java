package nu.metacraft.portable_jukebox.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import nu.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.Function;

public class Blocks {

	public static final PortableJukeboxBlock PORTABLE_JUKEBOX = register(
			"portable_jukebox", PortableJukeboxBlock::new,
			BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.PLAYER_HEAD)
	);

	public static void init() {
		BlockEntities.init();
	}

	private static <T extends Block> T register(String id, Function<BlockBehaviour.Properties, T> block, BlockBehaviour.Properties settings) {
		var key = ResourceKey.create(Registries.BLOCK, PortableJukebox.getID(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, block.apply(settings.setId(key)));
	}

	public static class BlockEntities {

		public static final BlockEntityType<PortableJukeboxBlockEntity> PORTABLE_JUKEBOX = register(
				"portable_jukebox", FabricBlockEntityTypeBuilder.create(
						PortableJukeboxBlockEntity::new, Blocks.PORTABLE_JUKEBOX
				).build()
		);

		public static void init() {

		}

		private static <T extends BlockEntityType<?>> T register(String id, T blockEntity) {
			PolymerBlockUtils.registerBlockEntity(blockEntity);
			return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PortableJukebox.getID(id), blockEntity);
		}

	}

}
