package se.datasektionen.mc.zones.mixin;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.zones.ZoneManager;
import se.datasektionen.mc.zones.spawns.BetterSpawnEntry;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;

import java.util.ArrayList;

@Mixin(SpawnHelper.class)
public abstract class MixinSpawnHelper {

	@ModifyReturnValue(method = "getSpawnEntries", at = @At("RETURN"))
	private static Pool<SpawnSettings.SpawnEntry> getSpawnEntryFromZone(
			Pool<SpawnSettings.SpawnEntry> original, ServerWorld world, StructureAccessor structureAccessor,
			ChunkGenerator chunkGenerator, SpawnGroup spawnGroup, BlockPos pos, @Nullable RegistryEntry<Biome> biomeEntry
	) {
		return applySpawnsAndRemovers(original, world, pos, spawnGroup);
	}

	@ModifyExpressionValue(
		method = "populateEntities",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/biome/SpawnSettings;getSpawnEntries(Lnet/minecraft/entity/SpawnGroup;)Lnet/minecraft/util/collection/Pool;"
		)
	)
	private static Pool<SpawnSettings.SpawnEntry> test(
			Pool<SpawnSettings.SpawnEntry> original, @Local ServerWorldAccess world, @Local ChunkPos chunkPos
	) {
		return applySpawnsAndRemovers(
				original, world.toServerWorld(),
				chunkPos.getStartPos().withY(world.getTopY() - 1), //This is what vanilla uses to fetch the biome, so this is no less accurate than vanilla.
				SpawnGroup.CREATURE
		);
	}

	@Unique
	private static Pool<SpawnSettings.SpawnEntry> applySpawnsAndRemovers(
			Pool<SpawnSettings.SpawnEntry> original, ServerWorld world, BlockPos pos, SpawnGroup spawnGroup
	) {
		MutableBoolean hasRemovers = new MutableBoolean(false);
		var zones = ZoneManager.getInstance(world.getServer()).getZonesAt(world.getRegistryKey(), pos, zone -> {
			return zone.get(ZoneDataRegistry.SPAWN).map(data -> {
				if (!data.getSpawnRemovers().isEmpty()) {
					hasRemovers.setTrue();
				}
				return data.hasSpawns() || !data.getSpawnRemovers().isEmpty();
			}).orElse(false);
		});
		if (!zones.isEmpty()) {
			var spawns = original.getEntries();
			if (hasRemovers.isTrue()) {
				Multimap<EntityType<?>, SpawnSettings.SpawnEntry> spawnsMap = spawns.stream().collect(
						Multimaps.toMultimap(entry -> entry.type, entry -> entry, HashMultimap::create)
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
				spawns = new ArrayList<>(original.getEntries());
			}
			for (var zone : zones) {
				var spawnData = zone.get(ZoneDataRegistry.SPAWN).orElse(null);
				if (spawnData != null) {
					spawnData.getSpawns(spawnGroup).addAllTo(spawns);
				}
			}
			return Pool.of(spawns);
		}
		return original;
	}

	@Inject(
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/SpawnHelper;isValidSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/MobEntity;D)Z"
			),
			method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V"
	)
	private static void addNBTBeforeSpawnCheck(
			SpawnGroup group, ServerWorld world, Chunk chunk, BlockPos pos, SpawnHelper.Checker checker,
			SpawnHelper.Runner runner, CallbackInfo ci, @Local SpawnSettings.SpawnEntry spawnEntry,
			@Local MobEntity mob
	) {
		applyNBTBeforeSpawnCheck(spawnEntry, mob);
	}

	@Inject(
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/mob/MobEntity;canSpawn(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;)Z"
			),
			method = "populateEntities"
	)
	private static void addNBTBeforeSpawnCheck(
			ServerWorldAccess world, RegistryEntry<Biome> biomeEntry, ChunkPos chunkPos, Random random,
			CallbackInfo ci, @Local SpawnSettings.SpawnEntry spawnEntry, @Local MobEntity mob
	) {
		applyNBTBeforeSpawnCheck(spawnEntry, mob);
	}

	@Unique
	private static void applyNBTBeforeSpawnCheck(SpawnSettings.SpawnEntry spawnEntry, MobEntity mob) {
		//Apply nbt before spawn check to allow modified nbt to impact the spawn check.
		if (spawnEntry instanceof BetterSpawnEntry betterSpawnEntry) {
			var nbt = mob.writeNbt(new NbtCompound());
			nbt.copyFrom(betterSpawnEntry.nbt);
			mob.readNbt(nbt);
		}
	}

	@WrapOperation(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/mob/MobEntity;initialize(Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/world/LocalDifficulty;Lnet/minecraft/entity/SpawnReason;Lnet/minecraft/entity/EntityData;)Lnet/minecraft/entity/EntityData;"
		),
		method = {
				"spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
				"populateEntities"
		}
	)
	private static EntityData onSpawnEntities(
			MobEntity mob, ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			EntityData entityData, Operation<EntityData> initialise, @Local SpawnSettings.SpawnEntry spawnEntry
	) {
		if (spawnEntry instanceof BetterSpawnEntry betterSpawnEntry) {
			EntityData data = null;
			if (betterSpawnEntry.shouldInitialise) {
				data = initialise.call(mob, world, difficulty, spawnReason, entityData);
				var nbt = mob.writeNbt(new NbtCompound()); //Apply nbt again after initialisation since initialisation might remove stuff.
				nbt.copyFrom(betterSpawnEntry.nbt);
				mob.readNbt(nbt);
			}
			return data;
		}
		return initialise.call(mob, world, difficulty, spawnReason, entityData);
	}

	@WrapOperation(
		method = {
				"canSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/SpawnSettings$SpawnEntry;Lnet/minecraft/util/math/BlockPos$Mutable;D)Z",
				"populateEntities"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/SpawnRestriction;canSpawn(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/ServerWorldAccess;Lnet/minecraft/entity/SpawnReason;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)Z"
		)
	)
	private static <T extends Entity> boolean canSpawn(
			EntityType<T> type, ServerWorldAccess world, SpawnReason spawnReason, BlockPos pos, Random random, Operation<Boolean> original
	) {
		var zones = ZoneManager.getInstance(world.getServer()).getZonesAt(world.toServerWorld().getRegistryKey(), pos, zone -> {
			return zone.get(ZoneDataRegistry.SPAWN).map(data -> {
				return !data.getSpawnRules().isEmpty();
			}).orElse(false);
		});
		for (var zone : zones) {
			if (zone.get(ZoneDataRegistry.SPAWN).isPresent()) {
				var data = zone.get(ZoneDataRegistry.SPAWN).get();
				var rule = data.getSpawnRules().find(
						checkRule -> checkRule.type().matches(type)
				);
				if (rule.isPresent()) {
					return rule.get().test(type, world, spawnReason, pos, random);
				}
			}
		}
		return original.call(type, world, spawnReason, pos, random);
	}

}
