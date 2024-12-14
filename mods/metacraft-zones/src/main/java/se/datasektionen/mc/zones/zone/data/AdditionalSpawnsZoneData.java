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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePMap;
import org.pcollections.TreePVector;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityHelper;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.spawns.BetterSpawnEntry;
import se.datasektionen.mc.zones.spawns.SpawnRemoverRegistry;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AdditionalSpawnsZoneData extends ZoneData {

	private static final NbtTextFormatter formatter = new NbtTextFormatter("");

	public static final MapCodec<AdditionalSpawnsZoneData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(
					SpawnGroup.CODEC,
					BetterSpawnEntry.CODEC.listOf()
			).fieldOf("spawns").forGetter(data -> (PMap<SpawnGroup, List<BetterSpawnEntry>>) (Object) data.spawns),
			SpawnRemoverRegistry.SpawnRemover.REGISTRY_CODEC.listOf().fieldOf("spawnRemovers").forGetter(data -> data.spawnRemovers),
			SpawnRuleEntry.CODEC.listOf().fieldOf("spawnRules").forGetter(data -> data.rules)
	).apply(instance, AdditionalSpawnsZoneData::new));


	private PMap<SpawnGroup, PVector<BetterSpawnEntry>> spawns;
	private PVector<SpawnRemoverRegistry.SpawnRemover> spawnRemovers;

	private PVector<SpawnRuleEntry> rules;


	public AdditionalSpawnsZoneData(
			Map<SpawnGroup, List<BetterSpawnEntry>> spawns, List<SpawnRemoverRegistry.SpawnRemover> spawnRemovers,
			List<SpawnRuleEntry> rules
	)  {
		this.spawns = spawns.entrySet().stream().reduce(
				TreePMap.empty(),
				(map, list) -> map.plus(
						list.getKey(), TreePVector.from(list.getValue())
				),
				TreePMap::plusAll
		);
		this.spawnRemovers = TreePVector.from(spawnRemovers);
		this.rules = TreePVector.from(rules);
	}

	public ListAccessor<BetterSpawnEntry> getSpawns(SpawnGroup spawnGroup) {
		return new ListAccessor<>(spawns.getOrDefault(spawnGroup, TreePVector.empty()), list -> {
			if (list.isEmpty()) {
				spawns = spawns.minus(spawnGroup);
			} else {
				spawns = spawns.plus(spawnGroup, list);
			}
			this.markDirty();
		});
	}

	public boolean hasSpawns() {
		return !spawns.isEmpty();
	}

	public ListAccessor<SpawnRemoverRegistry.SpawnRemover> getSpawnRemovers() {
		return new ListAccessor<>(spawnRemovers, list -> {
			spawnRemovers = list;
			markDirty();
		});
	}

	public ListAccessor<SpawnRuleEntry> getSpawnRules() {
		return new ListAccessor<>(rules, list -> {
			rules = list;
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

		private PVector<T> list;

		private final Consumer<PVector<T>> updater;

		public ListAccessor(PVector<T> list, Consumer<PVector<T>> updater) {
			this.list = list;
			this.updater = updater.andThen(result -> {
				this.list = result;
			});
		}

		public void add(T element) {
			updater.accept(list.plus(element));
		}

		public T remove(int index) {
			T removed = list.get(index);
			updater.accept(list.minus(index));
			return removed;
		}

		public int size() {
			return list.size();
		}

		public boolean isEmpty() {
			return list.isEmpty();
		}

		public void forEach(Consumer<T> action) {
			list.forEach(action);
		}

		public Optional<T> find(Predicate<T> action) {
			for (var element : list) {
				if (action.test(element)) {
					return Optional.of(element);
				}
			}
			return Optional.empty();
		}

		public PVector<T> get() {
			return list;
		}

	}

}
