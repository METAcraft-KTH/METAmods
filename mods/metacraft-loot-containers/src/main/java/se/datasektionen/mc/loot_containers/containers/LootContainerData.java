package se.datasektionen.mc.loot_containers.containers;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import org.apache.logging.log4j.util.TriConsumer;
import se.datasektionen.mc.loot_containers.METAcraftLootContainers;
import se.datasektionen.mc.loot_containers.containers.events.LootContainerEvent;
import se.datasektionen.mc.loot_containers.util.EntityOrBlockEntity;
import se.datasektionen.mc.loot_containers.util.PosOrUUID;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LootContainerData extends PersistentState {

	private static final PersistentStateType<LootContainerData> TYPE = new PersistentStateType<>(
			METAcraftLootContainers.MODID, ctx -> create(ctx.getWorldOrThrow().getServer()),
			ctx -> createCodec(ctx.getWorldOrThrow().getServer()), null
	);

	private static Codec<LootContainerData> createCodec(MinecraftServer server) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.unboundedMap(Codec.STRING, SerializedLootContainers.CODEC).fieldOf("Containers").forGetter(
								d -> d.containerGroups.entrySet().stream().map(
										e -> Pair.of(e.getKey(), e.getValue().serialize())
								).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond))
						),
						ExtraCodecs.unboundedMultimap(
								Codec.STRING, LootContainerEvent.REGISTRY_CODEC,
								MultimapBuilder.hashKeys().arrayListValues()::build
						).fieldOf("Events").forGetter(d -> d.containerEvents)
				).apply(instance, create(server)::load)
		);
	}

	public static LootContainerData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	private final Map<String, LootContainers> containerGroups = new HashMap<>();
	private final Multimap<String, LootContainerEvent> containerEvents = MultimapBuilder.hashKeys().arrayListValues().build();

	private final MinecraftServer server;

	private LootContainerData(MinecraftServer server) {
		this.server = server;
	}

	private static LootContainerData create(MinecraftServer server) {
		return new LootContainerData(server);
	}

	private LootContainerData load(
			Map<String, SerializedLootContainers> containerGroups,
			Multimap<String, LootContainerEvent> containerEvents
	) {
		containerGroups.forEach(
				(group, containers) -> {
					this.containerGroups.put(group, new LootContainers(server, this::markDirty).deserialize(containers));
				}
		);
		containerEvents.forEach(this::addEventInternal);
		return this;
	}

	private LootContainers get(String group) {
		return containerGroups.computeIfAbsent(group, key -> new LootContainers(server, this::markDirty));
	}

	private void addEventInternal(String group, LootContainerEvent event) {
		event.initialise(this::markDirty);
		containerEvents.put(group, event);
	}

	public Collection<String> getGroups() {
		return containerGroups.keySet();
	}

	public Stream<Pair<String, LootContainer>> getLootContainers(RegistryKey<World> dim, BlockPos pos) {
		return containerGroups.entrySet().stream().map(
				map -> Optional.ofNullable(map.getValue().blocks.getLootContainer(dim, pos)).map(
						container -> Pair.of(map.getKey(), container)
				).orElse(null)
		).filter(Objects::nonNull);
	}

	public LootContainer getLootContainer(String group, RegistryKey<World> dim, BlockPos pos) {
		return get(group).blocks.getLootContainer(dim, pos);
	}

	public void putLootContainer(String group, RegistryKey<World> dim, BlockPos pos, LootContainer container) {
		get(group).blocks.putLootContainer(dim, pos, container);
		markDirty();
	}

	public void removeLootContainers(RegistryKey<World> dim, BlockPos pos) {
		for (var group : containerGroups.values()) {
			group.blocks.removeLootContainer(dim, pos);
		}
		markDirty();
	}

	public void removeLootContainer(String group, RegistryKey<World> dim, BlockPos pos) {
		get(group).blocks.removeLootContainer(dim, pos);
		markDirty();
	}

	public void putLootContainer(String group, RegistryKey<World> dim, EntityOrBlockEntity entity, LootContainer container) {
		entity.run(
				e -> putLootContainer(group, dim, e.getUuid(), container),
				b -> putLootContainer(group, dim, b.getPos(), container)
		);
	}

	public LootContainer getLootContainer(String group, RegistryKey<World> dim, EntityOrBlockEntity entity) {
		return entity.map(
				e -> getLootContainer(group, dim, e.getUuid()),
				b -> getLootContainer(group, dim, b.getPos())
		);
	}

	public Stream<Pair<String, LootContainer>> getLootContainers(RegistryKey<World> dim, UUID entity) {
		return containerGroups.entrySet().stream().map(
				map -> Optional.ofNullable(map.getValue().entities.getLootContainer(dim, entity)).map(
						container -> Pair.of(map.getKey(), container)
				).orElse(null)
		).filter(Objects::nonNull);
	}

	public LootContainer getLootContainer(String group, RegistryKey<World> dim, UUID entity) {
		return get(group).entities.getLootContainer(dim, entity);
	}

	public void putLootContainer(String group, RegistryKey<World> dim, UUID entity, LootContainer container) {
		get(group).entities.putLootContainer(dim, entity, container);
		markDirty();
	}

	public void removeLootContainers(RegistryKey<World> dim, UUID entity) {
		for (var group : containerGroups.values()) {
			group.entities.removeLootContainer(dim, entity);
		}
		markDirty();
	}

	public void removeLootContainer(String group, RegistryKey<World> dim, UUID entity) {
		get(group).entities.removeLootContainer(dim, entity);
		markDirty();
	}

	public void addEvent(String group, LootContainerEvent event) {
		addEventInternal(group, event);
		markDirty();
	}

	public LootContainerEvent removeEvent(String group, int index) {
		var entries = containerEvents.get(group);
		if (entries instanceof List<LootContainerEvent> list) {
			var event = list.remove(index);
			markDirty();
			return event;
		}
		return null;
	}

	public LootContainerEvent getEvent(String group, int index) {
		var entries = containerEvents.get(group);
		if (entries instanceof List<LootContainerEvent> list) {
			return list.get(index);
		}
		return null;
	}

	public Collection<LootContainerEvent> getEvents(String group) {
		return Collections.unmodifiableCollection(containerEvents.get(group));
	}

	public Stream<LootContainer> getAllLootContainers(String group) {
		return get(group).getAllLootContainers();
	}

	public void tick() {
		containerGroups.values().removeIf(group -> {
			group.runAllTickers();
			return group.isEmpty();
		});
		containerEvents.entries().removeIf(entry -> {
			entry.getValue().tick(entry.getKey(), this, server);
			return entry.getValue().shouldRemove();
		});
	}

	public static class LootContainers {

		public static final Codec<BlockPos> POS_CODEC = BlockPos.CODEC;
		public static final Codec<UUID> UUID_CODEC = Uuids.STRICT_CODEC;

		private final LootContainerMap<BlockPos> blocks;
		private final LootContainerMap<UUID> entities;

		private static TriConsumer<RegistryKey<World>, BlockPos, LootContainer> getBlockPosContainerInitializer(
				MinecraftServer server, Runnable markDirty
		) {
			return (dim, pos, container) -> {
				var world = server.getWorld(dim);
				container.initialise(world, () -> {
					var blockEntity = world.getBlockEntity(pos);
					if (blockEntity instanceof Inventory) {
						return Optional.of(LootAccess.block((BlockEntity & Inventory) blockEntity));
					} else {
						return Optional.empty();
					}
				}, new PosOrUUID(pos), markDirty);
			};
		}

		private static TriConsumer<RegistryKey<World>, UUID, LootContainer> getEntityContainerInitializer(
				MinecraftServer server, Runnable markDirty
		) {
			return (dim, uuid, container) -> {
				var world = server.getWorld(dim);
				container.initialise(world, () -> {
					var entity = world.getEntity(uuid);
					if (entity instanceof Inventory) {
						return Optional.of(LootAccess.entity((Entity & Inventory) entity));
					} else {
						return Optional.empty();
					}
				}, new PosOrUUID(uuid), markDirty);
			};
		}

		public LootContainers(MinecraftServer server, Runnable markDirty) {
			this.blocks = new LootContainerMap<>(POS_CODEC, getBlockPosContainerInitializer(server, markDirty));
			this.entities = new LootContainerMap<>(UUID_CODEC, getEntityContainerInitializer(server, markDirty));
		}

		public SerializedLootContainers serialize() {
			return new SerializedLootContainers(
					blocks.serialize(),
					entities.serialize()
			);
		}

		public LootContainers deserialize(SerializedLootContainers containers) {
			blocks.deserializeMap(containers.blocks);
			entities.deserializeMap(containers.entities);
			return this;
		}

		public boolean isEmpty() {
			return blocks.isEmpty() && entities.isEmpty();
		}

		public void runAllTickers() {
			blocks.runAllTickers();
			entities.runAllTickers();
		}

		public Stream<LootContainer> getAllLootContainers() {
			return Stream.concat(
					blocks.getLootContainers().stream(),
					entities.getLootContainers().stream()
			);
		}
	}

	public record SerializedLootContainers(
			LootContainerMap.SerializedMap<BlockPos> blocks,
			LootContainerMap.SerializedMap<UUID> entities
	) {
		public static final Codec<SerializedLootContainers> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
					LootContainerMap.SerializedMap.createCodec(LootContainers.POS_CODEC).fieldOf("Blocks").forGetter(SerializedLootContainers::blocks),
					LootContainerMap.SerializedMap.createCodec(LootContainers.UUID_CODEC).fieldOf("Entities").forGetter(SerializedLootContainers::entities)
				).apply(instance, SerializedLootContainers::new)
		);
	}
}
