package nu.metacraft.core.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Keyable;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import nu.metacraft.core.METAcraftCore;

import java.util.function.UnaryOperator;

public class METAcraftComponents {

	/**
	 * Used to make an item disappear or transform into something else after a certain date.
	 * Useful to give out overpowered items for a specific event without affecting progression too much.
	 * See {@link ExpiresComponent}
	 */
	public static final DataComponentType<ExpiresComponent> EXPIRES_AT = register(
			"expires_at", builder -> builder.persistent(ExpiresComponent.CODEC)
	);

	/**
	 * Marker that states that the component should be removed from containers at load time.
	 * This exists because some vanilla codecs will crash when trying to load an empty item stack from a list
	 * (this is intended behaviour for some reason).
	 * This will not work for all codecs that access item stacks, but it will work with
	 * {@link net.minecraft.world.item.ItemStack#OPTIONAL_CODEC} and {@link net.minecraft.world.item.component.ItemContainerContents}.
	 * It will also work with {@link Codec#listOf()} and {@link Codec#optionalFieldOf(String)} of {@link net.minecraft.world.item.ItemStack#CODEC} and
	 * {@link net.minecraft.world.item.ItemStack#SINGLE_ITEM_CODEC}, but not in a general case
	 * (for example {@link Codec#simpleMap(Codec, Codec, Keyable)} and {@link Codec#list(Codec)} will not work).
	 * For this reason it is best not to rely on this component too much, and merely use it in addition to other means of removing the item stack.
	 */
	public static final DataComponentType<Unit> DELETED = register(
			"deleted", builder -> builder.networkSynchronized(Unit.STREAM_CODEC)
	);

	/**
	 * If this component exists on an item, it will be kept on death, as if keepInventory was enabled for that one item.
	 */
	public static final DataComponentType<Unit> SOULBOUND = register(
			"soulbound", builder -> builder.persistent(Unit.CODEC)
	);

	public static final DataComponentType<Unit> ANTI_KEEP_INVENTORY = register(
			"anti-keep-inventory", builder -> builder.persistent(Unit.CODEC)
	);


	public static void init() {
		ExpiresComponent.init();
	}

	protected static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE, METAcraftCore.getID(id),
				builderOperator.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
