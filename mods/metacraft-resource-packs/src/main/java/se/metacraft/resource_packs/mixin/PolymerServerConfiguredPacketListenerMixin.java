package se.metacraft.resource_packs.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import eu.pb4.polymer.autohost.impl.AutoHostTask;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import se.metacraft.resource_packs.CustomPackTask;
import se.metacraft.resource_packs.extension.AutoHostExtension;
import se.metacraft.resource_packs.extension.ServerConfigurationPacketListenerImplExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ServerConfigurationPacketListenerImpl.class, priority = 1500)
public abstract class PolymerServerConfiguredPacketListenerMixin extends ServerCommonPacketListenerImpl {

	@Shadow
	@Final
	private GameProfile gameProfile;

	public PolymerServerConfiguredPacketListenerMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
		super(server, connection, cookie);
	}

	@TargetHandler(name = "polymerAutoHost$addTask", mixin = "eu.pb4.polymer.autohost.mixin.ServerConfigurationPacketListenerImplMixin")
	@ModifyExpressionValue(
		method = "@MixinSquared:Handler",
		at = @At(
			value = "NEW",
			target = "(Ljava/util/Collection;ZLjava/util/function/Supplier;Ljava/util/function/BooleanSupplier;)Leu/pb4/polymer/autohost/impl/AutoHostTask;"
		)
	)
	private AutoHostTask sendPacketWithPolymer(AutoHostTask original) {
		((ServerConfigurationPacketListenerImplExtension) this).metacraft$disablePackTask();
		((AutoHostExtension) original).metacraft$addPackets(CustomPackTask.preparePacks(server, gameProfile, connection));
		return original;
	}

}
