package se.datasektionen.mc.zones.compat.resource_pack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import se.datasektionen.mc.resource_packs.ResourcePackCommand;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ResourcePackDataType {

	public static final ZoneDataType<ResourcePackData> RESOURCE_PACK = Registry.register(
			ZoneDataRegistry.REGISTRY, METAcraftZones.getID("resource_pack"),
			new ZoneDataType<>(ResourcePackData.CODEC, () -> new ResourcePackData(new HashSet<>()))
	);


	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				ZoneCommandUtils.zoneCommandRoot().then(
					literal("resource-pack").then(
						literal("add").then(
							ZoneCommandUtils.zone("zone").then(
								argument("pack", UuidArgumentType.uuid()).suggests(ResourcePackCommand.PACKS).executes(ctx -> {
									var zone = ZoneCommandUtils.getZone(ctx, "zone");
									var pack = UuidArgumentType.getUuid(ctx, "pack");
									if (zone.getOrCreate(ResourcePackDataType.RESOURCE_PACK).addPack(pack)) {
										ctx.getSource().sendFeedback(
												() -> Text.literal("Successfully added " + pack + " to " + zone.getName()),
												true
										);
									} else {
										ctx.getSource().sendError(Text.literal("That pack is already added!"));
										return 0;
									}
									return 1;
								})
							)
						)
					).then(
						literal("remove").then(
							ZoneCommandUtils.zone("zone").then(
								argument("pack", UuidArgumentType.uuid()).suggests(
										(ctx, builder) -> CommandSource.suggestMatching(
												ZoneCommandUtils.getZone(ctx, "zone").get(ResourcePackDataType.RESOURCE_PACK).map(
														data -> data.getPacks().stream().map(UUID::toString)
												).orElse(Stream.empty()),
												builder
										)
								).executes(ctx -> {
									var zone = ZoneCommandUtils.getZone(ctx, "zone");
									var pack = UuidArgumentType.getUuid(ctx, "pack");
									if (zone.getOrCreate(ResourcePackDataType.RESOURCE_PACK).removePack(pack)) {
										ctx.getSource().sendFeedback(
												() -> Text.literal("Successfully removed " + pack + " from " + zone.getName()),
												true
										);
									} else {
										ctx.getSource().sendError(Text.literal("That pack is not present!"));
										return 0;
									}
									return 1;
								})
							)
						)
					)
				)
			);
		});
	}

}
