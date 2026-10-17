package nu.metacraft.qol.void_anchor.block;

import nu.metacraft.qol.Qol;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import nu.metacraft.qol.void_anchor.AnchorBinding;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

/**
 * A respawn anchor that catches you when you fall into the End void. It keeps the respawn anchor's
 * charges, light and comparator output, but both of its use methods are replaced: vanilla's take
 * only glowstone, set spawn, and explode outside the Nether.
 */
public class VoidAnchorBlock extends RespawnAnchorBlock implements PolymerTexturedBlock {

	private final BlockState[] clientStates = new BlockState[MAX_CHARGES + 1];

	public VoidAnchorBlock(Properties properties) {
		super(properties);
		for (int charge = 0; charge <= MAX_CHARGES; charge++) {
			clientStates[charge] = PolymerBlockResourceUtils.requestBlock(
					BlockModelType.FULL_BLOCK, PolymerBlockModel.of(Qol.getID("block/void_anchor_" + charge))
			);
		}
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return clientStates[state.getValue(CHARGE)];
	}

	public static boolean canCharge(BlockState state) {
		return state.getValue(CHARGE) < MAX_CHARGES;
	}

	@Override
	protected InteractionResult useItemOn(
			ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
	) {
		var config = VoidAnchorConfig.getInstance();
		if (!config.enabled()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (config.isFuel(stack) && canCharge(state)) {
			charge(player, level, pos, state);
			stack.consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (hand == InteractionHand.MAIN_HAND && canCharge(state) && config.isFuel(player.getItemInHand(InteractionHand.OFF_HAND))) {
			// Let the offhand charge it, as vanilla does with glowstone.
			return InteractionResult.PASS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!VoidAnchorConfig.getInstance().enabled()) {
			player.sendOverlayMessage(Component.translatableWithFallback(
					"block.metacraft.void_anchor.disabled", "Void anchors are turned off on this server"
			).withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (level.dimension() != Level.END) {
			player.sendOverlayMessage(Component.translatableWithFallback(
					"block.metacraft.void_anchor.not_in_end", "Void anchors only work in the End"
			).withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS_SERVER;
		}
		var target = GlobalPos.of(level.dimension(), pos.immutable());
		if (!target.equals(AnchorBinding.get(serverPlayer))) {
			AnchorBinding.bind(serverPlayer, target);
			level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		player.sendOverlayMessage(Component.translatableWithFallback(
				"block.metacraft.void_anchor.bound", "This void anchor will catch you if you fall"
		).withStyle(ChatFormatting.LIGHT_PURPLE));
		return InteractionResult.SUCCESS_SERVER;
	}

}
