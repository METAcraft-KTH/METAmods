package se.datasektionen.mc.loot_containers.containers;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import se.datasektionen.mc.loot_containers.METAcraftLootContainers;
import se.datasektionen.mc.loot_containers.containers.events.LootContainerEvent;
import se.datasektionen.mc.loot_containers.util.EntityOrBlockEntity;
import se.datasektionen.mc.loot_containers.util.PosOrUUID;

import java.util.*;
import java.util.stream.Stream;

public class LootContainerData extends PersistentState {

	private static final String key = METAcraftLootContainers.MODID;

	private static final String CONTAINERS = "Containers";
	private static final String EVENTS = "Events";


	private static Type<LootContainerData> getType(MinecraftServer server) {
		return new Type<>(
				() -> create(server), (nbt, lookup) -> load(server, nbt, lookup), null
		);
	}


	public static LootContainerData getInstance(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(getType(server), key);
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

	private static LootContainerData load(MinecraftServer server, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		var data = new LootContainerData(server);
		data.readNBT(nbt, lookup);
		return data;
	}

	private LootContainers get(String group) {
		return containerGroups.computeIfAbsent(group, key -> new LootContainers());
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

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		this.containerGroups.clear();
		this.containerEvents.clear();
		NbtCompound containers = nbt.getCompound(CONTAINERS);
		for (var key : containers.getKeys()) {
			get(key).readNBT(containers.getCompound(key));
		}
		NbtCompound events = nbt.getCompound(EVENTS);
		for (var group : events.getKeys()) {
			NbtList eventsForGroup = events.getList(group, NbtElement.COMPOUND_TYPE);
			for (var event : eventsForGroup) {
				LootContainerEvent.REGISTRY_CODEC.parse(NbtOps.INSTANCE, event).resultOrPartial(
						METAcraftLootContainers.LOGGER::error
				).ifPresent(e -> addEventInternal(group, e));
			}
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtCompound containers = new NbtCompound();
		for (var container : containerGroups.entrySet()) {
			containers.put(container.getKey(), container.getValue().writeNbt(new NbtCompound()));
		}
		nbt.put(CONTAINERS, containers);

		NbtCompound events = new NbtCompound();
		for (var group : containerEvents.keySet()) {
			NbtList list = new NbtList();
			containerEvents.get(group).forEach(event -> {
				LootContainerEvent.REGISTRY_CODEC.encodeStart(NbtOps.INSTANCE, event).resultOrPartial(
						METAcraftLootContainers.LOGGER::error
				).ifPresent(list::add);
			});
			events.put(group, list);
		}
		nbt.put(EVENTS, events);

		return nbt;
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

	public class LootContainers {

		private static final String BLOCKS = "Blocks";
		private static final String ENTITIES = "Entities";

		private final LootContainerMap<BlockPos> blocks;
		private final LootContainerMap<UUID> entities;

		public LootContainers() {
			this.blocks = new LootContainerMap<>(
				BlockPos.CODEC, (dim, pos, container) -> {
					var world = server.getWorld(dim);
					container.initialise(world, () -> {
						var blockEntity = world.getBlockEntity(pos);
						if (blockEntity instanceof Inventory) {
							return Optional.of(LootAccess.block((BlockEntity & Inventory) blockEntity));
						} else {
							return Optional.empty();
						}
					}, new PosOrUUID(pos), LootContainerData.this::markDirty);
				}
			);
			this.entities = new LootContainerMap<>(
				Uuids.STRICT_CODEC, (dim, uuid, container) -> {
					var world = server.getWorld(dim);
					container.initialise(world, () -> {
						var entity = world.getEntity(uuid);
						if (entity instanceof Inventory) {
							return Optional.of(LootAccess.entity((Entity & Inventory) entity));
						} else {
							return Optional.empty();
						}
					}, new PosOrUUID(uuid), LootContainerData.this::markDirty);
				}
			);
		}

		public boolean isEmpty() {
			return blocks.isEmpty() && entities.isEmpty();
		}

		public void runAllTickers() {
			blocks.runAllTickers();
			entities.runAllTickers();
		}

		public void readNBT(NbtCompound nbt) {
			this.blocks.readNBT(nbt.getCompound(BLOCKS));
			this.entities.readNBT(nbt.getCompound(ENTITIES));
		}

		public NbtCompound writeNbt(NbtCompound nbt) {
			nbt.put(BLOCKS, blocks.writeNbt(new NbtCompound()));
			nbt.put(ENTITIES, entities.writeNbt(new NbtCompound()));
			return nbt;
		}

		public Stream<LootContainer> getAllLootContainers() {
			return Stream.concat(
					blocks.getLootContainers().stream(),
					entities.getLootContainers().stream()
			);
		}
	}
}
