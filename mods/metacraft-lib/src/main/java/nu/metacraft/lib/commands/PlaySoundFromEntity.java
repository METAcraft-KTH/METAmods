package nu.metacraft.lib.commands;

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
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import nu.metacraft.lib.mixin.AccessorServerChunkLoadingManager;
import nu.metacraft.lib.util.METACodecs;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PlaySoundFromEntity {

	private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.playsound.failed"));

	private static final DynamicCommandExceptionType INVALID_CATEGORY = new DynamicCommandExceptionType(
			category -> () -> category + " is not a valid category!"
	);


	private static final Keyable KEYS = new Keyable() {
		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Arrays.stream(SoundSource.values()).map(
					c -> METACodecs.SOUND_CATEGORY_CODEC.encodeStart(ops, c).result().orElseThrow()
			);
		}
	};

	private static final Decoder<SoundSource> DECODER = METACodecs.SOUND_CATEGORY_CODEC;

	private static SoundSource getCategory(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var orientationKey = StringArgumentType.getString(ctx, name);
		return DECODER.parse(JavaOps.INSTANCE, orientationKey).result().orElseThrow(() -> INVALID_CATEGORY.create(orientationKey));
	}

	private static ArgumentBuilder<CommandSourceStack, ?> category(String name) {
		return argument(name, StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
				KEYS.keys(JavaOps.INSTANCE).map(Object::toString), builder)
		);
	}

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
			literal("playsound-from-entity").requires(Permissions.require("metacraft.playsound-from-entity", 2)).then(
				argument("sound", ResourceLocationArgument.id()).suggests(
						SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS)
				).executes(
					ctx -> playSound(
							ctx, ResourceLocationArgument.getId(ctx, "sound"),
							List.of(ctx.getSource().getPlayerOrException()),
							ctx.getSource().getEntity()
					)
				).then(
					category("category").executes(
						ctx -> playSound(
								ctx, ResourceLocationArgument.getId(ctx, "sound"),
								List.of(ctx.getSource().getPlayerOrException()),
								ctx.getSource().getEntity(),
								getCategory(ctx, "category")
						)
					).then(
						argument("targets", EntityArgument.players()).executes(
							ctx -> playSound(
									ctx, ResourceLocationArgument.getId(ctx, "sound"),
									EntityArgument.getPlayers(ctx, "targets"),
									ctx.getSource().getEntity(),
									getCategory(ctx, "category")
							)
						).then(
							argument("source_entity", EntityArgument.entity()).executes(
								ctx -> playSound(
										ctx, ResourceLocationArgument.getId(ctx, "sound"),
										EntityArgument.getPlayers(ctx, "targets"),
										EntityArgument.getEntity(ctx, "source_entity"),
										getCategory(ctx, "category")
								)
							).then(
								argument("volume", FloatArgumentType.floatArg(0)).executes(
									ctx -> playSound(
											ctx, ResourceLocationArgument.getId(ctx, "sound"),
											EntityArgument.getPlayers(ctx, "targets"),
											EntityArgument.getEntity(ctx, "source_entity"),
											getCategory(ctx, "category"),
											FloatArgumentType.getFloat(ctx, "volume")
									)
								).then(
									argument("pitch", FloatArgumentType.floatArg(0.5f, 2)).executes(
										ctx -> playSound(
												ctx, ResourceLocationArgument.getId(ctx, "sound"),
												EntityArgument.getPlayers(ctx, "targets"),
												EntityArgument.getEntity(ctx, "source_entity"),
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
			CommandContext<CommandSourceStack> ctx, ResourceLocation sound, Collection<ServerPlayer> targets, Entity entity
	) throws CommandSyntaxException {
		return playSound(ctx, sound, targets, entity, SoundSource.MASTER);
	}

	private static int playSound(
			CommandContext<CommandSourceStack> ctx, ResourceLocation sound, Collection<ServerPlayer> targets, Entity entity, SoundSource category
	) throws CommandSyntaxException {
		return playSound(ctx, sound, targets, entity, category, 1);
	}

	private static int playSound(
			CommandContext<CommandSourceStack> ctx, ResourceLocation sound, Collection<ServerPlayer> targets, Entity entity, SoundSource category, float volume
	) throws CommandSyntaxException {
		return playSound(ctx, sound, targets, entity, category, volume, 1);
	}

	private static int playSound(
			CommandContext<CommandSourceStack> ctx, ResourceLocation sound, Collection<ServerPlayer> targets, Entity entity, SoundSource category, float volume, float pitch
	) throws CommandSyntaxException {
		if (!(entity.level() instanceof ServerLevel sw)) {
			return 0;
		}
		var tracker = ((AccessorServerChunkLoadingManager) sw.getChunkSource().chunkMap).getEntityMap().get(entity.getId());
		Set<ServerPlayer> inRangePlayers = ((AccessorServerChunkLoadingManager.EntityTracker) tracker).getSeenBy().stream().map(
				ServerPlayerConnection::getPlayer
		).collect(Collectors.toSet());
		Holder<SoundEvent> registryEntry = Holder.direct(SoundEvent.createVariableRangeEvent(sound));

		var packet = new ClientboundSoundEntityPacket(
				registryEntry, category, entity, volume, pitch, ctx.getSource().getLevel().getRandom().nextLong()
		);

		int count = 0;

		for (var target : targets) {
			if (inRangePlayers.contains(target) || entity == target) {
				target.connection.send(packet);
				count++;
			}
		}

		if (count == 0) {
			throw FAILED_EXCEPTION.create();
		}

		if (targets.size() == 1) {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.playsound.success.single", Component.translationArg(sound), targets.iterator().next().getDisplayName()), true);
		} else {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.playsound.success.multiple", Component.translationArg(sound), targets.size()), true);
		}
		return count;
	}

}
