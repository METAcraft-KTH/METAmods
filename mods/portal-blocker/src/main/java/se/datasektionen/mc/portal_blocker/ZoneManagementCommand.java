package se.datasektionen.mc.portal_blocker;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import se.datasektionen.mc.portal_blocker.portal_type.PortalType;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.zone.RealZone;
import se.datasektionen.mc.zones.zone.Zone;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

public class ZoneManagementCommand {

	public static final String NAME = "name";

	static void registerCommand(LiteralArgumentBuilder<ServerCommandSource> builder, CommandRegistryAccess registryAccess) {
		builder.then(
			literal("zone").then(
				literal("portal").then(
					addRemovePortal(literal("add"), true)
				).then(
					addRemovePortal(literal("remove"), false)
				).then(
					ZoneCommandUtils.queryZoneMulti(literal("list"), zone -> {
						return zone.get(PortalBlocker.DATA).map(data -> {
							return data.getAffectedPortals().stream().map(portal -> {
								return Text.literal(Commands.getIDAsString(portal.getID()));
							}).collect(Collectors.toList());
						}).orElse(new ArrayList<>());
					})
				)
			).then(
				literal("set-blocking-mode").then(
					zone().then(
						Commands.boolAllowBlockArgument("state").then(
							Commands.PortalBlockType.blockTypeArgument(Commands.TYPE).executes(ctx -> {
								setBlockingMode(
										ctx, getZone(ctx),
										Commands.PortalBlockType.getBoolAllowBlockArgument(ctx, Commands.TYPE)
								);
								return 1;
							})
						).executes(ctx -> {
							setBlockingMode(
									ctx, getZone(ctx),
									Commands.PortalBlockType.BOTH
							);
							return 1;
						})
					)
				)
			)
		);
	}

	private static void setBlockingMode(CommandContext<ServerCommandSource> ctx, Zone zone, Commands.PortalBlockType type) throws CommandSyntaxException {
		boolean state = Commands.getBoolAllowBlockArgument(ctx, "state");
		for (PortalState.BlockingType bType : type.blockingTypes) {
			zone.getOrCreate(PortalBlocker.DATA).setBlocking(bType, state);
		}
		ctx.getSource().sendFeedback(
				() -> Text.literal("Set zone state for " + type + " in " + zone.getName() + " to " + Commands.getBlockStateText(state, "ing")),
				true
		);
	}

	private static String upperFirstChar(String string) {
		return Character.toUpperCase(string.charAt(0)) + string.substring(1);
	}

	static ArgumentBuilder<ServerCommandSource, ?> addRemovePortal(LiteralArgumentBuilder<ServerCommandSource> name, boolean add) {
		return name.then(
			zone().then(
				PortalType.argument(Commands.PORTAL).executes(ctx -> {
					PortalType type = PortalType.getArgument(ctx, Commands.PORTAL);
					Zone zone =getZone(ctx);
					var data = zone.getOrCreate(PortalBlocker.DATA);
					if (data.getAffectedPortals().contains(type)) {
						if (add) {
							ctx.getSource().sendFeedback(
									() -> Text.literal(
											"Portal already " + data.getCommandStateMessage("ed")
									),
									false
							);
						} else {
							data.getAffectedPortals().remove(type);
							data.markDirty();
							ctx.getSource().sendFeedback(
									() -> Text.literal(
											"Removing " + type + " from " + zone.getName()
									),
									true
							);
						}
					} else {
						if (add) {
							data.getAffectedPortals().add(type);
							data.markDirty();
							ctx.getSource().sendFeedback(
									() -> Text.literal(
											upperFirstChar(
													data.getCommandStateMessage("ing")
											) + " " + type + " in zone " + zone.getName()
									),
									true
							);
						} else {
							ctx.getSource().sendFeedback(
									() -> Text.literal(
											"Portal not in this zone"
									),
									false
							);
						}
					}
					return 1;
				})
			)
		);
	}

	static RealZone getZone(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		return ZoneCommandUtils.getZone(ctx, NAME);
	}

	static RequiredArgumentBuilder<ServerCommandSource, ?> zone() {
		return ZoneCommandUtils.zone(NAME);
	}

}
