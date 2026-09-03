package se.metacraft.portalopening.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProviders;
import se.metacraft.portalopening.rifts.PortalRift;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;

public record AutoSpawnEntry(MobEntry entry, IntProvider spawnDelay, int maxMobs) {

	public static final Codec<AutoSpawnEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MobEntry.CODEC.fieldOf("mob").forGetter(AutoSpawnEntry::entry),
			IntProviders.NON_NEGATIVE_CODEC.fieldOf("spawnDelay").forGetter(AutoSpawnEntry::spawnDelay),
			Codec.INT.fieldOf("maxMobs").forGetter(AutoSpawnEntry::maxMobs)
	).apply(instance, AutoSpawnEntry::new));

	public void spawnMobsFromNBT(ServerLevel world, BlockPos pos, PortalRift rift) {
		spawnMobsFromNBT(world, pos, rift, e -> {});
	}

	public void spawnMobsFromNBT(ServerLevel world, BlockPos pos, PortalRift rift, Consumer<Entity> entityModifier) {
		entry.spawnMobsFromNBT(world, pos, e -> {
			rift.addEntity(e);
			entityModifier.accept(e);
		});
	}
}
