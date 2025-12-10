package nu.metacraft.zones.compat;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import nu.metacraft.core.commands.PlayMusic;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.compat.music.MusicData;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.zone.data.ZoneDataRegistry;
import nu.metacraft.zones.zone.data.ZoneDataType;

import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class CoreTypes {

	public static final ZoneDataType<MusicData> MUSIC = Registry.register(
			ZoneDataRegistry.REGISTRY, METAcraftZones.getID("music"),
			new ZoneDataType<>(MusicData.CODEC, () -> new MusicData(Optional.empty()))
	);

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				ZoneCommandUtils.zoneCommandRoot().then(
					literal("music").then(
						literal("set").then(
							ZoneCommandUtils.zone("zone").then(
								argument("music", CompoundTagArgument.compoundTag()).executes(ctx -> {
									var zone = ZoneCommandUtils.getZone(ctx, "zone");
									var music = PlayMusic.parse(
											CompoundTagArgument.getCompoundTag(ctx, "music"),
											ctx.getSource().registryAccess()
									);
									zone.getOrCreate(MUSIC).setMusic(Optional.of(music));
									ctx.getSource().sendSuccess(
											() -> Component.literal("Set music to " + music + " in " + zone.getName()),
											true
									);
									return 1;
								})
							)
						)
					).then(
						literal("remove").then(
							ZoneCommandUtils.zone("zone").executes(ctx -> {
								var zone = ZoneCommandUtils.getZone(ctx, "zone");
								zone.get(MUSIC).ifPresent(data -> {
									data.setMusic(Optional.empty());
								});
								ctx.getSource().sendSuccess(
										() -> Component.literal("Removed music from " + zone.getName()),
										true
								);
								return 1;
							})
						)
					)
				)
			);
		});
	}

}
