package nu.metacraft.relay.blocks.entity;

import com.mojang.serialization.DataResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import nu.metacraft.relay.blocks.RelayBlockEntities;
import nu.metacraft.relay.items.RelayComponents;
import nu.metacraft.relay.mixin.AccessorServerPlayerEntityRespawnPos;
import org.pcollections.HashTreePSet;

import java.util.Set;

public class RelayBlockEntity extends BlockEntity {

	protected RelayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public RelayBlockEntity(BlockPos pos, BlockState state) {
		this(RelayBlockEntities.RELAY, pos, state);
	}

	public DataResult<TeleportTarget> getTarget() {
		var mappings = getComponents().get(RelayComponents.VALID_DIMENSIONS);
		Set<RegistryKey<World>> validTargets;
		if (mappings != null) {
			validTargets = mappings.getOrDefault(world.getRegistryKey(), HashTreePSet.empty());
		} else {
			validTargets = HashTreePSet.empty();
		}
		var target = getComponents().get(DataComponentTypes.LODESTONE_TRACKER);
		if (target != null && target.target().isPresent()) {
			if (!validTargets.contains(target.target().get().dimension())) {
				if (validTargets.size() == 1 && validTargets.contains(world.getRegistryKey())) {
					return DataResult.error(() -> "Target is in another dimension");
				}
				return DataResult.error(() -> "Target dimension is not reachable");
			}
			var dim = world.getServer().getWorld(target.target().get().dimension());
			if (dim == null) return DataResult.error(() -> "Targeted dimension does not exist");
			return target.forWorld(dim).target().map(
					t -> {
						var respawnPos = RespawnAnchorBlock.findRespawnPosition(
								EntityType.PLAYER, dim, t.pos()
						);
						return respawnPos.map(pos -> DataResult.success(
								new TeleportTarget(
										dim, pos, Vec3d.ZERO,
										AccessorServerPlayerEntityRespawnPos.callGetYaw(pos, t.pos()),
										0, TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET
								)
						)).orElseGet(() -> DataResult.error(
								() -> "Target lodestone is obstructed (" +
										target.target().map(p -> p.pos().toShortString() + ", " + p.dimension().getValue()).orElse("missingno") + ")"
						));
					}
			).orElse(DataResult.error(
					() -> "Target lodestone missing (" +
							target.target().map(t -> t.pos().toShortString() + ", " + t.dimension().getValue()).orElse("missingno") + ")"
			));
		}
		return DataResult.error(() -> "No Target");
	}

	public boolean shouldExplode() {
		var mappings = getComponents().get(RelayComponents.VALID_DIMENSIONS);
		return mappings == null || !mappings.containsKey(world.getRegistryKey());
	}
}
