package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.enums.Orientation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.pool.StructurePool;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class EntranceDataBlock extends PortalDeeper {

	public static final Codec<EntranceDataBlock> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			ExtraCodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
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
			Optional<Orientation> direction,
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
