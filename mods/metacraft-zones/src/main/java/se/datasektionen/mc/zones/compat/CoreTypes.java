package se.datasektionen.mc.zones.compat;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import se.datasektionen.mc.metacraft_core.commands.PlayMusic;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.compat.music.MusicData;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.zone.data.ZoneDataRegistry;
import se.datasektionen.mc.zones.zone.data.ZoneDataType;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

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
								argument("music", NbtCompoundArgumentType.nbtCompound()).executes(ctx -> {
									var zone = ZoneCommandUtils.getZone(ctx, "zone");
									var music = PlayMusic.parse(
											NbtCompoundArgumentType.getNbtCompound(ctx, "music"),
											ctx.getSource().getRegistryManager()
									);
									zone.getOrCreate(MUSIC).setMusic(Optional.of(music));
									ctx.getSource().sendFeedback(
											() -> Text.literal("Set music to " + music + " in " + zone.getName()),
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
								ctx.getSource().sendFeedback(
										() -> Text.literal("Removed music from " + zone.getName()),
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
