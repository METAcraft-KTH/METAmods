package se.datasektionen.mc.metacraft_season_4.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.floatprovider.UniformFloatProvider;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import se.datasektionen.mc.metacraft_season_4.util.DoubleTeamHandler;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class DoubleTeamCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access) {
		dispatcher.register(
			literal("double-team").requires(Permissions.require(
					"metacraft.double-team", 2
			)).then(
				argument("target", EntityArgumentType.entity()).executes(ctx -> {
					var entity = EntityArgumentType.getEntity(ctx, "target");
					if (entity instanceof LivingEntity living) {
						DoubleTeamHandler.applyToEntity(
								new DoubleTeamHandler(living, ConstantIntProvider.create(5), 10, UniformFloatProvider.create(5, 15), 0.25)
						);
					}
					return 0;
				})
			)
		);
	}

}
