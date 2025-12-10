package nu.metacraft.dungeons.block;

import nu.metacraft.dungeons.METAcraftDungeons;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class DungeonBlocks {

	public static void init() {

	}

	private static Block register(
			String id, Function<BlockBehaviour.Properties, Block> block,
			BlockBehaviour.Properties settings
	) {
		var key = ResourceKey.create(Registries.BLOCK, METAcraftDungeons.getID(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, block.apply(settings.setId(key)));
	}

}
