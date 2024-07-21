package se.datasektionen.mc.zones.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.DataResult;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import se.datasektionen.mc.zones.METAcraftZones;
import se.datasektionen.mc.zones.ZoneManager;
import se.datasektionen.mc.zones.zone.RealZone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;
import se.datasektionen.mc.zones.zone.types.ZoneType;

import java.util.Objects;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;

public class ZoneCommandUtils {

	public static final SimpleCommandExceptionType NO_ZONE_EXISTS = new SimpleCommandExceptionType(Text.literal("No zone exists with that name"));
	public static final DynamicCommandExceptionType OTHER_ERROR = new DynamicCommandExceptionType(o -> {
		return o instanceof Text t ? t : Text.literal(Objects.toString(o));
	});

	public static final SuggestionProvider<ServerCommandSource> ZONE_NAME_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
			ZoneManager.getInstance(ctx.getSource().getServer()).getZoneNames(), suggestionsBuilder
		);
	};

	public static final SuggestionProvider<ServerCommandSource> ZONE_TYPE_NBT_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return CommandSource.suggestMatching(
			ZoneRegistry.REGISTRY.stream().map(type -> {
				return ZoneType.REGISTRY_CODEC.encodeStart(
						RegistryOps.of(NbtOps.INSTANCE, ctx.getSource().getRegistryManager()), type.defaultValue().get()
				).resultOrPartial(METAcraftZones.LOGGER::error).map(NbtElement::asString).orElse(null);
			}).filter(Objects::nonNull),
			suggestionsBuilder
		);
	};

	public static RealZone getZone(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, arg);
		if (!ZoneManager.getInstance(ctx.getSource().getServer()).containsZone(name)) {
			throw NO_ZONE_EXISTS.create();
		}
		return ZoneManager.getInstance(ctx.getSource().getServer()).getZone(name);
	}

	public static ArgumentBuilder<ServerCommandSource, ?> queryZoneMulti(
			LiteralArgumentBuilder<ServerCommandSource> commandName,
			Function<RealZone, Iterable<? extends Text>> printer
	) {
		return commandName.then(
				zone("zone").executes(ctx -> {
					RealZone zone = getZone(ctx, "zone");
					for (Text text : printer.apply(zone)) {
						ctx.getSource().sendFeedback(() -> text, false);
					}
					return 1;
				})
		);
	}

	public static RequiredArgumentBuilder<ServerCommandSource, ?> zone(String arg) {
		return argument(arg, StringArgumentType.string()).suggests(ZONE_NAME_SUGGESTIONS);
	}

	public static ArgumentBuilder<ServerCommandSource, ?> zoneType(String arg) {
		return argument(arg, NbtCompoundArgumentType.nbtCompound()).suggests(ZONE_TYPE_NBT_SUGGESTIONS);
	}

	public static ZoneType getZoneType(CommandContext<ServerCommandSource> ctx, String arg) throws CommandSyntaxException {
		NbtCompound nbt = NbtCompoundArgumentType.getNbtCompound(ctx, arg);
		try {
			var result = ZoneType.REGISTRY_CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, ctx.getSource().getRegistryManager()), nbt);
			return result.result().orElseThrow(() -> OTHER_ERROR.create(
					result.error().map(DataResult.Error::message).orElse("An unknown error occurred.")
			));
		} catch (IllegalArgumentException e) {
			throw OTHER_ERROR.create(e.getMessage());
		}
	}

}
