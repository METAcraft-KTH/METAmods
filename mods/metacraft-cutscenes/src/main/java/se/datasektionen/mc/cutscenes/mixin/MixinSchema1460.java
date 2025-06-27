package se.datasektionen.mc.cutscenes.mixin;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import net.minecraft.datafixer.schema.Schema1460;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.cutscenes.CutsceneDataFixer;
import se.datasektionen.mc.cutscenes.cutscene.MultiplayerCutsceneManager;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(Schema1460.class)
public class MixinSchema1460 {

	@Inject(
		method = "registerTypes",
		at = @At("RETURN")
	)
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes, CallbackInfo ci) {
		schema.registerType(
			false, CutsceneDataFixer.CUTSCENE,
			DSL::remainder
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
