package nu.metacraft.cutscenes.mixin;

import com.mojang.datafixers.DSL;
import net.minecraft.util.datafix.DataFixTypes;
import nu.metacraft.cutscenes.CutsceneDataFixer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DataFixTypes.class)
public enum DataFixTypesMixin {
	METACRAFT_SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER(CutsceneDataFixer.SAVED_DATA_MULTIPLAYER_CUTSCENE_MANAGER);

	@Shadow
	DataFixTypesMixin(final DSL.TypeReference type) {}

}
