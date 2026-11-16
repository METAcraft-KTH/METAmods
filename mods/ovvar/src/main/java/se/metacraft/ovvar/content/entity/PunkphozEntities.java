package se.metacraft.ovvar.content.entity;

import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/**
 * Punkphöz's entity type and model. Server-side (Polymer): vanilla clients see the BIL display model
 * {@code /model/ovvar/punkphoz.bbmodel}, the vanilla villager rig in Dante's look, with {@link PunkphozAnimations}'s animations.
 */
public final class PunkphozEntities {

	/** A wandering trader's box, eyes and tracking range. */
	public static final EntityType<Punkphoz> PUNKPHOZ = register();

	private static Model model;

	private PunkphozEntities() {}

	/** Registers the type and its attributes, and loads the model: BIL doesn't cache it, and it must exist before Polymer builds the pack. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(PUNKPHOZ, Mob.createMobAttributes());
		model = BbModelLoader.load(Identifier.fromNamespaceAndPath("ovvar", "punkphoz"));
	}

	public static Model model() {
		return model;
	}

	private static EntityType<Punkphoz> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("ovvar", "punkphoz"));
		var type = EntityType.Builder.<Punkphoz>of(Punkphoz::new, MobCategory.CREATURE)
				.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
