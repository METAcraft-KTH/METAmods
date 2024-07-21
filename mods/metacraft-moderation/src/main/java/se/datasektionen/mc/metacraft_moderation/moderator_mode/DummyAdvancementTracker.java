package se.datasektionen.mc.metacraft_moderation.moderator_mode;

import com.mojang.datafixers.DataFixer;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerAdvancementLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class DummyAdvancementTracker extends PlayerAdvancementTracker {
	public DummyAdvancementTracker(
			DataFixer dataFixer, PlayerManager playerManager,
			ServerAdvancementLoader advancementLoader, ServerPlayerEntity owner
	) {
		super(
				dataFixer, playerManager, advancementLoader,
				Path.of(Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS ? "NUL" : "/dev/null"), owner
		);
	}

	@Override
	public void save() {}

	@Override
	public boolean grantCriterion(AdvancementEntry advancement, String criterionName) {
		return false;
	}

	@Override
	public boolean revokeCriterion(AdvancementEntry advancement, String criterionName) {
		return false;
	}

	@Override
	public void reload(ServerAdvancementLoader advancementLoader) {}

	@Override
	public void clearCriteria() {}

	@Override
	public void sendUpdate(ServerPlayerEntity player) {}

	@Override
	public void setDisplayTab(@Nullable AdvancementEntry advancement) {}

	private static final AdvancementProgress MISSINGNO = new AdvancementProgress();
	@Override
	public AdvancementProgress getProgress(AdvancementEntry advancement) {
		return MISSINGNO;
	}
}
