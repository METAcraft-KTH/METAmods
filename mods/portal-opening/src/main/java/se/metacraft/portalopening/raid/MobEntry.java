package se.metacraft.portalopening.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;

public record MobEntry(WeightedList<EntityEntry> mobs, IntProvider amountPerSpawn, double probabilityToSpawnOtherRift, Optional<IntProvider> amountPerSpawnOtherRifts) {

	private static final Codec<WeightedList<EntityEntry>> ENTITIES_WEIGHTED = WeightedList.codec(EntityEntry.CODEC);
	public static final Codec<MobEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ENTITIES_WEIGHTED.fieldOf("mobs").forGetter(MobEntry::mobs),
			IntProvider.NON_NEGATIVE_CODEC.fieldOf("amountPerSpawn").forGetter(MobEntry::amountPerSpawn),
			Codec.DOUBLE.fieldOf("probabilityToSpawnOtherRift").orElse(1.0).forGetter(MobEntry::probabilityToSpawnOtherRift),
			IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("amountPerSpawnOtherRifts").forGetter(MobEntry::amountPerSpawnOtherRifts)
	).apply(instance, MobEntry::new));

	public void spawnMobsFromNBT(ServerLevel world, BlockPos pos) {
		spawnMobsFromNBT(world, pos, entity -> {});
	}

	public void spawnMobsFromNBT(ServerLevel world, BlockPos pos, Consumer<Entity> entityModifier) {
		mobs.getRandom(world.getRandom()).ifPresent(data -> {
			data.spawnMobFromNBT(world, pos, entityModifier);
		});
	}

	public record EntityEntry(CompoundTag data, Optional<Identifier> function, List<EntityEntry> leashedEntities) {
		private static final Codec<CompoundTag> ENTITY_CODEC = CompoundTag.CODEC.flatXmap(nbt -> {
			return nbt.read("id", EntityType.CODEC).map(type -> DataResult.success(nbt)).orElseGet(
					() -> {
						if (nbt.size() == 0) {
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

		public static EntityEntry fromData(CompoundTag data) {
			return new EntityEntry(data, Optional.empty(), List.of());
		}

		@Nullable
		public Entity spawnMobFromNBT(ServerLevel world, BlockPos pos, Consumer<Entity> entityModifier) {
			if (data.size() == 0) {
				return null;
			}
			var spawnedEntity = EntityType.loadEntityRecursive(data, world, EntitySpawnReason.EVENT,entity -> {
				entity.snapTo(pos, entity.getYRot(), entity.getXRot());
				function.flatMap(
						func -> world.getServer().getFunctions().get(func)
				).ifPresent(function -> {
					world.getServer().getFunctions().execute(
							function, entity.createCommandSourceStackForNameResolution(world).withPermission(2)
					);
				});
				entityModifier.accept(entity);
				world.addFreshEntity(entity);
				entity.setPortalCooldown();
				return entity;
			});

			for (var target : leashedEntities) {
				var entity = target.spawnMobFromNBT(world, pos, entityModifier);
				if (entity instanceof Leashable l) {
					l.setLeashedTo(spawnedEntity, true);
				}
			}

			return spawnedEntity;
		}

	}

}
