package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.enums.Orientation;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.math.BlockPos;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.dungeons.portal_data.Dungeon;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class PortalDeeper extends DataBlock implements MultiDataBlock {

	protected Optional<Orientation> direction;
	protected Optional<Integer> maxSize;
	protected Optional<RegistryKey<StructurePool>> jigsawPool;
	protected Optional<List<Dungeon.DepthSpecificPoolEntry>> depthSpecificPools;
	protected Optional<ContainerLock> lock;

	public static final MapCodec<PortalDeeper> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			ExtraCodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			RegistryKey.createCodec(RegistryKeys.TEMPLATE_POOL).optionalFieldOf("jigsaw_pool").forGetter(
					portal -> portal.jigsawPool
			),
			Codec.INT.optionalFieldOf("max_size").forGetter(portal -> portal.maxSize),
			Dungeon.DepthSpecificPoolEntry.CODEC.listOf().optionalFieldOf("depth_specific_pools").forGetter(
					portal -> portal.depthSpecificPools
			),
			ContainerLock.CODEC.optionalFieldOf("lock").forGetter(portal -> portal.lock)
		).apply(instance, PortalDeeper::new)
	);

	public PortalDeeper(
			Optional<Orientation> direction,
			Optional<RegistryKey<StructurePool>> jigsawPool,
			Optional<Integer> maxSize,
			Optional<List<Dungeon.DepthSpecificPoolEntry>> depthSpecificPools,
			Optional<ContainerLock> lock
	) {
		this.direction = direction;
		this.jigsawPool = jigsawPool;
		this.maxSize = maxSize;
		this.depthSpecificPools = depthSpecificPools;
		this.lock = lock;
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
	public void processDataBlocks(Collection<DataMultiBlockEntry<?>> blocks) {
		List<DataMultiBlockEntry<?>> doorBlocks = blocks.stream().toList();
		if (!doorBlocks.isEmpty()) {
			var entrance = doorBlocks.get(this.entrance.getWorld().getRandom().nextInt(doorBlocks.size()));
			parameters.dungeons.setBlockState(entrance.pos(), METAcraftBlocks.PORTAL_CORE.getDefaultState());
			var portalEntity = parameters.dungeons.getBlockEntity(entrance.pos());
			if (portalEntity instanceof PortalEntity portal) {
				portal.setPortalFacing(direction.map(
						direction -> OrientationHelper.rotate(direction, entrance.piece().getRotation())
				).orElse(null));
				lock.ifPresent(portal::setLock);
				List<Dungeon.DepthSpecificPoolEntry> pools = List.of();
				int newDepth = 1;
				int offset = 1;

				if (this.entrance.getTarget() instanceof Dungeon d) {
					pools = d.pools();
					newDepth = d.depthOffset() + d.dungeonDepth();
					offset = d.depthOffset();
				}

				portal.setTarget(
						new Dungeon(
								parameters.dungeons.getRegistryKey(),
								jigsawPool.orElse(parameters.entry.jigsawPool()),
								maxSize.orElse(parameters.entry.maxSize()),
								parameters.entry.aliases(),
								Optional.empty(),
								depthSpecificPools.orElse(pools),
								newDepth,
								offset
						)
				);
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
