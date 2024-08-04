package se.datasektionen.mc.simplecustomfeatures.objects.blocks.target_portal;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import se.datasektionen.mc.simplecustomfeatures.ObjectContainer;
import se.datasektionen.mc.simplecustomfeatures.objects.BaseObject;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectRegistry;
import se.datasektionen.mc.simplecustomfeatures.objects.ObjectType;
import se.datasektionen.mc.simplecustomfeatures.objects.blocks.BaseBlock;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BaseItem;
import se.datasektionen.mc.simplecustomfeatures.objects.items.BlockItemObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TargetPortalFrameObject implements BaseBlock {

	public static final MapCodec<TargetPortalFrameObject> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BlockStateProvider.TYPE_CODEC.fieldOf("portal_block").forGetter(p -> p.portalBlock),
					ItemPredicate.CODEC.fieldOf("activator").forGetter(p -> p.activator)
			).apply(instance, TargetPortalFrameObject::new)
	);

	private final BlockStateProvider portalBlock;
	private final ItemPredicate activator;

	public TargetPortalFrameObject(BlockStateProvider portalBlock, ItemPredicate activator) {
		this.portalBlock = portalBlock;
		this.activator = activator;
	}

	public ItemPredicate getActivator() {
		return activator;
	}

	public BlockStateProvider getPortalBlock() {
		return portalBlock;
	}

	@Override
	public Multimap<Identifier, BaseObject<?>> createChildren(ObjectContainer.Loaded<Block> container) {
		return Multimaps.forMap(Map.of(
				container.getID(), new BlockItemObject(
						Items.END_PORTAL_FRAME.getRegistryEntry(),
						new BaseItem.ItemSettings(Items.END_PORTAL_FRAME.getComponents(), Optional.empty()),
						container.getActualObject()
				)
		));
	}

	@Override
	public Collection<Registry<?>> getChildrenRegistries() {
		return List.of(Registries.ITEM);
	}

	@Override
	public ObjectType<? extends BaseObject<Block>, Block> getType() {
		return ObjectRegistry.TARGET_PORTAL_FRAME;
	}

	@Override
	public DataResult<Block> createObject() {
		return DataResult.success(
				new TargetPortalFrameBlock(AbstractBlock.Settings.copy(Blocks.END_PORTAL_FRAME), this)
		);
	}
}
