import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import se.metacraft.nether_rifts.NetherRifts;
import se.metacraft.nether_rifts.mob.HeldItems;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held-item markers on held_items_test.bbmodel (test only): a vanilla-sized humanoid in Blockbench's modded entity
 * space with {@code item_right}, {@code item_left} and {@code item_head} where {@link HeldItems} says, and a
 * {@code swing} animation that holds the right arm out at 90 degrees.
 */
public class HeldItemsTest {

	private static Model model;

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		model = BbModelLoader.load(NetherRifts.getID("held_items_test"));
	}

	private static Node node(String name) {
		var node = HeldItems.marker(model, name);
		assertNotNull(node, "no node " + name);
		return node;
	}

	/** BIL keeps an empty group as a model node (no model of its own), with a rest pose and a pose in every frame. */
	@Test
	public void emptyGroupsAreNodes() {
		for (var name : new String[] {HeldItems.RIGHT, HeldItems.LEFT, HeldItems.HEAD}) {
			var node = node(name);
			assertEquals(Node.NodeType.BONE, node.type(), name);
			assertNull(node.modelData(), name + " has a model");
			assertNotNull(model.defaultPose().get(node.uuid()), name + " has no rest pose");
			for (var animation : model.animations().values()) {
				for (var frame : animation.frames()) {
					assertNotNull(frame.poses().get(node.uuid()), name + " has no pose in a frame");
				}
			}
		}
		// in the head bone, so BIL turns it with the head
		assertEquals(Node.NodeTag.HEAD_CHILD, node(HeldItems.HEAD).tag());
		// BIL gives an empty group no scale: its poses keep their place, but not their turn
		var marker = node(HeldItems.RIGHT);
		assertEquals(0, marker.transform().localScale());
		assertEquals(0, model.defaultPose().get(marker.uuid()).matrix().getScale(new Vector3f()).length(), 1e-6);
		assertEquals(node("right_arm"), HeldItems.bone(marker));
		// so HeldItems works it out from its bone's, to the same place as BIL's, at rest and swung
		var swing = model.animations().get("swing").frames()[0].poses();
		for (var poses : List.of(model.defaultPose(), swing)) {
			var ours = HeldItems.markerPose(marker, poses.get(node("right_arm").uuid()).matrix()).getTranslation(new Vector3f());
			var bils = poses.get(marker.uuid()).matrix().getTranslation(new Vector3f());
			assertEquals(0, ours.distance(bils), 1e-5, "the marker is at " + ours + ", BIL has it at " + bils);
		}
		assertTrue(model.animations().get("swing").affectedBones().isEmpty(), "a .bbmodel animation names the bones it moves");
	}

	/** Where a marker's item is drawn: the item display's pose, turned to the body's yaw (BIL's yaw is the body's). */
	private static Matrix4f bilItem(HeldItems.Spot spot, Matrix4fc markerPose, float yaw) {
		// the display's own turn of its item (DisplayRenderer.ItemDisplayRenderer)
		return new Matrix4f().rotateY(-yaw * Mth.DEG_TO_RAD).mul(HeldItems.itemTransform(markerPose, spot)).rotateY(Mth.PI);
	}

	/** Where vanilla draws it: LivingEntityRenderer down to a humanoid part, then ItemInHandLayer or CustomHeadLayer. */
	private static Matrix4f vanilla(float yaw, Vector3f pivot) {
		return new Matrix4f().rotateY((180 - yaw) * Mth.DEG_TO_RAD).scale(-1, -1, 1).translate(0, -1.501f, 0)
				.translate(pivot.x / 16, pivot.y / 16, pivot.z / 16);
	}

	private static void assertMatrix(Matrix4fc expected, Matrix4fc actual, String what) {
		var e = new float[16];
		var a = new float[16];
		expected.get(e);
		actual.get(a);
		for (int i = 0; i < 16; i++) {
			// vanilla draws a mob 0.001 lower than its model space (the 1.501)
			assertEquals(e[i], a[i], 0.002f, what + ": expected\n" + expected + "but\n" + actual);
		}
	}

	/** At rest, the items sit where a vanilla mob of the same shape holds and wears them, turned the same way. */
	@Test
	public void restMatchesVanilla() {
		for (float yaw : new float[] {0, 37, -120}) {
			// the vanilla humanoid arms' pivots (model space: x mirrored, y down)
			var right = vanilla(yaw, new Vector3f(-5, 2, 0)).rotateX(-Mth.HALF_PI).rotateY(Mth.PI).translate(1 / 16f, 2 / 16f, -10 / 16f);
			assertMatrix(right, bilItem(HeldItems.Spot.RIGHT_HAND, rest(HeldItems.RIGHT), yaw), "right hand at yaw " + yaw);
			var left = vanilla(yaw, new Vector3f(5, 2, 0)).rotateX(-Mth.HALF_PI).rotateY(Mth.PI).translate(-1 / 16f, 2 / 16f, -10 / 16f);
			assertMatrix(left, bilItem(HeldItems.Spot.LEFT_HAND, rest(HeldItems.LEFT), yaw), "left hand at yaw " + yaw);
			var head = vanilla(yaw, new Vector3f(0, 0, 0)).translate(0, -0.25f, 0).rotateY(Mth.PI).scale(0.625f, -0.625f, -0.625f);
			assertMatrix(head, bilItem(HeldItems.Spot.HEAD, rest(HeldItems.HEAD), yaw), "head at yaw " + yaw);
		}
	}

	/** A marker's rest pose, from its bone's. */
	private static Matrix4fc rest(String name) {
		var marker = node(name);
		return HeldItems.markerPose(marker, model.defaultPose().get(HeldItems.bone(marker).uuid()).matrix());
	}

	/** Swung out at 90 degrees, the arm carries the item with it: to where the fist is now, turned with the arm. */
	@Test
	public void itemFollowsTheArm() {
		var frame = model.animations().get("swing").frames()[0];
		var armRest = model.defaultPose().get(node("right_arm").uuid()).matrix();
		var arm = frame.poses().get(node("right_arm").uuid()).matrix();
		var marker = HeldItems.markerPose(node(HeldItems.RIGHT), arm);
		var item = HeldItems.itemTransform(marker, HeldItems.Spot.RIGHT_HAND);

		// rigidly on the arm: the arm's move from rest, applied to the item at rest
		var armMove = new Matrix4f(arm).mul(new Matrix4f(armRest).invert());
		assertMatrix(armMove.mul(HeldItems.itemTransform(rest(HeldItems.RIGHT), HeldItems.Spot.RIGHT_HAND)), item, "swung item");

		// the bottom of the fist, 1 px out and 10 px down the arm from the shoulder, is level with the shoulder now and
		// 10 px out in front of or behind it; the item is 2 px in front of the fist
		var fist = arm.transformPosition(new Vector3f(1, -10, 0).div(16));
		var shoulder = arm.getTranslation(new Vector3f());
		var restFist = armRest.transformPosition(new Vector3f(1, -10, 0).div(16));
		assertEquals(shoulder.y, fist.y, 0.001f, "the swung fist isn't level with the shoulder");
		assertEquals(10 / 16f, Math.abs(fist.z - shoulder.z), 0.001f, "the swung fist isn't an arm's length out");
		assertEquals(10 / 16f, shoulder.y - restFist.y, 0.001f, "the fist at rest isn't an arm's length down");
		var at = item.getTranslation(new Vector3f());
		assertEquals(2 / 16f, at.distance(fist), 0.001f, "the item is " + at + ", the fist " + fist);
		assertEquals(fist.y, at.y, 2 / 16f + 0.001f, "the item isn't up with the swung fist");
	}
}
