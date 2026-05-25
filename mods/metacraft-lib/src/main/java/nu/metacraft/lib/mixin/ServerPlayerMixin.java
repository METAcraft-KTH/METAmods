package nu.metacraft.lib.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.METAcraftData;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.ServerPlayerExtensions;
import nu.metacraft.lib.extensions.MerchantOfferExtensions;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements ServerPlayerExtensions {

	public ServerPlayerMixin(Level world, GameProfile profile) {
		super(world, profile);
	}


	@Shadow protected abstract void completeUsingItem();

	@Shadow public abstract ServerLevel level();

	@Shadow
	@Final
	private MinecraftServer server;
	@Unique
	private boolean teleportingOnVehicle = false;

	@Unique
	private String customName;

	@Unique
	private boolean showInGUI = true;

	@Unique
	private boolean readOrWriteDataMap = true;

	@Unique
	private boolean announceAdvancements = true;

	@Unique
	private boolean announceJoinLeave = true;

	@Unique
	private boolean announceDeath = true;


	@Unique @Nullable
	private Identifier statHandler = null;

	@Unique @Nullable
	private Identifier advancementTracker = null;

	@Unique
	private PMap<Identifier, CompoundTag> dataMap = HashTreePMap.empty();

	@Unique
	private static final String CUSTOM_PLAYER_NAME = "CustomPlayerName";

	@Unique
	private static final String CUSTOM_PLAYER_NAME_SHOW_IN_GUI = "CustomPlayerNameShowInGUI";

	@Unique
	private static final Codec<PMap<Identifier, CompoundTag>> DATA_MAP_CODEC = Codec.unboundedMap(
			Identifier.CODEC, CompoundTag.CODEC
	).xmap(
			HashTreePMap::from,
			e -> e
	);




	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
		customName = ((ServerPlayerExtensions) oldPlayer).metacraft_lib$getCustomName();
		showInGUI = ((ServerPlayerExtensions) oldPlayer).metacraft_lib$showInGUI();
		dataMap = ((ServerPlayerMixin) (Object) oldPlayer).dataMap;

		statHandler = ((ServerPlayerMixin) (Object) oldPlayer).statHandler;
		advancementTracker = ((ServerPlayerMixin) (Object) oldPlayer).advancementTracker;
		announceAdvancements = ((ServerPlayerMixin) (Object) oldPlayer).announceAdvancements;
		announceJoinLeave = ((ServerPlayerMixin) (Object) oldPlayer).announceJoinLeave;
		announceDeath = ((ServerPlayerMixin) (Object) oldPlayer).announceDeath;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		if (customName != null) {
			nbt.putString(CUSTOM_PLAYER_NAME, customName);
			nbt.putBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI, showInGUI);
		}

		if (!dataMap.isEmpty() && readOrWriteDataMap) {
			nbt.store(PlayerDataHelper.PLAYER_DATA_ELEMENT, DATA_MAP_CODEC, dataMap);
		}
		nbt.storeNullable(PlayerDataHelper.STAT_HANDLER, Identifier.CODEC, statHandler);
		nbt.storeNullable(PlayerDataHelper.ADVANCEMENT_TRACKER, Identifier.CODEC, advancementTracker);
		nbt.putBoolean(PlayerDataHelper.ANNOUNCE_ADVANCEMENTS, announceAdvancements);
		nbt.putBoolean(PlayerDataHelper.ANNOUNCE_DEATH, announceDeath);
		nbt.putBoolean(PlayerDataHelper.ANNOUNCE_JOIN_LEAVE, announceJoinLeave);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		nbt.getString(CUSTOM_PLAYER_NAME).ifPresentOrElse(
			name -> {
				boolean show = nbt.getBooleanOr(CUSTOM_PLAYER_NAME_SHOW_IN_GUI, showInGUI);
				metacraft_lib$setCustomName(name, show);
			},
			() -> metacraft_lib$setCustomName(null, true)
		);
		if (readOrWriteDataMap) {
			nbt.read(PlayerDataHelper.PLAYER_DATA_ELEMENT, DATA_MAP_CODEC).ifPresent(
					d -> dataMap = d
			);
		}
		statHandler = nbt.read(PlayerDataHelper.STAT_HANDLER, Identifier.CODEC).orElse(null);
		if (statHandler != null) {
			PlayerDataHelper.setStatHandler((ServerPlayer) (Object) this, statHandler, false);
		} else {
			PlayerDataHelper.restoreStatHandler((ServerPlayer) (Object) this);
		}
		advancementTracker = nbt.read(PlayerDataHelper.ADVANCEMENT_TRACKER, Identifier.CODEC).orElse(null);
		if (advancementTracker != null) {
			PlayerDataHelper.setAdvancementTracker((ServerPlayer) (Object) this, advancementTracker, false);
		} else {
			PlayerDataHelper.restoreAdvancementTracker((ServerPlayer) (Object) this);
		}
		announceAdvancements = nbt.getBooleanOr(PlayerDataHelper.ANNOUNCE_ADVANCEMENTS, true);
		announceDeath = nbt.getBooleanOr(PlayerDataHelper.ANNOUNCE_DEATH, true);
		announceJoinLeave = nbt.getBooleanOr(PlayerDataHelper.ANNOUNCE_JOIN_LEAVE, true);
	}

	@WrapWithCondition(
		method = "die",
		at = {
				@At(
						value = "INVOKE",
						target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"
				),
				@At(
						value = "INVOKE",
						target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"
				)
		},
		require = 2
	)
	public boolean shouldSendDeathMessage(PlayerList manager, Player source, Component message) {
		return PlayerDataHelper.getAnnounceDeath((ServerPlayer) (Object) this);
	}

	@WrapWithCondition(
			method = "die",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
			)
	)
	public boolean shouldSendDeathMessage(PlayerList manager, Component message, boolean overlay) {
		return PlayerDataHelper.getAnnounceDeath((ServerPlayer) (Object) this);
	}

	@ModifyVariable(method = "sendMerchantOffers", at = @At(value = "HEAD"), argsOnly = true)
	public MerchantOffers modifyTradeOfferList(MerchantOffers tradeOfferList) {
		Player playerEntity = (Player) this;
		MerchantOffers newOffers = new MerchantOffers();
		for (MerchantOffer offer : tradeOfferList) {
			var ext = ((MerchantOfferExtensions) offer);
			int maxUsesPerPlayer = ext.metacraft$getMaxUsesPerPlayer();
			if (maxUsesPerPlayer == -1) {
				newOffers.add(offer);
				continue;
			}
			int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(playerEntity.getUUID(), 0);
			int globalUsesUntilDisabled = offer.getMaxUses() - offer.getUses();
			int playerUsesUntilDisabled = maxUsesPerPlayer - playerUses;
			if (globalUsesUntilDisabled <= playerUsesUntilDisabled) {
				// The global max uses will be hit before the player one. So send the global one.
				newOffers.add(offer);
				continue;
			}
			// Otherwise modify the max uses and uses to be the per-player ones.
			MerchantOffer copy = offer.copy();
			var copyExt = ((MerchantOfferExtensions) copy);
			copyExt.metacraft$setUses(playerUses);
			copyExt.metacraft$setMaxUses(maxUsesPerPlayer);
			copyExt.metacraft$setMaxUsesPerPlayer(maxUsesPerPlayer);
			copyExt.metacraft$getUsesPerPlayer().putAll(ext.metacraft$getUsesPerPlayer());
			newOffers.add(copy);
		}
		return newOffers;
	}

	@Override
	public void metacraft_lib$setCustomName(String customName, boolean showInGUI) {
		this.customName = customName;
		this.showInGUI = showInGUI;
		if (showInGUI) {
			METAcraftData.getInstance(level().getServer()).setName(getUUID(), customName);
		}
		var tracker = EntityTrackerHelper.getEntityTrackers(this.level()).get(this.getId());
		if (tracker == null) {
			return;
		}
		for (var player : this.server.getPlayerList().getPlayers()) {
			if (player != (Object) this) {
				player.connection.send(
						new ClientboundPlayerInfoRemovePacket(ImmutableList.of(this.getUUID()))
				);
				tracker.removePlayer(player);
				player.connection.send(
						ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(ImmutableList.of((ServerPlayer) (Object) this))
				);
				tracker.updatePlayer(player);
			}
		}
	}

	@Override
	public boolean metacraft_lib$isTeleportingOnVehicle() {
		return teleportingOnVehicle;
	}

	@Override
	public void metacraft_lib$setTeleportingOnVehicle(boolean teleportingOnVehicle) {
		this.teleportingOnVehicle = teleportingOnVehicle;
	}

	@Override
	public boolean metacraft_lib$showInGUI() {
		return showInGUI;
	}

	@Override
	public String metacraft_lib$getCustomName() {
		return customName;
	}


	@Override
	public void metacraft_lib$setPlayerData(Identifier id, CompoundTag value) {
		if (value != null) {
			dataMap = dataMap.plus(id, value);
		} else {
			dataMap = dataMap.minus(id);
		}
	}

	@Override
	public Optional<CompoundTag> metacraft_lib$getPlayerData(Identifier id) {
		return Optional.ofNullable(dataMap.get(id));
	}

	@Override
	public CompoundTag metacraft_lib$savePlayerDataExceptDataMap() {
		readOrWriteDataMap = false;
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerEntity#metacraft_lib$savePlayerDataExceptDataMap", METAcraftLib.LOGGER)) {
			TagValueOutput view = TagValueOutput.createWithContext(logging, this.registryAccess());
			saveWithoutId(view);
			return view.buildResult();
		} finally {
			readOrWriteDataMap = true;
		}
	}

	@Override
	public void metacraft_lib$loadPlayerDataExceptDataMap(ValueInput data) {
		readOrWriteDataMap = false;
		load(data);
		readOrWriteDataMap = true;
	}

	@Override
	public void metacraft_lib$setStatHandlerType(Identifier type) {
		this.statHandler = type;
	}

	@Override
	public void metacraft_lib$setAdvancementTrackerType(Identifier type) {
		this.advancementTracker = type;
	}

	@Override
	public void metacraft_lib$setAnnounceAdvancements(boolean announceAdvancements) {
		this.announceAdvancements = announceAdvancements;
	}

	@Override
	public boolean metacraft_lib$getAnnounceAdvancements() {
		return announceAdvancements;
	}

	@Override
	public void metacraft_lib$setAnnounceJoinLeave(boolean announceJoinLeave) {
		this.announceJoinLeave = announceJoinLeave;
	}

	@Override
	public boolean metacraft_lib$getAnnounceJoinLeave() {
		return announceJoinLeave;
	}

	@Override
	public void metacraft_lib$setAnnounceDeath(boolean announceDeath) {
		this.announceDeath = announceDeath;
	}

	@Override
	public boolean metacraft_lib$getAnnounceDeath() {
		return announceDeath;
	}
}
