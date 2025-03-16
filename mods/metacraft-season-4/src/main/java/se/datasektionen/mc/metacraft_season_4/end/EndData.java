package se.datasektionen.mc.metacraft_season_4.end;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.pool.StructurePool;
import se.datasektionen.mc.metacraft_season_4.Season4;

public class EndData {

	public static final RegistryKey<StructurePool> END_GATEWAY_RETURN = RegistryKey.of(RegistryKeys.TEMPLATE_POOL, Season4.getID("end_gateway_return"));
	public static final RegistryKey<StructurePool> END_GATEWAY_DRAGON = RegistryKey.of(RegistryKeys.TEMPLATE_POOL, Season4.getID("end_gateway_dragon"));

}
