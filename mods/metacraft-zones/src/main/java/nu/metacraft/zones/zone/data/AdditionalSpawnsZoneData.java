package nu.metacraft.zones.zone.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.predicates.entity.EntityTypePredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.apache.commons.lang3.mutable.MutableObject;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePMap;
import org.pcollections.TreePVector;
import nu.metacraft.lib.util.helper.EntityHelper;
import nu.metacraft.lib.util.helper.ThreadHelper;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.spawns.BetterSpawnEntry;
import nu.metacraft.zones.spawns.SpawnRemoverRegistry;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AdditionalSpawnsZoneData extends ZoneData {

	private static final TextComponentTagVisitor formatter = new TextComponentTagVisitor("");

	public static final MapCodec<AdditionalSpawnsZoneData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.unboundedMap(
					MobCategory.CODEC,
					BetterSpawnEntry.WEIGHTED_CODEC.listOf()
			).fieldOf("spawns").forGetter(data -> (PMap<MobCategory, List<Weighted<BetterSpawnEntry>>>) (Object) data.spawns.get()),
			SpawnRemoverRegistry.SpawnRemover.REGISTRY_CODEC.listOf().fieldOf("spawnRemovers").forGetter(data -> data.spawnRemovers.get()),
			SpawnRuleEntry.CODEC.listOf().fieldOf("spawnRules").forGetter(data -> data.rules.get())
	).apply(instance, AdditionalSpawnsZoneData::new));


	private final AtomicReference<PMap<MobCategory, PVector<Weighted<BetterSpawnEntry>>>> spawns;
	private final AtomicReference<PVector<SpawnRemoverRegistry.SpawnRemover>> spawnRemovers;

	private final AtomicReference<PVector<SpawnRuleEntry>> rules;


	public AdditionalSpawnsZoneData(
			Map<MobCategory, List<Weighted<BetterSpawnEntry>>> spawns, List<SpawnRemoverRegistry.SpawnRemover> spawnRemovers,
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

	public ListAccessor<Weighted<BetterSpawnEntry>> getSpawns(MobCategory spawnGroup) {
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
	public Component toText(HolderLookup.Provider lookup) {
		return CODEC.codec().encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), this).resultOrPartial(METAcraftZones.LOGGER::error).map(formatter::visit).orElse(Component.literal("Error").withStyle(ChatFormatting.RED));
	}

	public record SpawnRuleEntry(EntityTypePredicate type, LootItemCondition condition) {
		public static final Codec<SpawnRuleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				EntityTypePredicate.CODEC.fieldOf("type").forGetter(SpawnRuleEntry::type),
				LootItemCondition.DIRECT_CODEC.fieldOf("condition").forGetter(SpawnRuleEntry::condition)
		).apply(instance, SpawnRuleEntry::new));

		public boolean test(
				EntityType<?> entityType, ServerLevelAccessor world, EntitySpawnReason reason, BlockPos pos, RandomSource random
		) {
			return EntityHelper.canSpawn(
					world.getLevel(), random, entityType, condition, reason,
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
