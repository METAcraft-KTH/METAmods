package nu.metacraft.portal_blocker.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;

@Mixin(EndPortalBlock.class)
public class MixinEndPortalBlock {

	@Inject(
		method = "entityInside",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"
		),
		cancellable = true
	)
	public void onEntityCollision(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl, CallbackInfo ci) {
		if (world instanceof ServerLevel serverWorld) {
			MinecraftServer server = serverWorld.getServer();
			if (PortalBlockerSettings.getInstance(server).isPortalBlocked(
					PortalTypeRegistry.END, world.dimension(), PortalState.BlockingType.TRAVEL, pos)
			) {
				if (entity instanceof ServerPlayer player) {
					PortalTypeRegistry.END.getTravelMessage().ifPresent(message -> {
						player.displayClientMessage(message, true);
					});
				}
				ci.cancel();
			}
		}
	}

}
