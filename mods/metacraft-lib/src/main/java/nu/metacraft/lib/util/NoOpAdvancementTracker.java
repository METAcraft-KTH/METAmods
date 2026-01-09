package nu.metacraft.lib.util;

import com.mojang.datafixers.DataFixer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import nu.metacraft.lib.METAcraftLib;
import org.jetbrains.annotations.NotNull;

public class NoOpAdvancementTracker extends PlayerAdvancements implements CustomAdvancementTracker {

	public static final Identifier TYPE = METAcraftLib.getID("null");

	public NoOpAdvancementTracker(DataFixer dataFixer, PlayerList playerList, ServerAdvancementManager serverAdvancementManager, ServerPlayer serverPlayer) {
		super(dataFixer, playerList, serverAdvancementManager, null, serverPlayer);
	}

	@Override
	public void save() {

	}

	@Override
	public Identifier getType() {
		return TYPE;
	}

	@Override
	public boolean canReceiveCopy() {
		return false;
	}

	@Override
	public boolean award(@NotNull AdvancementHolder advancementHolder, @NotNull String string) {
		return false;
	}

	@Override
	public boolean revoke(@NotNull AdvancementHolder advancementHolder, @NotNull String string) {
		return false;
	}
}
