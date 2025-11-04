package nu.metacraft.lib.extensions;

import org.jetbrains.annotations.Nullable;
import nu.metacraft.lib.util.helper.CustomNameHelper;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;

public interface ServerPlayerEntityExtensions {


	boolean metacraft_lib$isTeleportingOnVehicle();

	void metacraft_lib$setTeleportingOnVehicle(boolean teleportingOnVehicle);

	/**
	 * Please use {@link CustomNameHelper#setCustomName(ServerPlayer, String, boolean)} instead.
	 * @param customName The custom name to set.
	 */
	void metacraft_lib$setCustomName(String customName, boolean showInGUI);

	boolean metacraft_lib$showInGUI();

	/**
	 * Please use {@link CustomNameHelper#getCustomName(ServerPlayer)} instead.
	 * @return The custom name.
	 */
	String metacraft_lib$getCustomName();


	void metacraft_lib$setPlayerData(ResourceLocation id, CompoundTag value);

	Optional<CompoundTag> metacraft_lib$getPlayerData(ResourceLocation id);

	CompoundTag metacraft_lib$savePlayerDataExceptDataMap();

	void metacraft_lib$loadPlayerDataExceptDataMap(ValueInput data);


	void metacraft_lib$setStatHandlerType(@Nullable ResourceLocation type);

	void metacraft_lib$setAdvancementTrackerType(@Nullable ResourceLocation type);

	void metacraft_lib$setAnnounceAdvancements(boolean announceAdvancements);

	boolean metacraft_lib$getAnnounceAdvancements();

	void metacraft_lib$setAnnounceJoinLeave(boolean announceJoinLeave);

	boolean metacraft_lib$getAnnounceJoinLeave();

	void metacraft_lib$setAnnounceDeath(boolean announceDeath);

	boolean metacraft_lib$getAnnounceDeath();

}
