package nu.metacraft.dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.core.portal.FixedPortalTarget;
import nu.metacraft.dungeons.METAcraftDungeons;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

public class EntranceDataBlock extends DataBlock implements MultiDataBlock {

	public static final MapCodec<EntranceDataBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			Codec.lazyInitialized(() -> DataBlockRegistry.CODEC).optionalFieldOf("fallback").forGetter(d -> d.fallback),
			METACodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			Codec.DOUBLE.optionalFieldOf("min_dist_from_center", 0.0).forGetter(portal -> portal.minDistFromCenter)
		).apply(instance, EntranceDataBlock::new)
	);

	protected final Optional<DataBlock> fallback;
	protected final Optional<FrontAndTop> direction;
	protected final double minDistFromCenter;

	public EntranceDataBlock(
			Optional<DataBlock> fallback,
			Optional<FrontAndTop> direction,
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
		parameters.dungeons.setBlockAndUpdate(pos, METAcraftBlocks.PORTAL_PADDING.defaultBlockState());
	}

	private void applyFallback(Collection<DataMultiBlockEntry<?>> blocks) {
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
				parameters.dungeons.setBlockAndUpdate(b.pos(), Blocks.AIR.defaultBlockState());
			}
		});
	}

	@Override
	public void processDataBlocks(Collection<DataMultiBlockEntry<?>> blocks) {
		if (!parameters.foundEntrance) {
			List<DataMultiBlockEntry<?>> entrances = blocks.stream().toList();
			if (!entrances.isEmpty()) {
				var entrance = entrances.get(this.entrance.getLevel().getRandom().nextInt(entrances.size()));
				if (entrance.pos().closerThan(parameters.spawnPos, minDistFromCenter)) {
					applyFallback(blocks);
					return;
				}
				parameters.dungeons.setBlockAndUpdate(entrance.pos(), METAcraftBlocks.PORTAL_CORE.defaultBlockState());
				if (parameters.dungeons.getBlockEntity(entrance.pos()) instanceof PortalEntity portal) {
					portal.setTarget(FixedPortalTarget.create(this.entrance.getLevel().dimension(), this.entrance.getBlockPos()));
					portal.setPortalFacing(direction.map(
							direction -> OrientationHelper.rotate(direction, entrance.piece().getRotation())
					).orElse(null));
					this.entrance.setTarget(this.entrance.getTarget().getWithPos(entrance.pos()));
					parameters.foundEntrance = true;
				} else {
					this.entrance.setTarget(this.entrance.getTarget().getWithPos(parameters.spawnPos));
					METAcraftDungeons.LOGGER.fatal("Entrance invalid!");
					applyFallback(blocks);
				}
			} else {
				this.entrance.setTarget(this.entrance.getTarget().getWithPos(parameters.spawnPos));
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
