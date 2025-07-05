package nu.metacraft.simplecustomfeatures.objects;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.poi.PointOfInterestType;
import nu.metacraft.simplecustomfeatures.Features;
import nu.metacraft.simplecustomfeatures.objects.blocks.dynamic_portal.PortalBlockObject;
import nu.metacraft.simplecustomfeatures.objects.blocks.target_portal.TargetPortalFrameObject;
import nu.metacraft.simplecustomfeatures.objects.blocks.target_portal.TargetPortalObject;
import nu.metacraft.simplecustomfeatures.objects.items.BlockItemObject;
import nu.metacraft.simplecustomfeatures.objects.items.simple.ArmorMaterialRegistry;
import nu.metacraft.simplecustomfeatures.objects.items.simple.SimpleItem;
import nu.metacraft.simplecustomfeatures.objects.items.simple.ToolMaterialRegistry;

public class ObjectRegistry {

	public static final Registry<ObjectType<?, ?>> REGISTRY = FabricRegistryBuilder.<ObjectType<?, ?>>createSimple(
			RegistryKey.ofRegistry(Features.getID("object_types"))
	).buildAndRegister();

	public static final ObjectType<SimpleItem, Item> SIMPLE_ITEM = register("simple_item", SimpleItem.CODEC, Registries.ITEM);
	public static final ObjectType<POI, PointOfInterestType> POINT_OF_INTEREST = register("poi", POI.CODEC, Registries.POINT_OF_INTEREST_TYPE);
	public static final ObjectType<PortalBlockObject, Block> VERTICAL_PORTAL = register("dynamic_portal", PortalBlockObject.CODEC, Registries.BLOCK);
	public static final ObjectType<TargetPortalObject, Block> TARGET_PORTAL = register("target_portal", TargetPortalObject.CODEC, Registries.BLOCK);
	public static final ObjectType<TargetPortalFrameObject, Block> TARGET_PORTAL_FRAME = register("target_portal_frame", TargetPortalFrameObject.CODEC, Registries.BLOCK);
	public static final ObjectType<BlockItemObject, Item> BLOCK_ITEM = register("block_item", BlockItemObject.CODEC, Registries.ITEM);
	public static final ObjectType<ArmorMaterialRegistry.MaterialObject, ArmorMaterial> ARMOR_MATERIAL = register("armor_material", ArmorMaterialRegistry.MaterialObject.CODEC, ArmorMaterialRegistry.REGISTRY);
	public static final ObjectType<ToolMaterialRegistry.MaterialObject, ToolMaterial> TOOL_MATERIAL = register("tool_material", ToolMaterialRegistry.MaterialObject.CODEC, ToolMaterialRegistry.REGISTRY);

	public static <T extends BaseObject<R>, R> ObjectType<T, R> register(Identifier id, MapCodec<T> objectType, Registry<R> registry) {
		return Registry.register(REGISTRY, id, new ObjectType<T, R>() {
			@Override
			public MapCodec<T> getCodec() {
				return objectType;
			}

			@Override
			public Registry<R> getRegistry() {
				return registry;
			}
		});
	}

	private static <T extends BaseObject<R>, R> ObjectType<T, R> register(String id, MapCodec<T> objectType, Registry<R> registry) {
		return register(Identifier.ofVanilla(id), objectType, registry);
	}

	public static void init() {

	}

}
