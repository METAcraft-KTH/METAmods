package nu.metacraft.bosses.boss.attacks;

import com.mojang.serialization.JavaOps;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.entity.EntityTypePredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.condition.conditions.NotInWall;
import nu.metacraft.lib.condition.conditions.ValidateSpawnPredicate;
import nu.metacraft.lib.condition.conditions.ValidateSpawnRestriction;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.mixin.LivingEntityAccessor;

import java.util.*;
import java.util.function.*;

public abstract class SpawnEntityAttackBase extends InstantAttack {

	public static final EntityPredicate PLAYER_PREDICATE = EntityPredicate.Builder.entity().entityType(EntityTypePredicate.of(BuiltInRegistries.ENTITY_TYPE, EntityTypes.PLAYER)).build();

	protected final WeightedList<EntityHelper.SpawnEntry> entities;

	public SpawnEntityAttackBase(WeightedList<EntityHelper.SpawnEntry> entities) {
		this.entities = entities;
	}

	public static Map<String, Object> createPotions(MobEffectInstance... effects) {
		return Map.of(
				LivingEntityAccessor.getActiveEffectsKey(),
				MobEffectInstance.CODEC.listOf().encodeStart(JavaOps.INSTANCE, Arrays.stream(effects).toList()).resultOrPartial(
						METAcraftBosses.LOGGER::error
				).orElse(new ArrayList<>())
		);
	}

	public static Map<String, Object> customName(Component name, boolean alwaysVisible, HolderLookup.Provider lookup) {
		return Map.of(
			"CustomName", ComponentSerialization.CODEC.encodeStart(
					lookup.createSerializationContext(JavaOps.INSTANCE), name
				).getOrThrow(),
			"CustomNameVisible", alwaysVisible
		);
	}

	@SafeVarargs
	public static Map<String, Object> merge(Map<String, Object>... maps) {
		return Arrays.stream(maps).collect(HashMap::new, HashMap::putAll, HashMap::putAll);
	}

	public static CompoundTag createNBTFromMap(Map<String, Object> map) {
		return CompoundTag.CODEC.parse(JavaOps.INSTANCE, map).resultOrPartial(METAcraftBosses.LOGGER::error).orElse(new CompoundTag());
	}

	public static <T extends Entity> CompoundTag createEntityNBTFrom(EntityType<T> type) {
		return createEntityNBTFrom(type, new CompoundTag());
	}

	public static <T extends Entity> CompoundTag createEntityNBTFrom(EntityType<T> type, CompoundTag data) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		data.putString("id", id.toString());
		return data;
	}

	protected void spawnEntity(
			BossContext<?> ctx, Vec3 around,
			Function<Entity, Optional<Entity>> getTarget
	) {
		var data = entities.getRandomOrThrow(ctx.random());
		EntityHelper.spawnEntity(
				data, e -> true, e -> true, around, ctx.getWorld(), ctx.random(), ctx.boss(), getTarget
		);
	}

	protected void spawnEntity(
			BossContext<?> ctx, Vec3 around,
			Predicate<EntityType<?>> canSpawnCheck1, Predicate<Entity> canSpawnCheck2,
			Function<Entity, Optional<Entity>> getTarget
	) {
		var data = entities.getRandomOrThrow(ctx.random());
		EntityHelper.spawnEntity(
				data, canSpawnCheck1, canSpawnCheck2, around, ctx.getWorld(), ctx.random(), ctx.boss(), getTarget
		);
	}

	public static EntityHelper.SpawnEntry createEntry(
			CompoundTag entity, int horizontalRange, int verticalRange
	) {
		return createEntry(entity, true, false, horizontalRange, verticalRange);
	}

	public static EntityHelper.SpawnEntry createEntry(
			CompoundTag entity, Optional<LootItemCondition> spawnCondition, EntitySpawnReason spawnReason, int horizontalRange, int verticalRange
	) {
		return new EntityHelper.SpawnEntry(
				entity, true, false,
				new EntityHelper.SpawnEntry.SpawnRules(
						spawnCondition, spawnReason,
						UniformInt.of(-horizontalRange, horizontalRange),
						UniformInt.of(verticalRange, verticalRange)
				),
				Optional.empty()
		);
	}

	public static EntityHelper.SpawnEntry createEntry(
			CompoundTag entity, boolean initialize, boolean preventDespawn, int horizontalRange, int verticalRange
	) {
		return createEntry(
				entity, initialize, preventDespawn, Optional.of(AllOfCondition.allOf(
						List.of(
								NotInWall.getInstance(), ValidateSpawnRestriction.getInstance(),
								new ValidateSpawnPredicate(Optional.empty())
						)
				)), EntitySpawnReason.TRIAL_SPAWNER, horizontalRange, verticalRange
		);
	}

	public static EntityHelper.SpawnEntry createEntry(
			CompoundTag entity, boolean initialize, boolean preventDespawn,
			Optional<LootItemCondition> spawnCondition, EntitySpawnReason spawnReason, int horizontalRange, int verticalRange
	) {
		return new EntityHelper.SpawnEntry(
				entity, initialize, preventDespawn,
				new EntityHelper.SpawnEntry.SpawnRules(
						spawnCondition, spawnReason,
						UniformInt.of(-horizontalRange, horizontalRange),
						UniformInt.of(verticalRange, verticalRange)
				),
				Optional.empty()
		);
	}
}
