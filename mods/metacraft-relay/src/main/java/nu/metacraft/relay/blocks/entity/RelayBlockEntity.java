package nu.metacraft.relay.blocks.entity;

import com.mojang.serialization.DataResult;
import nu.metacraft.relay.blocks.RelayBlockEntities;
import nu.metacraft.relay.items.RelayComponents;
import nu.metacraft.relay.mixin.AccessorServerPlayerEntityRespawnPos;
import org.pcollections.HashTreePSet;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public class RelayBlockEntity extends BlockEntity {

	protected RelayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public RelayBlockEntity(BlockPos pos, BlockState state) {
		this(RelayBlockEntities.RELAY, pos, state);
	}

	public DataResult<TeleportTransition> getTarget() {
		var mappings = components().get(RelayComponents.VALID_DIMENSIONS);
		Set<ResourceKey<Level>> validTargets;
		if (mappings != null) {
			validTargets = mappings.getOrDefault(level.dimension(), HashTreePSet.empty());
		} else {
			validTargets = HashTreePSet.empty();
		}
		var target = components().get(DataComponents.LODESTONE_TRACKER);
		if (target != null && target.target().isPresent()) {
			if (!validTargets.contains(target.target().get().dimension())) {
				if (validTargets.size() == 1 && validTargets.contains(level.dimension())) {
					return DataResult.error(() -> "Target is in another dimension");
				}
				return DataResult.error(() -> "Target dimension is not reachable");
			}
			var dim = level.getServer().getLevel(target.target().get().dimension());
			if (dim == null) return DataResult.error(() -> "Targeted dimension does not exist");
			return target.tick(dim).target().map(
					t -> {
						var respawnPos = RespawnAnchorBlock.findStandUpPosition(
								EntityType.PLAYER, dim, t.pos()
						);
						return respawnPos.map(pos -> DataResult.success(
								new TeleportTransition(
										dim, pos, Vec3.ZERO,
										AccessorServerPlayerEntityRespawnPos.callCalculateLookAtYaw(pos, t.pos()),
										0, TeleportTransition.PLAY_PORTAL_SOUND
								)
						)).orElseGet(() -> DataResult.error(
								() -> "Target lodestone is obstructed (" +
										target.target().map(p -> p.pos().toShortString() + ", " + p.dimension().location()).orElse("missingno") + ")"
						));
					}
			).orElse(DataResult.error(
					() -> "Target lodestone missing (" +
							target.target().map(t -> t.pos().toShortString() + ", " + t.dimension().location()).orElse("missingno") + ")"
			));
		}
		return DataResult.error(() -> "No Target");
	}

	public boolean shouldExplode() {
		var mappings = components().get(RelayComponents.VALID_DIMENSIONS);
		return mappings == null || !mappings.containsKey(level.dimension());
	}
}
