package nu.metacraft.minigame_util.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ForAllBlockEntities {

	private static final DynamicCommandExceptionType GENERIC = new DynamicCommandExceptionType(
			s -> Text.literal((String) s)
	);

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
			literal("for-all-block-entities").requires(player -> player.hasPermissionLevel(2)).then(
				argument("range", IntegerArgumentType.integer(0)).then(
					argument("id", IdentifierArgumentType.identifier()).fork(
						dispatcher.getRoot(), getRedirect((ctx, e) -> {
							var id = IdentifierArgumentType.getIdentifier(ctx, "id");
							if (id.getNamespace().startsWith("#")) {
								return e.getType().getRegistryEntry().isIn(TagKey.of(
										RegistryKeys.BLOCK_ENTITY_TYPE,
										Identifier.of(id.getNamespace().substring(1), id.getPath())
								));
							} else {
								return e.getType().getRegistryEntry().matchesId(id);
							}
						})
					)
				).then(
					literal("block_entity").then(
							argument("id", IdentifierArgumentType.identifier()).fork(
									dispatcher.getRoot(), getRedirect((ctx, e) -> {
										var id = IdentifierArgumentType.getIdentifier(ctx, "id");
										return e.getType().getRegistryEntry().matchesId(id);
									})
							)
					)
				).then(
					literal("tag").then(
							argument("tag", IdentifierArgumentType.identifier()).fork(
									dispatcher.getRoot(), getRedirect((ctx, e) -> {
										var id = IdentifierArgumentType.getIdentifier(ctx, "tag");
										return e.getType().getRegistryEntry().isIn(TagKey.of(
												RegistryKeys.BLOCK_ENTITY_TYPE, id
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

	private static RedirectModifier<ServerCommandSource> getRedirect(
			BiPredicate<CommandContext<ServerCommandSource>, BlockEntity> isValid
	) {
		return ctx -> {
			int range = IntegerArgumentType.getInteger(ctx, "range");
			List<ServerCommandSource> sources = new ArrayList<>();
			int cX = ChunkSectionPos.getSectionCoord(ctx.getSource().getPosition().getX());
			int cZ = ChunkSectionPos.getSectionCoord(ctx.getSource().getPosition().getZ());
			for (int x = cX - range; x <= cX + range; x++) {
				for (int z = cZ - range; z <= cZ + range; z++) {
					var chunk = ctx.getSource().getWorld().getChunk(x, z, ChunkStatus.FULL, false);
					if (chunk instanceof WorldChunk wc) {
						for (var e : wc.getBlockEntities().values()) {
							if (isValid.test(ctx, e)) {
								sources.add(ctx.getSource().withPosition(Vec3d.ofBottomCenter(e.getPos())));
							}
						}
					}
				}
			}
			return sources;
		};
	}

}
