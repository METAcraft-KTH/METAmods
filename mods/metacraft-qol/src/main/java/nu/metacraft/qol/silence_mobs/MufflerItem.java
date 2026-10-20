package nu.metacraft.qol.silence_mobs;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The muffler: right-click a mob to silence it, again to undo it. Never used up. A vanilla client
 * sees a brick wearing the muffler's model: not a block item, so using it on a block places no
 * ghost block on the client.
 */
public class MufflerItem extends SimplePolymerItem {

	public MufflerItem(Properties properties) {
		super(properties, Items.BRICK, true);
	}

	/**
	 * Toggles the mob silent. The client only sees a brick, which does nothing to a mob, so it
	 * neither predicts this nor swings: SUCCESS_SERVER has the server swing the arm, for the user
	 * and everyone watching.
	 */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer) || !SilenceMobs.canSilence(target)) {
			return InteractionResult.PASS;
		}
		SilenceMobs.apply(serverPlayer, target, !target.isSilent());
		return InteractionResult.SUCCESS_SERVER;
	}

}
