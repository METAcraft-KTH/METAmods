package nu.metacraft.faster_minecarts.mixin;

import org.pcollections.HashTreePSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import nu.metacraft.faster_minecarts.FasterMinecarts;
import nu.metacraft.faster_minecarts.FasterMinecartsConfig;

import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin {

	@ModifyArg(
		method = "startConfiguration",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/network/protocol/configuration/ClientboundUpdateEnabledFeaturesPacket;<init>(Ljava/util/Set;)V"
		)
	)
	public Set<Identifier> sendConfigurations(Set<Identifier> set) {
		if (!FasterMinecartsConfig.getConfig().experimentalMinecartMode().isEnabled()) {
			return set;
		}
		if (set.contains(FasterMinecarts.MINECART_IMPROVEMENTS)) return set;
		return HashTreePSet.from(set).plus(FasterMinecarts.MINECART_IMPROVEMENTS);
	}

}
