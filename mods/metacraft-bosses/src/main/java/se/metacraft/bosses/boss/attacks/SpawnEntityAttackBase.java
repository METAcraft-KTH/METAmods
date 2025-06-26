package se.metacraft.bosses.boss.attacks;

import com.mojang.serialization.JavaOps;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.EntityTypePredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import se.datasektionen.mc.metacraft_lib.condition.conditions.NotInWall;
import se.datasektionen.mc.metacraft_lib.condition.conditions.ValidateSpawnPredicate;
import se.datasektionen.mc.metacraft_lib.condition.conditions.ValidateSpawnRestriction;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.metacraft.bosses.METAcraftBosses;
import se.metacraft.bosses.mixin.AccessorLivingEntity;

import java.util.*;
import java.util.function.*;

public abstract class SpawnEntityAttackBase extends InstantAttack {

	public static final EntityPredicate PLAYER_PREDICATE = EntityPredicate.Builder.create().type(EntityTypePredicate.create(Registries.ENTITY_TYPE, EntityType.PLAYER)).build();

	protected final Pool<EntityHelper.SpawnEntry> entities;

	public SpawnEntityAttackBase(Pool<EntityHelper.SpawnEntry> entities) {
		this.entities = entities;
	}

	public static Map<String, Object> createPotions(StatusEffectInstance... effects) {
		return Map.of(
				AccessorLivingEntity.getActiveEffectsKey(),
				StatusEffectInstance.CODEC.listOf().encodeStart(JavaOps.INSTANCE, Arrays.stream(effects).toList()).resultOrPartial(
						METAcraftBosses.LOGGER::error
				).orElse(new ArrayList<>())
		);
	}

	public static Map<String, Object> customName(Text name, boolean alwaysVisible, RegistryWrapper.WrapperLookup lookup) {
		return Map.of(
			"CustomName", TextCodecs.CODEC.encodeStart(
					lookup.getOps(JavaOps.INSTANCE), name
				).getOrThrow(),
			"CustomNameVisible", alwaysVisible
		);
	}

	@SafeVarargs
	public static Map<String, Object> merge(Map<String, Object>... maps) {
		return Arrays.stream(maps).collect(HashMap::new, HashMap::putAll, HashMap::putAll);
	}

	public static NbtCompound createNBTFromMap(Map<String, Object> map) {
		return NbtCompound.CODEC.parse(JavaOps.INSTANCE, map).resultOrPartial(METAcraftBosses.LOGGER::error).orElse(new NbtCompound());
	}

	public static <T extends Entity> NbtCompound createEntityNBTFrom(EntityType<T> type) {
		return createEntityNBTFrom(type, new NbtCompound());
	}

	public static <T extends Entity> NbtCompound createEntityNBTFrom(EntityType<T> type, NbtCompound data) {
		Identifier id = Registries.ENTITY_TYPE.getId(type);
		data.putString("id", id.toString());
		return data;
	}

	protected void spawnEntity(
			BossContext<?> ctx, Vec3d around,
			Function<Entity, Optional<Entity>> getTarget
	) {
		var data = entities.get(ctx.random());
		EntityHelper.spawnEntity(
				data, e -> true, e -> true, around, ctx.getWorld(), ctx.random(), ctx.boss(), getTarget
		);
	}

	protected void spawnEntity(
			BossContext<?> ctx, Vec3d around,
			Predicate<EntityType<?>> canSpawnCheck1, Predicate<Entity> canSpawnCheck2,
			Function<Entity, Optional<Entity>> getTarget
	) {
		var data = entities.get(ctx.random());
		EntityHelper.spawnEntity(
				data, canSpawnCheck1, canSpawnCheck2, around, ctx.getWorld(), ctx.random(), ctx.boss(), getTarget
		);
	}

	public static EntityHelper.SpawnEntry createEntry(
			NbtCompound entity, int horizontalRange, int verticalRange
	) {
		return createEntry(entity, true, false, horizontalRange, verticalRange);
	}

	public static EntityHelper.SpawnEntry createEntry(
			NbtCompound entity, Optional<LootCondition> spawnCondition, SpawnReason spawnReason, int horizontalRange, int verticalRange
	) {
		return new EntityHelper.SpawnEntry(
				entity, true, false,
				new EntityHelper.SpawnEntry.SpawnRules(
						spawnCondition, spawnReason,
						UniformIntProvider.create(-horizontalRange, horizontalRange),
						UniformIntProvider.create(verticalRange, verticalRange)
				),
				Optional.empty()
		);
	}

	public static EntityHelper.SpawnEntry createEntry(
			NbtCompound entity, boolean initialize, boolean preventDespawn, int horizontalRange, int verticalRange
	) {
		return createEntry(
				entity, initialize, preventDespawn, Optional.of(AllOfLootCondition.create(
						List.of(
								NotInWall.getInstance(), ValidateSpawnRestriction.getInstance(),
								new ValidateSpawnPredicate(Optional.empty())
						)
				)), SpawnReason.TRIAL_SPAWNER, horizontalRange, verticalRange
		);
	}

	public static EntityHelper.SpawnEntry createEntry(
			NbtCompound entity, boolean initialize, boolean preventDespawn,
			Optional<LootCondition> spawnCondition, SpawnReason spawnReason, int horizontalRange, int verticalRange
	) {
		return new EntityHelper.SpawnEntry(
				entity, initialize, preventDespawn,
				new EntityHelper.SpawnEntry.SpawnRules(
						spawnCondition, spawnReason,
						UniformIntProvider.create(-horizontalRange, horizontalRange),
						UniformIntProvider.create(verticalRange, verticalRange)
				),
				Optional.empty()
		);
	}
}
