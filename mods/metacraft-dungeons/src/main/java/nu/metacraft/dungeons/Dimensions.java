package nu.metacraft.dungeons;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

public class Dimensions {

	public static final RegistryKey<World> DUNGEONS = RegistryKey.of(
		RegistryKeys.WORLD, METAcraftDungeons.getID("dungeons")
	);

}
