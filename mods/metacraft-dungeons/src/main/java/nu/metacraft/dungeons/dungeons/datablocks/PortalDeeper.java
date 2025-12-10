package nu.metacraft.dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.core.block.METAcraftBlocks;
import nu.metacraft.core.block.entities.PortalEntity;
import nu.metacraft.dungeons.dungeons.portal_data.Dungeon;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.LockCode;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class PortalDeeper extends DataBlock implements MultiDataBlock {

	protected Optional<FrontAndTop> direction;
	protected Optional<Integer> maxSize;
	protected Optional<Integer> maxDistanceFromCenter;
	protected Optional<ResourceKey<StructureTemplatePool>> jigsawPool;
	protected Optional<List<Dungeon.DepthSpecificPoolEntry>> pools;
	protected Optional<LockCode> lock;

	public static final MapCodec<PortalDeeper> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			METACodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			ResourceKey.codec(Registries.TEMPLATE_POOL).optionalFieldOf("jigsaw_pool").forGetter(
					portal -> portal.jigsawPool
			),
			Codec.INT.optionalFieldOf("max_size").forGetter(portal -> portal.maxSize),
			Codec.INT.optionalFieldOf("max_distance_from_center").forGetter(portal -> portal.maxDistanceFromCenter),
			Dungeon.DepthSpecificPoolEntry.CODEC.listOf().optionalFieldOf("pools").forGetter(
					portal -> portal.pools
			),
			LockCode.CODEC.optionalFieldOf("lock").forGetter(portal -> portal.lock)
		).apply(instance, PortalDeeper::new)
	);

	public PortalDeeper(
			Optional<FrontAndTop> direction,
			Optional<ResourceKey<StructureTemplatePool>> jigsawPool,
			Optional<Integer> maxSize,
			Optional<Integer> maxDistanceFromCenter,
			Optional<List<Dungeon.DepthSpecificPoolEntry>> pools,
			Optional<LockCode> lock
	) {
		this.direction = direction;
		this.jigsawPool = jigsawPool;
		this.maxSize = maxSize;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.pools = pools;
		this.lock = lock;
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.PORTAL_DEEPER;
	}

	@Override
	public void processDataBlock(BlockPos pos, StructurePiece piece) {
		parameters.dungeons.setBlockAndUpdate(pos, METAcraftBlocks.PORTAL_PADDING.defaultBlockState());
	}

	@Override
	public void processDataBlocks(Collection<DataMultiBlockEntry<?>> blocks) {
		List<DataMultiBlockEntry<?>> doorBlocks = blocks.stream().toList();
		if (!doorBlocks.isEmpty()) {
			var entrance = doorBlocks.get(this.entrance.getLevel().getRandom().nextInt(doorBlocks.size()));
			parameters.dungeons.setBlockAndUpdate(entrance.pos(), METAcraftBlocks.PORTAL_CORE.defaultBlockState());
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
								parameters.dungeons.dimension(),
								jigsawPool.orElse(parameters.entry.jigsawPool()),
								maxSize.orElse(parameters.entry.maxSize()),
								maxDistanceFromCenter.or(() -> parameters.entry.maxDistanceFromCenter()),
								parameters.entry.aliases(),
								Optional.empty(),
								this.pools.orElse(pools),
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
				(pools.isPresent() ? 1 : 0);
	}
}
