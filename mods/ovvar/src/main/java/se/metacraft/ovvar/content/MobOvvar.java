package se.metacraft.ovvar.content;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import se.metacraft.ovvar.MobOvvarConfig;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.OvvarConfig;
import se.metacraft.ovvar.content.enchantment.EnchantmentsConfig;
import se.metacraft.ovvar.content.enchantment.Magpie;
import se.metacraft.ovvar.content.item.OvveItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Mob ovvar: a mob that vanilla would spawn in leggings or a chestplate sometimes wears a patched
 * ovve or frack instead. The garment is marked ({@link ModComponents#MOB_OVVE}), unowned, and
 * carries one to three {@link PatchLoot#LOOT} patches, all of which ride in the dye colour, so it
 * draws for everyone without a pack. It never drops; the patches it wore sometimes do.
 */
public final class MobOvvar {
	private MobOvvar() {}

	/** A mob's garment is never an item on the ground: a marked stack cannot become an {@link ItemEntity}. */
	public static void init() {
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) -> {
			if (!(entity instanceof ItemEntity item && is(item.getItem()))) return true;
			Ovvar.LOGGER.debug("[ovvar] rejected a mob's garment as an item entity: {}", item.getItem());
			return false;
		});
	}

	/** Most draws spent finding cells for a garment's patches. */
	private static final int MAX_DRAWS = 16;

	/** The chapters worn in this slot, in enum order: ovves for the legs, fracks for the chest. */
	public static List<Chapter> chapters(EquipmentSlot slot) {
		List<Chapter> chapters = new ArrayList<>();
		for (Chapter chapter : Chapter.values()) if (chapter.slot == slot) chapters.add(chapter);
		return chapters;
	}

	/** The patches a mob may wear: the droppable ones, every one of which is instant. */
	public static List<Patches.Patch> patches() {
		return PatchLoot.looted().stream().filter(Looks::instant).toList();
	}

	/** Is this a mob's own ovve or frack? */
	public static boolean is(ItemStack stack) {
		return stack.has(ModComponents.MOB_OVVE);
	}

	/**
	 * A new unowned marked ovve (legs) or frack (chest) with one to three patches.
	 *
	 * @param topCells whether the top half shows (a frack's always does; an ovve's does when its top is
	 *                 up and nothing covers it): only then may a patch go on a top cell
	 * @return the garment, or EMPTY when no chapter wears this slot or no patch may be worn
	 */
	public static ItemStack garment(RandomSource random, EquipmentSlot slot, boolean topCells) {
		List<Chapter> chapters = chapters(slot);
		List<Patches.Patch> patches = patches();
		if (chapters.isEmpty() || patches.isEmpty()) return ItemStack.EMPTY;
		Chapter chapter = chapters.get(random.nextInt(chapters.size()));
		ItemStack stack = new ItemStack(ModContent.ovve(chapter));
		stack.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
		boolean up = slot == EquipmentSlot.CHEST || !chapter.rollable || random.nextBoolean();
		if (slot == EquipmentSlot.LEGS) OvveItem.setTopUp(stack, up);
		boolean tops = slot == EquipmentSlot.CHEST || (up && topCells);
		List<Spot> cells = new ArrayList<>();
		for (Piece piece : chapter.pieces()) {
			if (piece == Piece.TOP && !tops) continue;
			cells.addAll(Spot.cells(piece));
		}
		int wanted = 1 + random.nextInt(Looks.INSTANT);
		int placed = 0;
		for (int draw = 0; draw < MAX_DRAWS && placed < wanted; draw++) {
			Patches.Patch patch = patches.get(random.nextInt(patches.size()));
			List<Spot> open = new ArrayList<>();
			for (Spot cell : cells) {
				if (patch.fits(cell) && Looks.canSew(stack, new Placement(cell, patch))) open.add(cell);
			}
			if (open.isEmpty()) continue;
			if (Looks.sew(stack, new Placement(open.get(random.nextInt(open.size())), patch))) placed++;
		}
		return placed == 0 ? ItemStack.EMPTY : stack;
	}

	/**
	 * What a mob's spawn roll puts in this slot: {@code vanilla}, or now and then a garment in its
	 * place (whose slot then never drops). Vanilla rolls the chest before the legs (head, chest,
	 * legs, feet — {@code Mob.EQUIPMENT_POPULATION_ORDER}, and a piglin's own roll likewise), so
	 * when the legs come the chest is settled and says truly whether an ovve's top shows.
	 */
	public static ItemStack spawnPiece(Mob mob, EquipmentSlot slot, ItemStack vanilla, RandomSource random, MobOvvarConfig config) {
		if (slot != EquipmentSlot.LEGS && slot != EquipmentSlot.CHEST) return vanilla;
		if (!config.enabled() || vanilla.isEmpty() || !(random.nextFloat() < config.chance())) return vanilla;
		ItemStack garment = garment(random, slot, slot == EquipmentSlot.CHEST || mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
		if (garment.isEmpty()) return vanilla;
		mob.setDropChance(slot, 0f);
		return garment;
	}

	/** Puts a garment on the mob in its own slot; the slot never drops. Anything but an ovve or frack is left alone. */
	public static void wear(Mob mob, ItemStack garment) {
		if (!(garment.getItem() instanceof OvveItem item)) return;
		OvveItem.wear(mob, garment);
		mob.setDropChance(item.chapter.slot, 0f);
	}

	/**
	 * How likely each worn patch is to drop: {@code patch_drop_chance}, raised by Magpie on the killer's worn ovve or
	 * frack ({@link Magpie#mobOvvarChance}: plus {@code mob_ovvar_chance_per_level} per level, capped at 1). Without
	 * a killer, without Magpie, or with Magpie switched off, it is {@code patch_drop_chance} as it is.
	 *
	 * @param killer the creditee of the kill (see {@link #killer}); may be null
	 */
	public static float patchDropChance(MobOvvarConfig config, @Nullable Entity killer) {
		return patchDropChance(config, killer, OvvarConfig.get().enchantments().magpie());
	}

	/** As {@link #patchDropChance(MobOvvarConfig, Entity)}, with these Magpie numbers. */
	public static float patchDropChance(MobOvvarConfig config, @Nullable Entity killer, EnchantmentsConfig.Magpie magpie) {
		int level = Magpie.level(killer);
		return level == 0 ? config.patchDropChance() : Magpie.mobOvvarChance(config.patchDropChance(), level, magpie);
	}

	/** The patches this garment wore that drop: each placement independently, one patch item apiece. */
	public static List<ItemStack> patchDrops(ItemStack garment, RandomSource random, float chance) {
		List<ItemStack> drops = new ArrayList<>();
		for (Placement placement : SpotPlacements.asPlacementList(Looks.sewn(garment))) {
			if (random.nextFloat() < chance) drops.add(new ItemStack(ModContent.patchItem(placement.patch())));
		}
		return drops;
	}

	/** The player credited with this kill: the one the mob remembers hitting, else whoever dealt the damage. */
	public static @Nullable Entity killer(Mob mob, DamageSource source) {
		Player player = mob.getLastHurtByPlayer();
		return player != null ? player : source.getEntity();
	}

	/** A player's kill: the legs and chest garments each drop the patches they wore, by chance. The garments never drop. */
	public static void onDeath(Mob mob, ServerLevel level, DamageSource source, boolean killedByPlayer) {
		if (!killedByPlayer) return;
		float chance = patchDropChance(OvvarConfig.get().mobOvvar(), killer(mob, source));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.CHEST}) {
			ItemStack garment = mob.getItemBySlot(slot);
			// The companion top of a top-up ovve (OvveTopItem, in the empty chest slot) carries a copy of its patches: not a garment.
			if (!is(garment) || !(garment.getItem() instanceof OvveItem)) continue;
			for (ItemStack drop : patchDrops(garment, mob.getRandom(), chance)) mob.spawnAtLocation(level, drop);
		}
	}
}
