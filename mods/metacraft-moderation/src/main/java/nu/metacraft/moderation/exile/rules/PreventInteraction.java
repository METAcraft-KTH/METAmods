package nu.metacraft.moderation.exile.rules;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import nu.metacraft.moderation.exile.ExileData;
import nu.metacraft.moderation.exile.ExilePlayerData;

import java.util.UUID;

public class PreventInteraction implements ZoneRule {
	@Override
	public void enterAllowedArea(ServerPlayerEntity player) {
		player.getAbilities().allowModifyWorld = true;
		((ExilePlayerData) player).METAcraft_Moderation$setCanInteract(true);
	}

	@Override
	public void enterProhibitedArea(ServerPlayerEntity player) {
		player.getAbilities().allowModifyWorld = false;
		((ExilePlayerData) player).METAcraft_Moderation$setCanInteract(false);
	}

	@Override
	public void tick(ServerPlayerEntity player) {

	}

	public static boolean shouldCancelInteraction(ServerPlayerEntity player) {
		return !(((ExilePlayerData) player).METAcraft_Moderation$canInteract());
	}

	public static boolean shouldCancelInteraction(ServerPlayerEntity player, BlockPos pos) {
		if (shouldCancelInteraction(player)) {
			return true;
		} else {
			return shouldCancelInteractionAt(player.getServer(), player.getUuid(), player.getWorld().getRegistryKey(), pos);
		}
	}

	public static boolean shouldCancelInteractionAt(MinecraftServer server, UUID playerID, RegistryKey<World> dim, BlockPos pos) {
		return ExileData.getInstance(server).getExile(playerID).map(
			exile -> !exile.ruleAppliesAt(dim, pos, ZoneRuleRegistry.preventInteraction)
		).orElse(false);
	}
}
