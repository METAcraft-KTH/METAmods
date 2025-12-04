package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;
import nu.metacraft.lib.util.PotentialPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(SignBlockEntity.class)
public class SignBlockEntityMixin {

	@WrapWithCondition(
			method = "executeClickCommandsIfPresent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/ResourceLocation;Ljava/util/Optional;)V"
			)
	)
	public boolean executeClickCommandsIfPresent(
			MinecraftServer instance, ResourceLocation id,
			@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload,
			@Local(argsOnly = true) Player player
	) {
		if (player instanceof ServerPlayer sp) {
			return !CustomMessageRegistry.handleAction(id, payload, sp.level().getServer(), PotentialPlayer.get(sp));
		}
		return true;
	}

}
