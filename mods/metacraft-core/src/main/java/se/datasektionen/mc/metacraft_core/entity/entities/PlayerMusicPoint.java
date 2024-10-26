package se.datasektionen.mc.metacraft_core.entity.entities;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import xyz.nucleoid.packettweaker.PacketContext;

public class PlayerMusicPoint extends Entity implements PolymerEntity {

	private ServerPlayerEntity player;

	public PlayerMusicPoint(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext ctx) {
		return EntityType.MARKER;
	}

	public void setPlayer(ServerPlayerEntity player) {
		this.player = player;
	}

	public void sendToClient() {
		player.networkHandler.sendPacket(
				new EntitySpawnS2CPacket(
						this.getId(), this.getUuid(), getX(), getY(), getZ(), getPitch(), getYaw(),
						getPolymerEntityType(PacketContext.create(player)), 0, getVelocity(), getHeadYaw()
				)
		);
	}

	public ServerPlayerEntity getPlayer() {
		return player;
	}

	@Override
	public void tick() {
		if (player == null || ((ServerPlayerEntityExtensions) player).metacraft_lib$getMusicPoint() != this) {
			discard();
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {

	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {

	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {

	}

	@Override
	public void remove(RemovalReason removalReason) {
		if (player != null) {
			player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(this.getId()));
		}
		super.remove(removalReason);
	}
}
