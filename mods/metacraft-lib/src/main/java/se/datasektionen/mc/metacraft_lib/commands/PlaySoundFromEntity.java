package se.datasektionen.mc.metacraft_lib.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorServerChunkLoadingManager;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PlaySoundFromEntity {

	private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.playsound.failed"));

	private static final DynamicCommandExceptionType INVALID_CATEGORY = new DynamicCommandExceptionType(
			category -> () -> category + " is not a valid category!"
	);


	private static final Keyable KEYS = new Keyable() {
		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Arrays.stream(SoundCategory.values()).map(
					c -> ExtraCodecs.SOUND_CATEGORY_CODEC.encodeStart(ops, c).result().orElseThrow()
			);
		}
	};

	private static final Decoder<SoundCategory> DECODER = ExtraCodecs.SOUND_CATEGORY_CODEC;

	private static SoundCategory getCategory(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var orientationKey = StringArgumentType.getString(ctx, name);
		return DECODER.parse(JavaOps.INSTANCE, orientationKey).result().orElseThrow(() -> INVALID_CATEGORY.create(orientationKey));
	}

	private static ArgumentBuilder<ServerCommandSource, ?> category(String name) {
		return argument(name, StringArgumentType.word()).suggests((ctx, builder) -> CommandSource.suggestMatching(
				KEYS.keys(JavaOps.INSTANCE).map(Object::toString), builder)
		);
	}

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
			literal("playsound-from-entity").requires(Permissions.require("metacraft.playsound-from-entity", 2)).then(
				argument("sound", IdentifierArgumentType.identifier()).suggests(
						SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS)
				).executes(
					ctx -> playSound(
							ctx, IdentifierArgumentType.getIdentifier(ctx, "sound"),
							List.of(ctx.getSource().getPlayerOrThrow()),
							ctx.getSource().getEntity()
					)
				).then(
					category("category").executes(
						ctx -> playSound(
								ctx, IdentifierArgumentType.getIdentifier(ctx, "sound"),
								List.of(ctx.getSource().getPlayerOrThrow()),
								ctx.getSource().getEntity(),
								getCategory(ctx, "category")
						)
					).then(
						argument("targets", EntityArgumentType.players()).executes(
							ctx -> playSound(
									ctx, IdentifierArgumentType.getIdentifier(ctx, "sound"),
									EntityArgumentType.getPlayers(ctx, "targets"),
									ctx.getSource().getEntity(),
									getCategory(ctx, "category")
							)
						).then(
							argument("source_entity", EntityArgumentType.entity()).executes(
								ctx -> playSound(
										ctx, IdentifierArgumentType.getIdentifier(ctx, "sound"),
										EntityArgumentType.getPlayers(ctx, "targets"),
										EntityArgumentType.getEntity(ctx, "source_entity"),
										getCategory(ctx, "category")
								)
							).then(
								argument("volume", FloatArgumentType.floatArg(0)).executes(
									ctx -> playSound(
											ctx, IdentifierArgumentType.getIdentifier(ctx, "sound"),
											EntityArgumentType.getPlayers(ctx, "targets"),
											EntityArgumentType.getEntity(ctx, "source_entity"),
											getCategory(ctx, "category"),
											FloatArgumentType.getFloat(ctx, "volume")
									)
								).then(
									argument("pitch", FloatArgumentType.floatArg(0.5f, 2)).executes(
										ctx -> playSound(
												ctx, IdentifierArgumentType.getIdentifier(ctx, "sound"),
												EntityArgumentType.getPlayers(ctx, "targets"),
												EntityArgumentType.getEntity(ctx, "source_entity"),
												getCategory(ctx, "category"),
												FloatArgumentType.getFloat(ctx, "volume"),
												FloatArgumentType.getFloat(ctx, "pitch")
										)
									)
								)
							)
						)
					)
				)
			)
		);
	}

	private static int playSound(
			CommandContext<ServerCommandSource> ctx, Identifier sound, Collection<ServerPlayerEntity> targets, Entity entity
	) throws CommandSyntaxException {
		return playSound(ctx, sound, targets, entity, SoundCategory.MASTER);
	}

	private static int playSound(
			CommandContext<ServerCommandSource> ctx, Identifier sound, Collection<ServerPlayerEntity> targets, Entity entity, SoundCategory category
	) throws CommandSyntaxException {
		return playSound(ctx, sound, targets, entity, category, 1);
	}

	private static int playSound(
			CommandContext<ServerCommandSource> ctx, Identifier sound, Collection<ServerPlayerEntity> targets, Entity entity, SoundCategory category, float volume
	) throws CommandSyntaxException {
		return playSound(ctx, sound, targets, entity, category, volume, 1);
	}

	private static int playSound(
			CommandContext<ServerCommandSource> ctx, Identifier sound, Collection<ServerPlayerEntity> targets, Entity entity, SoundCategory category, float volume, float pitch
	) throws CommandSyntaxException {
		if (!(entity.getWorld() instanceof ServerWorld sw)) {
			return 0;
		}
		var tracker = ((AccessorServerChunkLoadingManager) sw.getChunkManager().chunkLoadingManager).getEntityTrackers().get(entity.getId());
		Set<ServerPlayerEntity> inRangePlayers = ((AccessorServerChunkLoadingManager.EntityTracker) tracker).getListeners().stream().map(
				PlayerAssociatedNetworkHandler::getPlayer
		).collect(Collectors.toSet());
		RegistryEntry<SoundEvent> registryEntry = RegistryEntry.of(SoundEvent.of(sound));

		var packet = new PlaySoundFromEntityS2CPacket(
				registryEntry, category, entity, volume, pitch, ctx.getSource().getWorld().getRandom().nextLong()
		);

		int count = 0;

		for (var target : targets) {
			if (inRangePlayers.contains(target) || entity == target) {
				target.networkHandler.sendPacket(packet);
				count++;
			}
		}

		if (count == 0) {
			throw FAILED_EXCEPTION.create();
		}

		if (targets.size() == 1) {
			ctx.getSource().sendFeedback(() -> Text.translatable("commands.playsound.success.single", Text.of(sound), targets.iterator().next().getDisplayName()), true);
		} else {
			ctx.getSource().sendFeedback(() -> Text.translatable("commands.playsound.success.multiple", Text.of(sound), targets.size()), true);
		}
		return count;
	}

}
