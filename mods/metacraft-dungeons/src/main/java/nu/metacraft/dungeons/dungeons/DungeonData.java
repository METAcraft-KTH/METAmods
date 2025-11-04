package nu.metacraft.dungeons.dungeons;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.entities.BlackHolePortalEntity;
import nu.metacraft.core.block.entities.MusicBlockEntity;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.portal.FixedPortalTarget;
import nu.metacraft.core.util.TeleportPredicate;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.dungeons.Tags;
import nu.metacraft.dungeons.compat.SquaremapCompat;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.lib.time_getter.RegularTimeGetter;
import nu.metacraft.lib.util.PositionFinder;
import nu.metacraft.dungeons.util.WorldDeleter;
import nu.metacraft.lib.util.helper.TeleportHelper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

public class DungeonData extends SavedData {

	private static final String key = METAcraftDungeons.MODID;

	private static final SavedDataType<DungeonData> TYPE = new SavedDataType<>(
			key, ctx -> create(ctx.levelOrThrow()),
			ctx -> createCodec(ctx.levelOrThrow()), null
	);

	private static Codec<DungeonData> createCodec(ServerLevel world) {
		return RecordCodecBuilder.create(
				instance -> instance.group(
						Codec.LONG.fieldOf("index").forGetter(d -> d.index),
						Codec.INT.fieldOf("width").forGetter(d -> d.dungeonWidth),
						Level.RESOURCE_KEY_CODEC.optionalFieldOf("exit_dim", world.getServer().getRespawnData().dimension()).forGetter(d -> d.exitDim),
						BlockPos.CODEC.fieldOf("exit_pos").orElse(world.getServer().getRespawnData().pos()).forGetter(d -> d.exitPos),
						Codec.DOUBLE.fieldOf("max_range_from_exit_pos").forGetter(d -> d.maxRangeFromExitPos),
						Codec.BOOL.fieldOf("clearing").forGetter(d -> d.clearing),
						Codec.BOOL.fieldOf("resetting").forGetter(d -> d.resetting),
						Codec.INT.fieldOf("time_since_reset").forGetter(d -> d.timeSinceReset),
						EntranceEntry.LIST_CODEC.fieldOf("external_entrances").forGetter(d -> d.externalEntrances),
						TeleportPredicate.LIST_CODEC.fieldOf("should_teleport").forGetter(d -> d.shouldTeleport),
						RegularTimeGetter.REGISTRY_CODEC.optionalFieldOf("reset_getter").forGetter(d -> d.resetGetter),
						ExtraCodecs.INSTANT_ISO8601.optionalFieldOf("next_reset").forGetter(d -> d.nextReset)
				).apply(instance, DungeonData.create(world)::load)
		);
	}

	private int dungeonWidth;
	private ResourceKey<Level> exitDim;
	private BlockPos exitPos;
	private double maxRangeFromExitPos = 100;
	private final List<TeleportPredicate> shouldTeleport = new ArrayList<>(
			ImmutableList.of(
					new TeleportPredicate(
							EntityPredicate.Builder.entity().entityType(
									EntityTypePredicate.of(
											BuiltInRegistries.ENTITY_TYPE, EntityType.FALLING_BLOCK
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


	public static DungeonData getInstance(ServerLevel world) {
		return world.getDataStorage().computeIfAbsent(TYPE);
	}

	public static Optional<DungeonData> getIfPresent(ServerLevel world) {
		return Optional.ofNullable(world.getDataStorage().get(TYPE));
	}

	private final ServerLevel world;

	private DungeonData(ServerLevel world) {
		this.world = world;
		dungeonWidth = world.getHeight();
		exitDim = world.getServer().getRespawnData().dimension();
		exitPos = world.getServer().getRespawnData().pos();
		fixSquaremap();
	}

	public int getDungeonWidth() {
		return dungeonWidth;
	}

	private static DungeonData create(ServerLevel world) {
		return new DungeonData(world);
	}

	private DungeonData load(
			long index, int width, ResourceKey<Level> exitDim, BlockPos exitPos,
			double maxRangeFromExitPos, boolean clearing, boolean resetting, int timeSinceReset,
			Set<EntranceEntry> externalEntrances, List<TeleportPredicate> shouldTeleport,
			Optional<RegularTimeGetter> resetGetter, Optional<Instant> nextReset
	) {
		this.index = index;
		this.dungeonWidth = width;
		this.exitDim = exitDim;
		this.exitPos = exitPos;
		this.maxRangeFromExitPos = maxRangeFromExitPos;
		this.clearing = clearing;
		this.resetting = resetting;
		this.timeSinceReset = timeSinceReset;
		this.externalEntrances.addAll(externalEntrances);
		this.shouldTeleport.addAll(shouldTeleport);
		this.resetGetter = resetGetter;
		this.nextReset = nextReset;

		if (!this.resetting) {
			this.clearing = false;
			this.timeSinceReset = 0;
		}
		if (this.clearing) {
			clearingRestarted = true;
		}

		return this;
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
		setDirty();
	}

	public void resetIndexCounter() {
		index = 0;
		setDirty();
	}
	public boolean isResetting() {
		return resetting;
	}

	public boolean isClearing() {
		return clearing;
	}

	public void resetDimension() {
		if (world.dimension() == Level.OVERWORLD) {
			METAcraftDungeons.LOGGER.error("No, I refuse to delete the overworld!");
			return;
		}
		world.noSave = true;
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
		var exitPos = getExitPos().getCenter();
		entity.teleport(
				new TeleportTransition(
						world.getServer().getLevel(exitDim), exitPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
						pet -> {
							pet.fallDistance = 0;
						}
				)
		);
	}

	private boolean hasOwnerPlayer(OwnableEntity tameable) {
		if (tameable.getRootOwner() instanceof ServerPlayer) return true;
		return tameable.getOwnerReference() != null && world.getServer().services().nameToIdCache().get(tameable.getOwnerReference().getUUID()).isPresent();
	}

	private <T extends LivingEntity & OwnableEntity> void forAllPets(Consumer<T> action) {
		for (var pet : world.getEntities(
				EntityTypeTest.forClass(LivingEntity.class),
				entity -> entity instanceof OwnableEntity t && hasOwnerPlayer(t))
		) {
			action.accept((T) pet);
		}
	}

	private void fixSquaremap() {
		IsLoaded.SQUAREMAP.ifLoaded(() -> { //Squaremap causes lag spikes so bad the server crashes, but not if we disable the renderer.
			SquaremapCompat.disableRenderer(world.dimension());
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
			if (pet.getY() < world.getMinY()) {
				pet.fallDistance = 0;
				var player = pet.getRootOwner() instanceof ServerPlayer p ? p : null;
				if (player == null) {
					teleportOut(pet);
				} else {
					TeleportHelper.teleportEntityToPlayer(player, pet);
				}
			}
		});
		for (var player : new ArrayList<>(world.players())) {
			if (player.getY() < world.getMinY()) {
				player.fallDistance = 0;
				teleportOut(player);
			}
		}
		if (!resetting) {
			if (nextReset.isPresent()) {
				var now = Instant.now();
				if (now.isAfter(nextReset.get().minus(15, ChronoUnit.MINUTES)) && !hasWarned) {
					world.players().forEach(
							player -> {
								player.sendSystemMessage(Component.literal("You hear an ominous sound in the distance").withStyle(style -> style.withColor(ChatFormatting.DARK_PURPLE)));
								player.sendSystemMessage(Component.literal("The sound fills you with dread").withStyle(style -> style.withColor(ChatFormatting.RED)));
								player.sendSystemMessage(Component.literal("Perhaps I should get out of here?").withStyle(style -> style.withColor(ChatFormatting.RED)));
								player.level().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.PORTAL_TRIGGER, SoundSource.MASTER, 0.15f, 0.5f
								);
								player.level().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 0.15f, 0.5f
								);
							}
					);
					hasWarned = true;
				}
				if (now.isAfter(nextReset.get())) {
					world.players().forEach(
							player -> {
								player.sendSystemMessage(Component.literal("The dimension is collapsing in on itself").withStyle(style -> style.withColor(ChatFormatting.DARK_RED)));
								player.sendSystemMessage(Component.literal("Get out, get out, GET OUT!").withStyle(style -> style.withColor(ChatFormatting.RED)));
								player.level().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.PORTAL_TRIGGER, SoundSource.MASTER,1, 0.5f
								);
								player.level().playSound(
										null, player.getX(), player.getY(), player.getZ(),
										SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 1, 0.5f
								);
							}
					);
					resetDimension();
				}
			}
			if (resetGetter.isPresent() && nextReset.isEmpty()) {
				nextReset = Optional.of(resetGetter.get().getNextTime(Instant.now()));
				setDirty();
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
			for (var player : world.players()) {
				if (player.tickCount % (player.getRandom().nextInt(comparison) + 1) == 0) {
					int count = player.getRandom().nextInt(250);
					int range = player.getRandom().nextInt(50);
					for (BlockPos pos : BlockPos.randomInCube(player.getRandom(), count, player.blockPosition(), range)) {
						if (world.getBlockState(pos).is(Tags.DUNGEON_RESET_UNBREAKABLE) || world.getBlockState(pos).isAir()) {
							continue;
						}
						var centerPos = pos.getCenter();
						world.sendParticles(
								new BlockParticleOption(ParticleTypes.FALLING_DUST, world.getBlockState(pos)),
								centerPos.x, centerPos.y-3, centerPos.z, 5,
								player.getRandom().nextDouble(),
								player.getRandom().nextDouble() * 3,
								player.getRandom().nextDouble(),
								1
						);
						if (world.getBlockState(pos.below()).isAir() && world.getBlockEntity(pos) == null && player.getRandom().nextDouble() > 0.5) {
							var falling = FallingBlockEntity.fall(world, pos, world.getBlockState(pos));
							falling.dropItem = false;
						} else {
							world.setBlock(pos, world.getBlockState(pos).getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
						}
					}
				}
			}
			if (timeSinceReset == 1500) {
				List<BlockPos> positions = new ArrayList<>(world.players().size());
				playerLoop: for (var player : world.players()) {
					BlockPos pos;
					do {
						pos = player.blockPosition().offset(
								player.getRandom().nextInt(50) - 25,
								Mth.clamp(
										player.getRandom().nextInt(50) - 25,
										world.getMinY() + 10,
										world.getMinY() + world.getHeight() - 10
								),
								player.getRandom().nextInt(50) - 25
						);
					} while (world.getBlockState(pos).is(Tags.DUNGEON_RESET_UNBREAKABLE));
					for (var position : positions) {
						if (pos.closerThan(position, dungeonWidth)) {
							continue playerLoop;
						}
					}
					positions.add(pos);
				}
				for (var pos : positions) {
					addBlackHole(pos);
				}
			}

			if (timeSinceReset > 2500 || world.players().isEmpty()) {
				clear();
			}
		}
	}

	private void clear() {
		if (clearing) return;
		clearing = true;
		List<ServerPlayer> players = new ArrayList<>(world.players());
		for (var player : players) {
			teleportOut(player);
		}
		List<ThrownEnderpearl> pearlsToRemove = new ArrayList<>();
		for (var player : world.getServer().getPlayerList().getPlayers()) {
			for (var pearl : player.getEnderPearls()) {
				if (pearl.level().dimension() == world.dimension()) {
					pearlsToRemove.add(pearl);
				}
			}
			if (player.getRespawnConfig() != null && player.getRespawnConfig().respawnData().dimension() == world.dimension()) {
				player.setRespawnPosition(null, false);
				player.sendSystemMessage(Component.literal("Respawn point reset"));
			}
		}
		pearlsToRemove.forEach(ThrownEnderpearl::discard);
		setDirty();
		List<EntranceEntry> entrancesToReinitialize = new ArrayList<>();
		for (var entrancePos : externalEntrances) {
			var entranceWorld = world.getServer().getLevel(entrancePos.dim);
			if (
					entranceWorld != null &&
					entranceWorld.getBlockEntity(entrancePos.pos) instanceof PortalEntity entrance
			) {
				entrancesToReinitialize.add(entrancePos);
				entrance.setTarget(entrance.getTarget().getAsEmpty());
			}
		}
		externalEntrances.clear();
		world.getDataStorage().saveAndJoin();

		WorldDeleter.deleteWorldTeleportingPlayers(
			world, () -> {
				clearing = false;
				resetting = false;
				DungeonData.getInstance(world.getServer().getLevel(world.dimension())).copyFromPrevious(this);
				for (var player : world.getServer().getPlayerList().getPlayers()) {
					player.sendSystemMessage(Component.literal("The dungeon portal opens again").withStyle(style -> style.withColor(ChatFormatting.DARK_AQUA)));
				}
				METAcraftDungeons.LOGGER.info("Reset of " + world.dimension().location() + " completed.");
				for (var entrance : entrancesToReinitialize) {
					var e = world.getServer().getLevel(entrance.dim).getBlockEntity(entrance.pos);
					if (e instanceof PortalEntity p) {
						p.initializeTarget();
					}
				}
			}, file -> file.endsWith(key + ".dat"),
			player -> new TeleportTransition(
					world.getServer().getLevel(exitDim),
					getExitPos().getCenter(), DisconnectedPlayerHelper.getVelocity(player),
					DisconnectedPlayerHelper.getYaw(player), DisconnectedPlayerHelper.getPitch(player),
					TeleportTransition.DO_NOTHING
			)
		);
	}

	public ServerLevel getExitWorld() {
		ServerLevel targetWorld = world.getServer().getLevel(exitDim);
		if (targetWorld == null) {
			exitDim = Level.OVERWORLD;
			targetWorld = world.getServer().overworld();
			setDirty();
		}
		return targetWorld;
	}

	public BlockPos getExitPos() {
		ServerLevel targetWorld = getExitWorld();
		BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
		int tries = 0;
		while (true) {
			int first = targetWorld.getRandom().nextInt(
					Mth.floor(maxRangeFromExitPos*2)
			) - Mth.floor(maxRangeFromExitPos);
			int second = targetWorld.getRandom().nextInt(
					(Mth.floor(maxRangeFromExitPos) - first)*2
			) - Mth.floor(Mth.floor(maxRangeFromExitPos) - first);
			target.setY(exitPos.getY());
			if (targetWorld.getRandom().nextBoolean()) {
				target.setX(first + exitPos.getX());
				target.setZ(second + exitPos.getZ());
			} else {
				target.setX(second + exitPos.getX());
				target.setZ(first + exitPos.getZ());
			}
			var nbt = targetWorld.getChunkSource().chunkMap.read(new ChunkPos(target)).join();
			if (nbt.isPresent() && tries++ < 1000) {
				BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
				below.set(target.getX(), target.getY()-1, target.getZ());
				if (targetWorld.getBlockState(target).isAir() && targetWorld.getBlockState(target.above()).isAir()) {
					if (!targetWorld.getBlockState(below).isRedstoneConductor(targetWorld, below)) {
						for (int i = 0; i < 100; i++) {
							below.setY(below.getY()-1);
							if (targetWorld.getBlockState(below).isRedstoneConductor(targetWorld, below)) {
								target.setY(below.getY());
								break;
							}
						}
						if (!targetWorld.getBlockState(below).isRedstoneConductor(targetWorld, below)) {
							target.setY(targetWorld.getHeight(Heightmap.Types.MOTION_BLOCKING, target.getX(), target.getZ()));
						}
					}
				} else {
					target.setY(targetWorld.getHeight(Heightmap.Types.MOTION_BLOCKING, target.getX(), target.getZ()));
				}
				if (world.getBlockState(target).isAir()) {
					return target.immutable();
				}
			} else {
				target.setY(targetWorld.getChunkSource().getGenerator().getFirstFreeHeight(
						target.getX(), target.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
						targetWorld, targetWorld.getChunkSource().randomState()
				));
				return target;
			}
		}
	}

	private void addBlackHole(BlockPos pos) {
		for (var spherePos : BlockPos.withinManhattan(pos, 15, 15, 15)) {
			if (world.getBlockState(spherePos).is(Tags.DUNGEON_RESET_UNBREAKABLE)) continue;
			if (spherePos.closerThan(pos, 5)) {
				world.setBlockAndUpdate(spherePos, METAcraftBlocks.PORTAL_PADDING.defaultBlockState());
			} else if (spherePos.closerThan(pos, 15)) {
				if (!world.isEmptyBlock(spherePos)) {
					world.setBlockAndUpdate(spherePos, world.getBlockState(spherePos).getFluidState().createLegacyBlock());
				}
			}
		}
		world.setBlockAndUpdate(pos, METAcraftBlocks.BLACK_HOLE_CORE.defaultBlockState());
		var entity = ((BlackHolePortalEntity) world.getBlockEntity(pos));
		entity.setAttractionRange(dungeonWidth/2.0);
		entity.setTarget(FixedPortalTarget.create(exitDim, getExitPos()));
		entity.setShouldTeleport(shouldTeleport);
	}

	public BlockPos getNextSpawnPos() {
		var pos = PositionFinder.findPosAroundOrigin(index++, dungeonWidth+1);
		setDirty();
		if (!world.getWorldBorder().isWithinBounds(pos.x(), pos.z())) {
			resetIndexCounter();
			METAcraftDungeons.LOGGER.error("Dungeon Dimension reached the maximum number of allowed dungeons, flushing dimension.");
			return getNextSpawnPos();
		}
		return BlockPos.containing(pos.x(), world.getMinY() + world.getHeight()/2.0, pos.z());
	}

	public void addExternalEntrance(ResourceKey<Level> dim, BlockPos pos) {
		externalEntrances.add(new EntranceEntry(dim, pos));
		setDirty();
	}

	public void loadMusicBlock(MusicBlockEntity block) {
		knownMusicBlocks.add(block);
	}
	public void unloadMusicBlock(MusicBlockEntity block) {
		knownMusicBlocks.remove(block);
	}

	public record EntranceEntry(ResourceKey<Level> dim, BlockPos pos) {
		public static final Codec<EntranceEntry> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(EntranceEntry::dim),
				BlockPos.CODEC.fieldOf("pos").forGetter(EntranceEntry::pos)
			).apply(instance, EntranceEntry::new)
		);
		public static final Codec<Set<EntranceEntry>> LIST_CODEC = CODEC.listOf().xmap(
				HashSet::new, ArrayList::new
		);
	}
}
