package nu.metacraft.portable_jukebox.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import nu.metacraft.portable_jukebox.PortableJukebox;

import java.util.function.Function;

public class Blocks {

	public static final PortableJukeboxBlock PORTABLE_JUKEBOX = register(
			"portable_jukebox", PortableJukeboxBlock::new,
			AbstractBlock.Settings.copy(net.minecraft.block.Blocks.PLAYER_HEAD)
	);

	public static void init() {
		BlockEntities.init();
	}

	private static <T extends Block> T register(String id, Function<AbstractBlock.Settings, T> block, AbstractBlock.Settings settings) {
		var key = RegistryKey.of(RegistryKeys.BLOCK, PortableJukebox.getID(id));
		return Registry.register(Registries.BLOCK, key, block.apply(settings.registryKey(key)));
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
			return Registry.register(Registries.BLOCK_ENTITY_TYPE, PortableJukebox.getID(id), blockEntity);
		}

	}

}
