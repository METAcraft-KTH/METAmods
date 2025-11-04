package nu.metacraft.cutscenes.cutscene.world;

import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.cutscenes.cutscene.Cutscene;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.cutscenes.extension.ServerScoreboardExtensions;
import nu.metacraft.cutscenes.mixin.MinecraftServerAccessor;
import nu.metacraft.cutscenes.mixin.ServerLevelAccessor;
import nu.metacraft.cutscenes.util.SerialisedStructure;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.StructureTemplateHelper;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class CutsceneWorld extends ServerLevel implements net.minecraft.world.level.ServerLevelAccessor {

	private final ServerLevel world;
	private final CutsceneInstance cutscene;
	private final CutsceneChunkManager manager;
	private final CutsceneEntityManager entities;

	private ServerScoreboard scoreboard;

	private final LevelEntityGetter<Entity> lookup;

	private CutscenePersistentStateManager persistentStateManager;
	private CompoundTag persistentStorage = new CompoundTag();

	protected boolean loaded = false;

	public static ChunkGenerator createDummyChunkGenerator(Level world) {
		return new FlatLevelSource(new FlatLevelGeneratorSettings(
				Optional.empty(), world.getBiome(BlockPos.ZERO), List.of()
		));
	}

	private static ServerLevelData readProperties(
			CompoundTag nbt, ServerLevel parent
	) {
		var p = parent.getServer().getWorldData();
		return PrimaryLevelData.parse(
				new Dynamic<>(
						NbtOps.INSTANCE,
						nbt
				), p.getLevelSettings(),
				p.isFlatWorld() ? PrimaryLevelData.SpecialWorldProperty.FLAT : (p.isDebugWorld() ? PrimaryLevelData.SpecialWorldProperty.DEBUG : PrimaryLevelData.SpecialWorldProperty.NONE),
				p.worldGenOptions(), p.worldGenSettingsLifecycle()
		);
	}

	private static CompoundTag getInitialSavePropertiesData(
			CutsceneWorldData data, ServerLevel world
	) {
		return data != null ? data.saveProperties() : world.getServer().getWorldData().createTag(
				world.registryAccess(), null
		);
	}

	private static ServerLevelData getProperties(
			CutsceneWorldData data, ServerLevel parent
	) {
		return readProperties(
				getInitialSavePropertiesData(data, parent), parent
		);
	}

	public CutsceneWorld(ServerLevel world, CutsceneInstance cutscene, CutsceneWorldData data) {
		super(
				world.getServer(), ((MinecraftServerAccessor) world.getServer()).getExecutor(),
				((MinecraftServerAccessor) world.getServer()).getStorageSource(),
				getProperties(data, world),
				world.dimension(),
				new LevelStem(
						world.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).wrapAsHolder(world.dimensionType()),
						createDummyChunkGenerator(world)
				),
				world.isDebug(), world.getSeed(), List.of(), true, world.getRandomSequences()
		);
		this.noSave = true;
		this.cutscene = cutscene;
		this.world = world;
		this.manager = new CutsceneChunkManager(this, this::getDataStorage);
		((ServerLevelAccessor) this).setChunkSource(manager);
		this.entities = new CutsceneEntityManager(this);
		this.lookup = entities.getLookup();
		((ServerLevelAccessor) this).setEntityManager(entities.createDummyEntityManager());
		initScoreboard(data == null);
		if (data != null) {
			load(data);
		}
		loaded = true;
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
							ob.getName(), ob.getCriteria(),
							ob.getDisplayName(), ob.getRenderType(),
							ob.displayAutoUpdate(), ob.numberFormat()
					);
				}
				for (var team : defaultScoreboard.getPlayerTeams()) {
					var newTeam = scoreboard.addPlayerTeam(team.getName());
					newTeam.setCollisionRule(team.getCollisionRule());
					newTeam.setColor(team.getColor());
					newTeam.setPlayerPrefix(team.getPlayerPrefix());
					newTeam.setDisplayName(team.getDisplayName());
					newTeam.setPlayerSuffix(team.getPlayerSuffix());
					newTeam.setDeathMessageVisibility(team.getDeathMessageVisibility());
					newTeam.setAllowFriendlyFire(team.isAllowFriendlyFire());
					newTeam.setSeeFriendlyInvisibles(team.canSeeFriendlyInvisibles());
					newTeam.setNameTagVisibility(team.getNameTagVisibility());
				}
				for (var holder : defaultScoreboard.getTrackedPlayers()) {
					for (var ob : defaultScoreboard.listPlayerScores(holder).object2IntEntrySet()) {
						scoreboard.getOrCreatePlayerScore(holder, ob.getKey(), true).set(
								ob.getIntValue()
						);
					}

					var name = holder.getScoreboardName();
					var team = defaultScoreboard.getPlayersTeam(name);
					if (team != null) {
						scoreboard.addPlayerToTeam(name, scoreboard.getPlayerTeam(team.getName()));
					}
				}
				for (var slot : DisplaySlot.values()) {
					var ob = defaultScoreboard.getDisplayObjective(slot);
					if (ob != null) {
						scoreboard.setDisplayObjective(slot, scoreboard.getObjective(ob.getName()));
					}
				}
			}

			var team = scoreboard.addPlayerTeam("metacraft_cutscenes_empty_player_holder");
			team.setNameTagVisibility(Team.Visibility.NEVER);
			scoreboard.addPlayerToTeam("", team);

			if (cutscene.getCutscene().getScoreboardMode() != Cutscene.ScoreboardMode.SYNC) {
				persistentStateManager.computeIfAbsent(ServerScoreboard.TYPE);
			}
		}
	}

	public CutsceneInstance getCutscene() {
		return cutscene;
	}

	public void transferFrom(CutsceneWorld prev) {
		if (getActualWorld().isRaining() == isRaining()) {
			createWeatherFixPacket(prev.isRaining(), isRaining(), rainLevel, thunderLevel).ifPresent(cutscene::sendToPlayers);
		}
	}

	private void sendBlocks(ServerPlayer player, UnaryOperator<ChunkAccess> chunkGetter) {
		List<Packet<? super ClientGamePacketListener>> list = new ArrayList<>();
		streamChangedChunks().filter(
				c -> player.getChunkTrackingView().contains(c.getPos())
		).flatMap(c -> {
			Int2ObjectMap<ShortSet> map = new Int2ObjectOpenHashMap<>();
			var chunk = chunkGetter.apply(c);
			c.getChangedBlocks().forEach(pos -> {
				map.computeIfAbsent(this.getSectionIndex(pos.getY()), i -> new ShortOpenHashSet()).add(
						SectionPos.sectionRelativePos(pos)
				);
			});
			if (chunk == null) return Stream.empty();
			return map.int2ObjectEntrySet().stream().map(entry -> new ClientboundSectionBlocksUpdatePacket(
					SectionPos.of(chunk.getPos(), this.getSectionYFromSectionIndex(entry.getIntKey())),
					entry.getValue(), chunk.getSection(entry.getIntKey())
			));
		}).forEach(list::add);
		if (!list.isEmpty()) {
			if (player.connection != null) {
				player.connection.send(new ClientboundBundlePacket(list));
			} else {
				Cutscenes.LOGGER.info("Skipped sending blocks because no network handler :(");
			}
		}
	}

	@Override
	public String toString() {
		return "Cutscene[" + getServer().getWorldData().getLevelName() + "]";
	}

	public Optional<LevelChunk> getChunkFromCacheIfPresent(ChunkAccess chunk) {
		return getChunkFromCacheIfPresent(chunk.getPos());
	}

	public boolean isLightingInCache(ChunkPos pos) {
		return getChunkSource().isLightingCached(pos.x, pos.z);
	}

	public Optional<LevelChunk> getChunkFromCacheIfPresent(ChunkPos pos) {
		return getChunkFromCacheIfPresent(pos.x, pos.z);
	}

	public Optional<LevelChunk> getChunkFromCacheIfPresent(int x, int z) {
		return manager.getChunkFromCacheIfPresent(x, z);
	}

	public void syncTime() {
		cutscene.sendToPlayers(
				new ClientboundSetTimePacket(
						getGameTime(), getDayTime(),
						getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
				)
		);
	}

	@Override
	public void tick(BooleanSupplier shouldKeepTicking) {
		if (this.getGameTime() % 20 == 0) {
			syncTime();
		}
		entities.tick();
		super.tick(shouldKeepTicking);

		getChunkSource().getLightEngine().tryScheduleUpdate();
	}

	public void addPlayer(ServerPlayer player) {
		this.players().add(player);
		entities.onAddPlayer(player);
		sendBlocks(player, c -> c);
		getChunkSource().cutsceneChunkLoadingManager.addPlayer(player);
		createWeatherFixPacket(player.level().isRaining(), isRaining(), rainLevel, thunderLevel).ifPresent(player.connection::send);
	}

	public Optional<Packet<?>> createWeatherFixPacket(
			boolean wasRaining, boolean isRaining, float rainGradient, float thunderGradient
	) {
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		if (wasRaining != isRaining) {
			if (wasRaining) {
				packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
			} else {
				packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
			}
			packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rainGradient));
			packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunderGradient));
		}
		if (packets.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(new ClientboundBundlePacket(packets));
		}
	}

	public void removePlayer(ServerPlayer player, boolean isLeavingCutscene) {
		this.players().remove(player);
		entities.onRemovePlayer(player);
		if (cutscene.getCutscene().shouldResendChunksBeforeNextCutscene() || getCutscene().skipNextCutscene(isLeavingCutscene)) {
			sendBlocks(player, c -> world.getChunk(c.getPos().x, c.getPos().z, ChunkStatus.FULL, false));
		}
		getChunkSource().cutsceneChunkLoadingManager.removePlayer(player);
		createWeatherFixPacket(
				isRaining(), player.level().isRaining(),
				player.level().getRainLevel(1),
				player.level().getThunderLevel(1)
		).ifPresent(player.connection::send);
	}

	protected void onEntityRemoved(Entity entity) {
		cutscene.onEntityRemoved(entity);
	}

	public boolean isPlayerWorld(Player player) {
		return player.level() == world;
	}

	public ServerLevel getActualWorld() {
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
	public boolean shouldTickBlocksAt(long chunkPos) {
		return world.shouldTickBlocksAt(chunkPos);
	}

	private void load(CutsceneWorldData data) {
		if (this.persistentStorage != null) {
			persistentStateManager.saveAndReload();
			this.persistentStorage.merge(data.persistentStateStorage());
		}

		var blocks = data.blocks().parse(world.registryAccess());

		blocks.placeInWorld(
				this, BlockPos.ZERO, BlockPos.ZERO, new StructurePlaceSettings(),
				this.getRandom(), Block.UPDATE_ALL
		);

		data.entities().entities().forEach(entity -> {
			entity.load(this);
		});
		data.entities().entitiesToHide().forEach(entities::addEntityToHide);
	}

	public CutsceneWorldData save() {
		persistentStateManager.saveAndJoin();
		return new CutsceneWorldData(
				entities.save(), saveAsStructure(), saveLevelProperties(), persistentStorage
		);
	}

	private CompoundTag saveLevelProperties() {
		return ((WorldData) this.getLevelData()).createTag(registryAccess(), null);
	}

	private SerialisedStructure saveAsStructure() {
		StructureTemplate template = new StructureTemplate();
		List<StructureTemplate.StructureBlockInfo> fullBlocks = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> blockWithNBT = new ArrayList<>();
		List<StructureTemplate.StructureBlockInfo> otherBlocks = new ArrayList<>();

		BlockPos.MutableBlockPos min = new BlockPos.MutableBlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		BlockPos.MutableBlockPos max = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

		try (var logging = LoggingErrorReporter.create(() -> "metacraft:CutsceneWorld#saveAsStructure", Cutscenes.LOGGER)) {
			streamChangedChunks().forEach(chunk -> {
				chunk.getChangedBlocks().forEach(pos -> {
					var newState = chunk.getBlockState(pos);
					var blockEntity = chunk.getBlockEntity(pos);
					CompoundTag blockData = null;
					if (blockEntity != null) {
						var writeView = TagValueOutput.createWithContext(logging, registryAccess());
						blockEntity.saveWithId(writeView);
						blockData = writeView.buildResult();
					}
					StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
							pos, newState, blockData
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
		}

		BoundingBox box = BoundingBox.fromCorners(min, max);
		StructureTemplateHelper.setSize(
				template,
				new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan())
		);
		List<StructureTemplate.StructureBlockInfo> blocks = StructureTemplateHelper.combineSorted(fullBlocks, blockWithNBT, otherBlocks);
		StructureTemplateHelper.getBlockInfoLists(template).add(
				StructureTemplateHelper.createPalettedBlockInfoList(blocks)
		);

		return new SerialisedStructure(template);
	}

	public void clear() {
		if (cutscene.getCutscene().shouldResendChunksBeforeNextCutscene() || getCutscene().skipNextCutscene(false)) {
			if (!this.players().isEmpty()) {
				streamChangedBlocks().forEach(pos -> {
					world.getChunkSource().blockChanged(pos);
				});
			}
		}
		entities.clear();
	}

	@Override
	public void blockEntityChanged(BlockPos pos) {
		if (this.hasChunkAt(pos)) {
			if (getChunkAt(pos) instanceof CutsceneChunk chunk) {
				chunk.getChangedBlocks().add(pos);
			}
		}
	}

	@Override
	public void playSeededSound(@Nullable Entity source, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
		world.playSeededSound(source, x, y, z, sound, category, volume, pitch, seed);
	}

	@Override
	public void playSeededSound(@Nullable Entity source, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
		world.playSeededSound(source, entity, sound, category, volume, pitch, seed);
	}

	@Override
	public DimensionDataStorage getDataStorage() {
		//ChunkManager is not initialized when this is run for the first time, so we must create it here.
		if (persistentStateManager == null) {
			if (persistentStorage == null) {
				persistentStorage = new CompoundTag();
			}
			persistentStateManager = new CutscenePersistentStateManager(
					new SavedData.Context(this), null,
					getServer().getFixerUpper(), registryAccess(), () -> persistentStorage
			);
		}
		return persistentStateManager;
	}

	@Override
	public String gatherChunkSourceStats() {
		return world.gatherChunkSourceStats();
	}

	@Nullable
	@Override
	public Entity getEntity(int id) {
		return getEntities().get(id);
	}

	@Override
	public TickRateManager tickRateManager() {
		return world.tickRateManager();
	}

	@Nullable
	@Override
	public MapItemSavedData getMapData(MapId id) {
		return world.getMapData(id);
	}

	@Override
	public void setMapData(MapId id, MapItemSavedData state) {
		world.setMapData(id, state);
	}

	@Override
	public MapId getFreeMapId() {
		return world.getFreeMapId();
	}

	@Override
	public void destroyBlockProgress(int entityId, BlockPos pos, int progress) {
		world.destroyBlockProgress(entityId, pos, progress);
	}

	@Override
	public ServerScoreboard getScoreboard() {
		return scoreboard;
	}

	@Override
	public RecipeManager recipeAccess() {
		return world.recipeAccess();
	}

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return lookup;
	}

	@Override
	public PotionBrewing potionBrewing() {
		return world.potionBrewing();
	}

	@Override
	public CutsceneChunkManager getChunkSource() {
		return manager;
	}

	public void addEntity(String id, Entity entity) {
		entities.addEntity(id, entity);
	}

	@Override
	public boolean addFreshEntity(Entity entity) {
		entities.addEntity("AddedByWorld" + entity.getId(), entity);
		return true;
	}

	@Override
	public boolean addWithUUID(Entity entity) {
		return addFreshEntity(entity);
	}

	@Override
	public void addDuringTeleport(Entity entity) {
		if (entity instanceof ServerPlayer) {
			world.addDuringTeleport(entity);
		} else {
			addFreshEntity(entity);
		}
	}

	@Override
	public void addNewPlayer(ServerPlayer player) {
		world.addNewPlayer(player);
	}

	@Override
	public void addRespawnedPlayer(ServerPlayer player) {
		world.addRespawnedPlayer(player);
	}

	@Override
	public void levelEvent(@Nullable Entity player, int eventId, BlockPos pos, int data) {
		world.levelEvent(player, eventId, pos, data);
	}

	@Override
	public void gameEvent(Holder<GameEvent> event, Vec3 emitterPos, GameEvent.Context emitter) {
		world.gameEvent(event, emitterPos, emitter);
	}

	@Override
	public float getShade(Direction direction, boolean shaded) {
		return world.getShade(direction, shaded);
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
		return world.getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return world.enabledFeatures();
	}

	@Override
	public ServerLevel getLevel() {
		return this;
	}
}
