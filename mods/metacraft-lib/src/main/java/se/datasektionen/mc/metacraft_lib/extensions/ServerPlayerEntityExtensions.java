package se.datasektionen.mc.metacraft_lib.extensions;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_lib.util.helper.CustomNameHelper;

import java.util.Optional;

public interface ServerPlayerEntityExtensions {

	/**
	 * Please use {@link CustomNameHelper#setCustomName(ServerPlayerEntity, String, boolean)} instead.
	 * @param customName The custom name to set.
	 */
	void metacraft_lib$setCustomName(String customName, boolean showInGUI);

	boolean metacraft_lib$showInGUI();

	/**
	 * Please use {@link CustomNameHelper#getCustomName(ServerPlayerEntity)} instead.
	 * @return The custom name.
	 */
	String metacraft_lib$getCustomName();


	void metacraft_lib$setPlayerData(Identifier id, NbtCompound value);

	Optional<NbtCompound> metacraft_lib$getPlayerData(Identifier id);

	NbtCompound metacraft_lib$savePlayerDataExceptDataMap();

	void metacraft_lib$loadPlayerDataExceptDataMap(NbtCompound data);


	void metacraft_lib$setStatHandlerType(Optional<Identifier> type);

	void metacraft_lib$setAdvancementTrackerType(Optional<Identifier> type);

	void metacraft_lib$setAnnounceAdvancements(boolean announceAdvancements);

	boolean metacraft_lib$getAnnounceAdvancements();

	void metacraft_lib$setAnnounceJoinLeave(boolean announceJoinLeave);

	boolean metacraft_lib$getAnnounceJoinLeave();

	void metacraft_lib$setAnnounceDeath(boolean announceDeath);

	boolean metacraft_lib$getAnnounceDeath();

}
