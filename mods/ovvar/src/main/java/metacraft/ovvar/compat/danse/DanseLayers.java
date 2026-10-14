package metacraft.ovvar.compat.danse;

import de.tomalbrc.danse.api.BodyLayerProvider;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.OvveFeetItem;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.OvveTopItem;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.SpotPlacements;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * What a gesturing stand-in wears for an ovve: a {@link DanseModels} item model per part, the
 * cloth and every patch chosen by {@code custom_model_data} strings, drawn by our Danse fork's body
 * layers at the textures' own resolution.
 *
 * <p>Danse's passes, kept: the bottom (a leggings layer) is the <em>inner</em> pass on body and
 * legs, from the legs slot; the top (a humanoid layer) is the <em>outer</em> pass on body and arms,
 * from the chest slot — the companion top, a frack, or a real chestplate worn over the ovve
 * ({@link ModComponents#WRAPPED_TOP}), whose own pixels Danse still draws over the top.
 */
public final class DanseLayers implements BodyLayerProvider {
	public static final DanseLayers INSTANCE = new DanseLayers();

	private DanseLayers() {}

	@Override
	public ItemStack layer(EntityEquipment equipment, BodyPart part, boolean inner) {
		ItemStack legs = equipment.get(EquipmentSlot.LEGS);
		if (inner) {
			if (!(legs.getItem() instanceof OvveItem ovve) || !DanseModels.draws(Piece.BOTTOM, part)) return ItemStack.EMPTY;
			boolean nercabbad = ovve.chapter.rollable && !OvveItem.topUp(legs);
			return stack(Piece.BOTTOM, part, ovve.chapter, nercabbad, placements(legs, Piece.BOTTOM));
		}
		if (!DanseModels.draws(Piece.TOP, part)) return ItemStack.EMPTY;
		ItemStack chest = equipment.get(EquipmentSlot.CHEST);
		if (chest.getItem() instanceof OvveTopItem top) {
			return stack(Piece.TOP, part, top.chapter, false, placements(chest, Piece.TOP));
		}
		// A frack: worn in the chest slot, its own top.
		if (chest.getItem() instanceof OvveItem frack && frack.chapter.ownPiece() == Piece.TOP) {
			return stack(Piece.TOP, part, frack.chapter, false, placements(chest, Piece.TOP));
		}
		if (chest.has(ModComponents.WRAPPED_TOP) && legs.getItem() instanceof OvveItem ovve && OvveItem.topUp(legs)) {
			return stack(Piece.TOP, part, ovve.chapter, false, placements(legs, Piece.TOP));
		}
		return ItemStack.EMPTY;
	}

	/** An ovve's server-side stack carries only its chapter's base asset: Danse must not draw it. */
	@Override
	public boolean replacesArmor(ItemStack stack) {
		return stack.getItem() instanceof OvveItem
				|| stack.getItem() instanceof OvveTopItem
				|| stack.getItem() instanceof OvveFeetItem;
	}

	private static ItemStack stack(Piece piece, BodyPart part, Chapter chapter, boolean nercabbad, List<Placement> placements) {
		ItemStack out = new ItemStack(Items.PAPER);
		out.set(DataComponents.ITEM_MODEL, DanseModels.itemModel(piece, part));
		out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
				List.of(), List.of(), DanseModels.strings(chapter, piece, nercabbad, placements), List.of()));
		return out;
	}

	private static List<Placement> placements(ItemStack stack, Piece piece) {
		return SpotPlacements.asPlacementList(Looks.sewn(stack, piece));
	}
}
