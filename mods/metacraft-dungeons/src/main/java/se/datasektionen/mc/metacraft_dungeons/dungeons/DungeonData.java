package se.datasektionen.mc.metacraft_dungeons.dungeons;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.EntityTypePredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.block.entities.BlackHolePortalEntity;
import se.datasektionen.mc.metacraft_core.block.entities.MusicBlockEntity;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_core.portal.FixedPortalTarget;
import se.datasektionen.mc.metacraft_core.util.TeleportPredicate;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.Tags;
import se.datasektionen.mc.metacraft_dungeons.compat.SquaremapCompat;
import se.datasektionen.mc.metacraft_dungeons.util.DisconnectedPlayerHelper;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.metacraft_lib.time_getter.RegularTimeGetter;
import se.datasektionen.mc.metacraft_lib.util.PositionFinder;
import se.datasektionen.mc.metacraft_dungeons.util.WorldDeleter;
import se.datasektionen.mc.metacraft_lib.util.helper.TeleportHelper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

	private static final String RESET_GETTER = "reset_getter";
	private static final String NEXT_RESET = "next_reset";

	private static PersistentState.Type<DungeonData> getType(ServerWorld world) {
		return new Type<>(
				() -> create(world), (nbt, wrapper) -> load(world, nbt, wrapper), null
		);
	}

	private int dungeonWidth;
	private RegistryKey<World> exitDim = World.OVERWORLD;
	private BlockPos exitPos;
	private double maxRangeFromExitPos = 100;
	private final List<TeleportPredicate> shouldTeleport = new ArrayList<>(
			ImmutableList.of(
					new TeleportPredicate(
							EntityPredicate.Builder.create().type(
									EntityTypePredicate.create(
											Registries.ENTITY_TYPE, EntityType.FALLING_BLOCK
									)
							).build(),
							false
					)
			)
	);


	private long index = 0;
	private boolean resetting = false;
	private boolean clearing = false;
	private boolean clearingRestarted = false;
	private int timeSinceReset = 0;
	private final Set<EntranceEntry> externalEntrances = new HashSet<>();
	private Optional<RegularTimeGetter> resetGetter = Optional.empty();
	private Optional<Instant> nextReset = Optional.empty();

	private boolean hasWarned = false;

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
		dungeonWidth = world.getHeight();
		exitPos = world.getServer().getOverworld().getSpawnPos();
		fixSquaremap();
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
			clearingRestarted = true;
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

		if (nbt.contains(NEXT_RESET)) {
			nextReset = Codecs.INSTANT.parse(
					lookup.getOps(NbtOps.INSTANCE),
					nbt.get(NEXT_RESET)
			).resultOrPartial(METAcraftDungeons.LOGGER::error);
		} else {
			nextReset = Optional.empty();
		}

		if (nbt.contains(RESET_GETTER)) {
			resetGetter = RegularTimeGetter.REGISTRY_CODEC.parse(
					lookup.getOps(NbtOps.INSTANCE),
					nbt.get(RESET_GETTER)
			).resultOrPartial(METAcraftDungeons.LOGGER::error);
		} else {
			resetGetter = Optional.empty();
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
		nextReset.ifPresent(instant -> nbt.put(
				NEXT_RESET,
				Codecs.INSTANT.encodeStart(
						lookup.getOps(NbtOps.INSTANCE),
						instant
				).getOrThrow()
		));
		resetGetter.ifPresent(
				resetGetter -> nbt.put(
						RESET_GETTER,
						RegularTimeGetter.REGISTRY_CODEC.encodeStart(
								lookup.getOps(NbtOps.INSTANCE),
								resetGetter
						).getOrThrow()
				)
		);
		return nbt;
	}

	private void copyFromPrevious(DungeonData data) {
		this.resetting = data.resetting;
		this.clearing = data.clearing;
		if (!clearing) {
			clearingRestarted = false;
		}
		this.exitDim = data.exitDim;
		this.exitPos = data.exitPos;
		this.maxRangeFromExitPos = data.maxRangeFromExitPos;
		this.dungeonWidth = data.dungeonWidth;
		this.resetGetter = data.resetGetter;
		this.shouldTeleport.clear();
		this.shouldTeleport.addAll(data.shouldTeleport);
		markDirty();
	}

	public void resetIndexCounter() {
		index = 0;
		markDirty();
	}
	public boolean isResetting() {
		return resetting;
	}

	public boolean isClearing() {
		return clearing;
	}

	public void resetDimension() {
		if (world.getRegistryKey() == World.OVERWORLD) {
			METAcraftDungeons.LOGGER.error("No, I refuse to delete the overworld!");
			return;
		}
		world.savingDisabled = true;
		resetting = true;
		nextReset = Optional.empty();
		resetIndexCounter();
		hasWarned = false;
	}

	private boolean shouldTeleport(Entity entity) {
		return TeleportPredicate.shouldTeleport(shouldTeleport, world, entity);
	}

	public void teleportOut(Entity entity) {
		entity = entity.getRootVehicle();
		if (!shouldTeleport(entity)) {
			entity.kill(world);
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

	private void fixSquaremap() {
		IsLoaded.SQUAREMAP.ifLoaded(() -> { //Squaremap causes lag spikes so bad the server crashes, but not if we disable the renderer.
			SquaremapCompat.disableRenderer(world.getRegistryKey());
		});
	}

	public void tick() {
		fixSquaremap();
		if (clearingRestarted) {
			clearingRestarted = false;
			clearing = false;
			clear();
			return;
		}
		forAllPets(pet -> {
			if (pet.getY() < world.getBottomY()) {
				pet.fallDistance = 0;
				var player = world.getServer().getPlayerManager().getPlayer(pet.getOwnerUuid());
				if (player == null) {
					teleportOut(pet);
				} else {
					TeleportHelper.teleportEntityToPlayer(player, pet);
				}
			}
		});
		for (var player : new ArrayList<>(world.getPlayers())) {
			if (player.getY() < world.getBottomY()) {
				player.fallDistance = 0;
				teleportOut(player);
			}
		}
		if (!resetting) {
			if (nextReset.isPresent()) {
				var now = Instant.now();
				if (now.isAfter(nextReset.get().minus(15, ChronoUnit.MINUTES)) && !hasWarned) {
					world.getPlayers().forEach(
							player -> {
								player.sendMessage(Text.literal("You hear an ominous sound in the distance").styled(style -> style.withColor(Formatting.DARK_PURPLE)));
								player.sendMessage(Text.literal("The sound fills you with dread").styled(style -> style.withColor(Formatting.RED)));
								player.sendMessage(Text.literal("Perhaps I should get out of here?").styled(style -> style.withColor(Formatting.RED)));
								player.getServerWorld().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.MASTER, 0.15f, 0.5f
								);
								player.getServerWorld().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.15f, 0.5f
								);
							}
					);
					hasWarned = true;
				}
				if (now.isAfter(nextReset.get())) {
					world.getPlayers().forEach(
							player -> {
								player.sendMessage(Text.literal("The dimension is collapsing in on itself").styled(style -> style.withColor(Formatting.DARK_RED)));
								player.sendMessage(Text.literal("Get out, get out, GET OUT!").styled(style -> style.withColor(Formatting.RED)));
								player.getServerWorld().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.MASTER,1, 0.5f
								);
								player.getServerWorld().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 1, 0.5f
								);
							}
					);
					resetDimension();
				}
			}
			if (resetGetter.isPresent() && nextReset.isEmpty()) {
				nextReset = Optional.of(resetGetter.get().getNextTime(Instant.now()));
				markDirty();
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
								centerPos.x, centerPos.y-3, centerPos.z, 5,
								player.getRandom().nextDouble(),
								player.getRandom().nextDouble() * 3,
								player.getRandom().nextDouble(),
								1
						);
						if (world.getBlockState(pos.down()).isAir() && world.getBlockEntity(pos) == null && player.getRandom().nextDouble() > 0.5) {
							var falling = FallingBlockEntity.spawnFromBlock(world, pos, world.getBlockState(pos));
							falling.dropItem = false;
						} else {
							world.setBlockState(pos, world.getBlockState(pos).getFluidState().getBlockState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
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
		List<EnderPearlEntity> pearlsToRemove = new ArrayList<>();
		for (var player : world.getServer().getPlayerManager().getPlayerList()) {
			for (var pearl : player.getEnderPearls()) {
				if (pearl.getWorld().getRegistryKey() == world.getRegistryKey()) {
					pearlsToRemove.add(pearl);
				}
			}
			if (player.getSpawnPointDimension() == world.getRegistryKey()) {
				player.setSpawnPoint(ServerWorld.OVERWORLD, null, 0, false, false);
				player.sendMessage(Text.literal("Respawn point reset"));
			}
		}
		pearlsToRemove.forEach(EnderPearlEntity::discard);
		markDirty();
		List<EntranceEntry> entrancesToReinitialize = new ArrayList<>();
		for (var entrancePos : externalEntrances) {
			var entranceWorld = world.getServer().getWorld(entrancePos.dim);
			if (
					entranceWorld != null &&
					entranceWorld.getBlockEntity(entrancePos.pos) instanceof PortalEntity entrance
			) {
				entrancesToReinitialize.add(entrancePos);
				entrance.setTarget(entrance.getTarget().getAsEmpty());
			}
		}
		externalEntrances.clear();
		world.getPersistentStateManager().save();

		WorldDeleter.deleteWorldTeleportingPlayers(
			world, () -> {
				clearing = false;
				resetting = false;
				DungeonData.getInstance(world.getServer().getWorld(world.getRegistryKey())).copyFromPrevious(this);
				for (var player : world.getServer().getPlayerManager().getPlayerList()) {
					player.sendMessage(Text.literal("The dungeon portal opens again").styled(style -> style.withColor(Formatting.DARK_AQUA)));
				}
				METAcraftDungeons.LOGGER.info("Reset of " + world.getRegistryKey().getValue() + " completed.");
				for (var entrance : entrancesToReinitialize) {
					var e = world.getServer().getWorld(entrance.dim).getBlockEntity(entrance.pos);
					if (e instanceof PortalEntity p) {
						p.initializeTarget();
					}
				}
			}, file -> file.endsWith(key + ".dat"),
			player -> new TeleportTarget(
					world.getServer().getWorld(exitDim),
					getExitPos().toCenterPos(), DisconnectedPlayerHelper.getVelocity(player),
					DisconnectedPlayerHelper.getYaw(player), DisconnectedPlayerHelper.getPitch(player),
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
		int tries = 0;
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
			var nbt = targetWorld.getChunkManager().chunkLoadingManager.getNbt(new ChunkPos(target)).join();
			if (nbt.isPresent() && tries++ < 1000) {
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
				world.setBlockState(spherePos, METAcraftBlocks.PORTAL_PADDING.getDefaultState());
			} else if (spherePos.isWithinDistance(pos, 15)) {
				if (!world.isAir(spherePos)) {
					world.setBlockState(spherePos, world.getBlockState(spherePos).getFluidState().getBlockState());
				}
			}
		}
		world.setBlockState(pos, METAcraftBlocks.BLACK_HOLE_CORE.getDefaultState());
		var entity = ((BlackHolePortalEntity) world.getBlockEntity(pos));
		entity.setAttractionRange(dungeonWidth/2.0);
		entity.setTarget(FixedPortalTarget.create(exitDim, getExitPos()));
		entity.setShouldTeleport(shouldTeleport);
	}

	public BlockPos getNextSpawnPos() {
		var pos = PositionFinder.findPosAroundOrigin(index++, dungeonWidth+1);
		markDirty();
		if (!world.getWorldBorder().contains(pos.x(), pos.z())) {
			resetIndexCounter();
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
		public static final Codec<Set<EntranceEntry>> LIST_CODEC = CODEC.listOf().xmap(
				HashSet::new, ArrayList::new
		);
	}
}
