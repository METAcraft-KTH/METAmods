package nu.metacraft.core.entity.entities.player_mob.renderer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.function.Consumer;

public interface PlayerRenderer {

	void tick();

	ResolvableProfile getSkinData();

	void onSetShoulderEntityLeft(CompoundTag entityNbt);

	void onSetShoulderEntityRight(CompoundTag entityNbt);

	void startSeenByPlayer(ServerPlayer player);

	void onSetCustomName(@Nullable Component name, @Nullable Component prevName);

	void setSkin(ResolvableProfile profile);

	void onBeforeSpawnPacket(ServerPlayer player, Consumer<Packet<?>> packetConsumer);

	EntityType<?> getPolymerEntityType(PacketContext context);

	void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial);

	void reset();

	void reinitialize();

	PlayerRendererType getType();

}
