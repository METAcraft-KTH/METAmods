package se.datasektionen.mc.portalopening.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.World;
import se.datasektionen.mc.portalopening.rifts.PortalRift;

import java.util.function.Consumer;

public record AutoSpawnEntry(MobEntry entry, IntProvider spawnDelay, int maxMobs) {

	public static final Codec<AutoSpawnEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MobEntry.CODEC.fieldOf("mob").forGetter(AutoSpawnEntry::entry),
			IntProvider.NON_NEGATIVE_CODEC.fieldOf("spawnDelay").forGetter(AutoSpawnEntry::spawnDelay),
			Codec.INT.fieldOf("maxMobs").forGetter(AutoSpawnEntry::maxMobs)
	).apply(instance, AutoSpawnEntry::new));

	public void spawnMobsFromNBT(World world, BlockPos pos, PortalRift rift) {
		spawnMobsFromNBT(world, pos, rift, e -> {});
	}

	public void spawnMobsFromNBT(World world, BlockPos pos, PortalRift rift, Consumer<Entity> entityModifier) {
		entry.spawnMobsFromNBT(world, pos, e -> {
			rift.addEntity(e);
			entityModifier.accept(e);
		});
	}
}
