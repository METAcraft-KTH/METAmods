package se.datasektionen.mc.zones.zone.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.predicate.entity.EntityTypePredicate;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.Weighted;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.apache.commons.lang3.mutable.MutableObject;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePMap;
import org.pcollections.TreePVector;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.ThreadHelper;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.spawns.BetterSpawnEntry;
import se.datasektionen.mc.zones.spawns.SpawnRemoverRegistry;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AdditionalSpawnsZoneData extends ZoneData {

	private static final NbtTextFormatter formatter = new NbtTextFormatter("");

	public static final MapCodec<AdditionalSpawnsZoneData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(
					SpawnGroup.CODEC,
					BetterSpawnEntry.WEIGHTED_CODEC.listOf()
			).fieldOf("spawns").forGetter(data -> (PMap<SpawnGroup, List<Weighted<BetterSpawnEntry>>>) (Object) data.spawns.get()),
			SpawnRemoverRegistry.SpawnRemover.REGISTRY_CODEC.listOf().fieldOf("spawnRemovers").forGetter(data -> data.spawnRemovers.get()),
			SpawnRuleEntry.CODEC.listOf().fieldOf("spawnRules").forGetter(data -> data.rules.get())
	).apply(instance, AdditionalSpawnsZoneData::new));


	private final AtomicReference<PMap<SpawnGroup, PVector<Weighted<BetterSpawnEntry>>>> spawns;
	private final AtomicReference<PVector<SpawnRemoverRegistry.SpawnRemover>> spawnRemovers;

	private final AtomicReference<PVector<SpawnRuleEntry>> rules;


	public AdditionalSpawnsZoneData(
			Map<SpawnGroup, List<Weighted<BetterSpawnEntry>>> spawns, List<SpawnRemoverRegistry.SpawnRemover> spawnRemovers,
			List<SpawnRuleEntry> rules
	)  {
		this.spawns = new AtomicReference<>(spawns.entrySet().stream().reduce(
				TreePMap.empty(),
				(map, list) -> map.plus(
						list.getKey(), TreePVector.from(list.getValue())
				),
				TreePMap::plusAll
		));
		this.spawnRemovers = new AtomicReference<>(TreePVector.from(spawnRemovers));
		this.rules = new AtomicReference<>(TreePVector.from(rules));
	}

	public ListAccessor<Weighted<BetterSpawnEntry>> getSpawns(SpawnGroup spawnGroup) {
		return new ListAccessor<>(() -> spawns.get().getOrDefault(spawnGroup, TreePVector.empty()), l -> {
			ThreadHelper.updateAtomic(spawns, () -> {
				var list = l.get();
				if (list.isEmpty()) {
					return spawns.get().minus(spawnGroup);
				} else {
					return spawns.get().plus(spawnGroup, list);
				}
			});
			this.markDirty();
		});
	}

	public boolean hasSpawns() {
		return !spawns.get().isEmpty();
	}

	public ListAccessor<SpawnRemoverRegistry.SpawnRemover> getSpawnRemovers() {
		return new ListAccessor<>(spawnRemovers::get, list -> {
			ThreadHelper.updateAtomic(spawnRemovers, list);
			markDirty();
		});
	}

	public ListAccessor<SpawnRuleEntry> getSpawnRules() {
		return new ListAccessor<>(rules::get, list -> {
			ThreadHelper.updateAtomic(rules, list);
			markDirty();
		});
	}

	@Override
	public ZoneDataType<? extends ZoneData> getType() {
		return ZoneDataRegistry.SPAWN;
	}

	@Override
	public Text toText(RegistryWrapper.WrapperLookup lookup) {
		return CODEC.codec().encodeStart(lookup.getOps(NbtOps.INSTANCE), this).resultOrPartial(METAcraftZones.LOGGER::error).map(formatter::apply).orElse(Text.literal("Error").formatted(Formatting.RED));
	}

	public record SpawnRuleEntry(EntityTypePredicate type, LootCondition condition) {
		public static final Codec<SpawnRuleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				EntityTypePredicate.CODEC.fieldOf("type").forGetter(SpawnRuleEntry::type),
				LootCondition.CODEC.fieldOf("condition").forGetter(SpawnRuleEntry::condition)
		).apply(instance, SpawnRuleEntry::new));

		public boolean test(
				EntityType<?> entityType, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random
		) {
			return EntityHelper.canSpawn(
					world.toServerWorld(), random, entityType, condition, reason,
					pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5
			);
		}
	}

	public static class ListAccessor<T> {

		private final Supplier<PVector<T>> list;

		private final Consumer<Supplier<PVector<T>>> updater;

		public ListAccessor(Supplier<PVector<T>> list, Consumer<Supplier<PVector<T>>> updater) {
			this.list = list;
			this.updater = updater;
		}

		public void add(T element) {
			updater.accept(() -> list.get().plus(element));
		}

		public T remove(int index) {
			MutableObject<T> removed = new MutableObject<>();
			updater.accept(() -> {
				var list = this.list.get();
				removed.setValue(list.get(index));
				return list.minus(index);
			});
			return removed.getValue();
		}

		public int size() {
			return list.get().size();
		}

		public boolean isEmpty() {
			return list.get().isEmpty();
		}

		public void forEach(Consumer<T> action) {
			list.get().forEach(action);
		}

		public Optional<T> find(Predicate<T> action) {
			for (var element : list.get()) {
				if (action.test(element)) {
					return Optional.of(element);
				}
			}
			return Optional.empty();
		}

		public PVector<T> get() {
			return list.get();
		}

	}

}
