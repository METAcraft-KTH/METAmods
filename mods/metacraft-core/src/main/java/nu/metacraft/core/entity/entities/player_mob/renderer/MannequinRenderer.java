package nu.metacraft.core.entity.entities.player_mob.renderer;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.EntityElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.mixin.MannequinAccessor;
import nu.metacraft.core.util.SynchedDataHelper;
import nu.metacraft.lib.mixin.ChunkMapAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class MannequinRenderer implements PlayerRenderer {

	private final EntityAttachment attachment;
	private EntityElement<?> leftShoulder;
	private EntityElement<?> rightShoulder;

	private final PlayerMob playerMob;

	public MannequinRenderer(PlayerMob playerMob) {
		this.playerMob = playerMob;
		this.attachment = EntityAttachment.ofTicking(new ElementHolder(), playerMob);
	}

	@Override
	public void tick() {
		if (leftShoulder != null) {
			leftShoulder.setOffset(getShoulderOffset(true));
			leftShoulder.entity().setYRot(playerMob.getYRot());
			leftShoulder.entity().setYHeadRot(playerMob.getYRot());
			leftShoulder.entity().setYBodyRot(playerMob.getYRot());
			leftShoulder.entity().setXRot(playerMob.getXRot());
		}
		if (rightShoulder != null) {
			rightShoulder.setOffset(getShoulderOffset(false));
			rightShoulder.entity().setYRot(playerMob.getYRot());
			rightShoulder.entity().setYHeadRot(playerMob.getYRot());
			rightShoulder.entity().setYBodyRot(playerMob.getYRot());
			rightShoulder.entity().setXRot(playerMob.getXRot());
		}
	}

	@Override
	public ResolvableProfile getSkinData() {
		return playerMob.getEntityData().get(PlayerMob.PLAYER_SKIN);
	}

	private EntityElement<?> createElement(CompoundTag entityNbt, Vec3 offset) {
		if (entityNbt.isEmpty()) {
			return null;
		}
		var entity = EntityType.loadEntityRecursive(entityNbt, playerMob.level(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), e -> e);
		if (entity == null) return null;
		entity.ejectPassengers();
		var element = new EntityElement<>(entity, ((ServerLevel) playerMob.level()));
		element.entity().setYRot(playerMob.getYRot());
		element.entity().setXRot(playerMob.getXRot());
		element.entity().setOnGround(true);
		element.setOffset(offset);
		element.setInitialPosition(playerMob.position());
		attachment.holder().addElement(element);
		return element;
	}

	private Vec3 getShoulderOffset(boolean left) {
		Vec3 offset = new Vec3(left ? 0.35 : -0.35, playerMob.isCrouching() ? 1.1F : 1.4F, 0);
		offset = offset.yRot(-playerMob.yBodyRot * Mth.DEG_TO_RAD);
		return offset;
	}

	@Override
	public void onSetShoulderEntityLeft(CompoundTag entityNbt) {
		if (leftShoulder != null) {
			attachment.holder().removeElement(leftShoulder);
		}
		leftShoulder = createElement(entityNbt, getShoulderOffset(true));
	}

	@Override
	public void onSetShoulderEntityRight(CompoundTag entityNbt) {
		if (rightShoulder != null) {
			attachment.holder().removeElement(rightShoulder);
		}
		rightShoulder = createElement(entityNbt, getShoulderOffset(false));
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {

	}

	@Override
	public void onSetCustomName(@Nullable Component name, @Nullable Component prevName) {

	}

	@Override
	public void setSkin(ResolvableProfile profile) {
		playerMob.getEntityData().set(PlayerMob.PLAYER_SKIN, profile);
	}

	@Override
	public void onBeforeSpawnPacket(ServerPlayer player, Consumer<Packet<?>> packetConsumer) {

	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.MANNEQUIN;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		if (initial) {
			data.add(
					SynchedEntityData.DataValue.create(
							MannequinAccessor.getDataDescription(), getDescription()
					)
			);
			data.add(
					SynchedEntityData.DataValue.create(
							MannequinAccessor.getDataProfile(), getSkinData()
					)
			);
		}
		for (int i = 0; i < data.size(); i++) {
			SynchedDataHelper.replace(data, i, PlayerMob.PLAYER_SKIN, MannequinAccessor.getDataProfile());
			SynchedDataHelper.replace(data, i, PlayerMob.BELOW_NAME, MannequinAccessor.getDataDescription());
		}
	}

	@Override
	public void reset() {

	}

	@Override
	public void reinitialize() {
		var manager = ((ServerChunkCache) playerMob.level().getChunkSource()).chunkMap;
		var tracker = ((ChunkMapAccessor) manager).getEntityMap().get(playerMob.getId());
		if (tracker != null) {
			var listeners = ((ChunkMapAccessor.TrackedEntity) tracker).getSeenBy();
			var players = listeners.stream().map(ServerPlayerConnection::getPlayer).toList();
			tracker.broadcastRemoved();
			listeners.clear(); //Necessary because stopTracking does not clear listeners.
			tracker.updatePlayers(players);
		}
	}

	@Override
	public PlayerRendererType getType() {
		return PlayerRendererType.MANNEQUIN;
	}

	@Override
	public void setDescription(Optional<Component> description) {
		playerMob.getEntityData().set(PlayerMob.BELOW_NAME, description);
	}

	@Override
	public Optional<Component> getDescription() {
		return playerMob.getEntityData().get(PlayerMob.BELOW_NAME);
	}
}
