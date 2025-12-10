package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import nu.metacraft.core.METAcraftCoreTags;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.portal.FixedPortalTarget;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.helper.TextHelper;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class LinkPortals {

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess
	) {
		dispatcher.register(
				literal("portal-link").requires(Permissions.require("metacraft.portal-link", 2)).then(
						argument("source", BlockPosArgument.blockPos()).then(
								argument("target", BlockPosArgument.blockPos()).executes(
										ctx -> linkPortals(
												ctx, ctx.getSource().getLevel(),
												BlockPosArgument.getBlockPos(ctx, "source"),
												ctx.getSource().getLevel(),
												BlockPosArgument.getBlockPos(ctx, "target")
										)
								).then(
										argument("target-world", DimensionArgument.dimension()).executes(
												ctx -> linkPortals(
														ctx, ctx.getSource().getLevel(),
														BlockPosArgument.getBlockPos(ctx, "source"),
														DimensionArgument.getDimension(ctx, "target-world"),
														BlockPosArgument.getBlockPos(ctx, "target")
												)
										)
								)
						).then(
								argument("source-world", DimensionArgument.dimension()).then(
										argument("target", BlockPosArgument.blockPos()).executes(
												ctx -> linkPortals(
														ctx, DimensionArgument.getDimension(ctx, "source-world"),
														BlockPosArgument.getBlockPos(ctx, "source"),
														ctx.getSource().getLevel(),
														BlockPosArgument.getBlockPos(ctx, "target")
												)
										)
								)
						)
				)
		);
		dispatcher.register(
				literal("portal-facing").requires(Permissions.require("metacraft.portal-link", 2)).then(
						argument("pos", BlockPosArgument.blockPos()).then(
								orientation("orientation").executes(
										ctx -> setFacing(
												ctx, BlockPosArgument.getBlockPos(ctx,"pos"),
												getOrientation(ctx, "orientation")
										)
								)
						)
				)
		);
	}

	private static final DynamicCommandExceptionType INVALID_ORIENTATION = new DynamicCommandExceptionType(
			orientation -> () -> orientation + " is not a valid orientation!"
	);

	private static final Keyable KEYS = new Keyable() {
		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Stream.concat(
					StringRepresentable.keys(FrontAndTop.values()).keys(ops),
					StringRepresentable.keys(Direction.values()).keys(ops)
			);
		}
	};

	private static final Decoder<FrontAndTop> DECODER = METACodecs.ORIENTATION_CODEC;

	private static FrontAndTop getOrientation(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		var orientationKey = StringArgumentType.getString(ctx, name);
		return DECODER.parse(JavaOps.INSTANCE, orientationKey).result().orElseThrow(() -> INVALID_ORIENTATION.create(orientationKey));
	}

	private static ArgumentBuilder<CommandSourceStack, ?> orientation(String name) {
		return argument(name, StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
				KEYS.keys(JavaOps.INSTANCE).map(Object::toString), builder)
		);
	}

	private static String invalidPortal(ServerLevel world, BlockPos pos) {
		return "No valid portal at " + pos.toShortString() + " in " + world.dimension().identifier();
	}

	private static PortalEntity getPortal(Level world, BlockPos pos) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof PortalEntity p) {
			return p;
		}
		return null;
	}

	private static List<BlockPos> getAllPortalPositions(Level world, BlockPos startPos) {
		List<BlockPos> positions = new ArrayList<>();
		positions.add(startPos);
		for (var pos : PortalEntity.forAllNearbyPortals(world, startPos, true)) {
			positions.add(pos.immutable());
		}
		return positions;
	}

	private static Stream<PortalEntity> getAllPortals(Level world, BlockPos startPos) {
		var it = StreamSupport.stream(
				PortalEntity.forAllNearbyPortals(world, startPos, true).spliterator(), false
		).map(pos -> getPortal(world, pos)).filter(Objects::nonNull);
		var p = getPortal(world, startPos);
		if (p != null) {
			return Stream.concat(Stream.of(p), it);
		} else {
			return it;
		}
	}

	private static List<PortalEntity> getAllPortals(Level world, List<BlockPos> portalPositions) {
		List<PortalEntity> portals = new ArrayList<>();
		for (var pos : portalPositions) {
			var portal = getPortal(world, pos);
			if (portal != null) {
				portals.add(portal);
			}
		}
		return portals;
	}

	private static PortalEntity insertPortal(Level world, List<BlockPos> portalPositions) {
		var pos = portalPositions.get(world.getRandom().nextInt(portalPositions.size()));
		world.setBlockAndUpdate(pos, METAcraftBlocks.PORTAL_CORE.defaultBlockState());
		return getPortal(world, pos);
	}

	private static String stringify(PortalEntity portal) {
		return portal.getBlockPos().toShortString();
	}

	private static String tooManyPortals(Level world, List<PortalEntity> portals) {
		return "Multiple portal cores present at " + TextHelper.combine(portals, LinkPortals::stringify)
				+ " in " + world.dimension().identifier() + ". Please remove them to link the portals.";
	}

	private static int setFacing(CommandContext<CommandSourceStack> ctx, BlockPos pos, FrontAndTop orientation) {
		var portals = (Iterable<PortalEntity>) getAllPortals(ctx.getSource().getLevel(), pos)::iterator;
		int foundPortals = 0;
		for (var portal : portals) {
			portal.setPortalFacing(orientation);
			foundPortals++;
		}
		if (foundPortals == 0) {
			ctx.getSource().sendFailure(
					Component.literal(invalidPortal(ctx.getSource().getLevel(), pos))
			);
		} else {
			ctx.getSource().sendSuccess(() -> Component.literal("Set facing to " + orientation.getSerializedName()), false);
		}
		return foundPortals;
	}

	private static int linkPortals(
			CommandContext<CommandSourceStack> ctx,
			ServerLevel sourceWorld, BlockPos sourcePos,
			ServerLevel targetWorld, BlockPos targetPos
	) throws CommandSyntaxException {
		var sourceState = sourceWorld.getBlockState(sourcePos);
		var targetState = targetWorld.getBlockState(targetPos);

		if (!sourceState.is(METAcraftCoreTags.PORTAL)) {
			ctx.getSource().sendFailure(
					Component.literal(invalidPortal(sourceWorld, sourcePos))
			);
			return 0;
		}

		if (!targetState.is(METAcraftCoreTags.PORTAL)) {
			ctx.getSource().sendFailure(
					Component.literal(invalidPortal(targetWorld, targetPos))
			);
			return 0;
		}

		var sourcePortalPositions = getAllPortalPositions(sourceWorld, sourcePos);
		var sourcePortals = getAllPortals(sourceWorld, sourcePortalPositions);
		if (sourcePortals.size() > 1) {
			ctx.getSource().sendFailure(Component.literal(tooManyPortals(sourceWorld, sourcePortals)));
			return 0;
		}

		var targetPortalPositions = getAllPortalPositions(targetWorld, targetPos);
		var targetPortals = getAllPortals(sourceWorld, targetPortalPositions);
		if (targetPortals.size() > 1) {
			ctx.getSource().sendFailure(Component.literal(tooManyPortals(targetWorld, targetPortals)));
			return 0;
		}

		PortalEntity sourcePortal = sourcePortals.isEmpty() ? null : sourcePortals.getFirst();
		if (sourcePortal == null) {
			sourcePortal = insertPortal(sourceWorld, sourcePortalPositions);
		}

		PortalEntity targetPortal = targetPortals.isEmpty() ? null : targetPortals.getFirst();
		if (targetPortal == null) {
			targetPortal = insertPortal(targetWorld, targetPortalPositions);
		}

		sourcePortal.setTarget(FixedPortalTarget.create(targetWorld.dimension(), targetPortal.getBlockPos()));
		targetPortal.setTarget(FixedPortalTarget.create(sourceWorld.dimension(), sourcePortal.getBlockPos()));

		ctx.getSource().sendSuccess(() -> Component.literal("Successfully linked portals"), false);

		return 1;
	}

}
