package nu.metacraft.metacraft_relay.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.network.ServerPlayerEntity$RespawnPos")
public interface AccessorServerPlayerEntityRespawnPos {

	@Invoker
	static float callGetYaw(Vec3d respawnPos, BlockPos currentPos) {
		throw new IllegalStateException("Mixin Error");
	}

}
