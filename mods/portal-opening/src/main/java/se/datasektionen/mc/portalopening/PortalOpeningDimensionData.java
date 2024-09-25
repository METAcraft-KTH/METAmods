package se.datasektionen.mc.portalopening;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import se.datasektionen.mc.portalopening.raid.Wave;
import se.datasektionen.mc.portalopening.rifts.PortalRift;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class PortalOpeningDimensionData extends PersistentState {

	private static final String KEY = "portal-opening-manager";

	private static final String MAIN_RIFT = "MainRift";
	private static final String RIFTS = "Rifts";
	private static final String CURRENT_WAVE = "CurrentWave";

	private final ServerWorld world;

	protected PortalRift mainRift;
	protected Set<PortalRift> rifts = new HashSet<>();
	protected OptionalInt currentWave = OptionalInt.empty();

	private final ServerCommandSource source;

	private static PersistentState.Type<PortalOpeningDimensionData> getType(ServerWorld world) {
		return new Type<>(
				() -> createNew(world), (nbt, lookup) -> fromNbt(world, nbt, lookup), null
		);
	}

	protected PortalOpeningDimensionData(ServerWorld world) {
		this.world = world;
		this.source = new ServerCommandSource(
				new CommandOutput() {
					@Override
					public void sendMessage(Text message) {
						PortalOpening.LOGGER.info("PortalOpeningCommandExecutor: " + message.getString());
					}

					@Override
					public boolean shouldReceiveFeedback() {
						return false;
					}

					@Override
					public boolean shouldTrackOutput() {
						return true;
					}

					@Override
					public boolean shouldBroadcastConsoleToOps() {
						return false;
					}
				}, Vec3d.ZERO, Vec2f.ZERO, world, 2, "PortalOpening",
				Text.literal("PortalOpening"), world.getServer(), null
		);
	}

	private static PortalOpeningDimensionData createNew(ServerWorld world) {
		return new PortalOpeningDimensionData(world);
	}

	private static PortalOpeningDimensionData fromNbt(ServerWorld world, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		var manager = new PortalOpeningDimensionData(world);
		manager.readNbt(nbt, lookup);
		return manager;
	}

	public static PortalOpeningDimensionData getInstance(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(getType(world), KEY);
	}

	public boolean createRift(BlockPos pos, int size, BlockState xAxisState, BlockState zAxisState, Direction.Axis axis) {
		PortalRift rift = new PortalRift(world, pos, size, axis, this::markDirty);
		if (!rift.successful()) {
			return false;
		}
		rift.getAxis().ifPresent(actualAxis -> {
			if (actualAxis == Direction.Axis.X) {
				rift.replaceBlocks(xAxisState);
			} else {
				rift.replaceBlocks(zAxisState);
			}
		});
		rifts.add(rift);
		markDirty();
		return true;
	}

	public boolean addExistingRift(
			BlockPos pos, int size, Direction.Axis axis, Predicate<BlockState> isValidBlock
	) {
		PortalRift rift = new PortalRift(world, pos, size, axis, isValidBlock, this::markDirty);
		if (!rift.successful()) {
			return false;
		}
		rifts.add(rift);
		markDirty();
		return true;
	}

	public void addManualRift(BlockPos pos1, BlockPos pos2) {
		this.rifts.add(new PortalRift(
				world, pos1, pos2, this::markDirty
		));
	}

	public boolean addExistingNetherPortalRift(BlockPos pos, int size) {
		var state = world.getBlockState(pos);
		if (!state.isOf(Blocks.NETHER_PORTAL)) {
			return false;
		}
		var axis = state.get(NetherPortalBlock.AXIS);
		return addExistingRift(
				pos, size, axis,
				block -> block.isOf(Blocks.NETHER_PORTAL) && block.get(NetherPortalBlock.AXIS).equals(axis)
		);
	}

	public boolean setMainRift(BlockPos pos) {
		if (mainRift != null) {
			if (mainRift.containsPos(pos)) {
				return true;
			}
		}
		PortalRift prevMain = mainRift;
		for (var rift : rifts) {
			if (rift.containsPos(pos)) {
				mainRift = rift;
				markDirty();
				break;
			}
		}
		if (mainRift == prevMain) {
			return false;
		} else {
			rifts.remove(mainRift);
			if (prevMain != null) {
				rifts.add(prevMain);
			}
			return true;
		}
	}

	public void combineRifts(PortalRift rift1, PortalRift rift2) {
		if (rift1 == rift2) return;
		rift1.addBlocksFromRift(rift2);
		rift2.clearMobs();
		if (!rift1.getAxis().equals(rift2.getAxis())) {
			rift1.removeAxis();
		}
		if (rift2 == mainRift) {
			this.rifts.remove(rift1);
			mainRift = rift1;
		} else {
			this.rifts.remove(rift2);
		}
		markDirty();
	}

	public void removeRiftAt(BlockPos pos) {
		if (rifts.removeIf(rift -> {
			boolean replaced = false;
			if (rift.containsPos(pos)) {
				rift.replaceBlocks(Blocks.AIR.getDefaultState());
				rift.clearMobs();
				replaced = true;
			}
			return replaced;
		})) {
			markDirty();
		}
		if (mainRift != null && mainRift.containsPos(pos)) {
			mainRift.replaceBlocks(Blocks.AIR.getDefaultState());
			mainRift.clearMobs();
			mainRift = null;
			markDirty();
		}
	}

	public OptionalInt getWave() {
		return currentWave;
	}

	public void stopRaid() {
		currentWave = OptionalInt.empty();
		world.getServer().getCommandManager().executeWithPrefix(
				source, PortalOpening.getConfig().getCommandOnRaidEnd()
		);
		markDirty();
	}

	public void nextWave() {
		setWave(currentWave.orElse(-1)+1);
	}

	public void fixMainRift() {
		if (mainRift == null) {
			if (rifts.isEmpty()) {
				stopRaid();
				return;
			}
			int chosenRift = world.getRandom().nextInt(rifts.size());
			mainRift = rifts.stream().skip(chosenRift).findAny().get();
			rifts.remove(mainRift);
			markDirty();
		}
	}

	public void setWave(int wave) {
		assert wave >= 0;
		if (wave < PortalOpening.getConfig().getWaves().size()) {
			currentWave = OptionalInt.of(wave);
		} else {
			stopRaid();
		}
		fixMainRift();
		getCurrentWave().ifPresent(actualWave -> {
			world.getServer().getCommandManager().executeWithPrefix(
					source, actualWave.command()
			);
			mainRift.nextWave(actualWave);
			for (var rift : rifts) {
				rift.nextWave(actualWave);
			}
			markDirty();
		});
	}

	public boolean isRaidHappening() {
		return currentWave.isPresent() && currentWave.getAsInt() < PortalOpening.getConfig().getWaves().size();
	}

	public Optional<Wave> getCurrentWave() {
		return currentWave.stream().flatMap(wave -> {
			if (wave < PortalOpening.getConfig().getWaves().size()) {
				return IntStream.of(wave);
			} else {
				return IntStream.empty();
			}
		}).mapToObj(PortalOpening.getConfig().getWaves()::get).findAny();
	}

	public void spawnFromName(String name) {
		fixMainRift();
		getCurrentWave().ifPresent(wave -> {
			for (var mobEntry : wave.manualSpawns().get(name)) {
				int toSpawn = mobEntry.amountPerSpawn().get(world.getRandom());
				for (int i = 0; i < toSpawn; i++) {
					mobEntry.spawnMobsFromNBT(world, mainRift.getRandomPos());
				}
				mobEntry.amountPerSpawnOtherRifts().ifPresent(amount -> {
					for (var rift : rifts) {
						if (mobEntry.probabilityToSpawnOtherRift() <= world.getRandom().nextDouble()) {
							int toSpawnOtherRift = amount.get(world.getRandom());
							for (int i = 0; i < toSpawnOtherRift; i++) {
								mobEntry.spawnMobsFromNBT(world, rift.getRandomPos());
							}
						}
					}
				});
			}
		});
	}

	public Collection<String> getCurrentSpawnNames() {
		return getCurrentWave().map(wave -> wave.manualSpawns().keySet()).orElse(ImmutableSet.of());
	}

	public void tick() {
		if (isRaidHappening()) {
			if (rifts.isEmpty() && mainRift == null) {
				stopRaid();
			}
		}

		getCurrentWave().ifPresent(wave -> {
			if (mainRift != null) {
				mainRift.tick(wave, true);
			}
			for (var rift : rifts) {
				rift.tick(wave, false);
			}
		});
	}

	public Optional<PortalRift> getRiftAt(BlockPos pos) {
		for (var rift : rifts) {
			if (rift.containsPos(pos)) {
				return Optional.of(rift);
			}
		}
		if (mainRift != null && mainRift.containsPos(pos)) {
			return Optional.of(mainRift);
		}
		return Optional.empty();
	}

	public Collection<PortalRift> getAllRifts() {
		return rifts;
	}

	public boolean createNetherPortalRift(BlockPos pos, int size, Direction.Axis axis) {
		var block = Blocks.NETHER_PORTAL.getDefaultState();
		return createRift(
				pos, size,
				block.with(NetherPortalBlock.AXIS, Direction.Axis.X),
				block.with(NetherPortalBlock.AXIS, Direction.Axis.Z),
				axis
		);
	}

	public void closeAllRifts(boolean includeMainRift) {
		for (var rift : rifts) {
			rift.replaceBlocks(Blocks.AIR.getDefaultState());
			rift.clearMobs();
		}
		if (mainRift != null && includeMainRift) {
			mainRift.replaceBlocks(Blocks.AIR.getDefaultState());
			mainRift.clearMobs();
			mainRift = null;
		}
		rifts.clear();
		markDirty();
	}

	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		if (nbt.contains(MAIN_RIFT)) {
			mainRift = new PortalRift(world, nbt.getCompound(MAIN_RIFT), this::markDirty);
		}
		NbtList rifts = nbt.getList(RIFTS, NbtElement.COMPOUND_TYPE);
		this.rifts.clear();
		for (NbtElement rift : rifts) {
			this.rifts.add(new PortalRift(world, (NbtCompound) rift, this::markDirty));
		}
		if (nbt.contains(CURRENT_WAVE)) {
			currentWave = OptionalInt.of(nbt.getInt(CURRENT_WAVE));
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		if (mainRift != null) {
			nbt.put(MAIN_RIFT, mainRift.toNBT());
		}
		NbtList rifts = new NbtList();
		this.rifts.forEach(rift -> rifts.add(rift.toNBT()));
		nbt.put(RIFTS, rifts);
		currentWave.ifPresent(wave -> {
			nbt.putInt(CURRENT_WAVE, wave);
		});
		return nbt;
	}
}
