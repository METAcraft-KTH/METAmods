package se.datasektionen.mc.metacraft_dungeons.dungeons.datablocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.enums.Orientation;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.METAcraftCoreTags;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import se.datasektionen.mc.metacraft_core.block.entities.PortalEntity;
import se.datasektionen.mc.metacraft_dungeons.METAcraftDungeons;
import se.datasektionen.mc.metacraft_dungeons.block.block_entities.DungeonEntranceEntity;
import se.datasektionen.mc.metacraft_dungeons.dungeons.DungeonData;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.OrientationHelper;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class PortalWithDestination extends DataBlock implements MultiDataBlock {

	protected Optional<Orientation> direction;
	protected Optional<RegistryKey<World>> targetDim;
	protected Optional<BlockPos> targetPos;
	protected Optional<ContainerLock> lock;
	protected boolean allowReturn;

	public static final MapCodec<PortalWithDestination> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
			ExtraCodecs.ORIENTATION_CODEC.optionalFieldOf("direction").forGetter(portal -> portal.direction),
			World.CODEC.optionalFieldOf("target_dim").forGetter(portal -> portal.targetDim),
			BlockPos.CODEC.optionalFieldOf("target_pos").forGetter(portal -> portal.targetPos),
			ContainerLock.CODEC.optionalFieldOf("lock").forGetter(portal -> portal.lock),
			Codec.BOOL.optionalFieldOf("allow_return", false).forGetter(portal -> portal.allowReturn)
		).apply(instance, PortalWithDestination::new)
	);

	public PortalWithDestination(
			Optional<Orientation> direction,
			Optional<RegistryKey<World>> targetDim,
			Optional<BlockPos> targetPos,
			Optional<ContainerLock> lock,
			boolean allowReturn
	) {
		this.direction = direction;
		this.targetDim = targetDim;
		this.targetPos = targetPos;
		this.lock = lock;
		this.allowReturn = allowReturn;
	}

	@Override
	public DataBlockRegistry.DataBlockType<?> getType() {
		return DataBlockRegistry.PORTAL;
	}

	@Override
	public void processDataBlock(BlockPos pos, StructurePiece piece) {
		parameters.dungeons.setBlockState(pos, METAcraftBlocks.PORTAL_PADDING.getDefaultState());
	}

	@Override
	public void processDataBlocks(Collection<DungeonEntranceEntity.DataMultiBlockEntry<?>> blocks) {
		if (targetPos.isEmpty() || targetDim.isEmpty()) {
			METAcraftDungeons.LOGGER.error(
					"Portal at " + blocks.stream().findAny().map(
							pos -> pos.pos().toShortString()
					).orElse("null") + " in " + parameters.dungeons.getRegistryKey() + " does not have a destination!"
			);
			return;
		}
		List<DungeonEntranceEntity.DataMultiBlockEntry<?>> doorBlocks = blocks.stream().toList();
		if (!doorBlocks.isEmpty()) {
			var chosenBlock = doorBlocks.get(this.entrance.getWorld().getRandom().nextInt(doorBlocks.size()));
			parameters.dungeons.setBlockState(chosenBlock.pos(), METAcraftBlocks.PORTAL_CORE.getDefaultState());
			var portalEntity = parameters.dungeons.getBlockEntity(chosenBlock.pos());
			if (portalEntity instanceof PortalEntity portal) {
				portal.setPortalFacing(direction.map(
						direction -> OrientationHelper.rotate(direction, chosenBlock.piece().getRotation())
				).orElse(null));
				lock.ifPresent(portal::setLock);

				var dim = targetDim.get();
				var pos = targetPos.get();

				var targetWorld = parameters.dungeons.getServer().getWorld(dim);
				if (targetWorld == null) {
					METAcraftDungeons.LOGGER.error(
							"Portal at " + blocks.stream().findAny().map(
									p -> p.pos().toShortString()
							).orElse("null") + " in " + parameters.dungeons.getRegistryKey() +
							"tried to set dimension " + dim + " as the destination but this dimension does not exist!"
					);
					return;
				}

				if (targetWorld.getBlockState(pos).isIn(METAcraftCoreTags.PORTAL_PADDING)) {
					for (var p : PortalEntity.forAllNearbyPortals(targetWorld, pos)) {
						if (targetWorld.getBlockEntity(p) instanceof PortalEntity targetPortal) {
							pos = p;
							if (allowReturn) {
								targetPortal.setTargetDim(parameters.dungeons.getRegistryKey());
								targetPortal.setTargetPos(chosenBlock.pos());
								DungeonData.getInstance(parameters.dungeons).addExternalEntrance(
										targetPortal.getWorld().getRegistryKey(), targetPortal.getPos()
								);
							}
							break;
						}
					}
				}

				portal.setTargetDim(dim);
				portal.setTargetPos(pos);
			}
		}
	}

	@Override
	public int getPriority() {
		return (direction.isPresent() ? 1 : 0) +
				(targetDim.isPresent() ? 1 : 0) +
				(targetPos.isPresent() ? 1 : 0);
	}
}
