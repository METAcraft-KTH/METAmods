package se.datasektionen.mc.metacraft_core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.block.enums.Orientation;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.METAcraftCoreTags;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.TextHelper;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LinkPortals {

	public static void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess
	) {
		dispatcher.register(
				literal("portal-link").requires(Permissions.require("metacraft.portal-link", 2)).then(
						argument("source", BlockPosArgumentType.blockPos()).then(
								argument("target", BlockPosArgumentType.blockPos()).executes(
										ctx -> linkPortals(
												ctx, ctx.getSource().getWorld(),
												BlockPosArgumentType.getBlockPos(ctx, "source"),
												ctx.getSource().getWorld(),
												BlockPosArgumentType.getBlockPos(ctx, "target")
										)
								).then(
										argument("target-world", DimensionArgumentType.dimension()).executes(
												ctx -> linkPortals(
														ctx, ctx.getSource().getWorld(),
														BlockPosArgumentType.getBlockPos(ctx, "source"),
														DimensionArgumentType.getDimensionArgument(ctx, "target-world"),
														BlockPosArgumentType.getBlockPos(ctx, "target")
												)
										)
								)
						).then(
								argument("source-world", DimensionArgumentType.dimension()).then(
										argument("target", BlockPosArgumentType.blockPos()).executes(
												ctx -> linkPortals(
														ctx, DimensionArgumentType.getDimensionArgument(ctx, "source-world"),
														BlockPosArgumentType.getBlockPos(ctx, "source"),
														ctx.getSource().getWorld(),
														BlockPosArgumentType.getBlockPos(ctx, "target")
												)
										)
								)
						)
				)
		);
		dispatcher.register(
				literal("portal-facing").requires(Permissions.require("metacraft.portal-link", 2)).then(
						argument("pos", BlockPosArgumentType.blockPos()).then(
								orientation("orientation").executes(
										ctx -> setFacing(
												ctx, BlockPosArgumentType.getBlockPos(ctx,"pos"),
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
					StringIdentifiable.toKeyable(Orientation.values()).keys(ops),
					StringIdentifiable.toKeyable(Direction.values()).keys(ops)
			);
		}
	};

	private static final Decoder<Orientation> DECODER = ExtraCodecs.ORIENTATION_CODEC;

	private static Orientation getOrientation(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
		var orientationKey = StringArgumentType.getString(ctx, name);
		return DECODER.parse(JavaOps.INSTANCE, orientationKey).result().orElseThrow(() -> INVALID_ORIENTATION.create(orientationKey));
	}

	private static ArgumentBuilder<ServerCommandSource, ?> orientation(String name) {
		return argument(name, StringArgumentType.word()).suggests((ctx, builder) -> CommandSource.suggestMatching(
				KEYS.keys(JavaOps.INSTANCE).map(Object::toString), builder)
		);
	}

	private static String invalidPortal(ServerWorld world, BlockPos pos) {
		return "No valid portal at " + pos.toShortString() + " in " + world.getRegistryKey().getValue();
	}

	private static PortalEntity getPortal(World world, BlockPos pos) {
		var entity = world.getBlockEntity(pos);
		if (entity instanceof PortalEntity p) {
			return p;
		}
		return null;
	}

	private static List<BlockPos> getAllPortalPositions(World world, BlockPos startPos) {
		List<BlockPos> positions = new ArrayList<>();
		positions.add(startPos);
		for (var pos : PortalEntity.forAllNearbyPortals(world, startPos, true)) {
			positions.add(pos.toImmutable());
		}
		return positions;
	}

	private static Stream<PortalEntity> getAllPortals(World world, BlockPos startPos) {
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

	private static List<PortalEntity> getAllPortals(World world, List<BlockPos> portalPositions) {
		List<PortalEntity> portals = new ArrayList<>();
		for (var pos : portalPositions) {
			var portal = getPortal(world, pos);
			if (portal != null) {
				portals.add(portal);
			}
		}
		return portals;
	}

	private static PortalEntity insertPortal(World world, List<BlockPos> portalPositions) {
		var pos = portalPositions.get(world.getRandom().nextInt(portalPositions.size()));
		world.setBlockState(pos, METAcraftBlocks.PORTAL_CORE.getDefaultState());
		return getPortal(world, pos);
	}

	private static String stringify(PortalEntity portal) {
		return portal.getPos().toShortString();
	}

	private static String tooManyPortals(World world, List<PortalEntity> portals) {
		return "Multiple portal cores present at " + TextHelper.combine(portals, LinkPortals::stringify)
				+ " in " + world.getRegistryKey().getValue() + ". Please remove them to link the portals.";
	}

	private static int setFacing(CommandContext<ServerCommandSource> ctx, BlockPos pos, Orientation orientation) {
		var portals = (Iterable<PortalEntity>) getAllPortals(ctx.getSource().getWorld(), pos)::iterator;
		int foundPortals = 0;
		for (var portal : portals) {
			portal.setPortalFacing(orientation);
			foundPortals++;
		}
		if (foundPortals == 0) {
			ctx.getSource().sendError(
					Text.literal(invalidPortal(ctx.getSource().getWorld(), pos))
			);
		} else {
			ctx.getSource().sendFeedback(() -> Text.literal("Set facing to " + orientation.asString()), false);
		}
		return foundPortals;
	}

	private static int linkPortals(
			CommandContext<ServerCommandSource> ctx,
			ServerWorld sourceWorld, BlockPos sourcePos,
			ServerWorld targetWorld, BlockPos targetPos
	) throws CommandSyntaxException {
		var sourceState = sourceWorld.getBlockState(sourcePos);
		var targetState = targetWorld.getBlockState(targetPos);

		if (!sourceState.isIn(METAcraftCoreTags.PORTAL)) {
			ctx.getSource().sendError(
					Text.literal(invalidPortal(sourceWorld, sourcePos))
			);
			return 0;
		}

		if (!targetState.isIn(METAcraftCoreTags.PORTAL)) {
			ctx.getSource().sendError(
					Text.literal(invalidPortal(targetWorld, targetPos))
			);
			return 0;
		}

		var sourcePortalPositions = getAllPortalPositions(sourceWorld, sourcePos);
		var sourcePortals = getAllPortals(sourceWorld, sourcePortalPositions);
		if (sourcePortals.size() > 1) {
			ctx.getSource().sendError(Text.literal(tooManyPortals(sourceWorld, sourcePortals)));
			return 0;
		}

		var targetPortalPositions = getAllPortalPositions(targetWorld, targetPos);
		var targetPortals = getAllPortals(sourceWorld, targetPortalPositions);
		if (targetPortals.size() > 1) {
			ctx.getSource().sendError(Text.literal(tooManyPortals(targetWorld, targetPortals)));
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

		sourcePortal.setTargetDim(targetWorld.getRegistryKey());
		sourcePortal.setTargetPos(targetPortal.getPos());
		targetPortal.setTargetDim(sourceWorld.getRegistryKey());
		targetPortal.setTargetPos(sourcePortal.getPos());

		ctx.getSource().sendFeedback(() -> Text.literal("Successfully linked portals"), false);

		return 1;
	}

}
