package nu.metacraft.core.entity.entities.player_mob.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.commands.RotateCommand;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.ResolvableProfile;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.mixin.PlayerAccessor;
import nu.metacraft.core.util.SynchedDataHelper;
import nu.metacraft.lib.mixin.ChunkMapAccessor;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FakePlayerRenderer implements PlayerRenderer {

	private ResolvableProfile skinData;
	private FakePlayer fakePlayer;
	private GameProfile actualProfile;

	private boolean shouldRespawnClient = false;

	private static final int REMOVE_PLAYER_LIST_ENTRY_DELAY = 20;

	private final List<SendPacketEntry> removePackets = new ArrayList<>();

	private final PlayerMob playerMob;

	public FakePlayerRenderer(PlayerMob playerMob, ResolvableProfile defaultSkin) {
		this.playerMob = playerMob;
		setSkin(defaultSkin);
		if (defaultSkin instanceof ResolvableProfile.Dynamic d) {
			setSkin(d.partialProfile());
		}
		resetFakePlayer();
		shouldRespawnClient = false;
	}

	@Override
	public void tick() {
		if (shouldRespawnClient) {
			if (actualProfile != null) {
				var manager = ((ServerChunkCache) playerMob.level().getChunkSource()).chunkMap;
				List<ServerPlayer> players = List.of();
				var tracker = ((ChunkMapAccessor) manager).getEntityMap().get(playerMob.getId());
				if (tracker != null) {
					var listeners = ((ChunkMapAccessor.TrackedEntity) tracker).getSeenBy();
					players = listeners.stream().map(ServerPlayerConnection::getPlayer).toList();
					tracker.broadcastRemoved();
					listeners.clear(); //Necessary because stopTracking does not clear listeners.
				}
				removePlayerEntryFrom(removePackets.stream().map(SendPacketEntry::player));
				removePackets.clear();
				for (var p : players) {
					schedulePlayerListEntryRemoval(p);
				}
				resetFakePlayer();
				if (tracker != null) {
					tracker.updatePlayers(players);
				}
			}
			shouldRespawnClient = false;
		} else if (!removePackets.isEmpty()) {
			removePlayerEntryFrom(removePackets.stream().filter(p -> p.time < playerMob.level().getGameTime()).map(SendPacketEntry::player));
			removePackets.removeIf(p -> p.time < playerMob.level().getGameTime());
		}
		if (playerMob.isPassenger() && !playerMob.level().isClientSide()) {
			var vehicle = playerMob.getRootVehicle();
			if (vehicle.getControllingPassenger() == playerMob) {
				if (playerMob.getYRot() != vehicle.getYRot()) {
					playerMob.setYRot(vehicle.getYRot());
					if (!playerMob.getLookControl().isLookingAtTarget()) {
						playerMob.setYHeadRot(vehicle.getYRot());
					}
				}
			}
		}
	}

	@Override
	public ResolvableProfile getSkinData() {
		return skinData;
	}

	@Override
	public void onSetShoulderEntityLeft(CompoundTag entityNbt) {
		playerMob.getEntityData().set(
				PlayerMob.LEFT_SHOULDER_ENTITY,
				PlayerAccessor.callConvertParrotVariant(PlayerAccessor.callExtractParrotVariant(entityNbt))
		);
	}

	@Override
	public void onSetShoulderEntityRight(CompoundTag entityNbt) {
		playerMob.getEntityData().set(
				PlayerMob.RIGHT_SHOULDER_ENTITY,
				PlayerAccessor.callConvertParrotVariant(PlayerAccessor.callExtractParrotVariant(entityNbt))
		);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		schedulePlayerListEntryRemoval(player);
	}

	private void setSkin(GameProfile profile) {
		if (profile.equals(actualProfile) && profile.properties().equals(actualProfile.properties())) return;
		actualProfile = profile;
		respawnForClients();
	}

	@Override
	public void onSetCustomName(@Nullable Component name, @Nullable Component prevName) {
		if (!Objects.equals(name, prevName) && actualProfile != null) {
			resetFakePlayer();
			respawnForClients();
		}
	}

	@Override
	public void setSkin(ResolvableProfile profile) {
		this.skinData = profile;
		switch (profile) {
			case ResolvableProfile.Static s -> setSkin(s.partialProfile());
			case ResolvableProfile.Dynamic d -> {
				var server = playerMob.level().getServer();
				d.resolveProfile(server.services().profileResolver()).thenAccept(p -> {
					if (server.isRunning()) {
						server.execute(() -> {
							if (playerMob.isAlive()) {
								setSkin(p);
							}
						});
					}
				});
			}
		}
	}

	private Packet<?> createPlayerInitPacket() {
		return ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakePlayer));
	}

	@Override
	public void onBeforeSpawnPacket(ServerPlayer player, Consumer<Packet<?>> packetConsumer) {
		player.connection.send(createPlayerInitPacket());
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityType.PLAYER;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		for (int i = 0; i < data.size(); i++) {
			SynchedDataHelper.replace(data, i, PlayerMob.RIGHT_SHOULDER_ENTITY, PlayerAccessor.getRightShoulderEntity());
			SynchedDataHelper.replace(data, i, PlayerMob.LEFT_SHOULDER_ENTITY, PlayerAccessor.getLeftShoulderEntity());
		}
	}

	@Override
	public void reset() {
		if (actualProfile == null) return;
		removePlayerEntryFrom(removePackets.stream().map(SendPacketEntry::player));
		removePackets.clear();
	}

	@Override
	public void reinitialize() {
		respawnForClients();
	}

	@Override
	public PlayerRendererType getType() {
		return PlayerRendererType.FAKE_PLAYER;
	}

	private void removePlayerEntryFrom(Stream<ServerPlayer> players) {
		if (fakePlayer == null) return;
		if (playerMob.level().getServer().getPlayerList().getPlayer(fakePlayer.getGameProfile().id()) == null) {
			players.forEach(player -> {
				player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(fakePlayer.getGameProfile().id())));
			});
		}
	}

	private void respawnForClients() {
		if (actualProfile == null) return;
		shouldRespawnClient = true;
	}

	private void resetFakePlayer() {
		fakePlayer = new FakePlayer((ServerLevel) playerMob.level(), adaptProfile(actualProfile)) {};
	}

	private void schedulePlayerListEntryRemoval(ServerPlayer player) {
		removePackets.add(new SendPacketEntry(player, playerMob.level().getGameTime()+REMOVE_PLAYER_LIST_ENTRY_DELAY));
	}

	private GameProfile adaptProfile(GameProfile profile) {
		var name = playerMob.getName().getString();
		if (!profile.id().equals(playerMob.getUUID()) || !profile.name().equals(name)) {
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			return new GameProfile(
					playerMob.getUUID(), name, new PropertyMap(profile.properties())
			);
		}
		return profile;
	}

	public record SendPacketEntry(ServerPlayer player, long time) {

	}


}
