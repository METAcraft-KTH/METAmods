package nu.metacraft.core.block.blocks;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import xyz.nucleoid.packettweaker.PacketContext;

public class PortalPadding extends Block implements PolymerBlock {

	public PortalPadding(Properties settings) {
		super(settings);
	}

	@Override
	public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl) {
		for (BlockPos portalPos : PortalEntity.forAllNearbyPortals(world, pos)) {
			if (world.getBlockEntity(portalPos) instanceof PortalEntity portal) {
				portal.onCollision(state, world, pos, entity);
			}
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		for (BlockPos portalPos : PortalEntity.forAllNearbyPortals(world, pos)) {
			if (world.getBlockEntity(portalPos) instanceof PortalEntity portal) {
				return portal.interactWithItem(stack, state, world, pos, player, hand, hit);
			}
		}
		return super.useItemOn(stack, state, world, pos, player, hand, hit);
	}

	public static void sendDummyEndGateway(BlockPos pos, PacketContext.NotNullWithPlayer ctx) {
		var lookup = ctx.getRegistryWrapperLookup();
		if (lookup != null) {
			var tile = new TheEndGatewayBlockEntity(pos, Blocks.END_GATEWAY.defaultBlockState());
			var nbt = tile.getUpdateTag(ctx.getPlayer().registryAccess());
			nbt.putLong("Age", 300);
			try (var logging = LoggingErrorReporter.create(() -> "metacraft:PortalPadding#sendDummyEndGateway", METAcraftCore.LOGGER)) {
				var readView = TagValueInput.create(logging, lookup, nbt);
				tile.loadWithComponents(readView);
			}
			tile.setLevel(ctx.getPlayer().level());
			ctx.getPlayer().connection.send(ClientboundBlockEntityDataPacket.create(tile));
		}
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.END_GATEWAY.defaultBlockState();
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.MutableBlockPos pos, PacketContext.NotNullWithPlayer ctx) {
		sendDummyEndGateway(pos, ctx);
	}
}
