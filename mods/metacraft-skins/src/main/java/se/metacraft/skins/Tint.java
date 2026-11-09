package se.metacraft.skins;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Optional;

/**
 * A Dyeable Hat's colour, as its Owner last had it on the hat's helmet: the helmet's dye
 * ({@code dyed_color}) and, with moredyes installed, the animated colour it's marked with. Saved
 * on the owned skin, so the colour comes back when the skin is applied again ({@link Skins#apply}).
 * In the ownership file: {@code "tint": { "color": "#4F7942" }}, or with
 * {@code "animated": "<moredyes colour id>"}.
 */
public record Tint(int rgb, Optional<String> animated) {
	private static final Codec<Integer> HEX = Codec.STRING.comapFlatMap(
			hex -> hex.matches("#[0-9a-fA-F]{6}") ? DataResult.success(Integer.parseInt(hex.substring(1), 16))
					: DataResult.error(() -> "\"color\" must be a colour written \"#RRGGBB\", not \"" + hex + "\""),
			rgb -> String.format("#%06X", rgb & 0xFFFFFF));

	public static final Codec<Tint> CODEC = StrictCodec.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
			HEX.fieldOf("color").forGetter(Tint::rgb),
			Codec.STRING.optionalFieldOf("animated").forGetter(Tint::animated)
	).apply(instance, Tint::new)));

	/** The helmet's colour, if it's dyed. */
	public static Optional<Tint> on(ItemStack helmet) {
		DyedItemColor dyed = helmet.get(DataComponents.DYED_COLOR);
		return dyed == null ? Optional.empty() : Optional.of(new Tint(dyed.rgb() & 0xFFFFFF, DyeSkinnedItemRecipe.DYES.animated(helmet)));
	}

	/** Dyes the helmet this colour, replacing any it had. */
	public void putOn(ItemStack helmet) {
		helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		DyeSkinnedItemRecipe.DYES.animated(helmet, animated);
	}

	/** Takes any colour off the helmet. */
	public static void takeOff(ItemStack helmet) {
		helmet.remove(DataComponents.DYED_COLOR);
		DyeSkinnedItemRecipe.DYES.animated(helmet, Optional.empty());
	}

	/** {@code "#4F7942"}. */
	public String hex() {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}
}
