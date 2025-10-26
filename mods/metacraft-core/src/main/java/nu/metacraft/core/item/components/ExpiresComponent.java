package nu.metacraft.core.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.random.Random;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public record ExpiresComponent(Instant at, Pool<ItemStack> replacement) {

	private static final Random RANDOM = Random.createThreadSafe();

	public static final Codec<ExpiresComponent> CODEC = Codec.withAlternative(
			RecordCodecBuilder.create(
					instance -> instance.group(
							Codecs.INSTANT.fieldOf("at").forGetter(ExpiresComponent::at),
							Codec.withAlternative(
									Pool.createCodec(ItemStack.CODEC),
									ItemStack.CODEC,
									stack -> stack.isEmpty() ? Pool.empty() : Pool.of(stack)
							).fieldOf("replacement").orElse(
									Pool.empty()
							).forGetter(ExpiresComponent::replacement)
					).apply(instance, ExpiresComponent::new)
			),
			Codecs.INSTANT.xmap(ExpiresComponent::createEmpty, ExpiresComponent::at)
	);

	public static ExpiresComponent createEmpty(Instant at) {
		return new ExpiresComponent(at, Pool.empty());
	}

	public static ExpiresComponent createWith(Instant at, ItemStack stack) {
		return new ExpiresComponent(at, Pool.of(stack));
	}

	public ItemStack getReplacement(ItemStack src, Random random) {
		return replacement.getOrEmpty(random).map(res -> {
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
		return stack.contains(METAcraftComponents.DELETED) ? Optional.empty() : Optional.of(stack);
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

	public static Optional<ItemStack> apply(ItemStack stack, Random random) {
		if (stack.contains(METAcraftComponents.EXPIRES_AT)) {
			var component = stack.get(METAcraftComponents.EXPIRES_AT);
			if (component.shouldReplace()) {
				return Optional.of(component.getReplacement(stack, random));
			}
		}
		return Optional.empty();
	}

	public static void init() {
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, player) -> {
			if (original.contains(METAcraftComponents.EXPIRES_AT) &&
					Optional.ofNullable(original.get(DataComponentTypes.TOOLTIP_DISPLAY))
							.map(tooltip -> tooltip.shouldDisplay(METAcraftComponents.EXPIRES_AT))
							.orElse(true)) {
				var result = original.get(METAcraftComponents.EXPIRES_AT).replacement.isEmpty() ?
						Text.literal("disappear") : Text.literal("transform into something else");
				client.set(
						DataComponentTypes.LORE,
						client.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).with(
								Text.literal("This item will ").append(result).formatted(Formatting.RED).styled(
										style -> style.withItalic(false)
								)
						).with(
								Text.literal("after ").append(
										Text.literal(original.get(METAcraftComponents.EXPIRES_AT).at.atZone(
												ZoneId.of("Europe/Stockholm")
										).format(
												DateTimeFormatter.ofPattern("HH:mm v d MMM uuuu")
										))
								).formatted(Formatting.RED).styled(style -> style.withItalic(false))
						)
				);
			}
			return client;
		});
	}

}
