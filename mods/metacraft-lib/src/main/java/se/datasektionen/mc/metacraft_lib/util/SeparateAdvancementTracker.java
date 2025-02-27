package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.datafixers.DataFixer;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PathUtil;
import net.minecraft.util.WorldSavePath;

public class SeparateAdvancementTracker extends PlayerAdvancementTracker {

	private final String suffix;

	public SeparateAdvancementTracker(
			DataFixer dataFixer, PlayerManager playerManager,
			ServerAdvancementLoader advancementLoader, ServerPlayerEntity owner,
			String suffix
	) {
		super(
				dataFixer, playerManager, advancementLoader,
				owner.server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(
						owner.getUuid() + "-" + PathUtil.replaceInvalidChars(suffix) + ".json"
				),
				owner
		);
		this.suffix = suffix;
	}

	public String getSuffix() {
		return suffix;
	}
}
