package se.metacraft.nether_rifts.mob;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Ghast;
import se.metacraft.nether_rifts.NetherRifts;

/** The rift's own entities: server-side (Polymer), shown to vanilla clients as display models, vanilla mobs or vanilla items. */
public final class RiftEntities {

	public static final EntityType<WarDrummer> WAR_DRUMMER = living("war_drummer",
			EntityType.Builder.of(WarDrummer::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10),
			WarDrummer.createAttributes());

	/** The artist's model at 1.5x ({@link PiglinGeneral#MODEL_SCALE}): his head is 3.3 blocks up. */
	public static final EntityType<PiglinGeneral> PIGLIN_GENERAL = living("piglin_general",
			EntityType.Builder.of(PiglinGeneral::new, MobCategory.MONSTER).sized(1.3f, 3.3f).eyeHeight(2.9f).clientTrackingRange(10),
			PiglinGeneral.createAttributes());

	/** A vanilla ghast's box and eye height; its deck hangs below the box (see {@link WarGhast#stackBox}). */
	public static final EntityType<WarGhast> WAR_GHAST = living("war_ghast",
			EntityType.Builder.of(WarGhast::new, MobCategory.MONSTER).sized(4.0f, 4.0f).eyeHeight(2.6f)
					.fireImmune().clientTrackingRange(10),
			Ghast.createAttributes());

	/** A vanilla piglin's box, eyes and seat, as clients see one. */
	public static final EntityType<GhastBombardier> GHAST_BOMBARDIER = living("ghast_bombardier",
			EntityType.Builder.of(GhastBombardier::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.79f)
					.passengerAttachments(2.0125f).ridingOffset(-0.7f).clientTrackingRange(10),
			GhastBombardier.createAttributes());

	/**
	 * A Chained Ghast's crew member, to blows and projectiles: an interaction to clients, its size set per crew member
	 * ({@link CrewHitbox}). Moved every tick.
	 */
	public static final EntityType<CrewHitbox> CREW_HITBOX = living("crew_hitbox",
			EntityType.Builder.of(CrewHitbox::new, MobCategory.MISC).sized(0.6f, 1.95f).noSummon().noSave().clientTrackingRange(10).updateInterval(1),
			CrewHitbox.createAttributes());

	/** A Chained Ghast's deck, to projectiles: 3 blocks square, a quarter block thick, as the model's deck floor. */
	public static final EntityType<DeckHitbox> DECK_HITBOX = register("deck_hitbox",
			EntityType.Builder.<DeckHitbox>of(DeckHitbox::new, MobCategory.MISC).sized((float) WarGhast.DECK_WIDTH, (float) WarGhast.DECK_THICKNESS)
					.noSummon().noSave().clientTrackingRange(10).updateInterval(1));

	public static final EntityType<MagmaBomb> MAGMA_BOMB = register("magma_bomb",
			EntityType.Builder.<MagmaBomb>of(MagmaBomb::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10));

	private RiftEntities() {}

	public static void init() {
	}

	static <T extends LivingEntity> EntityType<T> living(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes) {
		var type = register(name, builder);
		FabricDefaultAttributeRegistry.register(type, attributes);
		return type;
	}

	static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, NetherRifts.getID(name));
		var type = builder.build(key);
		PolymerEntityUtils.registerType(type);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
