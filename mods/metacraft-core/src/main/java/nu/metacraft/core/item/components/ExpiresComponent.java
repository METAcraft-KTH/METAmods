package nu.metacraft.core.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public record ExpiresComponent(Instant at, WeightedList<ItemStack> replacement) {

	private static final RandomSource RANDOM = RandomSource.createThreadSafe();

	public static final Codec<ExpiresComponent> CODEC = Codec.withAlternative(
			RecordCodecBuilder.create(
					instance -> instance.group(
							ExtraCodecs.INSTANT_ISO8601.fieldOf("at").forGetter(ExpiresComponent::at),
							Codec.withAlternative(
									WeightedList.codec(ItemStack.CODEC),
									ItemStack.CODEC,
									stack -> stack.isEmpty() ? WeightedList.of() : WeightedList.of(stack)
							).fieldOf("replacement").orElse(
									WeightedList.of()
							).forGetter(ExpiresComponent::replacement)
					).apply(instance, ExpiresComponent::new)
			),
			ExtraCodecs.INSTANT_ISO8601.xmap(ExpiresComponent::createEmpty, ExpiresComponent::at)
	);

	public static ExpiresComponent createEmpty(Instant at) {
		return new ExpiresComponent(at, WeightedList.of());
	}

	public static ExpiresComponent createWith(Instant at, ItemStack stack) {
		return new ExpiresComponent(at, WeightedList.of(stack));
	}

	public ItemStack getReplacement(ItemStack src, RandomSource random) {
		return replacement.getRandom(random).map(res -> {
			if (res.isEmpty()) return ItemStack.EMPTY;
			var stack = res.copy();
			stack.setCount(src.getCount() * stack.getCount());
			return stack;
		}).orElse(ItemStack.EMPTY);
	}

	public boolean shouldReplace() {
		return Instant.now().isAfter(at);
	}

	public static Optional<ItemStack> applyDelete(ItemStack stack) {
		return stack.has(METAcraftComponents.DELETED) ? Optional.empty() : Optional.of(stack);
	}

	public static Optional<ItemStack> applyLoadTime(ItemStack stack) {
		return apply(stack, RANDOM).map(s -> {
			if (s.isEmpty()) {
				var newStack = stack.copy();
				newStack.set(METAcraftComponents.DELETED, Unit.INSTANCE);
				return newStack;
			}
			return s;
		});
	}

	public static Optional<ItemStack> apply(ItemStack stack, RandomSource random) {
		if (stack.has(METAcraftComponents.EXPIRES_AT)) {
			var component = stack.get(METAcraftComponents.EXPIRES_AT);
			if (component.shouldReplace()) {
				return Optional.of(component.getReplacement(stack, random));
			}
		}
		return Optional.empty();
	}

	public static void init() {
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, player) -> {
			if (original.has(METAcraftComponents.EXPIRES_AT) &&
					Optional.ofNullable(original.get(DataComponents.TOOLTIP_DISPLAY))
							.map(tooltip -> tooltip.shows(METAcraftComponents.EXPIRES_AT))
							.orElse(true)) {
				var result = original.get(METAcraftComponents.EXPIRES_AT).replacement.isEmpty() ?
						Component.literal("disappear") : Component.literal("transform into something else");
				client.set(
						DataComponents.LORE,
						client.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(
								Component.literal("This item will ").append(result).withStyle(ChatFormatting.RED).withStyle(
										style -> style.withItalic(false)
								)
						).withLineAdded(
								Component.literal("after ").append(
										Component.literal(original.get(METAcraftComponents.EXPIRES_AT).at.atZone(
												ZoneId.of("Europe/Stockholm")
										).format(
												DateTimeFormatter.ofPattern("HH:mm v d MMM uuuu")
										))
								).withStyle(ChatFormatting.RED).withStyle(style -> style.withItalic(false))
						)
				);
			}
			return client;
		});
	}

}
