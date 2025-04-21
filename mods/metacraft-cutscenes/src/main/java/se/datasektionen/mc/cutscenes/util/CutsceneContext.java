package se.datasektionen.mc.cutscenes.util;

import net.minecraft.server.network.ServerPlayerEntity;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;

public record CutsceneContext(ServerPlayerEntity player, CutsceneInstance cutscene) {
}
