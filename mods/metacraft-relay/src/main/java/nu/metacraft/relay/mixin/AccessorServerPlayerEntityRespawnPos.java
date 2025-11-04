package nu.metacraft.relay.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.RespawnPosAngle.class)
public interface AccessorServerPlayerEntityRespawnPos {

	@Invoker
	static float callCalculateLookAtYaw(Vec3 respawnPos, BlockPos currentPos) {
		throw new IllegalStateException("Mixin Error");
	}

}
