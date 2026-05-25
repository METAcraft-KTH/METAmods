package nu.metacraft.zones.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.pcollections.PVector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.spawns.BetterSpawnEntry;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;

import java.util.ArrayList;
import java.util.List;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

	@ModifyReturnValue(method = "mobsAt", at = @At("RETURN"))
	private static WeightedList<MobSpawnSettings.SpawnerData> getSpawnEntryFromZone(
			WeightedList<MobSpawnSettings.SpawnerData> original, ServerLevel world, StructureManager structureAccessor,
			ChunkGenerator chunkGenerator, MobCategory spawnGroup, BlockPos pos, @Nullable Holder<Biome> biomeEntry
	) {
		return applySpawnsAndRemovers(original, world, pos, spawnGroup);
	}

	@ModifyExpressionValue(
		method = "spawnMobsForChunkGeneration",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobs(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;"
		)
	)
	private static WeightedList<MobSpawnSettings.SpawnerData> test(
			WeightedList<MobSpawnSettings.SpawnerData> original, @Local ServerLevelAccessor world, @Local ChunkPos chunkPos
	) {
		return applySpawnsAndRemovers(
				original, world.getLevel(),
				chunkPos.getWorldPosition().atY(world.getMaxY() - 1), //This is what vanilla uses to fetch the biome, so this is no less accurate than vanilla.
				MobCategory.CREATURE
		);
	}

	@Unique
	private static WeightedList<MobSpawnSettings.SpawnerData> applySpawnsAndRemovers(
			WeightedList<MobSpawnSettings.SpawnerData> original, ServerLevel world, BlockPos pos, MobCategory spawnGroup
	) {
		MutableBoolean hasRemovers = new MutableBoolean(false);
		var zones = ZoneManager.getInstance(world.getServer()).getZonesAt(world.dimension(), pos, zone -> {
			return zone.get(ZoneDataRegistry.SPAWN).map(data -> {
				if (!data.getSpawnRemovers().isEmpty()) {
					hasRemovers.setTrue();
				}
				return data.hasSpawns() || !data.getSpawnRemovers().isEmpty();
			}).orElse(false);
		});
		if (!zones.isEmpty()) {
			List<Weighted<MobSpawnSettings.SpawnerData>> spawns = original.unwrap();
			if (hasRemovers.isTrue()) {
				Multimap<EntityType<?>, Weighted<MobSpawnSettings.SpawnerData>> spawnsMap = spawns.stream().collect(
						Multimaps.toMultimap(entry -> entry.value().type(), entry -> entry, HashMultimap::create)
				);
				for (var zone : zones) {
					zone.get(ZoneDataRegistry.SPAWN).ifPresent(spawnData -> {
						spawnData.getSpawnRemovers().forEach(blocker -> {
							blocker.removeEntities(spawnGroup, spawnsMap);
						});
					});
				}
				spawns = new ArrayList<>(spawnsMap.values());
			} else {
				spawns = new ArrayList<>(original.unwrap());
			}
			for (var zone : zones) {
				var spawnData = zone.get(ZoneDataRegistry.SPAWN).orElse(null);
				if (spawnData != null) {
					spawns.addAll((PVector<Weighted<MobSpawnSettings.SpawnerData>>) (Object) spawnData.getSpawns(spawnGroup).get());
				}
			}
			return WeightedList.of(spawns);
		}
		return original;
	}

	@Inject(
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/NaturalSpawner;isValidPositionForMob(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;D)Z"
			),
			method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V"
	)
	private static void addNBTBeforeSpawnCheck(
			MobCategory group, ServerLevel world, ChunkAccess chunk, BlockPos pos, NaturalSpawner.SpawnPredicate checker,
			NaturalSpawner.AfterSpawnCallback runner, CallbackInfo ci, @Local MobSpawnSettings.SpawnerData spawnEntry,
			@Local Mob mob
	) {
		applyNBTBeforeSpawnCheck(spawnEntry, mob);
	}

	@Inject(
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z"
			),
			method = "spawnMobsForChunkGeneration"
	)
	private static void addNBTBeforeSpawnCheck(
			ServerLevelAccessor world, Holder<Biome> biomeEntry, ChunkPos chunkPos, RandomSource random,
			CallbackInfo ci, @Local MobSpawnSettings.SpawnerData spawnEntry, @Local Mob mob
	) {
		applyNBTBeforeSpawnCheck(spawnEntry, mob);
	}

	@Unique
	private static void applyNBTBeforeSpawnCheck(MobSpawnSettings.SpawnerData spawnEntry, Mob mob) {
		//Apply nbt before spawn check to allow modified nbt to impact the spawn check.
		if (spawnEntry instanceof BetterSpawnEntry betterSpawnEntry) {
			betterSpawnEntry.applyData(mob);
		}
	}

	@WrapOperation(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;"
		),
		method = {
				"spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
				"spawnMobsForChunkGeneration"
		}
	)
	private static SpawnGroupData onSpawnEntities(
			Mob mob, ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			SpawnGroupData entityData, Operation<SpawnGroupData> initialise, @Local MobSpawnSettings.SpawnerData spawnEntry
	) {
		if (spawnEntry instanceof BetterSpawnEntry betterSpawnEntry) {
			SpawnGroupData data = null;
			if (betterSpawnEntry.shouldInitialise) {
				data = initialise.call(mob, world, difficulty, spawnReason, entityData);
				betterSpawnEntry.applyData(mob);
			}
			return data;
		}
		return initialise.call(mob, world, difficulty, spawnReason, entityData);
	}

	@WrapOperation(
		method = {
				"isValidSpawnPostitionForType(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/MobSpawnSettings$SpawnerData;Lnet/minecraft/core/BlockPos$MutableBlockPos;D)Z",
				"spawnMobsForChunkGeneration"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/SpawnPlacements;checkSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z"
		)
	)
	private static <T extends Entity> boolean canSpawn(
			EntityType<T> type, ServerLevelAccessor world, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random, Operation<Boolean> original
	) {
		var zones = ZoneManager.getInstance(world.getServer()).getZonesAt(world.getLevel().dimension(), pos, zone -> {
			return zone.get(ZoneDataRegistry.SPAWN).map(data -> {
				return !data.getSpawnRules().isEmpty();
			}).orElse(false);
		});
		for (var zone : zones) {
			if (zone.get(ZoneDataRegistry.SPAWN).isPresent()) {
				var data = zone.get(ZoneDataRegistry.SPAWN).get();
				var rule = data.getSpawnRules().find(
						checkRule -> checkRule.type().matches(type.builtInRegistryHolder())
				);
				if (rule.isPresent()) {
					return rule.get().test(type, world, spawnReason, pos, random);
				}
			}
		}
		return original.call(type, world, spawnReason, pos, random);
	}

}
