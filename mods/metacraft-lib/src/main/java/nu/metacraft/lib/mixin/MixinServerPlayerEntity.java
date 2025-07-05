package nu.metacraft.lib.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.METAcraftData;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.lib.extensions.TradeOfferExtensions;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;
import nu.metacraft.lib.util.helper.PlayerDataHelper;

import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	public MixinServerPlayerEntity(World world, GameProfile profile) {
		super(world, profile);
	}

	@Shadow public abstract void sendMessage(Text message, boolean overlay);


	@Shadow protected abstract void consumeItem();

	@Shadow public abstract ServerWorld getWorld();

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
	private PMap<Identifier, NbtCompound> dataMap = HashTreePMap.empty();

	@Unique
	private static final String CUSTOM_PLAYER_NAME = "CustomPlayerName";

	@Unique
	private static final String CUSTOM_PLAYER_NAME_SHOW_IN_GUI = "CustomPlayerNameShowInGUI";

	@Unique
	private static final Codec<PMap<Identifier, NbtCompound>> DATA_MAP_CODEC = Codec.unboundedMap(
			Identifier.CODEC, NbtCompound.CODEC
	).xmap(
			HashTreePMap::from,
			e -> e
	);




	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		customName = ((ServerPlayerEntityExtensions) oldPlayer).metacraft_lib$getCustomName();
		showInGUI = ((ServerPlayerEntityExtensions) oldPlayer).metacraft_lib$showInGUI();
		dataMap = ((MixinServerPlayerEntity) (Object) oldPlayer).dataMap;

		statHandler = ((MixinServerPlayerEntity) (Object) oldPlayer).statHandler;
		advancementTracker = ((MixinServerPlayerEntity) (Object) oldPlayer).advancementTracker;
		announceAdvancements = ((MixinServerPlayerEntity) (Object) oldPlayer).announceAdvancements;
		announceJoinLeave = ((MixinServerPlayerEntity) (Object) oldPlayer).announceJoinLeave;
		announceDeath = ((MixinServerPlayerEntity) (Object) oldPlayer).announceDeath;
	}

	@Inject(method = "writeCustomData", at = @At("RETURN"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		if (customName != null) {
			nbt.putString(CUSTOM_PLAYER_NAME, customName);
			nbt.putBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI, showInGUI);
		}

		if (!dataMap.isEmpty() && readOrWriteDataMap) {
			nbt.put(PlayerDataHelper.PLAYER_DATA_ELEMENT, DATA_MAP_CODEC, dataMap);
		}
		nbt.putNullable(PlayerDataHelper.STAT_HANDLER, Identifier.CODEC, statHandler);
		nbt.putNullable(PlayerDataHelper.ADVANCEMENT_TRACKER, Identifier.CODEC, advancementTracker);
		nbt.putBoolean(PlayerDataHelper.ANNOUNCE_ADVANCEMENTS, announceAdvancements);
		nbt.putBoolean(PlayerDataHelper.ANNOUNCE_DEATH, announceDeath);
		nbt.putBoolean(PlayerDataHelper.ANNOUNCE_JOIN_LEAVE, announceJoinLeave);
	}

	@Inject(method = "readCustomData", at = @At("RETURN"))
	public void readNBT(ReadView nbt, CallbackInfo ci) {
		nbt.getOptionalString(CUSTOM_PLAYER_NAME).ifPresentOrElse(
			name -> {
				boolean show = nbt.getBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI, showInGUI);
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
			PlayerDataHelper.setStatHandler((ServerPlayerEntity) (Object) this, statHandler, false);
		} else {
			PlayerDataHelper.restoreStatHandler((ServerPlayerEntity) (Object) this);
		}
		advancementTracker = nbt.read(PlayerDataHelper.ADVANCEMENT_TRACKER, Identifier.CODEC).orElse(null);
		if (advancementTracker != null) {
			PlayerDataHelper.setAdvancementTracker((ServerPlayerEntity) (Object) this, advancementTracker, false);
		} else {
			PlayerDataHelper.restoreAdvancementTracker((ServerPlayerEntity) (Object) this);
		}
		announceAdvancements = nbt.getBoolean(PlayerDataHelper.ANNOUNCE_ADVANCEMENTS, true);
		announceDeath = nbt.getBoolean(PlayerDataHelper.ANNOUNCE_DEATH, true);
		announceJoinLeave = nbt.getBoolean(PlayerDataHelper.ANNOUNCE_JOIN_LEAVE, true);
	}

	@WrapWithCondition(
		method = "onDeath",
		at = {
				@At(
						value = "INVOKE",
						target = "Lnet/minecraft/server/PlayerManager;sendToTeam(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/text/Text;)V"
				),
				@At(
						value = "INVOKE",
						target = "Lnet/minecraft/server/PlayerManager;sendToOtherTeams(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/text/Text;)V"
				)
		},
		require = 2
	)
	public boolean shouldSendDeathMessage(PlayerManager manager, PlayerEntity source, Text message) {
		return PlayerDataHelper.getAnnounceDeath((ServerPlayerEntity) (Object) this);
	}

	@WrapWithCondition(
			method = "onDeath",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"
			)
	)
	public boolean shouldSendDeathMessage(PlayerManager manager, Text message, boolean overlay) {
		return PlayerDataHelper.getAnnounceDeath((ServerPlayerEntity) (Object) this);
	}

	@ModifyVariable(method = "sendTradeOffers", at = @At(value = "HEAD"), argsOnly = true)
	public TradeOfferList modifyTradeOfferList(TradeOfferList tradeOfferList) {
		PlayerEntity playerEntity = (PlayerEntity) this;
		TradeOfferList newOffers = new TradeOfferList();
		for (TradeOffer offer : tradeOfferList) {
			var ext = ((TradeOfferExtensions) offer);
			int maxUsesPerPlayer = ext.metacraft$getMaxUsesPerPlayer();
			if (maxUsesPerPlayer == -1) {
				newOffers.add(offer);
				continue;
			}
			int playerUses = ext.metacraft$getUsesPerPlayer().getOrDefault(playerEntity.getUuid(), 0);
			int globalUsesUntilDisabled = offer.getMaxUses() - offer.getUses();
			int playerUsesUntilDisabled = maxUsesPerPlayer - playerUses;
			if (globalUsesUntilDisabled <= playerUsesUntilDisabled) {
				// The global max uses will be hit before the player one. So send the global one.
				newOffers.add(offer);
				continue;
			}
			// Otherwise modify the max uses and uses to be the per-player ones.
			TradeOffer copy = offer.copy();
			var copyExt = ((TradeOfferExtensions) copy);
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
			METAcraftData.getInstance(getServer()).setName(getUuid(), customName);
		}
		var tracker = EntityTrackerHelper.getEntityTrackers(this.getWorld()).get(this.getId());
		if (tracker == null) {
			return;
		}
		for (var player : this.getWorld().getPlayers()) {
			if (player != (Object) this) {
				player.networkHandler.sendPacket(
						new PlayerRemoveS2CPacket(ImmutableList.of(this.getUuid()))
				);
				tracker.stopTracking(player);
				player.networkHandler.sendPacket(
						PlayerListS2CPacket.entryFromPlayer(ImmutableList.of((ServerPlayerEntity) (Object) this))
				);
				tracker.updateTrackedStatus(player);
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
	public void metacraft_lib$setPlayerData(Identifier id, NbtCompound value) {
		if (value != null) {
			dataMap = dataMap.plus(id, value);
		} else {
			dataMap = dataMap.minus(id);
		}
	}

	@Override
	public Optional<NbtCompound> metacraft_lib$getPlayerData(Identifier id) {
		return Optional.ofNullable(dataMap.get(id));
	}

	@Override
	public NbtCompound metacraft_lib$savePlayerDataExceptDataMap() {
		readOrWriteDataMap = false;
		try (var logging = LoggingErrorReporter.create(() -> "metacraft:PlayerEntity#metacraft_lib$savePlayerDataExceptDataMap", METAcraftLib.LOGGER)) {
			NbtWriteView view = NbtWriteView.create(logging, this.getRegistryManager());
			writeData(view);
			return view.getNbt();
		} finally {
			readOrWriteDataMap = true;
		}
	}

	@Override
	public void metacraft_lib$loadPlayerDataExceptDataMap(ReadView data) {
		readOrWriteDataMap = false;
		readData(data);
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
