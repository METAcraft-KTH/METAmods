package nu.metacraft.simplecustomfeatures.objects.blocks.target_portal;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ItemPredicate;
import nu.metacraft.lib.util.METACodecs;
import nu.metacraft.simplecustomfeatures.ObjectContainer;
import nu.metacraft.simplecustomfeatures.objects.BaseObject;
import nu.metacraft.simplecustomfeatures.objects.ObjectRegistry;
import nu.metacraft.simplecustomfeatures.objects.ObjectType;
import nu.metacraft.simplecustomfeatures.objects.blocks.BaseBlock;
import nu.metacraft.simplecustomfeatures.objects.items.BaseItem;
import nu.metacraft.simplecustomfeatures.objects.items.BlockItemObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record TargetPortalFrameObject(
	BlockStateProvider portalBlock, ItemPredicate activator,
	Optional<Portal> portalReference
) implements BaseBlock {

	public static final MapCodec<TargetPortalFrameObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BlockStateProvider.CODEC.fieldOf("portal_block").forGetter(p -> p.portalBlock),
					ItemPredicate.CODEC.fieldOf("activator").forGetter(p -> p.activator),
					METACodecs.RegistryDependent.PORTAL_CODEC.optionalFieldOf("portal_reference").forGetter(p -> p.portalReference)
			).apply(instance, TargetPortalFrameObject::new)
	);


	@Override
	public Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<Block> container) {
		return Multimaps.forMap(Map.of(
				container.getID(), new BlockItemObject(
						Items.END_PORTAL_FRAME.builtInRegistryHolder(),
						new BaseItem.ItemSettings(Items.END_PORTAL_FRAME.components(), Optional.empty()),
						container.getActualObject()
				)
		));
	}

	@Override
	public Collection<Registry<?>> getChildrenRegistries() {
		return List.of(BuiltInRegistries.ITEM);
	}

	@Override
	public ObjectType<? extends BaseObject<Block>, Block> getType() {
		return ObjectRegistry.TARGET_PORTAL_FRAME;
	}

	@Override
	public DataResult<Block> createObject(ResourceKey<Block> id) {
		return DataResult.success(
				new TargetPortalFrameBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.END_PORTAL_FRAME).setId(id), this)
		);
	}
}
