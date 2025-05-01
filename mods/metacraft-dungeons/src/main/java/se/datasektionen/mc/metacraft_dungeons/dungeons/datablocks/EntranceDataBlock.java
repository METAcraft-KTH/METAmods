package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.Orientation;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class EntranceDataBlock extends DataBlock implements MultiDataBlock {

	public static final MapCodec<EntranceDataBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.lazyInitialized(() -> DataBlockRegistry.CODEC).optionalFieldOf("fallback").forGetter(d -> d.fallback),
			ExtraCodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			Codec.DOUBLE.optionalFieldOf("min_dist_from_center", 0.0).forGetter(portal -> portal.minDistFromCenter)
		).apply(instance, EntranceDataBlock::new)
	);

	protected final Optional<DataBlock> fallback;
	protected final Optional<Orientation> direction;
	protected final double minDistFromCenter;

	public EntranceDataBlock(
			Optional<DataBlock> fallback,
			Optional<Orientation> direction,
			double minDistFromCenter
	) {
		this.fallback = fallback;
		this.direction = direction;
		this.minDistFromCenter = minDistFromCenter;
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.ENTRANCE;
	}

	@Override
	public void processDataBlock(BlockPos pos, StructurePiece piece) {
		parameters.dungeons.setBlockState(pos, METAcraftBlocks.PORTAL_PADDING.getDefaultState());
	}

	private void applyFallback(Collection<DungeonEntranceEntity.DataMultiBlockEntry<?>> blocks) {
		fallback.ifPresentOrElse(f -> {
			f.initialise(entrance, parameters);
			for (var b : blocks) {
				f.processDataBlock(b.pos(), b.piece());
			}
			if (f instanceof MultiDataBlock d) {
				d.processDataBlocks(blocks);
			}
		}, () -> {
			for (var b : blocks) {
				parameters.dungeons.setBlockState(b.pos(), Blocks.AIR.getDefaultState());
			}
		});
	}

	@Override
	public void processDataBlocks(Collection<DungeonEntranceEntity.DataMultiBlockEntry<?>> blocks) {
		if (!parameters.foundEntrance) {
			List<DungeonEntranceEntity.DataMultiBlockEntry<?>> entrances = blocks.stream().toList();
			if (!entrances.isEmpty()) {
				var entrance = entrances.get(this.entrance.getWorld().getRandom().nextInt(entrances.size()));
				if (entrance.pos().isWithinDistance(parameters.spawnPos, minDistFromCenter)) {
					applyFallback(blocks);
					return;
				}
				parameters.dungeons.setBlockState(entrance.pos(), METAcraftBlocks.PORTAL_CORE.getDefaultState());
				if (parameters.dungeons.getBlockEntity(entrance.pos()) instanceof PortalEntity portal) {
					portal.setTargetPos(this.entrance.getPos());
					portal.setTargetDim(this.entrance.getWorld().getRegistryKey());
					portal.setPortalFacing(direction.map(
							direction -> OrientationHelper.rotate(direction, entrance.piece().getRotation())
					).orElse(null));
					this.entrance.setTargetPos(entrance.pos());
					parameters.foundEntrance = true;
				} else {
					this.entrance.setTargetPos(parameters.spawnPos);
					METAcraftDungeons.LOGGER.fatal("Entrance invalid!");
					applyFallback(blocks);
				}
			} else {
				this.entrance.setTargetPos(parameters.spawnPos);
				METAcraftDungeons.LOGGER.fatal("Failed to find entrance!");
				applyFallback(blocks);
			}
		} else {
			applyFallback(blocks);
		}
	}

	@Override
	public int getPriority() {
		return fallback.map(f -> f instanceof MultiDataBlock b ? b.getPriority() : 1).orElse(0);
	}
}
