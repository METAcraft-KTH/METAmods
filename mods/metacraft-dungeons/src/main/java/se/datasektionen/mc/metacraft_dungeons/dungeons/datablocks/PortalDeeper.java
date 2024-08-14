package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.enums.Orientation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_dungeons.block.DungeonBlocks;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class PortalDeeper extends DataBlock implements MultiDataBlock {

	protected Optional<Orientation> direction;
	protected Optional<Integer> maxSize;
	protected Optional<RegistryKey<StructurePool>> jigsawPool;
	protected Optional<List<DungeonEntranceEntity.PoolEntry>> depthSpecificPools;

	public static final Codec<PortalDeeper> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			ExtraCodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			RegistryKey.createCodec(RegistryKeys.TEMPLATE_POOL).optionalFieldOf("jigsaw_pool").forGetter(
					portal -> portal.jigsawPool
			),
			Codec.INT.optionalFieldOf("max_size").forGetter(portal -> portal.maxSize),
			DungeonEntranceEntity.PoolEntry.CODEC.listOf().optionalFieldOf("depth_specific_pools").forGetter(
					portal -> portal.depthSpecificPools
			)
		).apply(instance, PortalDeeper::new)
	);

	public PortalDeeper(
			Optional<Orientation> direction,
			Optional<RegistryKey<StructurePool>> jigsawPool,
			Optional<Integer> maxSize,
			Optional<List<DungeonEntranceEntity.PoolEntry>> depthSpecificPools
	) {
		this.direction = direction;
		this.jigsawPool = jigsawPool;
		this.maxSize = maxSize;
		this.depthSpecificPools = depthSpecificPools;
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.PORTAL_DEEPER;
	}

	@Override
	public void processDataBlock(BlockPos pos, StructurePiece piece) {
		parameters.dungeons.setBlockState(pos, METAcraftBlocks.PORTAL_PADDING.getDefaultState());
	}

	@Override
	public void processDataBlocks(Collection<DungeonEntranceEntity.DataMultiBlockEntry<?>> blocks) {
		List<DungeonEntranceEntity.DataMultiBlockEntry<?>> doorBlocks = blocks.stream().toList();
		if (!doorBlocks.isEmpty()) {
			var entrance = doorBlocks.get(this.entrance.getWorld().getRandom().nextInt(doorBlocks.size()));
			parameters.dungeons.setBlockState(entrance.pos(), DungeonBlocks.DUNGEON_ENTRANCE.getDefaultState());
			var entranceEntity = parameters.dungeons.getBlockEntity(entrance.pos());
			if (entranceEntity instanceof DungeonEntranceEntity deeperEntrance) {
				deeperEntrance.setPortalFacing(direction.map(
						direction -> OrientationHelper.rotate(direction, entrance.piece().getRotation())
				).orElse(null));
				deeperEntrance.setMaxSize(maxSize.orElse(parameters.entry.maxSize()));
				deeperEntrance.setJigsawPool(jigsawPool.orElse(parameters.entry.jigsawPool()));
				deeperEntrance.setDepthSpecificPools(depthSpecificPools.orElse(this.entrance.getDepthSpecificPools()));
				deeperEntrance.setAliases(parameters.entry.aliases());
				deeperEntrance.setDepth(this.entrance.getDepth()+this.entrance.getDepthOffset());
				deeperEntrance.setDepthOffset(parameters.entry.depthOffset());
			}
		}
	}

	@Override
	public int getPriority() {
		return (direction.isPresent() ? 1 : 0) +
				(maxSize.isPresent() ? 1 : 0) +
				(jigsawPool.isPresent() ? 1 : 0) +
				(depthSpecificPools.isPresent() ? 1 : 0);
	}
}
