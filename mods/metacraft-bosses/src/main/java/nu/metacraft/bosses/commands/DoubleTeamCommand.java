package nu.metacraft.bosses.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.entity.LivingEntity;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.util.DoubleTeamHandler;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DoubleTeamCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access) {
		dispatcher.register(
			literal("double-team").requires(PermissionPredicates.require(
				METAcraftBosses.getID("double-team"), PermissionLevel.GAMEMASTERS
			)).then(
				argument("target", EntityArgument.entity()).executes(ctx -> {
					var entity = EntityArgument.getEntity(ctx, "target");
					if (entity instanceof LivingEntity living) {
						DoubleTeamHandler.applyToEntity(
								new DoubleTeamHandler(living, ConstantInt.of(5), 10, UniformFloat.of(5, 15), 0.25)
						);
					}
					return 0;
				})
			)
		);
	}

}
