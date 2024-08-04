package se.datasektionen.mc.simplecustomfeatures.objects.blocks.target_portal;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndPlatformFeature;
import se.datasektionen.mc.metacraft_lib.util.helper.TamedHelper;

public class TargetPortalBlock extends EndPortalBlock implements PolymerBlock {

	private final TargetPortalObject portal;

	public TargetPortalBlock(Settings settings, TargetPortalObject portal) {
		super(settings);
		this.portal = portal;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state) {
		return Blocks.END_PORTAL.getDefaultState();
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (!entity.canUsePortals(false)) return;
		if (!VoxelShapes.matchesAnywhere(VoxelShapes.cuboid(entity.getBoundingBox().offset(-pos.getX(), -pos.getY(), -pos.getZ())), state.getOutlineShape(world, pos), BooleanBiFunction.AND)) return;
		entity.tryUsePortal(this, pos);
	}

	@Override
	public TeleportTarget createTeleportTarget(ServerWorld world, Entity entity, BlockPos pos) {
		var target = portal.getTarget(world.getRegistryKey());
		if (target == null) return null;
		var targetWorld = world.getServer().getWorld(target.targetDim());
		if (targetWorld == null) return null;

		var targetPos = target.targetPos().orElse(targetWorld.getSpawnPos());
		var targetAngle = target.targetAngle().orElse(targetWorld.getSpawnAngle());

		var portalTransition = TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET);

		if (target.spawnObsidianPlatform()) {
			EndPlatformFeature.generate(targetWorld, targetPos.down(), true);
		} else if (target.usePlayerSpawn()) {
			var playerID = TamedHelper.getRelevantPlayer(entity);
			if (playerID.isPresent()) {
				var player = world.getServer().getPlayerManager().getPlayer(playerID.get());
				if (player != null) {
					var teleportTarget = player.getRespawnTarget(true, portalTransition);
					if (teleportTarget.world() != world) {
						return teleportTarget;
					}
				}
			}
		}

		if (target.targetPos().isEmpty()) {
			targetPos = entity.getWorldSpawnPos(targetWorld, targetPos);
		}

		return new TeleportTarget(
				targetWorld, targetPos.toBottomCenterPos(), entity.getVelocity(),
				targetAngle, entity.getPitch(), portalTransition
		);
	}
}
