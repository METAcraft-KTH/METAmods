package se.datasektionen.mc.portable_jukebox.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import se.datasektionen.mc.portable_jukebox.PortableJukebox;

import java.util.Set;

public class Blocks {

	public static final PortableJukeboxBlock PORTABLE_JUKEBOX = register("portable_jukebox", new PortableJukeboxBlock(
			AbstractBlock.Settings.copy(net.minecraft.block.Blocks.PLAYER_HEAD)
	));

	public static void init() {
		BlockEntities.init();
	}

	private static <T extends Block> T register(String id, T block) {
		return Registry.register(Registries.BLOCK, PortableJukebox.getID(id), block);
	}

	public static class BlockEntities {

		public static final BlockEntityType<PortableJukeboxBlockEntity> PORTABLE_JUKEBOX = register("portable_jukebox", new BlockEntityType<>(
				PortableJukeboxBlockEntity::new, Set.of(Blocks.PORTABLE_JUKEBOX), null
		));

		public static void init() {

		}

		private static <T extends BlockEntityType<?>> T register(String id, T blockEntity) {
			PolymerBlockUtils.registerBlockEntity(blockEntity);
			return Registry.register(Registries.BLOCK_ENTITY_TYPE, PortableJukebox.getID(id), blockEntity);
		}

	}

}
