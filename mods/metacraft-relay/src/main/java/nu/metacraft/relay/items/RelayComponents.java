package nu.metacraft.relay.items;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import nu.metacraft.relay.Relay;
import org.pcollections.HashTreePSet;
import org.pcollections.PMap;
import org.pcollections.PSet;
import nu.metacraft.lib.util.helper.PCollectionsHelper;

import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class RelayComponents {

	public static final DataComponentType<PMap<ResourceKey<Level>, PSet<ResourceKey<Level>>>> VALID_DIMENSIONS = register(
			"relay_valid_dimensions", builder -> builder.persistent(
					Codec.unboundedMap(
							Level.RESOURCE_KEY_CODEC, Level.RESOURCE_KEY_CODEC.listOf()
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

	public static final DataComponentType<TagKey<Item>> VALID_CHARGE_ITEM = register(
			"relay_valid_charge_item", builder -> builder.persistent(TagKey.hashedCodec(Registries.ITEM))
	);

	public static final DataComponentType<Identifier> BLOCK_MODEL = register(
			"block_model", builder -> builder.persistent(Identifier.CODEC)
	);

	public static void init() {

	}

	protected static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
		var entry = Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE, Relay.getID(id),
				builderOperator.apply(DataComponentType.builder()).build()
		);
		PolymerComponent.registerDataComponent(entry);
		return entry;
	}

}
