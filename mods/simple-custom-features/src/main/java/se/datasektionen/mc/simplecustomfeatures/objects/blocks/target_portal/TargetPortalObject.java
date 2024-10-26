package se.datasektionen.mc.simplecustomfeatures.objects.blocks.target_portal;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_lib.compat.IsLoaded;
import se.datasektionen.mc.simplecustomfeatures.ObjectContainer;
import se.datasektionen.mc.simplecustomfeatures.compat.PortalBlockerCompat;
import se.datasektionen.mc.simplecustomfeatures.compat.PortalTypeData;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.BaseBlock;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TargetPortalObject implements BaseBlock {

	public static final MapCodec<TargetPortalObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.unboundedMap(World.CODEC, DimensionTarget.CODEC).fieldOf("dimensions").forGetter(p -> p.dimensions),
					PortalTypeData.CODEC.forGetter(t -> t.portalType)
			).apply(instance, TargetPortalObject::new)
	);

	private final Map<RegistryKey<World>, DimensionTarget> dimensions;
	private final PortalTypeData portalType;

	private TargetPortalBlock block;

	public TargetPortalObject(
			Map<RegistryKey<World>, DimensionTarget> dimensions,
			PortalTypeData portalType
	) {
		this.dimensions = dimensions;
		this.portalType = portalType;
	}

	public DimensionTarget getTarget(RegistryKey<World> src) {
		return dimensions.get(src);
	}

	@Override
	public ObjectType<? extends BaseObject<Block>, Block> getType() {
		return ObjectRegistry.TARGET_PORTAL;
	}

	@Override
	public DataResult<Block> createObject(RegistryKey<Block> id) {
		return DataResult.success(
				block = new TargetPortalBlock(AbstractBlock.Settings.copy(Blocks.END_PORTAL).registryKey(id), this)
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
			RegistryKey<World> targetDim, Optional<BlockPos> targetPos, Optional<Float> targetAngle, boolean spawnObsidianPlatform,
			boolean usePlayerSpawn
	) {
		public static final Codec<DimensionTarget> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						World.CODEC.fieldOf("target_dim").forGetter(DimensionTarget::targetDim),
						BlockPos.CODEC.optionalFieldOf("target_pos").forGetter(DimensionTarget::targetPos),
						Codec.floatRange(-180, 180).optionalFieldOf("target_angle").forGetter(DimensionTarget::targetAngle),
						Codec.BOOL.fieldOf("spawn_obsidian_platform").forGetter(DimensionTarget::spawnObsidianPlatform),
						Codec.BOOL.fieldOf("use_player_spawn").forGetter(DimensionTarget::usePlayerSpawn)
				).apply(instance, DimensionTarget::new)
		);
	}
}
