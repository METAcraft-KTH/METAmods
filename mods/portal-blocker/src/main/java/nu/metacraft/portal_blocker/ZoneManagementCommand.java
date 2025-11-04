package nu.metacraft.portal_blocker;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import nu.metacraft.portal_blocker.portal_type.PortalType;
import nu.metacraft.portal_blocker.zone.ZoneDataPortalBlocker;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.zone.RealZone;
import nu.metacraft.zones.zone.Zone;

import java.util.ArrayList;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public class ZoneManagementCommand {

	public static final String NAME = "name";
	public static final String TYPE = "type";
	public static final String STATE = "state";

	static void registerCommand(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext registryAccess) {
		builder.then(
			literal("zone").then(
				literal("set-portal").then(
					zone().then(
							PortalType.argument(Commands.PORTAL).then(
									Commands.PortalBlockType.blockTypeArgument(TYPE).then(
											Commands.allowBlockArgument(STATE, true).executes(
													ZoneManagementCommand::addRemovePortal
											)
									)
							)
					)
				).then(
					ZoneCommandUtils.queryZoneMulti(literal("list"), zone -> {
						return zone.get(ZoneDataPortalBlocker.PORTAL_DATA).map(data -> {
							return data.getPortalStates().entrySet().stream().map(portal -> {
								return Component.literal(Commands.getIDAsString(portal.getKey().getID()) + " " + portal.getValue());
							}).collect(Collectors.toList());
						}).orElse(new ArrayList<>());
					})
				)
			)
		);
	}

	static int addRemovePortal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var type = Commands.PortalBlockType.getAllowBlockArgument(ctx, TYPE);
		PortalType portal = PortalType.getArgument(ctx, Commands.PORTAL);
		Zone zone = getZone(ctx);
		var data = zone.getOrCreate(ZoneDataPortalBlocker.PORTAL_DATA);
		var state = Commands.getAllowBlockArgument(ctx, STATE, true);
		data.setBlocking(portal, type, state);
		ctx.getSource().sendSuccess(
				() -> Component.literal(
						"Set portal " + portal + " for " + type + " to " + state + " in " + zone.getName()
				),
				true
		);
		return 1;
	}

	static RealZone getZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ZoneCommandUtils.getZone(ctx, NAME);
	}

	static RequiredArgumentBuilder<CommandSourceStack, ?> zone() {
		return ZoneCommandUtils.zone(NAME);
	}

}
