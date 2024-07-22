package se.datasektionen.mc.faster_minecarts.configs;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;

import java.util.List;

public class EntityFactorConfig extends NumberedRegistryEntry<EntityType<?>> {

	public EntityFactorConfig(List<? extends String> config) {
		super(config, Registries.ENTITY_TYPE, "\\*");
	}
}
