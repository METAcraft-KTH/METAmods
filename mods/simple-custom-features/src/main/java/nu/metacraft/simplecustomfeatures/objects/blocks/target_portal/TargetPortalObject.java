package nu.metacraft.simplecustomfeatures.objects.blocks.target_portal;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.compat.IsLoaded;
import nu.metacraft.simplecustomfeatures.ObjectContainer;
import nu.metacraft.simplecustomfeatures.compat.PortalBlockerCompat;
import nu.metacraft.simplecustomfeatures.compat.PortalTypeData;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;
import nu.metacraft.simplecustomfeatures.objects.blocks.BaseBlock;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class TargetPortalObject implements BaseBlock {

	public static final MapCodec<TargetPortalObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, DimensionTarget.CODEC).fieldOf("dimensions").forGetter(p -> p.dimensions),
					PortalTypeData.CODEC.forGetter(t -> t.portalType)
			).apply(instance, TargetPortalObject::new)
	);

	private final Map<ResourceKey<Level>, DimensionTarget> dimensions;
	private final PortalTypeData portalType;

	private TargetPortalBlock block;

	public TargetPortalObject(
			Map<ResourceKey<Level>, DimensionTarget> dimensions,
			PortalTypeData portalType
	) {
		this.dimensions = dimensions;
		this.portalType = portalType;
	}

	public DimensionTarget getTarget(ResourceKey<Level> src) {
		return dimensions.get(src);
	}

	@Override
	public ObjectType<? extends BaseObject<Block>, Block> getType() {
		return ObjectRegistry.TARGET_PORTAL;
	}

	@Override
	public DataResult<Block> createObject(ResourceKey<Block> id) {
		return DataResult.success(
				block = new TargetPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.END_PORTAL).setId(id), this)
		);
	}

	@Override
	public Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<Block> container) {
		Multimap<Identifier, BaseObject<?>> map = Multimaps.forMap(Map.of());
		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			map = PortalBlockerCompat.addPortalType(map, container.getID(), block, portalType);
		}
		return map;
	}

	@Override
	public Collection<Registry<?>> getChildrenRegistries() {
		var list = List.<Registry<?>>of();
		if (IsLoaded.PORTAL_BLOCKER.isLoaded()) {
			list = PortalBlockerCompat.addPortalTypeRegistry(list);
		}
		return list;
	}

	public record DimensionTarget(
			ResourceKey<Level> targetDim, Optional<BlockPos> targetPos, Optional<Float> targetAngle, boolean spawnObsidianPlatform,
			boolean usePlayerSpawn
	) {
		public static final Codec<DimensionTarget> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Level.RESOURCE_KEY_CODEC.fieldOf("target_dim").forGetter(DimensionTarget::targetDim),
						BlockPos.CODEC.optionalFieldOf("target_pos").forGetter(DimensionTarget::targetPos),
						Codec.floatRange(-180, 180).optionalFieldOf("target_angle").forGetter(DimensionTarget::targetAngle),
						Codec.BOOL.fieldOf("spawn_obsidian_platform").forGetter(DimensionTarget::spawnObsidianPlatform),
						Codec.BOOL.fieldOf("use_player_spawn").forGetter(DimensionTarget::usePlayerSpawn)
				).apply(instance, DimensionTarget::new)
		);
	}
}
