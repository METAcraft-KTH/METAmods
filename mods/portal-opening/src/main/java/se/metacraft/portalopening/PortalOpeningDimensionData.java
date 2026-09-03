package se.metacraft.portalopening;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.util.SavedDataTypeCache;
import se.metacraft.portalopening.raid.Wave;
import se.metacraft.portalopening.rifts.PortalRift;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class PortalOpeningDimensionData extends SavedData {

	private static final String MAIN_RIFT = "MainRift";
	private static final String RIFTS = "Rifts";
	private static final String CURRENT_WAVE = "CurrentWave";

	private final ServerLevel world;

	protected PortalRift mainRift;
	protected Set<PortalRift> rifts = new HashSet<>();
	protected OptionalInt currentWave = OptionalInt.empty();

	private final CommandSourceStack source;

	private static final SavedDataTypeCache.Type<PortalOpeningDimensionData> TYPE = new SavedDataTypeCache.Type<>(
			level -> new SavedDataType<>(
					METAcraftLib.getID("portal-opening-manager"), () -> createNew(level),
					createCodec(level), null
			)
	);

	private PortalOpeningDimensionData(
			ServerLevel world, PortalRift mainRift, List<PortalRift> rifts, Optional<Integer> currentWave
	) {
		this(world);
		if (mainRift != null) {
			mainRift.setSaveCallback(this::setDirty);
		}
		for (var rift : rifts) {
			rift.setSaveCallback(this::setDirty);
		}
		this.mainRift = mainRift;
		this.rifts = new HashSet<>(rifts);
		this.currentWave = currentWave.stream().mapToInt(i -> i).findAny();
	}

	protected PortalOpeningDimensionData(ServerLevel world) {
		this.world = world;
		this.source = new CommandSourceStack(
				new CommandSource() {
					@Override
					public void sendSystemMessage(Component message) {
						PortalOpening.LOGGER.info("PortalOpeningCommandExecutor: " + message.getString());
					}

					@Override
					public boolean acceptsSuccess() {
						return false;
					}

					@Override
					public boolean acceptsFailure() {
						return true;
					}

					@Override
					public boolean shouldInformAdmins() {
						return false;
					}
				}, Vec3.ZERO, Vec2.ZERO, world, LevelBasedPermissionSet.GAMEMASTER, "PortalOpening",
				Component.literal("PortalOpening"), world.getServer(), null
		);
	}

	private static PortalOpeningDimensionData createNew(ServerLevel world) {
		return new PortalOpeningDimensionData(world);
	}

	private static Codec<PortalOpeningDimensionData> createCodec(ServerLevel world) {
		var riftCodec = PortalRift.createCodec(world);
		return RecordCodecBuilder.create(instance -> instance.group(
				riftCodec.optionalFieldOf(MAIN_RIFT).forGetter(d -> Optional.ofNullable(d.mainRift)),
				riftCodec.listOf().fieldOf(RIFTS).forGetter(d -> new ArrayList<>(d.rifts)),
				Codec.INT.optionalFieldOf(CURRENT_WAVE).forGetter(d -> d.currentWave.stream().boxed().findAny())
		).apply(
				instance,
				(mainRift, rifts, currentWave) -> new PortalOpeningDimensionData(
						world, mainRift.orElse(null), rifts, currentWave
				)
		));
	}

	public static PortalOpeningDimensionData getInstance(ServerLevel world) {
		return world.getDataStorage().computeIfAbsent(SavedDataTypeCache.get(world, TYPE));
	}

	public boolean createRift(BlockPos pos, int size, BlockState xAxisState, BlockState zAxisState, Direction.Axis axis) {
		PortalRift rift = new PortalRift(world, pos, size, axis, this::setDirty);
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
		setDirty();
		return true;
	}

	public boolean addExistingRift(
			BlockPos pos, int size, Direction.Axis axis, Predicate<BlockState> isValidBlock
	) {
		PortalRift rift = new PortalRift(world, pos, size, axis, isValidBlock, this::setDirty);
		if (!rift.successful()) {
			return false;
		}
		rifts.add(rift);
		setDirty();
		return true;
	}

	public void addManualRift(BlockPos pos1, BlockPos pos2) {
		this.rifts.add(new PortalRift(
				world, pos1, pos2, this::setDirty
		));
	}

	public boolean addExistingNetherPortalRift(BlockPos pos, int size) {
		var state = world.getBlockState(pos);
		if (!state.is(Blocks.NETHER_PORTAL)) {
			return false;
		}
		var axis = state.getValue(NetherPortalBlock.AXIS);
		return addExistingRift(
				pos, size, axis,
				block -> block.is(Blocks.NETHER_PORTAL) && block.getValue(NetherPortalBlock.AXIS).equals(axis)
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
				setDirty();
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
		setDirty();
	}

	public void removeRiftAt(BlockPos pos) {
		if (rifts.removeIf(rift -> {
			boolean replaced = false;
			if (rift.containsPos(pos)) {
				rift.replaceBlocks(Blocks.AIR.defaultBlockState());
				rift.clearMobs();
				replaced = true;
			}
			return replaced;
		})) {
			setDirty();
		}
		if (mainRift != null && mainRift.containsPos(pos)) {
			mainRift.replaceBlocks(Blocks.AIR.defaultBlockState());
			mainRift.clearMobs();
			mainRift = null;
			setDirty();
		}
	}

	public OptionalInt getWave() {
		return currentWave;
	}

	public void stopRaid() {
		currentWave = OptionalInt.empty();
		world.getServer().getCommands().performPrefixedCommand(
				source, PortalOpening.getConfig().getCommandOnRaidEnd()
		);
		setDirty();
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
			setDirty();
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
			world.getServer().getCommands().performPrefixedCommand(
					source, actualWave.command()
			);
			mainRift.nextWave(actualWave);
			for (var rift : rifts) {
				rift.nextWave(actualWave);
			}
			setDirty();
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
				int toSpawn = mobEntry.amountPerSpawn().sample(world.getRandom());
				for (int i = 0; i < toSpawn; i++) {
					mobEntry.spawnMobsFromNBT(world, mainRift.getRandomPos(), mainRift::attemptLaunch);
				}
				mobEntry.amountPerSpawnOtherRifts().ifPresent(amount -> {
					for (var rift : rifts) {
						if (mobEntry.probabilityToSpawnOtherRift() <= world.getRandom().nextDouble()) {
							int toSpawnOtherRift = amount.sample(world.getRandom());
							for (int i = 0; i < toSpawnOtherRift; i++) {
								mobEntry.spawnMobsFromNBT(world, rift.getRandomPos(), rift::attemptLaunch);
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
		var block = Blocks.NETHER_PORTAL.defaultBlockState();
		return createRift(
				pos, size,
				block.setValue(NetherPortalBlock.AXIS, Direction.Axis.X),
				block.setValue(NetherPortalBlock.AXIS, Direction.Axis.Z),
				axis
		);
	}

	public void closeAllRifts(boolean includeMainRift) {
		for (var rift : rifts) {
			rift.replaceBlocks(Blocks.AIR.defaultBlockState());
			rift.clearMobs();
		}
		if (mainRift != null && includeMainRift) {
			mainRift.replaceBlocks(Blocks.AIR.defaultBlockState());
			mainRift.clearMobs();
			mainRift = null;
		}
		rifts.clear();
		setDirty();
	}
}
