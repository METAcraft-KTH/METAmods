package se.datasektionen.mc.cutscenes.cutscene.world;

import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.block.Block;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardState;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.*;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.cutscene.Cutscene;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.extension.ServerScoreboardExtensions;
import se.datasektionen.mc.cutscenes.mixin.*;
import se.datasektionen.mc.cutscenes.util.SerialisedStructure;
import se.datasektionen.mc.metacraft_lib.util.helper.StructureTemplateHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.WorldHelper;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class CutsceneWorld extends ServerWorld implements ServerWorldAccess {

	private final ServerWorld world;
	private final CutsceneInstance cutscene;
	private CutsceneChunkManager manager;
	private final CutsceneEntityManager entities;

	private ServerScoreboard scoreboard;

	private final EntityLookup<Entity> lookup;

	private CutscenePersistentStateManager persistentStateManager;
	private NbtCompound persistentStorage = new NbtCompound();

	public static ChunkGenerator createDummyChunkGenerator(World world) {
		return new FlatChunkGenerator(new FlatChunkGeneratorConfig(
				Optional.empty(), world.getBiome(BlockPos.ORIGIN), List.of()
		));
	}

	private static ServerWorldProperties readProperties(
			NbtCompound nbt, ServerWorld parent
	) {
		var p = parent.getServer().getSaveProperties();
		return LevelProperties.readProperties(
				new Dynamic<>(
						NbtOps.INSTANCE,
						nbt
				), p.getLevelInfo(),
				p.isFlatWorld() ? LevelProperties.SpecialProperty.FLAT : p.isDebugWorld() ? LevelProperties.SpecialProperty.DEBUG : LevelProperties.SpecialProperty.NONE,
				p.getGeneratorOptions(), p.getLifecycle()
		);
	}

	private static NbtCompound getInitialSavePropertiesData(
			CutsceneWorldData data, ServerWorld world
	) {
		return data != null ? data.saveProperties() : world.getServer().getSaveProperties().cloneWorldNbt(
				world.getRegistryManager(), null
		);
	}

	private static ServerWorldProperties getProperties(
			CutsceneWorldData data, ServerWorld parent
	) {
		return readProperties(
				getInitialSavePropertiesData(data, parent), parent
		);
	}

	public CutsceneWorld(ServerWorld world, CutsceneInstance cutscene, CutsceneWorldData data) {
		super(
				world.getServer(), ((AccessorMinecraftServer) world.getServer()).getWorkerExecutor(),
				((AccessorMinecraftServer) world.getServer()).getSession(),
				getProperties(data, world),
				world.getRegistryKey(),
				new DimensionOptions(
						world.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION_TYPE).getEntry(world.getDimension()),
						createDummyChunkGenerator(world)
				),
				WorldHelper.getGenerationProgressListener(world),
				world.isDebugWorld(), world.getSeed(), List.of(), true, world.getRandomSequences()
		);
		this.savingDisabled = true;
		this.cutscene = cutscene;
		this.world = world;
		this.manager = new CutsceneChunkManager(this, this::getPersistentStateManager);
		this.entities = new CutsceneEntityManager(this);
		this.lookup = new CombinedEntityLookup(
				List.of(entities.getLookup(), ((AccessorServerWorld) world).callGetEntityLookup())
		);
		((AccessorServerWorld) this).setEntityManager(entities.createDummyEntityManager());
		if (data != null) {
			load(data);
		}
		initScoreboard(data == null);
	}

	private void initScoreboard(boolean hasDataToLoad) {
		if (cutscene.getCutscene().getScoreboardMode() == Cutscene.ScoreboardMode.SYNC) {
			scoreboard = world.getScoreboard();
		} else {
			scoreboard = new ServerScoreboard(getServer());
			((ServerScoreboardExtensions) scoreboard).metacraft$setConnectedCutscene(cutscene);
			if (cutscene.getCutscene().getScoreboardMode() == Cutscene.ScoreboardMode.COPY && !hasDataToLoad) {
				var defaultScoreboard = world.getScoreboard();
				for (var ob : defaultScoreboard.getObjectives()) {
					scoreboard.addObjective(
							ob.getName(), ob.getCriterion(),
							ob.getDisplayName(), ob.getRenderType(),
							ob.shouldDisplayAutoUpdate(), ob.getNumberFormat()
					);
				}
				for (var team : defaultScoreboard.getTeams()) {
					var newTeam = scoreboard.addTeam(team.getName());
					newTeam.setCollisionRule(team.getCollisionRule());
					newTeam.setColor(team.getColor());
					newTeam.setPrefix(team.getPrefix());
					newTeam.setDisplayName(team.getDisplayName());
					newTeam.setSuffix(team.getSuffix());
					newTeam.setDeathMessageVisibilityRule(team.getDeathMessageVisibilityRule());
					newTeam.setFriendlyFireAllowed(team.isFriendlyFireAllowed());
					newTeam.setShowFriendlyInvisibles(team.shouldShowFriendlyInvisibles());
					newTeam.setNameTagVisibilityRule(team.getNameTagVisibilityRule());
				}
				for (var holder : defaultScoreboard.getKnownScoreHolders()) {
					for (var ob : defaultScoreboard.getScoreHolderObjectives(holder).object2IntEntrySet()) {
						scoreboard.getOrCreateScore(holder, ob.getKey(), true).setScore(
								ob.getIntValue()
						);
					}

					var name = holder.getNameForScoreboard();
					var team = defaultScoreboard.getScoreHolderTeam(name);
					if (team != null) {
						scoreboard.addScoreHolderToTeam(name, scoreboard.getTeam(team.getName()));
					}
				}
				for (var slot : ScoreboardDisplaySlot.values()) {
					var ob = defaultScoreboard.getObjectiveForSlot(slot);
					if (ob != null) {
						scoreboard.setObjectiveSlot(slot, scoreboard.getNullableObjective(ob.getName()));
					}
				}
			}

			var team = scoreboard.addTeam("metacraft_cutscenes_empty_player_holder");
			team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
			scoreboard.addScoreHolderToTeam("", team);

			if (cutscene.getCutscene().getScoreboardMode() != Cutscene.ScoreboardMode.SYNC) {
				persistentStateManager.getOrCreate(scoreboard.getPersistentStateType(), ScoreboardState.SCOREBOARD_KEY);
			}
		}
	}

	public CutsceneInstance getCutscene() {
		return cutscene;
	}

	public void transferFrom(CutsceneWorld prev) {
		if (getActualWorld().isRaining() == isRaining()) {
			createWeatherFixPacket(prev.isRaining(), isRaining(), rainGradient, thunderGradient).ifPresent(cutscene::sendToPlayers);
		}
	}

	private void sendBlocks(ServerPlayerEntity player, UnaryOperator<Chunk> chunkGetter) {
		List<Packet<? super ClientPlayPacketListener>> list = new ArrayList<>();
		streamChangedChunks().filter(
				c -> player.getChunkFilter().isWithinDistance(c.getPos())
		).flatMap(c -> {
			Int2ObjectMap<ShortSet> map = new Int2ObjectOpenHashMap<>();
			var chunk = chunkGetter.apply(c);
			c.getChangedBlocks().forEach(pos -> {
				map.computeIfAbsent(this.getSectionIndex(pos.getY()), i -> new ShortOpenHashSet()).add(
						ChunkSectionPos.packLocal(pos)
				);
			});
			if (chunk == null) return Stream.empty();
			return map.int2ObjectEntrySet().stream().map(entry -> new ChunkDeltaUpdateS2CPacket(
					ChunkSectionPos.from(chunk.getPos(), this.sectionIndexToCoord(entry.getIntKey())),
					entry.getValue(), chunk.getSection(entry.getIntKey())
			));
		}).forEach(list::add);
		if (!list.isEmpty()) {
			if (player.networkHandler != null) {
				player.networkHandler.sendPacket(new BundleS2CPacket(list));
			} else {
				Cutscenes.LOGGER.info("Skipped sending blocks because no network handler :(");
			}
		}
	}

	public Optional<WorldChunk> getChunkFromCacheIfPresent(Chunk chunk) {
		return getChunkFromCacheIfPresent(chunk.getPos());
	}

	public boolean isLightingInCache(ChunkPos pos) {
		return getChunkManager().isLightingCached(pos.x, pos.z);
	}

	public Optional<WorldChunk> getChunkFromCacheIfPresent(ChunkPos pos) {
		return getChunkFromCacheIfPresent(pos.x, pos.z);
	}

	public Optional<WorldChunk> getChunkFromCacheIfPresent(int x, int z) {
		return manager.getChunkFromCacheIfPresent(x, z);
	}

	public void syncTime() {
		cutscene.sendToPlayers(
				new WorldTimeUpdateS2CPacket(
						getTime(), getTimeOfDay(),
						getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)
				)
		);
	}

	@Override
	public void tick(BooleanSupplier shouldKeepTicking) {
		if (this.getTime() % 20 == 0) {
			syncTime();
		}
		entities.tick();
		super.tick(shouldKeepTicking);

		getChunkManager().getLightingProvider().tick();
	}

	public void addPlayer(ServerPlayerEntity player) {
		this.getPlayers().add(player);
		entities.onAddPlayer(player);
		sendBlocks(player, c -> c);
		getChunkManager().cutsceneChunkLoadingManager.addPlayer(player);
		createWeatherFixPacket(player.getWorld().isRaining(), isRaining(), rainGradient, thunderGradient).ifPresent(player.networkHandler::sendPacket);
	}

	public Optional<Packet<?>> createWeatherFixPacket(
			boolean wasRaining, boolean isRaining, float rainGradient, float thunderGradient
	) {
		List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
		if (wasRaining != isRaining) {
			if (wasRaining) {
				packets.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STOPPED, 0));
			} else {
				packets.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STARTED, 0));
			}
			packets.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, rainGradient));
			packets.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED, thunderGradient));
		}
		if (packets.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(new BundleS2CPacket(packets));
		}
	}

	public void removePlayer(ServerPlayerEntity player) {
		this.getPlayers().remove(player);
		entities.onRemovePlayer(player);
		sendBlocks(player, c -> world.getChunk(c.getPos().x, c.getPos().z, ChunkStatus.FULL, false));
		getChunkManager().cutsceneChunkLoadingManager.removePlayer(player);
		createWeatherFixPacket(
				isRaining(), player.getWorld().isRaining(),
				player.getWorld().getRainGradient(1),
				player.getWorld().getThunderGradient(1)
		).ifPresent(player.networkHandler::sendPacket);
	}

	public boolean isPlayerWorld(PlayerEntity player) {
		return player.getWorld() == world;
	}

	public ServerWorld getActualWorld() {
		return world;
	}

	public CutsceneEntityManager getEntityManager() {
		return entities;
	}

	public Stream<CutsceneChunk> streamChangedChunks() {
		return manager.streamChangedChunks();
	}

	public Stream<BlockPos> streamChangedBlocks() {
		return streamChangedChunks().flatMap(c -> c.getChangedBlocks().stream());
	}

	@Override
	public boolean shouldTickBlocksInChunk(long chunkPos) {
		return world.shouldTickBlocksInChunk(chunkPos);
	}

	private void load(CutsceneWorldData data) {
		if (this.persistentStorage != null) {
			persistentStateManager.saveAndReload();
			this.persistentStorage.copyFrom(data.persistentStateStorage());
		}

		var blocks = data.blocks().parse(world.getRegistryManager());

		blocks.place(
				this, BlockPos.ORIGIN, BlockPos.ORIGIN, new StructurePlacementData(),
				this.getRandom(), Block.NOTIFY_ALL
		);

		data.entities().forEach(entity -> {
			entity.load(this);
		});
	}

	public CutsceneWorldData save() {
		persistentStateManager.save();
		return new CutsceneWorldData(
				entities.save().toList(), saveAsStructure(), saveLevelProperties(), persistentStorage
		);
	}

	private NbtCompound saveLevelProperties() {
		return ((SaveProperties) this.getLevelProperties()).cloneWorldNbt(getRegistryManager(), null);
	}

	private SerialisedStructure saveAsStructure() {
		StructureTemplate template = new StructureTemplate();
		List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();

		BlockPos.Mutable min = new BlockPos.Mutable(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		BlockPos.Mutable max = new BlockPos.Mutable(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

		streamChangedChunks().forEach(chunk -> {
			chunk.getChangedBlocks().forEach(pos -> {
				var newState = chunk.getBlockState(pos);
				var blockEntity = chunk.getBlockEntity(pos);
				StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
						pos, newState, blockEntity != null ? blockEntity.createNbtWithId(getRegistryManager()) : null
				);
				StructureTemplateHelper.categorize(info, fullBlocks, blockWithNBT, otherBlocks);

				if (pos.getX() < min.getX()) {
					min.setX(pos.getX());
				}
				if (pos.getY() < min.getY()) {
					min.setY(pos.getY());
				}
				if (pos.getZ() < min.getZ()) {
					min.setZ(pos.getZ());
				}
				if (pos.getX() > max.getX()) {
					max.setX(pos.getX());
				}
				if (pos.getY() > max.getY()) {
					max.setY(pos.getY());
				}
				if (pos.getZ() > max.getZ()) {
					max.setZ(pos.getZ());
				}
			});
		});
		BlockBox box = BlockBox.create(min, max);
		StructureTemplateHelper.setSize(
				template,
				new Vec3i(box.getBlockCountX(), box.getBlockCountY(), box.getBlockCountZ())
		);
		List<StructureTemplate.StructureBlockInfo> blocks = StructureTemplateHelper.combineSorted(fullBlocks, blockWithNBT, otherBlocks);
		StructureTemplateHelper.getBlockInfoLists(template).add(
				StructureTemplateHelper.createPalettedBlockInfoList(blocks)
		);

		return new SerialisedStructure(template);
	}

	public void clear() {
		if (!this.getPlayers().isEmpty()) {
			streamChangedBlocks().forEach(pos -> {
				world.getChunkManager().markForUpdate(pos);
			});
		}
		entities.clear();
	}

	@Override
	public void markDirty(BlockPos pos) {
		if (this.isChunkLoaded(pos)) {
			if (getWorldChunk(pos) instanceof CutsceneChunk chunk) {
				chunk.getChangedBlocks().add(pos);
			}
		}
	}

	@Override
	public void playSound(@Nullable PlayerEntity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
		world.playSound(source, x, y, z, sound, category, volume, pitch, seed);
	}

	@Override
	public void playSoundFromEntity(@Nullable PlayerEntity source, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
		world.playSoundFromEntity(source, entity, sound, category, volume, pitch, seed);
	}

	@Override
	public PersistentStateManager getPersistentStateManager() {
		//ChunkManager is not initialized when this is run for the first time, so we must create it here.
		if (persistentStateManager == null) {
			if (persistentStorage == null) {
				persistentStorage = new NbtCompound();
			}
			persistentStateManager = new CutscenePersistentStateManager(
					null, getServer().getDataFixer(), getRegistryManager(), () -> persistentStorage
			);
		}
		return persistentStateManager;
	}

	@Override
	public String asString() {
		return world.asString();
	}

	@Nullable
	@Override
	public Entity getEntityById(int id) {
		return getEntityLookup().get(id);
	}

	@Override
	public TickManager getTickManager() {
		return world.getTickManager();
	}

	@Nullable
	@Override
	public MapState getMapState(MapIdComponent id) {
		return world.getMapState(id);
	}

	@Override
	public void putMapState(MapIdComponent id, MapState state) {
		world.putMapState(id, state);
	}

	@Override
	public MapIdComponent increaseAndGetMapId() {
		return world.increaseAndGetMapId();
	}

	@Override
	public void setBlockBreakingInfo(int entityId, BlockPos pos, int progress) {
		world.setBlockBreakingInfo(entityId, pos, progress);
	}

	@Override
	public ServerScoreboard getScoreboard() {
		return scoreboard;
	}

	@Override
	public ServerRecipeManager getRecipeManager() {
		return world.getRecipeManager();
	}

	@Override
	protected EntityLookup<Entity> getEntityLookup() {
		return lookup;
	}

	@Override
	public BrewingRecipeRegistry getBrewingRecipeRegistry() {
		return world.getBrewingRecipeRegistry();
	}

	@Override
	public CutsceneChunkManager getChunkManager() {
		return manager;
	}

	public void addEntity(String id, Entity entity) {
		entities.addEntity(id, entity);
	}

	@Override
	public boolean spawnEntity(Entity entity) {
		entities.addEntity("AddedByWorld" + entity.getId(), entity);
		return true;
	}

	@Override
	public boolean tryLoadEntity(Entity entity) {
		return spawnEntity(entity);
	}

	@Override
	public void onDimensionChanged(Entity entity) {
		if (entity instanceof ServerPlayerEntity) {
			world.onDimensionChanged(entity);
		} else {
			spawnEntity(entity);
		}
	}

	@Override
	public void onPlayerConnected(ServerPlayerEntity player) {
		world.onPlayerConnected(player);
	}

	@Override
	public void onPlayerRespawned(ServerPlayerEntity player) {
		world.onPlayerRespawned(player);
	}

	@Override
	public void syncWorldEvent(@Nullable PlayerEntity player, int eventId, BlockPos pos, int data) {
		world.syncWorldEvent(player, eventId, pos, data);
	}

	@Override
	public void emitGameEvent(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter) {
		world.emitGameEvent(event, emitterPos, emitter);
	}

	@Override
	public float getBrightness(Direction direction, boolean shaded) {
		return world.getBrightness(direction, shaded);
	}

	@Override
	public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ) {
		return world.getGeneratorStoredBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public FeatureSet getEnabledFeatures() {
		return world.getEnabledFeatures();
	}

	@Override
	public ServerWorld toServerWorld() {
		return this;
	}
}
