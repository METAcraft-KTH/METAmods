package se.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import de.tomalbrc.bil.core.holder.wrapper.ItemBone;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import se.metacraft.nether_rifts.NetherRifts;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows the armour a BIL mob wears, as vanilla draws it on a piglin: BIL draws no equipment. The model has a bone per
 * armour piece, inside the bone it covers, so it moves with it ({@link Piece}). tools/import_drummer.py adds them: vanilla's
 * piglin armour cubes, at vanilla's size. A piece shows while its slot holds armour (an equippable for that slot with an
 * equipment asset, as vanilla's armour layer wants), and hides otherwise.
 *
 * <p>BIL builds one model per bone, so the import also writes an item model per piece for each vanilla material, onto
 * vanilla's own armour textures ({@code nether_rifts:<model>/armour/<material>/<bone>}, the model being the
 * {@link RiftModels} name). A piece shows its material's model; a material without one (another mod's armour) isn't
 * shown. The first texture layer takes the dye colour: leather's dye (vanilla's leather brown when undyed), white for the
 * rest. A second layer (leather's overlay) takes custom model data colour 0. Both go red while the mob is hurt, as its
 * other bones do ({@link RiftModels#tint}). Enchanted armour glints. Trims aren't drawn.
 *
 * <p>Unlike the rest of the model, worn armour stays in sight when the mob is invisible, as vanilla's does. BIL shows or
 * hides every bone whenever the mob's flags change, and {@link RiftModels#hurtTint} recolours every bone, so each
 * {@link #tick} sets the pieces right again. It changes only what differs.
 */
public final class ArmourPieces {

	/** The armour bones, and the slot each shows. */
	public enum Piece {
		HEAD("armor_head", EquipmentSlot.HEAD),
		CHEST("armor_chest", EquipmentSlot.CHEST),
		CHEST_LEFT("armor_chest_left", EquipmentSlot.CHEST),
		CHEST_RIGHT("armor_chest_right", EquipmentSlot.CHEST),
		LEGS("armor_legs", EquipmentSlot.LEGS),
		LEGS_LEFT("armor_legs_left", EquipmentSlot.LEGS),
		LEGS_RIGHT("armor_legs_right", EquipmentSlot.LEGS),
		FEET_LEFT("armor_feet_left", EquipmentSlot.FEET),
		FEET_RIGHT("armor_feet_right", EquipmentSlot.FEET);

		public final String bone;
		public final EquipmentSlot slot;

		Piece(String bone, EquipmentSlot slot) {
			this.bone = bone;
			this.slot = slot;
		}
	}

	/** Materials whose first layer is dyed: vanilla's assets/minecraft/equipment/leather.json. */
	private static final Map<String, Integer> UNDYED = Map.of("leather", DyedItemColor.LEATHER_COLOR);
	/** Whether the mod has an item model, by id: the import writes one per material and piece. */
	private static final Map<Identifier, Boolean> MODELS = new ConcurrentHashMap<>();

	private record Shown(Piece piece, ItemBone bone) {}

	private final LivingEntity mob;
	@Nullable private final String models;
	private final List<Shown> shown = new ArrayList<>();

	/** Finds the model's armour bones and hides them; none on a model without them, or not loaded by RiftModels. */
	public ArmourPieces(LivingEntityHolder<?> holder) {
		this.mob = holder.getParent();
		this.models = RiftModels.name(holder.getModel());
		if (models == null) {
			return;
		}
		for (var piece : Piece.values()) {
			if (holder.getBone(piece.bone) instanceof ItemBone bone) {
				bone.setInvisible(true);
				shown.add(new Shown(piece, bone));
			}
		}
	}

	/** Whether the model has no armour bones, so nothing is shown. */
	public boolean isEmpty() {
		return shown.isEmpty();
	}

	/** A piece's bone, or null without it. */
	@Nullable
	public ItemBone bone(Piece piece) {
		for (var s : shown) {
			if (s.piece == piece) {
				return s.bone;
			}
		}
		return null;
	}

	/** Armour worn in a slot, as vanilla draws it: an equippable for that slot with an equipment asset. */
	public static boolean isWorn(ItemStack stack, EquipmentSlot slot) {
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot && equippable.assetId().isPresent();
	}

	/** The armour's material (its equipment asset's name), or null when it isn't worn there or isn't vanilla's. */
	@Nullable
	public static String material(ItemStack stack, EquipmentSlot slot) {
		if (!isWorn(stack, slot)) {
			return null;
		}
		var id = stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier();
		return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : null;
	}

	/** A piece's item model in a material, or null when the mod has none. */
	@Nullable
	public static Identifier model(String models, String material, Piece piece) {
		var id = NetherRifts.getID(models + "/armour/" + material + "/" + piece.bone);
		boolean exists = MODELS.computeIfAbsent(id, key ->
				ArmourPieces.class.getResource("/assets/" + key.getNamespace() + "/items/" + key.getPath() + ".json") != null);
		return exists ? id : null;
	}

	/** Shows each piece in its slot's armour, coloured, or hides it. Call it each tick, after the mob's own tick. */
	public void tick() {
		if (shown.isEmpty()) {
			return;
		}
		int tint = RiftModels.tint(mob);
		for (var s : shown) {
			var stack = mob.getItemBySlot(s.piece.slot);
			var material = material(stack, s.piece.slot);
			var model = material == null ? null : model(models, material, s.piece);
			if (model == null) {
				s.bone.setInvisible(true);
				continue;
			}
			s.bone.setInvisible(false);
			var undyed = UNDYED.get(material);
			int colour = undyed == null ? tint : ARGB.multiply(DyedItemColor.getOrDefault(stack, undyed), tint);
			// the bone's own stack, which its display shows while it is visible
			var item = s.bone.element().getItem();
			boolean changed = set(item, DataComponents.ITEM_MODEL, model);
			changed |= set(item, DataComponents.DYED_COLOR, new DyedItemColor(colour));
			changed |= set(item, DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(tint)));
			changed |= set(item, DataComponents.ENCHANTMENT_GLINT_OVERRIDE, stack.hasFoil() ? true : null);
			if (changed) {
				// sends the stack again
				s.bone.updateModel(model);
			}
		}
	}

	/** Sets a component (removes it for null) if it differs; returns whether it did. */
	private static <T> boolean set(ItemStack item, DataComponentType<T> type, @Nullable T value) {
		if (Objects.equals(item.get(type), value)) {
			return false;
		}
		if (value == null) {
			item.remove(type);
		} else {
			item.set(type, value);
		}
		return true;
	}
}
