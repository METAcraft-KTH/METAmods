package se.datasektionen.mc.cutscenes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.datafixer.schema.Schema1460;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.cutscenes.CutsceneDataFixer;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(Schema1460.class)
public class MixinSchema1460 {

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
		method = "method_5260",
		at = @At(
				value = "INVOKE",
				target = "Lcom/mojang/datafixers/DSL;optionalFields([Lcom/mojang/datafixers/util/Pair;)Lcom/mojang/datafixers/types/templates/TypeTemplate;"
		)
	)
	private static Pair<String, TypeTemplate>[] addToPlayer(
			Pair<String, TypeTemplate>[] fields, @Local(argsOnly = true) Schema schema
	) {

		Pair<String, TypeTemplate>[] newArray = new Pair[fields.length+1];
		System.arraycopy(fields, 0, newArray, 0, fields.length);
		newArray[fields.length] = Pair.of(
			"cutscene", CutsceneDataFixer.CUTSCENE.in(schema)
		);
		return newArray;
	}

	@Inject(
		method = "registerTypes",
		at = @At("RETURN")
	)
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes, CallbackInfo ci) {
		schema.registerType(
			false, CutsceneDataFixer.CUTSCENE,
			() -> DSL.optionalFields(
				"saved_players", DSL.compoundList(
						TypeReferences.PLAYER.in(schema)
				)
			)
		);
		schema.registerType(
				false, CutsceneDataFixer.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER,
				() -> DSL.optionalFields(
						"data", DSL.optionalFields(
								MultiplayerCutsceneManager.CUTSCENES, DSL.compoundList(
										CutsceneDataFixer.CUTSCENE.in(schema)
								),
								MultiplayerCutsceneManager.OFFLINE_PLAYERS_KEY, DSL.compoundList(
										CutsceneDataFixer.CUTSCENE.in(schema)
								)
						)
				)
		);
	}
}
