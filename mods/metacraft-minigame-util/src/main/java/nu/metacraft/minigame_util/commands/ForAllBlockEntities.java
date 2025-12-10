package nu.metacraft.minigame_util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ForAllBlockEntities {

	private static final DynamicCommandExceptionType GENERIC = new DynamicCommandExceptionType(
			s -> Component.literal((String) s)
	);

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
			literal("for-all-block-entities").requires(
					player -> player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
			).then(
				argument("range", IntegerArgumentType.integer(0)).then(
					argument("id", IdentifierArgument.id()).fork(
						dispatcher.getRoot(), getRedirect((ctx, e) -> {
							var id = IdentifierArgument.getId(ctx, "id");
							if (id.getNamespace().startsWith("#")) {
								return e.getType().builtInRegistryHolder().is(TagKey.create(
										Registries.BLOCK_ENTITY_TYPE,
										Identifier.fromNamespaceAndPath(id.getNamespace().substring(1), id.getPath())
								));
							} else {
								return e.getType().builtInRegistryHolder().is(id);
							}
						})
					)
				).then(
					literal("block_entity").then(
							argument("id", IdentifierArgument.id()).fork(
									dispatcher.getRoot(), getRedirect((ctx, e) -> {
										var id = IdentifierArgument.getId(ctx, "id");
										return e.getType().builtInRegistryHolder().is(id);
									})
							)
					)
				).then(
					literal("tag").then(
							argument("tag", IdentifierArgument.id()).fork(
									dispatcher.getRoot(), getRedirect((ctx, e) -> {
										var id = IdentifierArgument.getId(ctx, "tag");
										return e.getType().builtInRegistryHolder().is(TagKey.create(
												Registries.BLOCK_ENTITY_TYPE, id
										));
									})
							)
					)
				).then(
					literal("*").fork(
							dispatcher.getRoot(), getRedirect((ctx, e) -> true)
					)
				)
			)
		);
	}

	private static RedirectModifier<CommandSourceStack> getRedirect(
			BiPredicate<CommandContext<CommandSourceStack>, BlockEntity> isValid
	) {
		return ctx -> {
			int range = IntegerArgumentType.getInteger(ctx, "range");
			List<CommandSourceStack> sources = new ArrayList<>();
			int cX = SectionPos.posToSectionCoord(ctx.getSource().getPosition().x());
			int cZ = SectionPos.posToSectionCoord(ctx.getSource().getPosition().z());
			for (int x = cX - range; x <= cX + range; x++) {
				for (int z = cZ - range; z <= cZ + range; z++) {
					var chunk = ctx.getSource().getLevel().getChunk(x, z, ChunkStatus.FULL, false);
					if (chunk instanceof LevelChunk wc) {
						for (var e : wc.getBlockEntities().values()) {
							if (isValid.test(ctx, e)) {
								sources.add(ctx.getSource().withPosition(Vec3.atBottomCenterOf(e.getBlockPos())));
							}
						}
					}
				}
			}
			return sources;
		};
	}

}
