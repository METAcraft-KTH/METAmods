package nu.metacraft.core.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Keyable;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Unit;
import org.apache.commons.lang3.math.Fraction;
import nu.metacraft.core.METAcraftCore;
import nu.metacraft.lib.util.ExtraCodecs;

import java.util.function.UnaryOperator;

public class METAcraftComponents {

	/**
	 * Used to make an item disappear or transform into something else after a certain date.
	 * Useful to give out overpowered items for a specific event without affecting progression too much.
	 * See {@link ExpiresComponent}
	 */
	public static final ComponentType<ExpiresComponent> EXPIRES_AT = register(
			"expires_at", builder -> builder.codec(ExpiresComponent.CODEC)
	);

	/**
	 * Marker that states that the component should be removed from containers at load time.
	 * This exists because some vanilla codecs will crash when trying to load an empty item stack from a list
	 * (this is intended behaviour for some reason).
	 * This will not work for all codecs that access item stacks, but it will work with
	 * {@link net.minecraft.item.ItemStack#OPTIONAL_CODEC} and {@link net.minecraft.component.type.ContainerComponent}.
	 * It will also work with {@link Codec#listOf()} and {@link Codec#optionalFieldOf(String)} of {@link net.minecraft.item.ItemStack#CODEC} and
	 * {@link net.minecraft.item.ItemStack#UNCOUNTED_CODEC}, but not in a general case
	 * (for example {@link Codec#simpleMap(Codec, Codec, Keyable)} and {@link Codec#list(Codec)} will not work).
	 * For this reason it is best not to rely on this component too much, and merely use it in addition to other means of removing the item stack.
	 */
	public static final ComponentType<Unit> DELETED = register(
			"deleted", builder -> builder.packetCodec(PacketCodec.unit(Unit.INSTANCE))
	);

	/**
	 * If this component exists on an item, it will be kept on death, as if keepInventory was enabled for that one item.
	 */
	public static final ComponentType<Unit> SOULBOUND = register(
			"soulbound", builder -> builder.codec(Codec.unit(Unit.INSTANCE))
	);

	public static final ComponentType<Unit> ANTI_KEEP_INVENTORY = register(
			"anti-keep-inventory", builder -> builder.codec(Codec.unit(Unit.INSTANCE))
	);

	public static final ComponentType<Fraction> BUNDLE_SIZE_FACTOR = register(
			"bundle_size_factor", builder -> builder.codec(ExtraCodecs.POSITIVE_FRACTION_CODEC)
	);



	public static void init() {
		ExpiresComponent.init();
		CommandComponents.init();
	}

	protected static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, METAcraftCore.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
