package se.metacraft.portalopening.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.IntProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public record MobEntry(Pool<EntityEntry> mobs, IntProvider amountPerSpawn, double probabilityToSpawnOtherRift, Optional<IntProvider> amountPerSpawnOtherRifts) {

	private static final Codec<Pool<EntityEntry>> ENTITIES_WEIGHTED = Pool.createCodec(EntityEntry.CODEC);
	public static final Codec<MobEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ENTITIES_WEIGHTED.fieldOf("mobs").forGetter(MobEntry::mobs),
			IntProvider.NON_NEGATIVE_CODEC.fieldOf("amountPerSpawn").forGetter(MobEntry::amountPerSpawn),
			Codec.DOUBLE.fieldOf("probabilityToSpawnOtherRift").orElse(1.0).forGetter(MobEntry::probabilityToSpawnOtherRift),
			IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("amountPerSpawnOtherRifts").forGetter(MobEntry::amountPerSpawnOtherRifts)
	).apply(instance, MobEntry::new));

	public void spawnMobsFromNBT(ServerWorld world, BlockPos pos) {
		spawnMobsFromNBT(world, pos, entity -> {});
	}

	public void spawnMobsFromNBT(ServerWorld world, BlockPos pos, Consumer<Entity> entityModifier) {
		mobs.getOrEmpty(world.getRandom()).ifPresent(data -> {
			data.spawnMobFromNBT(world, pos, entityModifier);
		});
	}

	public record EntityEntry(NbtCompound data, Optional<Identifier> function, List<EntityEntry> leashedEntities) {
		private static final Codec<NbtCompound> ENTITY_CODEC = NbtCompound.CODEC.flatXmap(nbt -> {
			return nbt.get("id", EntityType.CODEC).map(type -> DataResult.success(nbt)).orElseGet(
					() -> {
						if (nbt.getSize() == 0) {
							return DataResult.success(nbt);
						} else {
							return DataResult.error(
									() -> "Invalid entity " + nbt.asString() + ", please specify the entity id in the \"id\" parameter."
							);
						}
					}
			);
		}, DataResult::success);

		public static final Codec<EntityEntry> CODEC = Codec.recursive(
				"metacraft-portal-blocker:entity-entry", entityEntryCodec -> Codec.withAlternative(
						RecordCodecBuilder.create(
								instance -> instance.group(
										ENTITY_CODEC.fieldOf("entity_data").forGetter(EntityEntry::data),
										Identifier.CODEC.optionalFieldOf("function").forGetter(EntityEntry::function),
										entityEntryCodec.listOf().optionalFieldOf("leashed_entities", List.of()).forGetter(EntityEntry::leashedEntities)
								).apply(instance, EntityEntry::new)
						),
						ENTITY_CODEC.xmap(
								EntityEntry::fromData,
								entry -> entry.data
						)
				)
		);

		public static EntityEntry fromData(NbtCompound data) {
			return new EntityEntry(data, Optional.empty(), List.of());
		}

		@Nullable
		public Entity spawnMobFromNBT(ServerWorld world, BlockPos pos, Consumer<Entity> entityModifier) {
			if (data.getSize() == 0) {
				return null;
			}
			var spawnedEntity = EntityType.loadEntityWithPassengers(data, world, SpawnReason.EVENT,entity -> {
				entity.refreshPositionAndAngles(pos, entity.getYaw(), entity.getPitch());
				function.flatMap(
						func -> world.getServer().getCommandFunctionManager().getFunction(func)
				).ifPresent(function -> {
					world.getServer().getCommandFunctionManager().execute(
							function, entity.getCommandSource(world).withLevel(2)
					);
				});
				entityModifier.accept(entity);
				world.spawnEntity(entity);
				entity.resetPortalCooldown();
				return entity;
			});

			for (var target : leashedEntities) {
				var entity = target.spawnMobFromNBT(world, pos, entityModifier);
				if (entity instanceof Leashable l) {
					l.attachLeash(spawnedEntity, true);
				}
			}

			return spawnedEntity;
		}

	}

}
