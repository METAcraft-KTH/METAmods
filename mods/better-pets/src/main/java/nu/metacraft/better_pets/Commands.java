package nu.metacraft.better_pets;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import nu.metacraft.core.gui.MultiplePlayerSelector;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("petgui").executes(ctx -> {
						var player = ctx.getSource().getPlayerOrThrow();
						var facingVec = player.getRotationVector().multiply(6);
						var result = ProjectileUtil.raycast(
								player, player.getEyePos(), player.getEyePos().add(facingVec),
								player.getBoundingBox().stretch(facingVec),
								e -> e instanceof TameableEntity, 6
						);
						if (result != null) {
							var tameable = (TameableEntity) result.getEntity();
							if (tameable.isTamed() && tameable.getOwner() == player) {
								var accessor = (TameableExtension) result.getEntity();
								MultiplePlayerSelector selector = new TrustPlayerSelector(player, accessor);
								selector.open();
							}
						}
						return 0;
					})
			);
		});
	}

}
