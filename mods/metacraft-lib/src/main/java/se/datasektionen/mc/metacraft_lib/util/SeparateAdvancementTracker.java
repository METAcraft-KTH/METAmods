package se.datasektionen.mc.metacraft_lib.util;

import com.mojang.datafixers.DataFixer;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.path.PathUtil;
import java.nio.file.Path;

public class SeparateAdvancementTracker extends PlayerAdvancementTracker {

	private final Identifier type;

	public static Path getPath(WorldSavePath path, ServerPlayerEntity owner, Identifier type) {
		return owner.server.getSavePath(path).resolve(
				owner.getUuid() + "-" + PathUtil.replaceInvalidChars(type.getNamespace())
		).resolve(type.getPath() + ".json");
	}

	public SeparateAdvancementTracker(
			DataFixer dataFixer, PlayerManager playerManager,
			ServerAdvancementLoader advancementLoader, ServerPlayerEntity owner,
			Identifier type
	) {
		super(
				dataFixer, playerManager, advancementLoader,
				getPath(WorldSavePath.ADVANCEMENTS, owner, type), owner
		);
		this.type = type;
	}

	public Identifier getType() {
		return type;
	}
}
