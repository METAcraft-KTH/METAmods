import de.tomalbrc.bil.core.component.AnimationComponent;
import de.tomalbrc.bil.core.element.PerPlayerItemDisplayElement;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.mob.HeldItems;
import se.metacraft.nether_rifts.mob.RiftEntities;
import org.joml.Vector3f;

/** {@link HeldItems} on a live mob: nothing without markers, and items on the markers of a model that has them. */
public class HeldItemsTests {

	static void registerTests() {
		NetherRiftsTests.register("held_items/drummer_shows_gear", ctx -> {
			// The Piglin Drummer's model has all three markers (the import adds them): its sword and pumpkin show.
			var drummer = ctx.spawn(RiftEntities.WAR_DRUMMER, new BlockPos(12, 1, 12));
			drummer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
			ctx.runAtTickTime(5, () -> {
				var held = drummer.getHolder().heldItems();
				held.tick();
				var right = (PerPlayerItemDisplayElement) held.display(HeldItems.Spot.RIGHT_HAND);
				var head = (PerPlayerItemDisplayElement) held.display(HeldItems.Spot.HEAD);
				ctx.assertTrue(right != null && head != null && held.display(HeldItems.Spot.LEFT_HAND) != null, "the drummer lacks a marker");
				ctx.assertTrue(right.getItem().is(Items.DIAMOND_SWORD), "the drummer's right hand shows " + right.getItem());
				ctx.assertTrue(head.getItem().is(Items.CARVED_PUMPKIN), "the drummer's head shows " + head.getItem());
				ctx.assertTrue(drummer.isAlive(), "a mob with held items died");
				ctx.killAllEntities();
				ctx.succeed();
			});
		});
		NetherRiftsTests.register("held_items/on_the_markers", ctx -> {
			// held_items_test.bbmodel (test only) has all three markers, and a swing that holds the right arm out. On a
			// drummer, the sword shows on the right arm's marker, placed as BIL places its bones, and swings out with it.
			var model = BbModelLoader.load(NetherRifts.getID("held_items_test"));
			var drummer = ctx.spawn(RiftEntities.WAR_DRUMMER, new BlockPos(12, 1, 12));
			var holder = new LivingEntityHolder<>(drummer, model);
			var held = new HeldItems(holder);
			var right = (PerPlayerItemDisplayElement) held.display(HeldItems.Spot.RIGHT_HAND);
			var left = (PerPlayerItemDisplayElement) held.display(HeldItems.Spot.LEFT_HAND);
			var head = (PerPlayerItemDisplayElement) held.display(HeldItems.Spot.HEAD);
			ctx.assertTrue(right != null && left != null && head != null, "a marker has no display");
			ctx.assertTrue(holder.getDisplayIds().length == holder.getBones().length + 3, "the displays don't ride with the bones");

			drummer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
			held.tick();
			ctx.assertTrue(right.getItem().is(Items.DIAMOND_SWORD), "the right hand shows " + right.getItem());
			ctx.assertTrue(left.getItem().isEmpty() && head.getItem().isEmpty(), "an empty slot shows an item");
			ctx.assertTrue(right.getItemDisplayContext() == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, "the right hand's view");
			ctx.assertTrue(head.getItemDisplayContext() == ItemDisplayContext.HEAD, "the head's view");
			// BIL draws a bone at its pose less the mob's height (its displays ride on top of the mob)
			float drop = drummer.getType().getDimensions().height() - 0.01f;
			var marker = HeldItems.marker(model, HeldItems.RIGHT);
			var arm = HeldItems.bone(marker).uuid();
			var rest = new Vector3f(right.getTranslation(null));
			var restExpected = HeldItems.itemTransform(HeldItems.markerPose(marker, model.defaultPose().get(arm).matrix()), HeldItems.Spot.RIGHT_HAND)
					.getTranslation(new Vector3f()).sub(0, drop, 0);
			ctx.assertTrue(rest.distance(restExpected) < 0.001f, "the sword is at " + rest + ", not " + restExpected);

			var animator = (AnimationComponent) holder.getAnimator();
			animator.playAnimation("swing", 0, true);
			animator.tickAnimations();
			held.tick();
			var swung = new Vector3f(right.getTranslation(null));
			var swungArm = model.animations().get("swing").frames()[0].poses().get(arm).matrix();
			var swungExpected = HeldItems.itemTransform(HeldItems.markerPose(marker, swungArm), HeldItems.Spot.RIGHT_HAND)
					.getTranslation(new Vector3f()).sub(0, drop, 0);
			ctx.assertTrue(swung.distance(swungExpected) < 0.001f, "the swung sword is at " + swung + ", not " + swungExpected);
			ctx.assertTrue(swung.distance(rest) > 0.5f, "the sword didn't swing out with the arm: " + rest + " to " + swung);

			// a left-handed mob holds its main hand's item in its left hand
			drummer.setLeftHanded(true);
			held.tick();
			ctx.assertTrue(left.getItem().is(Items.DIAMOND_SWORD) && right.getItem().isEmpty(), "left-handed, the sword is in " + right.getItem() + " / " + left.getItem());

			// worn armour isn't drawn as an item; a carved pumpkin is
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			held.tick();
			ctx.assertTrue(head.getItem().isEmpty(), "a helmet shows as an item");
			drummer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
			held.tick();
			ctx.assertTrue(head.getItem().is(Items.CARVED_PUMPKIN), "the head shows " + head.getItem());

			// in the head bone, it turns with the head's look
			var level = new Vector3f(head.getTranslation(null));
			drummer.setXRot(40);
			drummer.xRotO = 40;
			held.tick();
			ctx.assertTrue(new Vector3f(head.getTranslation(null)).distance(level) > 0.05f, "the pumpkin doesn't turn with the head");
			ctx.killAllEntities();
			ctx.succeed();
		});
	}
}
