package nu.metacraft.zones.compat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.zones.compat.leukocyte.LeukocyteZoneManager;
import xyz.nucleoid.leukocyte.authority.Authority;
import xyz.nucleoid.leukocyte.command.ProtectCommand;

import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

@Mixin(value = ProtectCommand.class, remap = false)
public class MixinProtectCommand {

	@Inject(
		method = "addAuthority(Lcom/mojang/brigadier/context/CommandContext;Ljava/util/function/UnaryOperator;)I",
		at = @At(
			value = "INVOKE",
			target = "Lxyz/nucleoid/leukocyte/Leukocyte;get(Lnet/minecraft/server/MinecraftServer;)Lxyz/nucleoid/leukocyte/Leukocyte;"
		),
		cancellable = true
	)
	private static void addAuthority(
			CommandContext<CommandSourceStack> context, UnaryOperator<Authority> operator,
			CallbackInfoReturnable<Integer> cir, @Local String key, @Local CommandSourceStack source
	) {
		if (LeukocyteZoneManager.isMETAcraftZoneName(key)) {
			source.sendFailure(Component.literal(
					key + " is managed by METAcraft-Zones!\nPlease use \"/zone create " +
							LeukocyteZoneManager.getZoneNameFromAuthorityName(key) + "\" instead."
			));
			cir.setReturnValue(0);
		}
	}

	@Inject(
		method = "remove",
		at = @At(
			value = "INVOKE",
			target = "Lxyz/nucleoid/leukocyte/Leukocyte;get(Lnet/minecraft/server/MinecraftServer;)Lxyz/nucleoid/leukocyte/Leukocyte;"
		),
		cancellable = true
	)
	private static void remove(
		CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir,
		@Local Authority authority
	) {
		if (LeukocyteZoneManager.isMETAcraftZoneName(authority.getKey())) {
			context.getSource().sendFailure(Component.literal(
					authority.getKey() + " is managed by METAcraft-Zones!\nPlease use \"/zone remove " +
							LeukocyteZoneManager.getZoneNameFromAuthorityName(authority.getKey()) + "\" instead."
			));
			cir.setReturnValue(0);
		}
	}

}
