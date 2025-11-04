package nu.metacraft.zones.spawns;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.zones.METAcraftZones;

public class BetterSpawnEntry extends MobSpawnSettings.SpawnerData {

	private static final Codec<EntityEntry> entityCodec = new Codec<>() {
		@Override
		public <T> DataResult<Pair<EntityEntry, T>> decode(DynamicOps<T> ops, T input) {
			return ops.get(input, "id").flatMap(
					i -> EntityType.CODEC.decode(ops, i)
			).flatMap(
					id -> CompoundTag.CODEC.decode(ops, input).map(
							data -> Pair.of(
									new EntityEntry(id.getFirst(), data.getFirst()), data.getSecond()
							)
					)
			);
		}

		@Override
		public <T> DataResult<T> encode(EntityEntry input, DynamicOps<T> ops, T prefix) {
			return CompoundTag.CODEC.encode(input.data, ops, prefix).flatMap(
					data -> EntityType.CODEC.encodeStart(ops, input.type).map(
							id -> ops.set(data, "id", id)
					)
			);
		}
	};

	protected static final MapCodec<BetterSpawnEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			entityCodec.fieldOf("entity").forGetter(entry -> entry.nbt),
			Codec.BOOL.fieldOf("shouldInitialise").forGetter(entry -> entry.shouldInitialise),
			Codec.INT.fieldOf("minGroupSize").forGetter(MobSpawnSettings.SpawnerData::minCount),
			Codec.INT.fieldOf("maxGroupSize").forGetter(MobSpawnSettings.SpawnerData::maxCount)
	).apply(instance, BetterSpawnEntry::new));

	public static final Codec<Weighted<BetterSpawnEntry>> WEIGHTED_CODEC = Weighted.codec(CODEC);

	public final EntityEntry nbt;
	public final boolean shouldInitialise;
	
	public BetterSpawnEntry(EntityEntry nbt, boolean shouldInitialise, int minGroupSize, int maxGroupSize) {
		super(nbt.type, minGroupSize, maxGroupSize);
		this.nbt = nbt;
		this.shouldInitialise = shouldInitialise;
	}

	public void applyData(Mob mob) {
		var lookup = mob.registryAccess();
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:BetterSpawnEntry#applyData", METAcraftZones.LOGGER)) {
			var writeView = TagValueOutput.createWithContext(logging, lookup);
			mob.saveWithoutId(writeView); //Apply nbt again after initialisation since initialisation might remove stuff.
			var nbt = writeView.buildResult();
			nbt.merge(this.nbt.data());
			var readView = TagValueInput.create(logging, lookup, nbt);
			mob.load(readView);
		}
	}

	@Override
	public String toString() {
		return CODEC.codec().encodeStart(NbtOps.INSTANCE, this).resultOrPartial(METAcraftZones.LOGGER::error).map(Tag::toString).orElse("Error");
	}

	public static String toString(Weighted<BetterSpawnEntry> entry) {
		return WEIGHTED_CODEC.encodeStart(NbtOps.INSTANCE, entry).resultOrPartial(METAcraftZones.LOGGER::error).map(Tag::toString).orElse("Error");
	}
	
	public record EntityEntry(EntityType<?> type, CompoundTag data) {}

}
