package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;
import nu.metacraft.lib.util.PotentialPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

	@Shadow
	protected abstract GameProfile playerProfile();

	@WrapWithCondition(
		method = "handleCustomClickAction",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/ResourceLocation;Ljava/util/Optional;)V"
		)
	)
	public boolean handleCustomClickAction(
			MinecraftServer server, ResourceLocation id,
			@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Tag> payload
	) {
		var player = (Object) this instanceof ServerGamePacketListenerImpl p ? PotentialPlayer.get(p.player) : PotentialPlayer.get(playerProfile().id());
		return !CustomMessageRegistry.handleAction(id, payload, server, player);
	}

}
