package se.datasektionen.mc.simplecustomfeatures.objects;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import se.datasektionen.mc.simplecustomfeatures.Features;
import se.datasektionen.mc.simplecustomfeatures.objects.items.SimpleItem;

public class ObjectRegistry {

	public static final Registry<ObjectType<?, ?>> REGISTRY = FabricRegistryBuilder.<ObjectType<?, ?>>createSimple(
			RegistryKey.ofRegistry(Features.getID("object_types"))
	).buildAndRegister();

	public static final ObjectType<SimpleItem, Item> SIMPLE_ITEM = register("simple_item", SimpleItem.CODEC, Registries.ITEM);

	private static <T extends BaseObject<R>, R> ObjectType<T, R> register(String id, MapCodec<T> objectType, Registry<R> registry) {
		return Registry.register(REGISTRY, Features.getID(id), new ObjectType<T, R>() {
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

	public static void init() {

	}

}
