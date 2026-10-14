package metacraft.ovvar.gametest;

import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveFeetItem;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.OvveTopItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import metacraft.ovvar.sewing.SewingGame;
import metacraft.ovvar.sewing.StandAim;
import metacraft.ovvar.sewing.StandDisplays;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Media frack is a chest-slot garment: a tailcoat, not an overall. It has pockets like an
 * ovve and takes patches on its top cells, but there is no companion top, no rolling, no legs
 * and no cuffs — the chest slot holds the garment itself.
 */
public final class FrackTests {
	private static final Chapter FRACK = Chapter.MEDIA;
	private static final Chapter OVVE = Chapter.DATA;

	@GameTest
	public void aFrackIsWornInTheChestSlot(GameTestHelper helper) {
		if (FRACK.slot != EquipmentSlot.CHEST) helper.fail("the frack's slot is " + FRACK.slot);
		if (OVVE.slot != EquipmentSlot.LEGS) helper.fail("an ovve's slot is " + OVVE.slot);
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		Equippable equippable = frack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != EquipmentSlot.CHEST) helper.fail("the frack item equips in " + (equippable == null ? "no slot" : equippable.slot()));
		if (equippable.assetId().isEmpty() || !equippable.assetId().get().equals(Looks.asset(FRACK, Piece.TOP, false, ""))) {
			helper.fail("the frack wears " + equippable.assetId() + ", not its top asset");
		}
		if (!frack.has(DataComponents.BUNDLE_CONTENTS)) helper.fail("the frack has no pockets");
		helper.succeed();
	}

	@GameTest
	public void aFrackHasOnlyTopCells(GameTestHelper helper) {
		if (!FRACK.pieces().equals(Set.of(Piece.TOP))) helper.fail("the frack's pieces are " + FRACK.pieces());
		if (!OVVE.pieces().equals(Set.of(Piece.TOP, Piece.BOTTOM))) helper.fail("an ovve's pieces are " + OVVE.pieces());
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		Patches.Patch itk = Patches.get("itk");
		if (!Looks.canSew(frack, new Placement(Spot.FRONT_TOP_LEFT, itk))) helper.fail("a chest cell refused a patch on the frack");
		if (Looks.canSew(frack, new Placement(Spot.LEG_FRONT_TOP_R, itk))) helper.fail("a leg cell took a patch on the frack, which has no legs");
		if (Looks.sew(frack, new Placement(Spot.LEG_FRONT_TOP_R, itk))) helper.fail("a leg patch was sewn on the frack");
		if (Looks.sewn(frack).isPresent()) helper.fail("something was sewn on the frack: " + Looks.sewn(frack));
		helper.succeed();
	}

	/** No companion top and no cuffs are registered for it: nothing else of ours ever sits in another slot. */
	@GameTest
	public void aFrackHasNoCompanionItems(GameTestHelper helper) {
		if (ModContent.top(FRACK) != null) helper.fail("the frack has a companion top item");
		if (ModContent.feet(FRACK) != null) helper.fail("the frack has a cuffs item");
		if (ModContent.top(OVVE) == null || ModContent.feet(OVVE) == null) helper.fail("an ovve lost its companions");
		for (Item item : ModContent.items()) {
			if (item instanceof OvveTopItem top && top.chapter == FRACK) helper.fail("a frack top is in the item list");
			if (item instanceof OvveFeetItem feet && feet.chapter == FRACK) helper.fail("frack cuffs are in the item list");
		}
		helper.succeed();
	}

	@GameTest
	public void wornFindsTheGarmentInEitherSlot(GameTestHelper helper) {
		ArmorStand stand = stand(helper);
		if (!OvveItem.worn(stand).isEmpty()) helper.fail("a bare stand wears " + OvveItem.worn(stand));
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		OvveItem.wear(stand, frack);
		if (stand.getItemBySlot(EquipmentSlot.CHEST) != frack) helper.fail("wear() put the frack in the wrong slot");
		if (OvveItem.worn(stand) != frack) helper.fail("worn() missed the frack in the chest slot");
		ArmorStand other = stand(helper);
		ItemStack ovve = new ItemStack(ModContent.ovve(OVVE));
		OvveItem.wear(other, ovve);
		if (other.getItemBySlot(EquipmentSlot.LEGS) != ovve) helper.fail("wear() put the ovve in the wrong slot");
		if (OvveItem.worn(other) != ovve) helper.fail("worn() missed the ovve in the legs slot");
		helper.succeed();
	}

	/** Worn on a stand, tick after tick: the chest slot keeps the frack itself, and no companion appears anywhere. */
	@GameTest(maxTicks = 100)
	public void aWornFrackKeepsItsSlotsToItself(GameTestHelper helper) {
		ArmorStand stand = stand(helper);
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		Looks.setSewn(frack, SpotPlacements.fromList(List.of(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("itk")))).getOrThrow());
		OvveItem.wear(stand, frack);
		helper.runAfterDelay(10, () -> {
			if (!(stand.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof OvveItem)) helper.fail("the chest slot lost the frack: " + stand.getItemBySlot(EquipmentSlot.CHEST));
			if (!stand.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) helper.fail("something is in the legs slot: " + stand.getItemBySlot(EquipmentSlot.LEGS));
			if (!stand.getItemBySlot(EquipmentSlot.FEET).isEmpty()) helper.fail("something is in the feet slot: " + stand.getItemBySlot(EquipmentSlot.FEET));
			// Its patches show on the stand: the top is the garment, so it is always "up".
			List<StandDisplays.Sprite> sprites = StandDisplays.sprites(stand);
			if (sprites.size() != 1) helper.fail("expected one sprite for the frack's patch, got " + sprites);
			helper.succeed();
		});
	}

	/** Somebody else's frack does not go on, and one forced into the chest slot comes off on the next tick. */
	@GameTest
	public void foreignFrackCannotBeWorn(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		OvveItem.setOwner(frack, UUID.randomUUID());
		if (player.isEquippableInSlot(frack, EquipmentSlot.CHEST)) helper.fail("the chest slot took a foreign frack");
		player.setItemSlot(EquipmentSlot.CHEST, frack);
		frack.inventoryTick(player.level(), player, EquipmentSlot.CHEST);
		if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) helper.fail("the tick left a foreign frack on");
		if (!player.getInventory().contains(stack -> stack.getItem() instanceof OvveItem)) helper.fail("the evicted frack went nowhere");
		helper.succeed();
	}

	@GameTest
	public void ownerCanWearTheirFrack(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		OvveItem.setOwner(frack, player.getUUID());
		if (!player.isEquippableInSlot(frack, EquipmentSlot.CHEST)) helper.fail("the owner could not put their own frack on");
		player.setItemSlot(EquipmentSlot.CHEST, frack);
		frack.inventoryTick(player.level(), player, EquipmentSlot.CHEST);
		if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) helper.fail("the tick took the owner's own frack off");
		helper.succeed();
	}

	/**
	 * A click on a frack stand's body with an empty hand is left to vanilla, whose chest-slot swap
	 * already hands the garment over — unlike an ovve, where the chest holds a companion and the
	 * click has to take the ovve off the legs instead.
	 */
	@GameTest(maxTicks = 100)
	public void clickingAFrackStandsChestIsVanillasSwap(GameTestHelper helper) {
		ArmorStand stand = stand(helper);
		ItemStack frack = new ItemStack(ModContent.ovve(FRACK));
		OvveItem.wear(stand, frack);
		ServerPlayer player = sewer(helper, stand);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.runAfterDelay(5, () -> {
			StandAim.CellPoint at = StandAim.cell(stand, Spot.FRONT_TOP_LEFT);
			Vec3 from = at.centre().add(at.normal().scale(1.5));
			player.setPos(from.x, from.y - player.getEyeHeight(), from.z);
			Vec3 d = at.centre().subtract(player.getEyePosition());
			double flat = Math.sqrt(d.x * d.x + d.z * d.z);
			player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
			player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, flat)));
			player.setYHeadRot(player.getYRot());
			InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, stand, null);
			if (result != InteractionResult.PASS) helper.fail("a click on a frack stand's chest was taken by us (" + result + "), not left to vanilla's swap");
			if (!(stand.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof OvveItem)) helper.fail("the frack came off on a passed click");
			helper.succeed();
		});
	}

	/** The seam sews onto the frack on a stand, found in its own slot. */
	@GameTest
	public void stitchingSewsOnAFrack(GameTestHelper helper) {
		ArmorStand stand = stand(helper);
		OvveItem.wear(stand, new ItemStack(ModContent.ovve(FRACK)));
		ServerPlayer player = sewer(helper, stand);
		Patches.Patch itk = Patches.get("itk");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.patchItem(itk), 3));
		Placement placement = new Placement(Spot.SLEEVE_OUT_TOP_R, itk);
		SewingGame.start(player, stand, placement, itk);
		int pulls = 0;
		while (Looks.at(OvveItem.worn(stand), Spot.SLEEVE_OUT_TOP_R) == null) {
			CompoundTag next = SewingGame.nextPull(player);
			if (next == null) helper.fail("seam closed after " + pulls + " pull(s) without sewing");
			SewingGame.click(player, SewingGame.PULL, Optional.of(next));
			if (++pulls > 32) helper.fail("still not sewn after " + pulls + " pulls");
		}
		if (!placement.equals(Looks.at(stand.getItemBySlot(EquipmentSlot.CHEST), Spot.SLEEVE_OUT_TOP_R))) helper.fail("the patch is not on the frack in the chest slot");
		helper.succeed();
	}

	private static ArmorStand stand(GameTestHelper helper) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
		stand.setNoGravity(true);
		return stand;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer sewer(GameTestHelper helper, ArmorStand stand) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(stand.getX(), stand.getY(), stand.getZ() + 2);
		return player;
	}
}
