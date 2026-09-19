package nu.metacraft.rivals.paint;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers the two paint blocks per colour: the {@link ConnectedPaintBlock} single-face cell as
 * {@code rivals-paint:paint_<id>_face} and the {@link PaintBlock} splat fallback as
 * {@code rivals-paint:paint_<id>}, which keeps v3's id. No items.
 */
public final class PaintBlocks {
	private static final Map<PaintColor, PaintBlock> SPLATS = new EnumMap<>(PaintColor.class);
	private static final Map<PaintColor, ConnectedPaintBlock> CONNECTED = new EnumMap<>(PaintColor.class);

	private PaintBlocks() {}

	/** The multiface fallback: a cell painted on more than one face. */
	public static PaintBlock splat(PaintColor color) {
		return SPLATS.get(color);
	}

	/** The single-face cell that carries the four connection bits. */
	public static ConnectedPaintBlock connected(PaintColor color) {
		return CONNECTED.get(color);
	}

	public static void register() {
		for (PaintColor color : PaintColor.values()) {
			Identifier splat = Rivals.id("paint_" + color.id);
			Identifier connected = Rivals.id("paint_" + color.id + "_face");
			SPLATS.put(color, Registry.register(BuiltInRegistries.BLOCK, splat, new PaintBlock(properties(splat), color)));
			CONNECTED.put(color, Registry.register(BuiltInRegistries.BLOCK, connected, new ConnectedPaintBlock(properties(connected), color)));
		}
	}

	/** What both paint blocks are made of: no collision, no drops, broken by a touch, pushed out by pistons. */
	private static BlockBehaviour.Properties properties(Identifier id) {
		return BlockBehaviour.Properties.of()
				.noCollision()
				.noOcclusion()
				.instabreak()
				.noLootTable()
				.pushReaction(PushReaction.POPPED)
				.setId(ResourceKey.create(Registries.BLOCK, id));
	}
}
