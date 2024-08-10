package se.datasektionen.mc.portal_blocker.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.portal_blocker.PortalBlockerSettings;
import se.datasektionen.mc.portal_blocker.PortalState;
import se.datasektionen.mc.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(EndPortalBlock.class)
public class MixinEndPortalBlock {

	@Inject(
		method = "onEntityCollision",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
		),
		cancellable = true
	)
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
		if (world instanceof ServerWorld serverWorld) {
			MinecraftServer server = serverWorld.getServer();
			if (PortalBlockerSettings.getInstance(server).isPortalBlocked(
					PortalTypeRegistry.END, world.getRegistryKey(), PortalState.BlockingType.TRAVEL, pos)
			) {
				if (entity instanceof ServerPlayerEntity player) {
					PortalTypeRegistry.END.getTravelMessage().ifPresent(message -> {
						player.sendMessage(message, true);
					});
				}
				ci.cancel();
			}
		}
	}

}
