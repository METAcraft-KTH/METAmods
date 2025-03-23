package se.datasektionen.mc.zones.spawns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.collection.Weighted;
import net.minecraft.world.biome.SpawnSettings;
import se.datasektionen.mc.zones.METAcraftZones;

public class BetterSpawnEntry extends SpawnSettings.SpawnEntry {

	private static final Codec<NbtCompound> entityCodec = NbtCompound.CODEC.flatXmap(
			nbt -> {
				if (EntityType.fromNbt(nbt).isPresent()) {
					return DataResult.success(nbt);
				} else {
					return DataResult.error(() -> "The nbt " + nbt + " does not contain a valid entity id.");
				}
			}, DataResult::success
	);

	protected static final MapCodec<BetterSpawnEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			entityCodec.fieldOf("entity").forGetter(entry -> entry.nbt),
			Codec.BOOL.fieldOf("shouldInitialise").forGetter(entry -> entry.shouldInitialise),
			Codec.INT.fieldOf("minGroupSize").forGetter(SpawnSettings.SpawnEntry::minGroupSize),
			Codec.INT.fieldOf("maxGroupSize").forGetter(SpawnSettings.SpawnEntry::maxGroupSize)
	).apply(instance, BetterSpawnEntry::new));

	public static final Codec<Weighted<BetterSpawnEntry>> WEIGHTED_CODEC = Weighted.createCodec(CODEC);

	public final NbtCompound nbt;
	public final boolean shouldInitialise;

	public BetterSpawnEntry(NbtCompound nbt, boolean shouldInitialise, int minGroupSize, int maxGroupSize) {
		super(EntityType.fromNbt(nbt).orElse(EntityType.PIG), minGroupSize, maxGroupSize);
		this.nbt = nbt;
		this.shouldInitialise = shouldInitialise;
	}

	@Override
	public String toString() {
		return CODEC.codec().encodeStart(NbtOps.INSTANCE, this).resultOrPartial(METAcraftZones.LOGGER::error).map(NbtElement::toString).orElse("Error");
	}

	public static String toString(Weighted<BetterSpawnEntry> entry) {
		return WEIGHTED_CODEC.encodeStart(NbtOps.INSTANCE, entry).resultOrPartial(METAcraftZones.LOGGER::error).map(NbtElement::toString).orElse("Error");
	}

}
