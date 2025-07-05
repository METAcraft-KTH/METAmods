package nu.metacraft.relay.items;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import nu.metacraft.relay.Relay;
import org.pcollections.HashTreePSet;
import org.pcollections.PMap;
import org.pcollections.PSet;
import nu.metacraft.lib.util.helper.PCollectionsHelper;

import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class RelayComponents {

	public static final ComponentType<PMap<RegistryKey<World>, PSet<RegistryKey<World>>>> VALID_DIMENSIONS = register(
			"relay_valid_dimensions", builder -> builder.codec(
					Codec.unboundedMap(
							World.CODEC, World.CODEC.listOf()
					).xmap(
							map -> PCollectionsHelper.collectToMap(
									map.entrySet().stream().map(
											e -> Pair.of(e.getKey(), HashTreePSet.from(e.getValue()))
									), Pair::getFirst, Pair::getSecond
							),
							m -> m.entrySet().stream().map(
									e -> Pair.of(e.getKey(), e.getValue().stream().toList())
							).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond))
					)
			)
	);

	public static final ComponentType<TagKey<Item>> VALID_CHARGE_ITEM = register(
			"relay_valid_charge_item", builder -> builder.codec(TagKey.codec(RegistryKeys.ITEM))
	);

	public static final ComponentType<Identifier> BLOCK_MODEL = register(
			"block_model", builder -> builder.codec(Identifier.CODEC)
	);

	public static void init() {

	}

	protected static <T> ComponentType<T> register(String id, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				Registries.DATA_COMPONENT_TYPE, Relay.getID(id),
				builderOperator.apply(ComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
