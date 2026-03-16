package nu.metacraft.lib.util;

import com.mojang.datafixers.DataFixer;
import java.nio.file.Path;
import net.minecraft.util.FileUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelResource;

public class SeparateAdvancementTracker extends PlayerAdvancements implements CustomAdvancementTracker {

	private final Identifier type;

	public static Path getPath(LevelResource path, ServerPlayer owner, Identifier type) {
		return owner.level().getServer().getWorldPath(path).resolve(
				owner.getUUID() + "-" + FileUtil.sanitizeName(type.getNamespace())
		).resolve(type.getPath() + ".json");
	}

	public SeparateAdvancementTracker(
			DataFixer dataFixer, PlayerList playerManager,
			ServerAdvancementManager advancementLoader, ServerPlayer owner,
			Identifier type
	) {
		super(
				dataFixer, playerManager, advancementLoader,
				getPath(LevelResource.PLAYER_ADVANCEMENTS_DIR, owner, type), owner
		);
		this.type = type;
	}

	@Override
	public Identifier getType() {
		return type;
	}
}
