package nu.metacraft.season_4.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.datafixer.Schemas;
import nu.metacraft.season_4.FixFrostCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Schemas.class)
public class MixinSchemas {

	@Inject(
		method = "build",
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/datafixer/fix/AreaEffectCloudDurationScaleFix;<init>(Lcom/mojang/datafixers/schemas/Schema;)V"
		),
		slice = @Slice(
				from = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/datafixer/fix/BlendingDataFix;<init>(Lcom/mojang/datafixers/schemas/Schema;)V"
				),
				to = @At(
						value = "INVOKE",
						target = "Lnet/minecraft/datafixer/fix/ForcedChunkToTicketFix;<init>(Lcom/mojang/datafixers/schemas/Schema;)V"
				)
		)
	)
	private static void fixStellarityFrostCloud(
			DataFixerBuilder builder, CallbackInfo ci,
			@Local(ordinal = 252) Schema schema253 //Would have been nice to be able to limit ordinals with a slice.
	) {
		builder.addFixer(new FixFrostCloud(schema253));
	}

}
