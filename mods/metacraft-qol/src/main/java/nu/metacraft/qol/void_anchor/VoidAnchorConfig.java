package nu.metacraft.qol.void_anchor;

import nu.metacraft.qol.Qol;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import se.metacraft.config.util.CommentCodec;
import nu.metacraft.qol.QolConfig;
import nu.metacraft.qol.void_anchor.rift.RiftStyle;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The void anchor's section of {@code metacraft-qol.json}. */
public record VoidAnchorConfig(
		boolean enabled, Identifier fuelItem, int triggerYOffset, double riftDepth, double descentSpeed, int riftTicks, float riftSize,
		RiftStyle riftStyle
) {

	public static final VoidAnchorConfig DEFAULT = new VoidAnchorConfig(
			true, Identifier.withDefaultNamespace("end_crystal"), 0, 6.0, 0.2, 40, 4.0f, RiftStyle.SHATTER
	);

	public static final MapCodec<VoidAnchorConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					CommentCodec.comment(
							Codec.BOOL.fieldOf("enabled"),
							"Whether void anchors rescue anyone. Off, the block stays, but using it only says void anchors are off."
					).forGetter(VoidAnchorConfig::enabled),
					CommentCodec.comment(
							Identifier.CODEC.fieldOf("fuel_item"),
							"The item that adds one charge to a void anchor.",
							"An id that names no item is logged once, and then nothing charges an anchor."
					).forGetter(VoidAnchorConfig::fuelItem),
					CommentCodec.comment(
							Codec.intRange(-64, 256).fieldOf("trigger_y_offset"),
							"A rift opens when a player drops below the End's lowest Y (0) plus this."
					).forGetter(VoidAnchorConfig::triggerYOffset),
					CommentCodec.comment(
							Codec.doubleRange(1, 24).fieldOf("rift_depth"),
							"How far below the falling player the rift opens, in blocks."
					).forGetter(VoidAnchorConfig::riftDepth),
					CommentCodec.comment(
							Codec.doubleRange(0.05, 2).fieldOf("descent_speed"),
							"How fast the player sinks into the rift once it has opened, in blocks per tick. (While it opens, they hang in the air.)"
					).forGetter(VoidAnchorConfig::descentSpeed),
					CommentCodec.comment(
							Codec.intRange(1, 200).fieldOf("rift_ticks"),
							"The longest a player spends sinking once the rift has opened, before it takes them, in ticks."
					).forGetter(VoidAnchorConfig::riftTicks),
					CommentCodec.comment(
							Codec.floatRange(0.5f, 8).fieldOf("rift_size"),
							"How wide the rift is, in blocks."
					).forGetter(VoidAnchorConfig::riftSize),
					CommentCodec.comment(
							RiftStyle.CODEC.fieldOf("rift_style"),
							"How a rift looks. \"crack\": one crack in space under the player, the End's void showing through.",
							"\"shatter\": that crack, crossed by glowing cracks at every angle through a bright core, as if space broke like glass."
					).forGetter(VoidAnchorConfig::riftStyle)
			).apply(instance, VoidAnchorConfig::new)
	);

	private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();

	public static VoidAnchorConfig getInstance() {
		return QolConfig.getInstance().voidAnchor();
	}

	/** The configured fuel item; empty (and logged once) when the id names no item. */
	public Optional<Item> fuel() {
		var item = BuiltInRegistries.ITEM.getOptional(fuelItem);
		if (item.isEmpty() && WARNED.add(fuelItem)) {
			Qol.LOGGER.warn("fuel_item {} names no item; nothing can charge a void anchor", fuelItem);
		}
		return item;
	}

	public boolean isFuel(ItemStack stack) {
		return !stack.isEmpty() && fuel().map(stack::is).orElse(false);
	}

}
