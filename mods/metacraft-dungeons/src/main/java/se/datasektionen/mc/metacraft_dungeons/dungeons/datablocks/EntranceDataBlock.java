package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.math.Direction;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonBlocks;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class EntranceDataBlock extends PortalDeeper {

	public static final Codec<EntranceDataBlock> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Direction.CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			RegistryKey.createCodec(RegistryKeys.TEMPLATE_POOL).optionalFieldOf("jigsaw_pool").forGetter(
					portal -> portal.jigsawPool
			),
			Codec.INT.optionalFieldOf("max_size").forGetter(portal -> portal.maxSize),
			DungeonEntranceEntity.PoolEntry.CODEC.listOf().optionalFieldOf("depth_specific_pools").forGetter(
					portal -> portal.depthSpecificPools
			)
		).apply(instance, EntranceDataBlock::new)
	);

	public EntranceDataBlock(
			Optional<Direction> direction,
			Optional<RegistryKey<StructurePool>> jigsawPool,
			Optional<Integer> maxSize,
			Optional<List<DungeonEntranceEntity.PoolEntry>> depthSpecificPools
	) {
		super(direction, jigsawPool, maxSize, depthSpecificPools);
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.ENTRANCE;
	}

	@Override
	public void processDataBlocks(Collection<DungeonEntranceEntity.DataMultiBlockEntry<?>> blocks) {
		if (!parameters.foundEntrance) {
			List<DungeonEntranceEntity.DataMultiBlockEntry<?>> entrances = blocks.stream().toList();
			if (!entrances.isEmpty()) {
				var entrance = entrances.get(this.entrance.getWorld().getRandom().nextInt(entrances.size()));
				parameters.dungeons.setBlockState(entrance.pos(), DungeonBlocks.PORTAL.getDefaultState());
				if (parameters.dungeons.getBlockEntity(entrance.pos()) instanceof PortalEntity portal) {
					portal.setTargetPos(this.entrance.getPos());
					portal.setTargetDim(this.entrance.getWorld().getRegistryKey());
					portal.setPortalFacing(direction.map(
							direction -> entrance.piece().getRotation().rotate(direction)
					).orElse(null));
					this.entrance.setTargetPos(entrance.pos());
					parameters.foundEntrance = true;
				} else {
					this.entrance.setTargetPos(parameters.spawnPos);
					METAcraftDungeons.LOGGER.fatal("Entrance invalid!");
				}
			} else {
				this.entrance.setTargetPos(parameters.spawnPos);
				METAcraftDungeons.LOGGER.fatal("Failed to find entrance!");
			}
		} else {
			super.processDataBlocks(blocks);
		}
	}
}
