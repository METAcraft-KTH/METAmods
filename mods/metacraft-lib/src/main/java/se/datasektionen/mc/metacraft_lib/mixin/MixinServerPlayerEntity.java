package se.datasektionen.mc.metacraft_lib.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.METAcraftData;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_lib.extensions.TradeOfferExtensions;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;

import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	@Shadow public ServerPlayNetworkHandler networkHandler;

	@Shadow public abstract ServerWorld getServerWorld();

	@Shadow public abstract void sendMessage(Text message, boolean overlay);


	@Shadow protected abstract void consumeItem();

	@Unique
	private String customName;

	@Unique
	private boolean showInGUI = true;

	@Unique
	private boolean readOrWriteDataMap = true;

	@Unique
	private boolean announceAdvancements = true;

	@Unique
	private Optional<String> statHandler = Optional.empty();

	@Unique
	private Optional<String> advancementTracker = Optional.empty();

	@Unique
	private PMap<Identifier, NbtCompound> dataMap = HashTreePMap.empty();

	@Unique
	private static final String CUSTOM_PLAYER_NAME = "CustomPlayerName";

	@Unique
	private static final String CUSTOM_PLAYER_NAME_SHOW_IN_GUI = "CustomPlayerNameShowInGUI";

	@Unique
	private static final MapCodec<PMap<Identifier, NbtCompound>> DATA_MAP_CODEC = Codec.unboundedMap(
			Identifier.CODEC, NbtCompound.CODEC
	).xmap(
			map -> (PMap<Identifier, NbtCompound>) HashTreePMap.from(map),
			e -> e
	).optionalFieldOf("metacraft:data_map", HashTreePMap.empty());

	@Unique
	private static final MapCodec<Optional<String>> STAT_HANDLER = Codec.STRING.optionalFieldOf("metacraft:stat_handler");

	@Unique
	private static final MapCodec<Optional<String>> ADVANCEMENT_TRACKER = Codec.STRING.optionalFieldOf("metacraft:advancment_tracker");

	@Unique
	private static final MapCodec<Boolean> ANNOUNCE_ADVANCEMENTS = Codec.BOOL.fieldOf("metacraft:announce_advancements");



	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		customName = ((ServerPlayerEntityExtensions) oldPlayer).metacraft_lib$getCustomName();
		showInGUI = ((ServerPlayerEntityExtensions) oldPlayer).metacraft_lib$showInGUI();
		dataMap = ((MixinServerPlayerEntity) (Object) oldPlayer).dataMap;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		if (customName != null) {
			nbt.putString(CUSTOM_PLAYER_NAME, customName);
			nbt.putBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI, showInGUI);
		}

		var builder = NbtOps.INSTANCE.mapBuilder();
		if (!dataMap.isEmpty() && readOrWriteDataMap) {
			builder = DATA_MAP_CODEC.encode(dataMap, NbtOps.INSTANCE, builder);
		}
		builder = STAT_HANDLER.encode(statHandler, NbtOps.INSTANCE, builder);
		builder = ADVANCEMENT_TRACKER.encode(advancementTracker, NbtOps.INSTANCE, builder);
		builder = ANNOUNCE_ADVANCEMENTS.encode(announceAdvancements, NbtOps.INSTANCE, builder);

		builder.build(nbt).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(n -> {
			nbt.copyFrom((NbtCompound) n);
		});
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(CUSTOM_PLAYER_NAME)) {
			boolean show = nbt.contains(CUSTOM_PLAYER_NAME_SHOW_IN_GUI) ? nbt.getBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI) : showInGUI;
			metacraft_lib$setCustomName(nbt.getString(CUSTOM_PLAYER_NAME), show);
		} else {
			metacraft_lib$setCustomName(null, true);
		}
		NbtOps.INSTANCE.getMap(nbt).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(map -> {
			if (readOrWriteDataMap) {
				DATA_MAP_CODEC.decode(NbtOps.INSTANCE, map).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(data -> {
					this.dataMap = data;
				});
			}
			STAT_HANDLER.decode(NbtOps.INSTANCE, map).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(s -> {
				statHandler = s;
				statHandler.ifPresentOrElse(handler -> {
					PlayerDataHelper.setStatHandler((ServerPlayerEntity) (Object) this, handler, false);
				}, () -> {
					PlayerDataHelper.restoreStatHandler((ServerPlayerEntity) (Object) this);
				});
			});
			ADVANCEMENT_TRACKER.decode(NbtOps.INSTANCE, map).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(a -> {
				advancementTracker = a;
				advancementTracker.ifPresentOrElse(handler -> {
					PlayerDataHelper.setAdvancementHandler((ServerPlayerEntity) (Object) this, handler, false);
				}, () -> {
					PlayerDataHelper.restoreAdvancementTracker((ServerPlayerEntity) (Object) this);
				});
			});
			ANNOUNCE_ADVANCEMENTS.decode(NbtOps.INSTANCE, map).resultOrPartial().ifPresent(a -> announceAdvancements = a);
		});
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
		var tracker = EntityTrackerHelper.getEntityTrackers(this.getServerWorld()).get(this.getId());
		if (tracker == null) {
			return;
		}
		for (var player : this.getWorld().getPlayers()) {
			if (player != this) {
				((ServerPlayerEntity) player).networkHandler.sendPacket(
						new PlayerRemoveS2CPacket(ImmutableList.of(this.getUuid()))
				);
				tracker.stopTracking((ServerPlayerEntity) player);
				((ServerPlayerEntity) player).networkHandler.sendPacket(
						PlayerListS2CPacket.entryFromPlayer(ImmutableList.of((ServerPlayerEntity) (Object) this))
				);
				tracker.updateTrackedStatus((ServerPlayerEntity) player);
			}
		}
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
		var data = writeNbt(new NbtCompound());
		readOrWriteDataMap = true;
		return data;
	}

	@Override
	public void metacraft_lib$loadPlayerDataExceptDataMap(NbtCompound data) {
		readOrWriteDataMap = false;
		readNbt(data);
		readOrWriteDataMap = true;
	}

	@Override
	public void metacraft_lib$setStatHandlerSuffix(Optional<String> suffix) {
		this.statHandler = suffix;
	}

	@Override
	public void metacraft_lib$setAdvancementTrackerSuffix(Optional<String> suffix) {
		this.advancementTracker = suffix;
	}

	@Override
	public void metacraft_lib$setAnnounceAdvancements(boolean announceAdvancements) {
		this.announceAdvancements = announceAdvancements;
	}

	@Override
	public boolean metacraft_lib$getAnnounceAdvancements() {
		return announceAdvancements;
	}

}
