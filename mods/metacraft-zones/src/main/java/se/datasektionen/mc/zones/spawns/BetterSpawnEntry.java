package se.datasektionen.mc.zones.spawns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.collection.Weight;
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

	public static final Codec<BetterSpawnEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			entityCodec.fieldOf("entity").forGetter(entry -> entry.nbt),
			Codec.BOOL.fieldOf("shouldInitialise").forGetter(entry -> entry.shouldInitialise),
			Weight.CODEC.fieldOf("weight").forGetter(SpawnSettings.SpawnEntry::getWeight),
			Codec.INT.fieldOf("minGroupSize").forGetter(entry -> entry.minGroupSize),
			Codec.INT.fieldOf("maxGroupSize").forGetter(entry -> entry.maxGroupSize)
	).apply(instance, BetterSpawnEntry::new));

	public final NbtCompound nbt;
	public final boolean shouldInitialise;

	public BetterSpawnEntry(NbtCompound nbt, boolean shouldInitialise, Weight weight, int minGroupSize, int maxGroupSize) {
		super(EntityType.fromNbt(nbt).orElse(EntityType.PIG), weight, minGroupSize, maxGroupSize);
		this.nbt = nbt;
		this.shouldInitialise = shouldInitialise;
	}

	@Override
	public String toString() {
		return CODEC.encodeStart(NbtOps.INSTANCE, this).resultOrPartial(METAcraftZones.LOGGER::error).map(NbtElement::asString).orElse("Error");
	}

}
