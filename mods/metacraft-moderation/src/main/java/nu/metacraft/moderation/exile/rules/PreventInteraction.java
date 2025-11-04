package nu.metacraft.moderation.exile.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import nu.metacraft.moderation.exile.ExileData;
import nu.metacraft.moderation.exile.ExilePlayerData;

import java.util.UUID;

public class PreventInteraction implements ZoneRule {
	@Override
	public void enterAllowedArea(ServerPlayer player) {
		player.getAbilities().mayBuild = true;
		((ExilePlayerData) player).METAcraft_Moderation$setCanInteract(true);
	}

	@Override
	public void enterProhibitedArea(ServerPlayer player) {
		player.getAbilities().mayBuild = false;
		((ExilePlayerData) player).METAcraft_Moderation$setCanInteract(false);
	}

	@Override
	public void tick(ServerPlayer player) {

	}

	public static boolean shouldCancelInteraction(ServerPlayer player) {
		return !(((ExilePlayerData) player).METAcraft_Moderation$canInteract());
	}

	public static boolean shouldCancelInteraction(ServerPlayer player, BlockPos pos) {
		if (shouldCancelInteraction(player)) {
			return true;
		} else {
			return shouldCancelInteractionAt(player.level().getServer(), player.getUUID(), player.level().dimension(), pos);
		}
	}

	public static boolean shouldCancelInteractionAt(MinecraftServer server, UUID playerID, ResourceKey<Level> dim, BlockPos pos) {
		return ExileData.getInstance(server).getExile(playerID).map(
			exile -> !exile.ruleAppliesAt(dim, pos, ZoneRuleRegistry.preventInteraction)
		).orElse(false);
	}
}
