package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import net.minecraft.datafixer.schema.Schema4312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import se.datasektionen.mc.metacraft_lib.event.datafixer.AddToPlayer;

@Mixin(Schema4312.class)
public class MixinSchema4312 {

	@ModifyArg(
		method = "registerTypes",
		slice = @Slice(
				from = @At(
						value = "FIELD",
						target = "Lnet/minecraft/datafixer/TypeReferences;PLAYER:Lcom/mojang/datafixers/DSL$TypeReference;"
				)
		),
		at = @At(
				value = "INVOKE",
				target = "Lcom/mojang/datafixers/schemas/Schema;registerType(ZLcom/mojang/datafixers/DSL$TypeReference;Ljava/util/function/Supplier;)V",
				ordinal = 0
		),
		index = 0
	)
	public boolean makePlayerRecursive(
			boolean recursive
	) {
		return true;
	}

	@ModifyArg(
		method = "method_67510",
		at = @At(
				value = "INVOKE",
				target = "Lcom/mojang/datafixers/DSL;optionalFields([Lcom/mojang/datafixers/util/Pair;)Lcom/mojang/datafixers/types/templates/TypeTemplate;"
		)
	)
	private static Pair<String, TypeTemplate>[] addToPlayer(
			Pair<String, TypeTemplate>[] fields, @Local(argsOnly = true) Schema schema
	) {
		return AddToPlayer.append(fields, schema);
	}
}
