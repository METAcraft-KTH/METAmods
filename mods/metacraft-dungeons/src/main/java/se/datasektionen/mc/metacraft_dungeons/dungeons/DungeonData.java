package se.datasektionen.mc.metacraft_dungeons.dungeons;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.entities.MusicBlockEntity;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.Tags;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.BlackHolePortalEntity;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_lib.util.PositionFinder;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonBlocks;
import se.datasektionen.mc.metacraft_dungeons.util.Teleporter;
import se.datasektionen.mc.metacraft_dungeons.util.WorldDeleter;
import se.datasektionen.mc.metacraft_lib.util.TaskScheduler;

import java.util.*;
import java.util.function.Consumer;

public class DungeonData extends PersistentState {

	private static final String key = METAcraftDungeons.MODID;
	private static final String INDEX = "index";
	private static final String WIDTH = "width";
	private static final String EXIT_DIM = "exit_dim";
	private static final String EXIT_POS = "exit_pos";
	private static final String MAX_RANGE_FROM_EXIT_POS = "max_range_from_exit_pos";

	private static final String TIME_SINCE_RESET = "time_since_reset";
	private static final String CLEARING = "clearing";
	private static final String RESETTING = "resetting";
	private static final String EXTERNAL_ENTRANCES = "external_entrances";
	private static final String SHOULD_TELEPORT = "should_teleport";

	private static PersistentState.Type<DungeonData> getType(ServerWorld world) {
		return new Type<>(
				() -> create(world), (nbt, wrapper) -> load(world, nbt, wrapper), null
		);
	}

	private int dungeonWidth;
	private RegistryKey<World> exitDim = World.OVERWORLD;
	private BlockPos exitPos;
	private double maxRangeFromExitPos = 10000;
	private final List<TeleportPredicate> shouldTeleport = new ArrayList<>(
			ImmutableList.of(
					new TeleportPredicate(
							EntityPredicate.Builder.create().type(EntityType.FALLING_BLOCK).build(),
							false
					)
			)
	);


	private long index = 0;
	private boolean resetting = false;
	private boolean clearing = false;
	private int timeSinceReset = 0;
	private final List<EntranceEntry> externalEntrances = new ArrayList<>();

	private final Set<MusicBlockEntity> knownMusicBlocks = new HashSet<>();


	public static DungeonData getInstance(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(getType(world), key);
	}

	public static Optional<DungeonData> getIfPresent(ServerWorld world) {
		return Optional.ofNullable(world.getPersistentStateManager().get(getType(world), key));
	}

	private final ServerWorld world;

	private DungeonData(ServerWorld world) {
		this.world = world;
		dungeonWidth = world.getHeight()*4;
		exitPos = world.getServer().getOverworld().getSpawnPos();
	}

	public int getDungeonWidth() {
		return dungeonWidth;
	}

	private static DungeonData create(ServerWorld world) {
		return new DungeonData(world);
	}

	private static DungeonData load(ServerWorld world, NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
		var data = new DungeonData(world);
		data.readNBT(nbt, wrapperLookup);
		return data;
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		index = nbt.getLong(INDEX);
		dungeonWidth = nbt.getInt(WIDTH);
		exitDim = Optional.ofNullable(nbt.get(EXIT_DIM)).flatMap(
				exitDim -> World.CODEC.parse(NbtOps.INSTANCE, exitDim).resultOrPartial(
						METAcraftDungeons.LOGGER::error
				)
		).orElse(World.OVERWORLD);
		exitPos = NbtHelper.toBlockPos(nbt, EXIT_POS).orElse(world.getServer().getOverworld().getSpawnPos());
		maxRangeFromExitPos = nbt.getDouble(MAX_RANGE_FROM_EXIT_POS);
		clearing = nbt.getBoolean(CLEARING);
		resetting = nbt.getBoolean(RESETTING);
		timeSinceReset = nbt.getInt(TIME_SINCE_RESET);
		if (!resetting) {
			clearing = false;
			timeSinceReset = 0;
		}
		if (clearing) {
			clearing = false;
			clear();
		}

		externalEntrances.clear();
		if (nbt.contains(EXTERNAL_ENTRANCES)) {
			EntranceEntry.LIST_CODEC.parse(NbtOps.INSTANCE, nbt.get(EXTERNAL_ENTRANCES)).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).ifPresent(externalEntrances::addAll);
		}

		shouldTeleport.clear();
		if (nbt.contains(SHOULD_TELEPORT)) {
			TeleportPredicate.LIST_CODEC.parse(lookup.getOps(NbtOps.INSTANCE), nbt.get(SHOULD_TELEPORT)).resultOrPartial(
					METAcraftDungeons.LOGGER::error
			).ifPresent(this.shouldTeleport::addAll);
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		nbt.putLong(INDEX, index);
		nbt.putInt(WIDTH, dungeonWidth);
		World.CODEC.encodeStart(NbtOps.INSTANCE, exitDim).resultOrPartial(
				METAcraftDungeons.LOGGER::error
		).ifPresent(exitDim -> {
			nbt.put(EXIT_DIM, exitDim);
		});
		nbt.put(EXIT_POS, NbtHelper.fromBlockPos(exitPos));
		nbt.putDouble(MAX_RANGE_FROM_EXIT_POS, maxRangeFromExitPos);
		nbt.putBoolean(RESETTING, resetting);
		nbt.putBoolean(CLEARING, clearing);
		nbt.putInt(TIME_SINCE_RESET, timeSinceReset);
		EntranceEntry.LIST_CODEC.encodeStart(NbtOps.INSTANCE, externalEntrances).resultOrPartial(
				METAcraftDungeons.LOGGER::error
		).ifPresent(entrances -> {
			nbt.put(EXTERNAL_ENTRANCES, entrances);
		});
		TeleportPredicate.LIST_CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), shouldTeleport).resultOrPartial(
				METAcraftDungeons.LOGGER::error
		).ifPresent(shouldTeleport -> {
			nbt.put(SHOULD_TELEPORT, shouldTeleport);
		});
		return nbt;
	}

	private void copyFromPrevious(DungeonData data) {
		this.exitDim = data.exitDim;
		this.exitPos = data.exitPos;
		this.maxRangeFromExitPos = data.maxRangeFromExitPos;
		this.dungeonWidth = data.dungeonWidth;
		this.shouldTeleport.clear();
		this.shouldTeleport.addAll(data.shouldTeleport);
		markDirty();
	}

	public void resetAllDungeons() {
		index = 0;
		markDirty();
	}
	public boolean isResetting() {
		return resetting;
	}

	public boolean isClearing() {
		return clearing;
	}

	public void clearDimension() {
		if (world.getRegistryKey() == World.OVERWORLD) {
			METAcraftDungeons.LOGGER.error("No, I refuse to delete the overworld!");
			return;
		}
		world.savingDisabled = true;
		resetting = true;
		for (var entrancePos : externalEntrances) {
			var entranceWorld = world.getServer().getWorld(entrancePos.dim);
			if (
				entranceWorld != null &&
				entranceWorld.getBlockEntity(entrancePos.pos) instanceof DungeonEntranceEntity entrance
			) {
				entrance.setTargetPos(null);
			}
		}
		externalEntrances.clear();
		resetAllDungeons();
	}

	private boolean shouldTeleport(Entity entity) {
		return TeleportPredicate.shouldTeleport(shouldTeleport, world, entity);
	}

	public void teleportOut(Entity entity) {
		if (!shouldTeleport(entity)) {
			entity.kill();
		}
		var exitPos = getExitPos().toCenterPos();
		entity.teleportTo(
				new TeleportTarget(
						world.getServer().getWorld(exitDim), exitPos, entity.getVelocity(), entity.getYaw(), entity.getPitch(),
						pet -> {
							pet.fallDistance = 0;
						}
				)
		);
	}

	private <T extends LivingEntity & Tameable> void forAllPets(Consumer<T> action) {
		for (var pet : world.getEntitiesByType(
				TypeFilter.instanceOf(LivingEntity.class),
				entity -> entity instanceof Tameable && ((Tameable) entity).getOwnerUuid() != null)
		) {
			action.accept((T) pet);
		}
	}

	public void tick() {
		forAllPets(pet -> {
			if (pet.getY() < world.getBottomY()) {
				pet.fallDistance = 0;
				var player = world.getServer().getPlayerManager().getPlayer(pet.getOwnerUuid());
				if (player == null) {
					teleportOut(pet);
				} else {
					Teleporter.teleportEntityToPlayer(player, pet);
				}
			}
		});
		for (var player : new ArrayList<>(world.getPlayers())) {
			if (player.getY() < world.getBottomY()) {
				player.fallDistance = 0;
				teleportOut(player);
			}
		}
		if (resetting) {
			for (var musicBlock : knownMusicBlocks) {
				musicBlock.setMusic("resetting");
			}

			timeSinceReset++;
			int comparison = 100;
			if (timeSinceReset > 500) {
				comparison = 50;
			}
			if (timeSinceReset > 1000) {
				comparison = 20;
			}
			for (var player : world.getPlayers()) {
				if (player.age % (player.getRandom().nextInt(comparison) + 1) == 0) {
					int count = player.getRandom().nextInt(250);
					int range = player.getRandom().nextInt(50);
					for (BlockPos pos : BlockPos.iterateRandomly(player.getRandom(), count, player.getBlockPos(), range)) {
						if (world.getBlockState(pos).isIn(Tags.DUNGEON_RESET_UNBREAKABLE) || world.getBlockState(pos).isAir()) {
							continue;
						}
						var centerPos = pos.toCenterPos();
						world.spawnParticles(
								new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, world.getBlockState(pos)),
								centerPos.x, centerPos.y-3, centerPos.z, 10,
								player.getRandom().nextDouble(),
								player.getRandom().nextDouble() * 3,
								player.getRandom().nextDouble(),
								1
						);
						if (world.getBlockState(pos.down()).isAir() && world.getBlockEntity(pos) == null) {
							var falling = FallingBlockEntity.spawnFromBlock(world, pos, world.getBlockState(pos));
							falling.dropItem = false;
						} else {
							world.breakBlock(pos, false);
						}
					}
				}
			}
			if (timeSinceReset == 1500) {
				List<BlockPos> positions = new ArrayList<>(world.getPlayers().size());
				playerLoop: for (var player : world.getPlayers()) {
					BlockPos pos;
					do {
						pos = player.getBlockPos().add(
								player.getRandom().nextInt(50) - 25,
								MathHelper.clamp(
										player.getRandom().nextInt(50) - 25,
										world.getBottomY() + 10,
										world.getBottomY() + world.getHeight() - 10
								),
								player.getRandom().nextInt(50) - 25
						);
					} while (world.getBlockState(pos).isIn(Tags.DUNGEON_RESET_UNBREAKABLE));
					for (var position : positions) {
						if (pos.isWithinDistance(position, dungeonWidth)) {
							continue playerLoop;
						}
					}
					positions.add(pos);
				}
				for (var pos : positions) {
					addBlackHole(pos);
				}
			}

			if (timeSinceReset > 2500 || world.getPlayers().isEmpty()) {
				clear();
			}
		}
	}

	private void clear() {
		if (clearing) return;
		clearing = true;
		List<ServerPlayerEntity> players = new ArrayList<>(world.getPlayers());
		for (var player : players) {
			teleportOut(player);
		}
		WorldDeleter.deleteWorldTeleportingPlayers(
			world, () -> {
				clearing = false;
				resetting = false;
				world.savingDisabled = false;
				TaskScheduler.scheduleImmediately(world.getServer(), () -> {
					DungeonData.getInstance(world.getServer().getWorld(world.getRegistryKey())).copyFromPrevious(this);
				});
				METAcraftDungeons.LOGGER.info("Reset of " + world.getRegistryKey().getValue() + " completed.");
			},
			player -> new TeleportTarget(
					world.getServer().getWorld(exitDim),
					getExitPos().toCenterPos(), player.getVelocity(), player.getYaw(), player.getPitch(),
					TeleportTarget.NO_OP
			)
		);
	}

	public ServerWorld getExitWorld() {
		ServerWorld targetWorld = world.getServer().getWorld(exitDim);
		if (targetWorld == null) {
			exitDim = World.OVERWORLD;
			targetWorld = world.getServer().getOverworld();
			markDirty();
		}
		return targetWorld;
	}

	public BlockPos getExitPos() {
		ServerWorld targetWorld = getExitWorld();
		BlockPos.Mutable target = new BlockPos.Mutable();
		while (true) {
			int first = targetWorld.getRandom().nextInt(
					MathHelper.floor(maxRangeFromExitPos*2)
			) - MathHelper.floor(maxRangeFromExitPos);
			int second = targetWorld.getRandom().nextInt(
					(MathHelper.floor(maxRangeFromExitPos) - first)*2
			) - MathHelper.floor(MathHelper.floor(maxRangeFromExitPos) - first);
			target.setY(exitPos.getY());
			if (targetWorld.getRandom().nextBoolean()) {
				target.setX(first + exitPos.getX());
				target.setZ(second + exitPos.getZ());
			} else {
				target.setX(second + exitPos.getX());
				target.setZ(first + exitPos.getZ());
			}
			var nbt = targetWorld.getChunkManager().chunkLoadingManager.getNbt(new ChunkPos(exitPos)).join();
			if (nbt.isPresent()) {
				BlockPos.Mutable below = new BlockPos.Mutable();
				below.set(target.getX(), target.getY()-1, target.getZ());
				if (targetWorld.getBlockState(target).isAir() && targetWorld.getBlockState(target.up()).isAir()) {
					if (!targetWorld.getBlockState(below).isSolidBlock(targetWorld, below)) {
						for (int i = 0; i < 100; i++) {
							below.setY(below.getY()-1);
							if (targetWorld.getBlockState(below).isSolidBlock(targetWorld, below)) {
								target.setY(below.getY());
								break;
							}
						}
						if (!targetWorld.getBlockState(below).isSolidBlock(targetWorld, below)) {
							target.setY(targetWorld.getTopY(Heightmap.Type.MOTION_BLOCKING, target.getX(), target.getZ()));
						}
					}
				} else {
					target.setY(targetWorld.getTopY(Heightmap.Type.MOTION_BLOCKING, target.getX(), target.getZ()));
				}
				if (world.getBlockState(target).isAir()) {
					return target.toImmutable();
				}
			} else {
				target.setY(targetWorld.getChunkManager().getChunkGenerator().getHeightOnGround(
						target.getX(), target.getZ(), Heightmap.Type.WORLD_SURFACE_WG,
						targetWorld, targetWorld.getChunkManager().getNoiseConfig()
				));
				return target;
			}
		}
	}

	private void addBlackHole(BlockPos pos) {
		for (var spherePos : BlockPos.iterateOutwards(pos, 15, 15, 15)) {
			if (world.getBlockState(spherePos).isIn(Tags.DUNGEON_RESET_UNBREAKABLE)) continue;
			if (spherePos.isWithinDistance(pos, 5)) {
				world.setBlockState(spherePos, DungeonBlocks.DUMMY_PORTAL.getDefaultState());
			} else if (spherePos.isWithinDistance(pos, 15)) {
				if (!world.isAir(spherePos)) {
					world.breakBlock(spherePos, false);
				}
			}
		}
		world.setBlockState(pos, DungeonBlocks.BLACK_HOLE.getDefaultState());
		var entity = ((BlackHolePortalEntity) world.getBlockEntity(pos));
		entity.setAttractionRange(dungeonWidth/2.0);
		entity.setTargetDim(exitDim);
		entity.setTargetPos(getExitPos());
		entity.setShouldTeleport(shouldTeleport);
	}

	public BlockPos getNextSpawnPos() {
		var pos = PositionFinder.findPosAroundOrigin(index++, dungeonWidth+1);
		markDirty();
		if (!world.getWorldBorder().contains(pos.x(), pos.z())) {
			resetAllDungeons();
			METAcraftDungeons.LOGGER.error("Dungeon Dimension reached the maximum number of allowed dungeons, flushing dimension.");
			return getNextSpawnPos();
		}
		return BlockPos.ofFloored(pos.x(), world.getBottomY() + world.getHeight()/2.0, pos.z());
	}

	public void addExternalEntrance(RegistryKey<World> dim, BlockPos pos) {
		externalEntrances.add(new EntranceEntry(dim, pos));
		markDirty();
	}

	public void loadMusicBlock(MusicBlockEntity block) {
		knownMusicBlocks.add(block);
	}
	public void unloadMusicBlock(MusicBlockEntity block) {
		knownMusicBlocks.remove(block);
	}

	public record EntranceEntry(RegistryKey<World> dim, BlockPos pos) {
		public static final Codec<EntranceEntry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				World.CODEC.fieldOf("dim").forGetter(EntranceEntry::dim),
				BlockPos.CODEC.fieldOf("pos").forGetter(EntranceEntry::pos)
			).apply(instance, EntranceEntry::new)
		);
		public static final Codec<List<EntranceEntry>> LIST_CODEC = CODEC.listOf();
	}
}
