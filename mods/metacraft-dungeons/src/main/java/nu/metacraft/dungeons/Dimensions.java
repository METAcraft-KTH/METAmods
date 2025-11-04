package nu.metacraft.dungeons;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class Dimensions {

	public static final ResourceKey<Level> DUNGEONS = ResourceKey.create(
		Registries.DIMENSION, METAcraftDungeons.getID("dungeons")
	);

}
