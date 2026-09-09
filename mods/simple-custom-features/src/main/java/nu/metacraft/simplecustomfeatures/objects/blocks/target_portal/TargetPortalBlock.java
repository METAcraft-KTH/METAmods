package nu.metacraft.simplecustomfeatures.objects.blocks.target_portal;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import nu.metacraft.lib.util.helper.TamedHelper;

public class TargetPortalBlock extends EndPortalBlock implements PolymerBlock {

	private final TargetPortalObject portal;

	public TargetPortalBlock(Properties settings, TargetPortalObject portal) {
		super(settings);
		this.portal = portal;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return null;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
		return Blocks.END_PORTAL.defaultBlockState();
	}

	@Override
	protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl) {
		if (!entity.canUsePortal(false)) return;
		if (!Shapes.joinIsNotEmpty(Shapes.create(entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ())), state.getShape(world, pos), BooleanOp.AND)) return;
		entity.setAsInsidePortal(this, pos);
	}

	@Override
	public TeleportTransition getPortalDestination(ServerLevel world, Entity entity, BlockPos pos) {
		var target = portal.getTarget(world.dimension());
		if (target == null) return null;
		var targetWorld = world.getServer().getLevel(target.targetDim());
		if (targetWorld == null) return null;

		var targetPos = target.targetPos().orElse(targetWorld.getRespawnData().pos());
		var targetAngle = target.targetAngle().orElse(targetWorld.getRespawnData().yaw());

		var portalTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);

		if (target.spawnObsidianPlatform()) {
			EndPlatformFeature.createEndPlatform(targetWorld, targetPos.below(), true);
		} else if (target.usePlayerSpawn()) {
			var playerID = TamedHelper.getRelevantPlayer(entity);
			if (playerID.isPresent()) {
				var player = world.getServer().getPlayerList().getPlayer(playerID.get());
				if (player != null) {
					var teleportTarget = player.findRespawnPositionAndUseSpawnBlock(true, portalTransition);
					if (teleportTarget.newLevel() != world) {
						return teleportTarget;
					}
				}
			}
		}

		if (target.targetPos().isEmpty()) {
			targetPos = entity.adjustSpawnLocation(targetWorld, targetPos);
		}

		return new TeleportTransition(
				targetWorld, Vec3.atBottomCenterOf(targetPos), entity.getDeltaMovement(),
				targetAngle, entity.getXRot(), portalTransition
		);
	}

	@Override
	public void onPolymerBlockSend(BlockState blockState, BlockPos.MutableBlockPos pos, ServerPlayer player) {
		var blockEntity = new TheEndPortalBlockEntity(pos, Blocks.END_PORTAL.defaultBlockState());
		blockEntity.setLevel(player.level());
		player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
	}
}
