package se.datasektionen.mc.better_pets;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import se.datasektionen.mc.metacraft_core.gui.MultiplePlayerSelector;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.literal;

public class Commands {

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("pet-gui").executes(ctx -> {
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
								MultiplePlayerSelector selector = new MultiplePlayerSelector(
										player, accessor.metacraft$getTrustedPlayers().stream().map(
												id -> player.getServer().getUserCache().getByUuid(id)
										).filter(Optional::isPresent).map(Optional::get ).toList(),
										p -> p.distanceTo(player) < 16,
										p -> {
											accessor.metacraft$addTrustedPlayer(p.getId());
										}, p -> {
											accessor.metacraft$removeTrustedPlayer(p.getId());
										}, false, false
								);
								selector.open();
							}
						}
						return 0;
					})
			);
		});
	}

}
