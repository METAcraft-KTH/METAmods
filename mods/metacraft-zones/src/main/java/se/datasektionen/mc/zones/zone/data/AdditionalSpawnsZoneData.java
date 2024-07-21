package se.datasektionen.mc.zones.zone.data;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.datafixers.util.Pair;
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
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.spawns.BetterSpawnEntry;
import se.datasektionen.mc.zones.spawns.SpawnRemoverRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AdditionalSpawnsZoneData extends ZoneData {

	private static final NbtTextFormatter formatter = new NbtTextFormatter("");

	private static final MapCodec<Multimap<SpawnGroup, BetterSpawnEntry>> SPAWN_ENTRY_MAP_CODEC = Codec.simpleMap(
			SpawnGroup.CODEC,
			BetterSpawnEntry.CODEC.listOf(),
			StringIdentifiable.toKeyable(SpawnGroup.values())
	).xmap(map -> {
		var multimap = HashMultimap.<SpawnGroup, BetterSpawnEntry>create();
		for (var entry : map.entrySet()) {
			multimap.putAll(entry.getKey(), entry.getValue());
		}
		return multimap;
	}, multimap -> {
		return multimap.asMap().entrySet().stream().map(
				entry -> Pair.of(
						entry.getKey(),
						new ArrayList<>(entry.getValue())
				)
		).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	});

	public static final MapCodec<AdditionalSpawnsZoneData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			SPAWN_ENTRY_MAP_CODEC.fieldOf("spawns").forGetter(data -> data.spawns),
			SpawnRemoverRegistry.SpawnRemover.REGISTRY_CODEC.listOf().fieldOf("spawnRemovers").forGetter(data -> data.spawnRemovers),
			SpawnRuleEntry.CODEC.listOf().fieldOf("spawnRules").forGetter(data -> data.rules)
	).apply(instance, AdditionalSpawnsZoneData::new));


	private final ListMultimap<SpawnGroup, BetterSpawnEntry> spawns;
	private final List<SpawnRemoverRegistry.SpawnRemover> spawnRemovers;

	private final List<SpawnRuleEntry> rules;

	private final ReadWriteLock spawnLock = new ReentrantReadWriteLock();
	private final ReadWriteLock removerLock = new ReentrantReadWriteLock();
	private final ReadWriteLock ruleLock = new ReentrantReadWriteLock();


	public AdditionalSpawnsZoneData(
			Multimap<SpawnGroup, BetterSpawnEntry> spawns, List<SpawnRemoverRegistry.SpawnRemover> spawnRemovers,
			List<SpawnRuleEntry> rules
	)  {
		this.spawns = MultimapBuilder.hashKeys().arrayListValues().build(spawns);
		this.spawnRemovers = new ArrayList<>(spawnRemovers);
		this.rules = new ArrayList<>(rules);
	}

	public ListAccessor<BetterSpawnEntry> getSpawns(SpawnGroup spawnGroup) {
		return new ListAccessor<>(spawns.get(spawnGroup), spawnLock, this::markDirty);
	}

	public boolean hasSpawns() {
		return !spawns.isEmpty();
	}

	public ListAccessor<SpawnRemoverRegistry.SpawnRemover> getSpawnRemovers() {
		return new ListAccessor<>(spawnRemovers, removerLock, this::markDirty);
	}

	public ListAccessor<SpawnRuleEntry> getSpawnRules() {
		return new ListAccessor<>(rules, ruleLock, this::markDirty);
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

		private final List<T> list;

		private final ReadWriteLock lock;
		private final Runnable save;

		public ListAccessor(List<T> list, ReadWriteLock lock, Runnable save) {
			this.list = list;
			this.lock = lock;
			this.save = save;
		}

		public void add(T element) {
			lock.writeLock().lock();
			try {
				list.add(element);
			} finally {
				lock.writeLock().unlock();
			}
			save.run();
		}

		public T remove(int index) {
			lock.writeLock().lock();
			T removed;
			try {
				removed = list.remove(index);
			} finally {
				lock.writeLock().unlock();
			}

			save.run();
			return removed;
		}

		public int size() {
			return list.size();
		}

		public boolean isEmpty() {
			return list.isEmpty();
		}

		public void forEach(Consumer<T> action) {
			lock.readLock().lock();
			try {
				list.forEach(action);
			} finally {
				lock.readLock().unlock();
			}
		}

		public Optional<T> find(Predicate<T> action) {
			lock.readLock().lock();
			try {
				for (var element : list) {
					if (action.test(element)) {
						return Optional.of(element);
					}
				}
			} finally {
				lock.readLock().unlock();
			}
			return Optional.empty();
		}

		public void addAllTo(Collection<? super T> other) {
			lock.readLock().lock();
			try {
				other.addAll(list);
			} finally {
				lock.readLock().unlock();
			}
		}

	}

}
