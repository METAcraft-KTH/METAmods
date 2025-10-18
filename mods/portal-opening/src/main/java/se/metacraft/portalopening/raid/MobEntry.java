package se.metacraft.portalopening.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Consumer;

public record MobEntry(Pool<NbtCompound> mobs, IntProvider amountPerSpawn, double probabilityToSpawnOtherRift, Optional<IntProvider> amountPerSpawnOtherRifts) {
	private static final Codec<NbtCompound> entityCodec = NbtCompound.CODEC.flatXmap(nbt -> {
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
	private static final Codec<Pool<NbtCompound>> NBT_WEIGHTED = Pool.createCodec(entityCodec);
	public static final Codec<MobEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			NBT_WEIGHTED.fieldOf("mobs").forGetter(MobEntry::mobs),
			IntProvider.NON_NEGATIVE_CODEC.fieldOf("amountPerSpawn").forGetter(MobEntry::amountPerSpawn),
			Codec.DOUBLE.fieldOf("probabilityToSpawnOtherRift").orElse(1.0).forGetter(MobEntry::probabilityToSpawnOtherRift),
			IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("amountPerSpawnOtherRifts").forGetter(MobEntry::amountPerSpawnOtherRifts)
	).apply(instance, MobEntry::new));

	public void spawnMobsFromNBT(World world, BlockPos pos) {
		spawnMobsFromNBT(world, pos, entity -> {});
	}

	public void spawnMobsFromNBT(World world, BlockPos pos, Consumer<Entity> entityModifier) {
		mobs.getOrEmpty(world.getRandom()).ifPresent(mobNBT -> {
			if (mobNBT.getSize() == 0) {
				return;
			}
			EntityType.loadEntityWithPassengers(mobNBT, world, SpawnReason.EVENT,entity -> {
				entity.refreshPositionAndAngles(pos, entity.getYaw(), entity.getPitch());
				entityModifier.accept(entity);
				world.spawnEntity(entity);
				entity.resetPortalCooldown();
				return entity;
			});
		});
	}

}
