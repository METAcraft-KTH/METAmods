package metacraft.moredyes.content;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Block families that exist per colour. Ids are always {@code moredyes:<color>_<family>}, and
 * {@code tools/gen_assets.py} must emit assets for exactly this list (its FAMILIES table).
 *
 * Order matters: a family may depend on an earlier one (powder on concrete, stairs on wool,
 * candle cake on candle).
 */
public enum Family {
	WOOL("wool", Kind.SIMPLE, Blocks.WOOL.white(), Items.WOOL.white(), null),
	CARPET("carpet", Kind.CARPET, Blocks.CARPET.white(), Items.CARPET.white(), null),
	CONCRETE("concrete", Kind.SIMPLE, Blocks.CONCRETE.white(), Items.CONCRETE.white(), null),
	CONCRETE_POWDER("concrete_powder", Kind.POWDER, Blocks.CONCRETE_POWDER.white(), Items.CONCRETE_POWDER.white(), null),
	TERRACOTTA("terracotta", Kind.SIMPLE, Blocks.DYED_TERRACOTTA.white(), Items.DYED_TERRACOTTA.white(), null),
	GLAZED_TERRACOTTA("glazed_terracotta", Kind.GLAZED, Blocks.GLAZED_TERRACOTTA.white(), Items.GLAZED_TERRACOTTA.white(), null),
	CANDLE("candle", Kind.CANDLE, Blocks.DYED_CANDLE.white(), Items.DYED_CANDLE.white(), null),
	// Stairs and slabs copy the material block's properties and, for the client, its item (so the
	// placer's predicted placement sound is the material's, per craftycorvid/wool-polymer).
	WOOL_STAIRS("wool_stairs", Kind.STAIRS, Blocks.WOOL.white(), Items.WOOL.white(), "wool"),
	WOOL_SLAB("wool_slab", Kind.SLAB, Blocks.WOOL.white(), Items.WOOL.white(), "wool"),
	CONCRETE_STAIRS("concrete_stairs", Kind.STAIRS, Blocks.CONCRETE.white(), Items.CONCRETE.white(), "concrete"),
	CONCRETE_SLAB("concrete_slab", Kind.SLAB, Blocks.CONCRETE.white(), Items.CONCRETE.white(), "concrete"),
	BED("bed", Kind.BED, Blocks.BED.white(), Items.BED.white(), null),
	SHULKER_BOX("shulker_box", Kind.SHULKER_BOX, Blocks.DYED_SHULKER_BOX.white(), Items.DYED_SHULKER_BOX.white(), null),
	STAINED_GLASS("stained_glass", Kind.GLASS, Blocks.STAINED_GLASS.white(), Items.STAINED_GLASS.white(), null),
	STAINED_GLASS_PANE("stained_glass_pane", Kind.PANE, Blocks.STAINED_GLASS_PANE.white(), Items.STAINED_GLASS_PANE.white(), "stained_glass"),
	// No item: a candle cake only exists by putting our candle on a cake.
	CANDLE_CAKE("candle_cake", Kind.CANDLE_CAKE, Blocks.DYED_CANDLE_CAKE.white(), null, "candle");

	public enum Kind { SIMPLE, POWDER, GLAZED, CARPET, CANDLE, STAIRS, SLAB, BED, SHULKER_BOX, GLASS, PANE, CANDLE_CAKE }

	/** family suffix in ids and asset names */
	public final String id;
	public final Kind kind;
	/** the vanilla white block whose properties (sound, hardness, tool) are copied */
	public final Block template;
	/** the vanilla item a client is told it holds; drives its placement-sound prediction. Null: no item. */
	public final @Nullable Item clientItem;
	/** the family this one is made of / depends on (stairs of wool, cake with candle) */
	public final @Nullable String material;

	Family(String id, Kind kind, Block template, @Nullable Item clientItem, @Nullable String material) {
		this.id = id;
		this.kind = kind;
		this.template = template;
		this.clientItem = clientItem;
		this.material = material;
	}

	public boolean hasItem() {
		return clientItem != null;
	}

	public Family materialFamily() {
		for (Family f : values()) {
			if (f.id.equals(material)) return f;
		}
		throw new IllegalStateException("Family " + id + " has no material family");
	}
}
