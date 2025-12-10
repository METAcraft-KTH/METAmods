package nu.metacraft.zones.util;

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
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.zone.RealZone;
import nu.metacraft.zones.zone.ZoneRegistry;
import nu.metacraft.zones.zone.types.ZoneType;

import java.util.Objects;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ZoneCommandUtils {

	public static final SimpleCommandExceptionType NO_ZONE_EXISTS = new SimpleCommandExceptionType(Component.literal("No zone exists with that name"));
	public static final DynamicCommandExceptionType OTHER_ERROR = new DynamicCommandExceptionType(o -> {
		return o instanceof Component t ? t : Component.literal(Objects.toString(o));
	});

	public static final SuggestionProvider<CommandSourceStack> ZONE_NAME_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
			ZoneManager.getInstance(ctx.getSource().getServer()).getZoneNames(), suggestionsBuilder
		);
	};

	public static final SuggestionProvider<CommandSourceStack> ZONE_TYPE_NBT_SUGGESTIONS = (ctx, suggestionsBuilder) -> {
		return SharedSuggestionProvider.suggest(
			ZoneRegistry.REGISTRY.stream().map(type -> {
				return ZoneType.REGISTRY_CODEC.encodeStart(
						RegistryOps.create(NbtOps.INSTANCE, ctx.getSource().registryAccess()), type.defaultValue().get()
				).resultOrPartial(METAcraftZones.LOGGER::error).map(Tag::toString).orElse(null);
			}).filter(Objects::nonNull),
			suggestionsBuilder
		);
	};

	public static RealZone getZone(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, arg);
		if (!ZoneManager.getInstance(ctx.getSource().getServer()).containsZone(name)) {
			throw NO_ZONE_EXISTS.create();
		}
		return ZoneManager.getInstance(ctx.getSource().getServer()).getZone(name);
	}

	public static ArgumentBuilder<CommandSourceStack, ?> queryZoneMulti(
			LiteralArgumentBuilder<CommandSourceStack> commandName,
			Function<RealZone, Iterable<? extends Component>> printer
	) {
		return commandName.then(
				zone("zone").executes(ctx -> {
					RealZone zone = getZone(ctx, "zone");
					for (Component text : printer.apply(zone)) {
						ctx.getSource().sendSuccess(() -> text, false);
					}
					return 1;
				})
		);
	}

	public static RequiredArgumentBuilder<CommandSourceStack, ?> zone(String arg) {
		return argument(arg, StringArgumentType.string()).suggests(ZONE_NAME_SUGGESTIONS);
	}

	public static ArgumentBuilder<CommandSourceStack, ?> zoneType(String arg) {
		return argument(arg, CompoundTagArgument.compoundTag()).suggests(ZONE_TYPE_NBT_SUGGESTIONS);
	}

	public static ZoneType getZoneType(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
		CompoundTag nbt = CompoundTagArgument.getCompoundTag(ctx, arg);
		try {
			var result = ZoneType.REGISTRY_CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, ctx.getSource().registryAccess()), nbt);
			return result.result().orElseThrow(() -> OTHER_ERROR.create(
					result.error().map(DataResult.Error::message).orElse("An unknown error occurred.")
			));
		} catch (IllegalArgumentException e) {
			throw OTHER_ERROR.create(e.getMessage());
		}
	}

	public static LiteralArgumentBuilder<CommandSourceStack> zoneCommandRoot() {
		return literal("zone").requires(Permissions.require("metacraft.zone", 2));
	}

}
