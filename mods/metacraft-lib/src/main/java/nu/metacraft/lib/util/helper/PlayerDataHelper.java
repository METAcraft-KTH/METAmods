package nu.metacraft.lib.util.helper;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.lib.mixin.PlayerAdvancementsAccessor;
import nu.metacraft.lib.mixin.PlayerListAccessor;
import nu.metacraft.lib.mixin.ServerPlayerAccessor;
import nu.metacraft.lib.mixin.StatsCounterAccessor;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.SeparateAdvancementTracker;
import nu.metacraft.lib.util.SeparateStatHandler;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class PlayerDataHelper {

	private static final CompoundTag CLEAR_PLAYER = new CompoundTag();

	static {
		CLEAR_PLAYER.putBoolean("seenCredits", true);
		CLEAR_PLAYER.put("EnderItems", new ListTag());
		CLEAR_PLAYER.put("ShoulderEntityLeft", new CompoundTag());
		CLEAR_PLAYER.put("ShoulderEntityRight", new CompoundTag());
	}

	private static ServerPlayerEntityExtensions ext(ServerPlayer player) {
		return (ServerPlayerEntityExtensions) player;
	}

	public static final String PLAYER_DATA_ELEMENT = "metacraft:data_map";

	public static final String STAT_HANDLER = "metacraft:stat_handler";

	public static final String ADVANCEMENT_TRACKER = "metacraft:advancement_tracker";

	public static final String ANNOUNCE_ADVANCEMENTS = "metacraft:announce_advancements";
	public static final String ANNOUNCE_DEATH = "metacraft:announce_death";
	public static final String ANNOUNCE_JOIN_LEAVE = "metacraft:announce_join_leave";

	/**
	 * Saves the current player data to the given id slot.
	 * @param player The player to save data from.
	 * @param id The id slot within that player to save to.
	 */
	public static void saveCurrentPlayerData(ServerPlayer player, ResourceLocation id) {
		var ext = ext(player);
		ext.metacraft_lib$setPlayerData(id, ext.metacraft_lib$savePlayerDataExceptDataMap());
	}

	public static void removePlayerData(ServerPlayer player, ResourceLocation id) {
		ext(player).metacraft_lib$setPlayerData(id, null);
	}

	public static Optional<CompoundTag> getPlayerData(ServerPlayer player, ResourceLocation id) {
		return ext(player).metacraft_lib$getPlayerData(id);
	}

	public static void setPlayerData(ServerPlayer player, ResourceLocation id, CompoundTag data) {
		ext(player).metacraft_lib$setPlayerData(id, data);
	}

	/**
	 * Loads the given id slot for the given player.
	 * @param player The player.
	 * @param id The id slot.
	 * @param moveToDataPosition Whether to teleport the player to their position in the data.
	 * @param includeVehicleAndPassengers Whether to spawn any passengers/vehicles.
	 * @param includeFarawayEntities Whether to spawn any additional entities such as ender pearls stored in player data.
	 */
	public static void loadPlayerData(
			ServerPlayer player, ResourceLocation id, boolean moveToDataPosition,
			boolean includeVehicleAndPassengers, boolean includeFarawayEntities
	) {
		ext(player).metacraft_lib$getPlayerData(id).map(
				data -> updatePlayerData(data, player.level().getServer().getFixerUpper())
		).ifPresent(data -> {
			try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerDataHelper#loadPlayerData", METAcraftLib.LOGGER)) {
				var view = TagValueInput.create(
						logging, player.registryAccess(), data
				);
				applyPlayerData(player, view, moveToDataPosition, includeVehicleAndPassengers, includeFarawayEntities);
			}
		});
	}


	/**
	 * Detaches player from any vehicle/passengers if another player is riding them.
	 * Run this before saving the player data before {@link PlayerDataHelper#unloadPassengersAndVehicles(ServerPlayer)}
	 * or {@link PlayerDataHelper#unloadAllPlayerConnectedEntities(ServerPlayer)}
	 * @param player The player to update.
	 */
	public static void detachPassengersBeforeSaving(ServerPlayer player) {
		if (player.getRootVehicle().countPlayerPassengers() != 1) {
			player.removeVehicle();
		}
		if (player.countPlayerPassengers() != 0) {
			player.ejectPassengers();
		}
	}

	/**
	 * Unloads all vehicles the player is riding if no other player is also riding them.
	 * WARNING: If they have not been saved using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayer, ResourceLocation)}
	 * they will be lost forever!
	 * Also, make sure to run {@link PlayerDataHelper#detachPassengersBeforeSaving(ServerPlayer)}
	 * before actually saving the data before running this function. Otherwise, you might get duplicate entities when loading!
	 * @param player The player to remove vehicles from.
	 */
	public static void unloadPassengersAndVehicles(ServerPlayer player) {
		var rootVehicle = player.getRootVehicle();
		if (rootVehicle.countPlayerPassengers() == 1) {
			player.removeVehicle();
			rootVehicle.getPassengersAndSelf().forEach(entity -> {
				entity.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
			});
		}
	}

	/**
	 * Unloads all entities connected but not directly attached to players (basically just ender pearls).
	 * WARNING: If they have not been saved using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayer, ResourceLocation)}
	 * they will be lost forever!
	 * @param player The player to remove vehicle from.
	 */
	public static void unloadFarawayEntities(ServerPlayer player) {
		player.getEnderPearls().forEach(pearl -> pearl.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
		player.getEnderPearls().clear();
	}

	/**
	 * Unloads all entities that should unload when a player disconnects (at least in vanilla).
	 * WARNING: If they have not been saved using {@link PlayerDataHelper#saveCurrentPlayerData(ServerPlayer, ResourceLocation)}
	 * they will be lost forever!
	 * Also, make sure to run {@link PlayerDataHelper#detachPassengersBeforeSaving(ServerPlayer)}
	 * before actually saving the data before running this function. Otherwise, you might get duplicate entities when loading!
	 * @param player The player to remove vehicle from.
	 * @see PlayerDataHelper#unloadPassengersAndVehicles(ServerPlayer)
	 * @see PlayerDataHelper#unloadFarawayEntities(ServerPlayer)
	 */
	public static void unloadAllPlayerConnectedEntities(ServerPlayer player) {
		unloadPassengersAndVehicles(player);
		unloadFarawayEntities(player);
	}

	/**
	 * Resets the player data to nothing. This includes the inventory, ender chest and basically everything.
	 * Note that vehicle and ender pearls are not reset.
	 * To remove them, please use {@link PlayerDataHelper#unloadAllPlayerConnectedEntities(ServerPlayer)}
	 * @param player The player to modify.
	 */
	public static void resetPlayerData(ServerPlayer player) {
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerDataHelper#resetPlayerData", METAcraftLib.LOGGER)) {
			var view = TagValueInput.create(
					logging,
					player.registryAccess(),
					getEmptyPlayerData()
			);
			applyPlayerData(
					player, view,
					false, false, false
			);
		}
	}

	/**
	 * Returns player data that will reset a player.
	 * @return The data.
	 */
	public static CompoundTag getEmptyPlayerData() {
		return CLEAR_PLAYER.copy();
	}

	/**
	 * Gets the world from the given player data.
	 * @param server The server to load the world in.
	 * @param view The data containing a dimension tag.
	 * @return The world or empty if that world did not exist.
	 */
	public static Optional<ServerLevel> getWorld(MinecraftServer server, ValueInput view) {
		return view.read("Dimension", Level.RESOURCE_KEY_CODEC).map(server::getLevel);
	}

	/**
	 * Loads any passengers and root vehicle from the given data and forces them to ride each other.
	 * @param entity The entity to modify.
	 * @param nbt The data.
	 * @param spawner A modifier to run for each entity loaded. If player is already in a world, this would likely include a spawnEntity call.
	 * @see PlayerDataHelper#loadPassengers(LivingEntity, ValueInput, UnaryOperator)
	 * @see PlayerDataHelper#loadRootVehicle(LivingEntity, ValueInput, UnaryOperator)
	 */
	public static void loadRootVehicleAndPassengers(LivingEntity entity, ValueInput nbt, UnaryOperator<Entity> spawner) {
		loadRootVehicle(entity, nbt, spawner);
		loadPassengers(entity, nbt, spawner);
	}

	/**
	 * Loads any passengers from the given data and forces them to ride the provided entity.
	 * @param entity The entity to modify.
	 * @param nbt The data.
	 * @param spawner A modifier to run for each entity loaded. If player is already in a world, this would likely include a spawnEntity call.
	 */
	public static void loadPassengers(LivingEntity entity, ValueInput nbt, UnaryOperator<Entity> spawner) {
		for (var e : nbt.childrenListOrEmpty(Entity.TAG_PASSENGERS)) {
			Entity entity2 = EntityType.loadEntityRecursive(
					e, entity.level(), EntitySpawnReason.LOAD, spawner
			);
			if (entity2 != null) {
				entity2.startRiding(entity, true, false);
			}
		}
	}

	/**
	 * Loads the given root vehicle from the given data and forces the player to ride it.
	 * @param player The player to modify.
	 * @param data The data.
	 * @param spawner A modifier to run for each entity loaded. If player is already in a world, this would likely include a spawnEntity call.
	 */
	public static void loadRootVehicle(LivingEntity player, ValueInput data, UnaryOperator<Entity> spawner) {
		data.child("RootVehicle").ifPresent(vehicle -> {
			var e = EntityType.loadEntityRecursive(vehicle.childOrEmpty("Entity"), player.level(), EntitySpawnReason.LOAD, spawner);
			if (e != null) {
				Runnable clearEntity = () -> {
					e.getPassengersAndSelf().forEach(Entity::discard);
					METAcraftLib.LOGGER.error("Unable to reattach player to entity.");
				};
				vehicle.read("Attach", UUIDUtil.CODEC).ifPresentOrElse(id -> {
					for (var entity : (Iterable<Entity>) e.getSelfAndPassengers()::iterator) {
						if (entity.getUUID().equals(id)) {
							player.startRiding(entity, true, false);
						}
					}
					if (!player.isPassenger()) {
						clearEntity.run();
					}
				}, clearEntity);
			}
		});
	}

	/**
	 * Reset a player to the provided player data.
	 * Will always try to spawn any entities that may be present in the player data such as vehicles and ender pearls.
	 * @param player The player to apply data to.
	 * @param data The data to apply.
	 * @param moveToDataPosition Whether to teleport the player to their position in the data.
	 */
	public static void applyPlayerData(
			ServerPlayer player, ValueInput data, boolean moveToDataPosition
	) {
		applyPlayerData(player, data, moveToDataPosition, true, true);
	}

	public static CompoundTag updatePlayerData(CompoundTag data, DataFixer dataFixer) {
		int oldVersion = NbtUtils.getDataVersion(data, -1);
		if (oldVersion >= SharedConstants.getCurrentVersion().dataVersion().version()) return data;
		CompoundTag newData = (CompoundTag) dataFixer.update(
				References.PLAYER, new Dynamic<>(NbtOps.INSTANCE, data),
				oldVersion, SharedConstants.getCurrentVersion().dataVersion().version()
		).getValue();
		NbtUtils.addCurrentDataVersion(newData);
		return newData;
	}

	/**
	 * Reset a player to the provided player data.
	 * @param player The player to apply data to.
	 * @param data The data to apply.
	 * @param moveToDataPosition Whether to teleport the player to their position in the data.
	 * @param spawnVehicleAndPassengers Whether to spawn any passengers/vehicles.
	 * @param spawnFarawayEntities Whether to spawn any additional entities such as ender pearls stored in player data.
	 */
	public static void applyPlayerData(
			ServerPlayer player, ValueInput data, boolean moveToDataPosition,
			boolean spawnVehicleAndPassengers, boolean spawnFarawayEntities
	) {
		player.removeAllEffects();
		BuiltInRegistries.ATTRIBUTE.listElements().forEach(attribute -> {
			var inst = player.getAttribute(attribute);
			if (inst != null) {
				inst.removeModifiers();
			}
		});
		var prevPos = player.position();
		var prevYaw = player.getYRot();
		var prevPitch = player.getXRot();
		Vec3 prevVelocity = player.getDeltaMovement();

		var prevVehiclePos = player.getRootVehicle().position();
		var prevVehicleYaw = player.getRootVehicle().getYRot();
		var prevVehiclePitch = player.getRootVehicle().getXRot();
		var prevVehicleVelocity = player.getRootVehicle().getDeltaMovement();

		var gameMode = ServerPlayerAccessor.callReadPlayerMode(data, "playerGameType");
		if (gameMode != null) {
			player.setGameMode(gameMode);
		}

		ext(player).metacraft_lib$loadPlayerDataExceptDataMap(data);
		Optional<ServerLevel> world = getWorld(player.level().getServer(), data);
		if (moveToDataPosition) {
			world.ifPresentOrElse(w -> {
				player.teleportTo(w, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot(), false);
				player.hurtMarked = true;
			}, () -> {
				player.teleport(player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING));
			});
		} else {
			player.setPosRaw(prevPos.x(), prevPos.y(), prevPos.z());
			player.setYRot(prevYaw);
			player.setXRot(prevPitch);
			player.setDeltaMovement(prevVelocity);
		}
		if (spawnVehicleAndPassengers) {
			loadRootVehicleAndPassengers(player, data, e -> {
				if (!moveToDataPosition) {
					e.setPosRaw(prevVehiclePos.x, prevVehiclePos.y, prevVehiclePos.z);
					e.setYRot(prevVehicleYaw);
					e.setXRot(prevVehiclePitch);
					e.setDeltaMovement(prevVehicleVelocity);
				}
				if (!player.level().addFreshEntity(e)) {
					return null;
				}
				return e;
			});
		}
		if (spawnFarawayEntities) {
			player.loadAndSpawnEnderPearls(data);
		}
	}

	public static void setAnnounceAdvancements(ServerPlayer player, boolean announceAdvancements) {
		((ServerPlayerEntityExtensions) player).metacraft_lib$setAnnounceAdvancements(announceAdvancements);
	}

	public static boolean getAnnounceAdvancements(ServerPlayer player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$getAnnounceAdvancements();
	}

	public static void setAnnounceJoinLeave(ServerPlayer player, boolean announceJoinLeave) {
		((ServerPlayerEntityExtensions) player).metacraft_lib$setAnnounceJoinLeave(announceJoinLeave);
	}

	public static boolean getAnnounceJoinLeave(ServerPlayer player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$getAnnounceJoinLeave();
	}

	public static void setAnnounceDeath(ServerPlayer player, boolean announceDeath) {
		((ServerPlayerEntityExtensions) player).metacraft_lib$setAnnounceDeath(announceDeath);
	}

	public static boolean getAnnounceDeath(ServerPlayer player) {
		return ((ServerPlayerEntityExtensions) player).metacraft_lib$getAnnounceDeath();
	}

	public static void setAdvancementTracker(ServerPlayer player, ResourceLocation type, boolean copy) {
		if (!(player.getAdvancements() instanceof SeparateAdvancementTracker h) || !h.getType().equals(type)) {
			var prevTracker = player.getAdvancements();
			prevTracker.save();
			prevTracker.stopListening();
			var playerManager = player.level().getServer().getPlayerList();
			((ServerPlayerAccessor) player).setAdvancements(
					new SeparateAdvancementTracker(
							player.level().getServer().getFixerUpper(), playerManager,
							player.level().getServer().getAdvancements(), player, type
					)
			);
			((PlayerListAccessor) playerManager).getAdvancements().put(
					player.getUUID(), player.getAdvancements()
			);
			((ServerPlayerEntityExtensions) player).metacraft_lib$setAdvancementTrackerType(type);

			if (copy) {
				var progress = ((PlayerAdvancementsAccessor) prevTracker).getProgress();
				var tracker = (PlayerAdvancementsAccessor) player.getAdvancements();
				tracker.getProgress().putAll(progress);
				progress.forEach((entry, p) -> {
					tracker.callStartProgress(entry, p);
					tracker.getProgressChanged().add(entry);
					tracker.callMarkForVisibilityUpdate(entry);
				});
			}
		}
	}

	public static void restoreAdvancementTracker(ServerPlayer player) {
		var playerManager = player.level().getServer().getPlayerList();
		if (player.getAdvancements() instanceof SeparateAdvancementTracker t) {
			t.save();
			t.stopListening();
			((PlayerListAccessor) playerManager).getAdvancements().remove(player.getUUID());
			((ServerPlayerAccessor) player).setAdvancements(playerManager.getPlayerAdvancements(player));
			((ServerPlayerEntityExtensions) player).metacraft_lib$setAdvancementTrackerType(null);
		}
	}

	public static void setStatHandler(ServerPlayer player, ResourceLocation type, boolean copy) {
		if (!(player.getStats() instanceof SeparateStatHandler h) || !h.getType().equals(type)) {
			var prevHandler = player.getStats();
			player.getStats().save();
			((ServerPlayerAccessor) player).setStats(new SeparateStatHandler(player.level().getServer(), player, type));
			((PlayerListAccessor) player.level().getServer().getPlayerList()).getStats().put(
					player.getUUID(), player.getStats()
			);
			((ServerPlayerEntityExtensions) player).metacraft_lib$setStatHandlerType(type);

			if (copy) {
				for (var entry : ((StatsCounterAccessor) prevHandler).getStats().object2IntEntrySet()) {
					player.getStats().setValue(player, entry.getKey(), entry.getIntValue());
				}
			}
		}
	}

	public static void restoreStatHandler(ServerPlayer player) {
		var playerManager = player.level().getServer().getPlayerList();
		if (player.getStats() instanceof SeparateStatHandler t) {
			t.save();
			((PlayerListAccessor) playerManager).getStats().remove(player.getUUID());
			((ServerPlayerAccessor) player).setStats(playerManager.getPlayerStats(player));
			((ServerPlayerEntityExtensions) player).metacraft_lib$setStatHandlerType(null);
		}
	}

}
