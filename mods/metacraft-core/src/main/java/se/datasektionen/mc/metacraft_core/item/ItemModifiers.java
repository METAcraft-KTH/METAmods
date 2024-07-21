package se.datasektionen.mc.metacraft_core.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.metacraft_core.item.components.ExpiresComponent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ItemModifiers {

	public static Optional<ItemStack> modifyTick(ItemStack stack, Random random) {
		return ExpiresComponent.apply(stack, random);
	}

	public static ItemStack modifyLoad(ItemStack stack) {
		return ItemTransmutation.transmute(ExpiresComponent.applyLoadTime(stack).orElse(stack));
	}

	public static Optional<ItemStack> deleteOnLoad(Optional<ItemStack> stack) {
		return stack.flatMap(ItemModifiers::deleteOnLoad);
	}

	public static Optional<ItemStack> deleteOnLoad(ItemStack stack) {
		return ExpiresComponent.applyDelete(stack);
	}

	public static ItemStack setToEmptyOnLoad(ItemStack stack) {
		return deleteOnLoad(stack).orElse(ItemStack.EMPTY);
	}

	public static Codec<ItemStack> wrap(Codec<ItemStack> codec) {
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
				return codec.decode(ops, input);
			}

			@Override
			public <T> DataResult<T> encode(ItemStack input, DynamicOps<T> ops, T prefix) {
				return codec.encode(input, ops, prefix);
			}

			@Override
			public Codec<List<ItemStack>> listOf() {
				return codec.listOf().xmap(
						list -> list.stream().filter(stack -> ExpiresComponent.applyDelete(stack).isPresent()).toList(),
						l -> l
				);
			}

			@Override
			public Codec<List<ItemStack>> listOf(final int minSize, final int maxSize) {
				return codec.listOf(minSize, maxSize).xmap(
						list -> list.stream().filter(stack -> ExpiresComponent.applyDelete(stack).isPresent()).toList(),
						l -> l
				);
			}

			@Override
			public MapCodec<Optional<ItemStack>> optionalFieldOf(final String name) {
				return codec.optionalFieldOf(name).xmap(
						stack -> stack.flatMap(ExpiresComponent::applyDelete),
						stack -> stack
				);
			}

			@Override
			public MapCodec<Optional<ItemStack>> lenientOptionalFieldOf(final String name) {
				return codec.lenientOptionalFieldOf(name).xmap(
						stack -> stack.flatMap(ExpiresComponent::applyDelete),
						stack -> stack
				);
			}

			@Override
			public MapCodec<ItemStack> optionalFieldOf(final String name, final ItemStack defaultValue) {
				return codec.optionalFieldOf(name).xmap(
						stack -> stack.flatMap(ExpiresComponent::applyDelete).orElse(defaultValue),
						stack -> Objects.equals(stack, defaultValue) ? Optional.empty() : Optional.of(stack)
				);
			}

			@Override
			public MapCodec<ItemStack> lenientOptionalFieldOf(final String name, final ItemStack defaultValue) {
				return codec.lenientOptionalFieldOf(name).xmap(
						stack -> stack.flatMap(ExpiresComponent::applyDelete).orElse(defaultValue),
						stack -> Objects.equals(stack, defaultValue) ? Optional.empty() : Optional.of(stack)
				);
			}

			@Override
			public MapCodec<ItemStack> lenientOptionalFieldOf(
					final String name, final Lifecycle fieldLifecycle, final ItemStack defaultValue, final Lifecycle lifecycleOfDefault
			) {
				return codec.lenientOptionalFieldOf(name).stable().flatXmap(
						stack -> stack.flatMap(ExpiresComponent::applyDelete).map(v -> DataResult.success(v, fieldLifecycle)).orElse(DataResult.success(defaultValue, lifecycleOfDefault)),
						stack -> Objects.equals(stack, defaultValue) ? DataResult.success(Optional.empty(), lifecycleOfDefault) : DataResult.success(Optional.of(stack), fieldLifecycle)
				);
			}

			@Override
			public MapCodec<ItemStack> optionalFieldOf(
					final String name, final Lifecycle fieldLifecycle, final ItemStack defaultValue, final Lifecycle lifecycleOfDefault
			) {
				// setting lifecycle to stable on the outside since it will be overriden by the passed parameters
				return codec.optionalFieldOf(name).stable().flatXmap(
						stack -> stack.flatMap(ExpiresComponent::applyDelete).map(v -> DataResult.success(v, fieldLifecycle)).orElse(DataResult.success(defaultValue, lifecycleOfDefault)),
						stack -> Objects.equals(stack, defaultValue) ? DataResult.success(Optional.empty(), lifecycleOfDefault) : DataResult.success(Optional.of(stack), fieldLifecycle)
				);
			}
		};
	}

}
