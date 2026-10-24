package se.metacraft.moredyes;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import se.metacraft.moredyes.color.ModColor;
import se.metacraft.moredyes.color.ModColors;
import se.metacraft.moredyes.content.Family;
import se.metacraft.moredyes.content.ModContent;
import se.metacraft.moredyes.sheep.SheepColors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.util.Prediction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Admin / testing commands:
 * <ul>
 *   <li>{@code /moredyes dye <targets> <color>} — dye sheep from the console.</li>
 *   <li>{@code /moredyes give [player]} — one stack of every dye and block item.</li>
 *   <li>{@code /moredyes showcase} — a debug-world style grid of every block, one row per colour,
 *	   starting a few blocks in front of the player.</li>
 *   <li>{@code /moredyes showcase backdrop} — the same grid for showing off: a white concrete wall
 *	   (grey in its own shade) behind every row, on stone, with room to stand in front of each row.</li>
 * </ul>
 * Vanilla clients can't see server-side creative tabs (the creative menu is client-side), so
 * Polymer's {@code /polymer creative} GUI and these commands are the way to get at the items.
 */
public final class ModCommands {
	private static final SimpleCommandExceptionType UNKNOWN_COLOR =
			new SimpleCommandExceptionType(Component.literal("Unknown More Dyes colour"));

	private ModCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal(MoreDyes.MOD_ID)
						.requires(PermissionPredicates.require(Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "command"), PermissionLevel.GAMEMASTERS))
						.then(Commands.literal("dye")
								.then(Commands.argument("targets", EntityArgument.entities())
										.then(Commands.argument("color", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														ModColors.all().stream().map(ModColor::id), builder))
												.executes(ModCommands::dye))))
						.then(Commands.literal("give")
								.executes(ctx -> give(ctx, ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("showcase")
								.executes(ctx -> showcase(ctx, false))
								.then(Commands.literal("backdrop").executes(ctx -> showcase(ctx, true))))
						// Debug: toggle a shulker box's lid without opening its menu (for looking at the model).
						.then(Commands.literal("lid")
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.then(Commands.literal("open").executes(ctx -> lid(ctx, true)))
										.then(Commands.literal("close").executes(ctx -> lid(ctx, false)))))));
	}

	private static int lid(CommandContext<CommandSourceStack> ctx, boolean open) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		if (!(player.level().getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) {
			ctx.getSource().sendFailure(Component.literal("No shulker box at " + pos.toShortString()));
			return 0;
		}
		if (open) box.startOpen(player);
		else box.stopOpen(player);
		return 1;
	}

	private static int dye(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ModColor color = ModColors.getOrNull(StringArgumentType.getString(ctx, "color"));
		if (color == null) throw UNKNOWN_COLOR.create();
		int n = 0;
		for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
			if (e instanceof Sheep sheep) {
				SheepColors.set(sheep, color);
				n++;
			}
		}
		int count = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Dyed " + count + " sheep " + color.name()), true);
		return n;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		int n = 0;
		for (ModColor color : ModColors.all()) {
			for (Item item : ModContent.items(color)) {
				n += giveStack(player, new ItemStack(item, item.getDefaultMaxStackSize() == 1 ? 1 : 16));
			}
		}
		int count = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + " More Dyes stacks"), true);
		return n;
	}

	private static int giveStack(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
		return 1;
	}

	/**
	 * Grid: rows per colour (away from the player), columns per family (to the right), 2 blocks
	 * apart on a smooth-stone platform. Shaped blocks get a representative state. With a backdrop,
	 * rows are 5 apart instead of 3: the row, its beds' heads, a 3-high wall, and two blocks to
	 * stand in front of the next row, on stone.
	 */
	private static int showcase(CommandContext<CommandSourceStack> ctx, boolean backdrop) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		Direction facing = player.getDirection();
		Direction right = facing.getClockWise();
		BlockPos origin = player.blockPosition().relative(facing, 3);

		int families = Family.values().length;
		int colors = ModColors.all().size() + 2; // + vanilla white and red rows for comparison
		int spacing = backdrop ? 5 : 3;
		BlockState floor = (backdrop ? Blocks.STONE : Blocks.SMOOTH_STONE).defaultBlockState();
		for (int r = -1; r < colors * spacing; r++) {
			for (int c = -1; c < families * 2; c++) {
				BlockPos p = origin.relative(facing, r).relative(right, c);
				level.setBlockAndUpdate(p.below(), floor);
				// the wall sits right behind each row's bed heads
				boolean wall = backdrop && r % spacing == 2;
				for (int up = 0; up < (backdrop ? 4 : 2); up++) {
					level.setBlockAndUpdate(p.above(up), wall && up < 3 ? Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		int placed = 0;
		int row = 0;
		for (ModColor color : ModColors.all()) {
			int col = 0;
			for (Family family : Family.values()) {
				Block block = ModContent.block(color, family);
				BlockPos pos = origin.relative(facing, row * spacing).relative(right, col * 2);
				placeShowcase(level, pos, block, facing);
				placed++;
				col++;
			}
			row++;
		}
		// Vanilla comparison rows: the same families in white and red (no vanilla wool/concrete
		// stairs or slabs exist on 26.2, so those columns stay empty).
		for (DyeColor vanilla : new DyeColor[]{DyeColor.WHITE, DyeColor.RED}) {
			int col = 0;
			for (Family family : Family.values()) {
				Block block = switch (family) {
					case WOOL -> Blocks.WOOL.pick(vanilla);
					case CARPET -> Blocks.CARPET.pick(vanilla);
					case CONCRETE -> Blocks.CONCRETE.pick(vanilla);
					case CONCRETE_POWDER -> Blocks.CONCRETE_POWDER.pick(vanilla);
					case TERRACOTTA -> Blocks.DYED_TERRACOTTA.pick(vanilla);
					case GLAZED_TERRACOTTA -> Blocks.GLAZED_TERRACOTTA.pick(vanilla);
					case CANDLE -> Blocks.DYED_CANDLE.pick(vanilla);
					case BED -> Blocks.BED.pick(vanilla);
					case SHULKER_BOX -> Blocks.DYED_SHULKER_BOX.pick(vanilla);
					case STAINED_GLASS -> Blocks.STAINED_GLASS.pick(vanilla);
					case STAINED_GLASS_PANE -> Blocks.STAINED_GLASS_PANE.pick(vanilla);
					case CANDLE_CAKE -> Blocks.DYED_CANDLE_CAKE.pick(vanilla);
					default -> null;
				};
				if (block != null) {
					BlockPos pos = origin.relative(facing, row * spacing).relative(right, col * 2);
					placeShowcase(level, pos, block, facing);
					placed++;
				}
				col++;
			}
			row++;
		}
		int count = placed;
		ctx.getSource().sendSuccess(() -> Component.literal("Placed " + count + " blocks; /moredyes give for the items"), true);
		return placed;
	}

	private static void placeShowcase(ServerLevel level, BlockPos pos, Block block, Direction facing) {
		BlockState state = showcaseState(block, facing.getOpposite());
		level.setBlockAndUpdate(pos, state);
		if (block instanceof BedBlock) {
			level.setBlockAndUpdate(pos.relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD));
		}
	}

	private static BlockState showcaseState(Block block, Direction towardPlayer) {
		BlockState state = block.defaultBlockState();
		if (block instanceof BedBlock) {
			// foot here, head one block further from the player (placed by the caller)
			return state.setValue(BedBlock.FACING, towardPlayer.getOpposite()).setValue(BedBlock.PART, BedPart.FOOT);
		}
		if (block instanceof StairBlock) {
			return state.setValue(StairBlock.FACING, towardPlayer.getOpposite());
		}
		if (block instanceof SlabBlock) {
			return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		}
		if (block instanceof CandleBlock) {
			return state.setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true);
		}
		if (block instanceof CandleCakeBlock) {
			return state.setValue(CandleCakeBlock.LIT, true);
		}
		if (block instanceof IronBarsBlock) {
			return state.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
		}
		if (block instanceof GlazedTerracottaBlock) {
			return state.setValue(GlazedTerracottaBlock.FACING, towardPlayer);
		}
		return state;
	}
}
