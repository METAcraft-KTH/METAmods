package se.datasektionen.mc.zones.spawns;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.collection.Weighted;
import net.minecraft.world.biome.SpawnSettings;
import se.datasektionen.mc.metacraft_lib.util.error_reporters.LoggingErrorReporter;
import se.datasektionen.mc.zones.METAcraftZones;

public class BetterSpawnEntry extends SpawnSettings.SpawnEntry {

	private static final Codec<EntityEntry> entityCodec = new Codec<>() {
		@Override
		public <T> DataResult<Pair<EntityEntry, T>> decode(DynamicOps<T> ops, T input) {
			return ops.get(input, "id").flatMap(
					i -> EntityType.CODEC.decode(ops, i)
			).flatMap(
					id -> NbtCompound.CODEC.decode(ops, input).map(
							data -> Pair.of(
									new EntityEntry(id.getFirst(), data.getFirst()), data.getSecond()
							)
					)
			);
		}

		@Override
		public <T> DataResult<T> encode(EntityEntry input, DynamicOps<T> ops, T prefix) {
			return NbtCompound.CODEC.encode(input.data, ops, prefix).flatMap(
					data -> EntityType.CODEC.encodeStart(ops, input.type).map(
							id -> ops.set(data, "id", id)
					)
			);
		}
	};

	protected static final MapCodec<BetterSpawnEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			entityCodec.fieldOf("entity").forGetter(entry -> entry.nbt),
			Codec.BOOL.fieldOf("shouldInitialise").forGetter(entry -> entry.shouldInitialise),
			Codec.INT.fieldOf("minGroupSize").forGetter(SpawnSettings.SpawnEntry::minGroupSize),
			Codec.INT.fieldOf("maxGroupSize").forGetter(SpawnSettings.SpawnEntry::maxGroupSize)
	).apply(instance, BetterSpawnEntry::new));

	public static final Codec<Weighted<BetterSpawnEntry>> WEIGHTED_CODEC = Weighted.createCodec(CODEC);

	public final EntityEntry nbt;
	public final boolean shouldInitialise;
	
	public BetterSpawnEntry(EntityEntry nbt, boolean shouldInitialise, int minGroupSize, int maxGroupSize) {
		super(nbt.type, minGroupSize, maxGroupSize);
		this.nbt = nbt;
		this.shouldInitialise = shouldInitialise;
	}

	public void applyData(MobEntity mob) {
		var lookup = mob.getRegistryManager();
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:BetterSpawnEntry#applyData", METAcraftZones.LOGGER)) {
			var writeView = NbtWriteView.create(logging, lookup);
			mob.writeData(writeView); //Apply nbt again after initialisation since initialisation might remove stuff.
			var nbt = writeView.getNbt();
			nbt.copyFrom(this.nbt.data());
			var readView = NbtReadView.create(logging, lookup, nbt);
			mob.readData(readView);
		}
	}

	@Override
	public String toString() {
		return CODEC.codec().encodeStart(NbtOps.INSTANCE, this).resultOrPartial(METAcraftZones.LOGGER::error).map(NbtElement::toString).orElse("Error");
	}

	public static String toString(Weighted<BetterSpawnEntry> entry) {
		return WEIGHTED_CODEC.encodeStart(NbtOps.INSTANCE, entry).resultOrPartial(METAcraftZones.LOGGER::error).map(NbtElement::toString).orElse("Error");
	}
	
	public record EntityEntry(EntityType<?> type, NbtCompound data) {}

}
