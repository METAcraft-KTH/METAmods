package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import com.mojang.datafixers.DataFixer;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PathUtil;
import net.minecraft.util.WorldSavePath;

public class ModAdvancementTracker extends PlayerAdvancementTracker {
	public ModAdvancementTracker(
			DataFixer dataFixer, PlayerManager playerManager,
			ServerAdvancementLoader advancementLoader, ServerPlayerEntity owner,
			ModeratorModeDefinition def
	) {
		super(
				dataFixer, playerManager, advancementLoader,
				owner.server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(
						owner.getUuid() + "-" + PathUtil.replaceInvalidChars(def.getName()) + ".json"
				),
				owner
		);
	}
}
