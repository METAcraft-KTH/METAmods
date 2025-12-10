package nu.metacraft.zones.compat.resource_pack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import nu.metacraft.lib.util.helper.DisconnectedPlayerHelper;
import nu.metacraft.resource_packs.EarlyPacksCallback;
import nu.metacraft.resource_packs.ResourcePackCommand;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;
import nu.metacraft.zones.zone.data.ZoneDataType;
import org.pcollections.HashTreePSet;

import java.util.UUID;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ResourcePackDataType {

	public static final ZoneDataType<ResourcePackData> RESOURCE_PACK = Registry.register(
			ZoneDataRegistry.REGISTRY, METAcraftZones.getID("resource_pack"),
			new ZoneDataType<>(ResourcePackData.CODEC, () -> new ResourcePackData(HashTreePSet.empty()))
	);


	public static void init() {
		EarlyPacksCallback.EVENT.register((server, profile, playerData, resourcePackAdder) -> {
			if (playerData.isEmpty()) return;
			var data = playerData.get();
			var pos = DisconnectedPlayerHelper.getPos(data);
			var dim = DisconnectedPlayerHelper.getPlayerDim(data);
			var zones = ZoneManager.getInstance(server).getZonesAt(dim, BlockPos.containing(pos),zone -> zone.get(RESOURCE_PACK).isPresent());
			for (var zone : zones) {
				zone.get(RESOURCE_PACK).orElseThrow().getPacks().forEach(resourcePackAdder);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				ZoneCommandUtils.zoneCommandRoot().then(
					literal("resource-pack").then(
						literal("add").then(
							ZoneCommandUtils.zone("zone").then(
								argument("pack", UuidArgument.uuid()).suggests(ResourcePackCommand.PACKS).executes(ctx -> {
									var zone = ZoneCommandUtils.getZone(ctx, "zone");
									var pack = UuidArgument.getUuid(ctx, "pack");
									if (zone.getOrCreate(ResourcePackDataType.RESOURCE_PACK).addPack(pack)) {
										ctx.getSource().sendSuccess(
												() -> Component.literal("Successfully added " + pack + " to " + zone.getName()),
												true
										);
									} else {
										ctx.getSource().sendFailure(Component.literal("That pack is already added!"));
										return 0;
									}
									return 1;
								})
							)
						)
					).then(
						literal("remove").then(
							ZoneCommandUtils.zone("zone").then(
								argument("pack", UuidArgument.uuid()).suggests(
										(ctx, builder) -> SharedSuggestionProvider.suggest(
												ZoneCommandUtils.getZone(ctx, "zone").get(ResourcePackDataType.RESOURCE_PACK).map(
														data -> data.getPacks().stream().map(UUID::toString)
												).orElse(Stream.empty()),
												builder
										)
								).executes(ctx -> {
									var zone = ZoneCommandUtils.getZone(ctx, "zone");
									var pack = UuidArgument.getUuid(ctx, "pack");
									if (zone.getOrCreate(ResourcePackDataType.RESOURCE_PACK).removePack(pack)) {
										ctx.getSource().sendSuccess(
												() -> Component.literal("Successfully removed " + pack + " from " + zone.getName()),
												true
										);
									} else {
										ctx.getSource().sendFailure(Component.literal("That pack is not present!"));
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
