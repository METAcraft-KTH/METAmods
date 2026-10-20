package metacraft.moredyes.sheep;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.mixin.SheepAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A sheep in one of our colours, rebuilt entirely from item displays: six body parts (always) and
 * six wool parts (while unshorn), driven every tick from the sheep's own state. The vanilla sheep
 * is sent to clients invisible by {@link SheepOverlay}: mixing its client-interpolated body with
 * server-driven wool displays never lined up, so everything now lives in one interpolation frame.
 * Vanilla's walk, head and grazing animation are reproduced server-side.
 *
 * <p>Coordinate contract with {@code gen_assets.py}'s {@code sheep_part}: models are authored in
 * "P space" — vanilla model x → +x, model y (down) → −y, model z (back) → −z — centred on the
 * part's pivot. A part at model pivot (px, py, pz) sits at world offset (px, 24 − py, −pz) / 16
 * for body yaw 0 (facing +z); yaw ψ rotates that by −ψ about Y. Rotations: model xRot α → rotateX(α),
 * model yRot β → rotateY(−β). Item displays in FIXED context show block models turned 180° about
 * Y relative to a placed block, so R_Y(π) is applied innermost.
 */
public final class SheepWoolRig extends ElementHolder {
	private static final float DEG = (float) (Math.PI / 180.0);
	private static final Quaternionf FIXED_FLIP = new Quaternionf().rotateY((float) Math.PI);
	private static final String[] PARTS = {"head", "body", "leg", "leg", "leg", "leg"};
	/** vanilla model pivots per part index: head, body, right hind, left hind, right front, left front */
	private static final float[][] PIVOTS = {{0, 6, -8}, {0, 5, 2}, {-3, 12, 7}, {3, 12, 7}, {-3, 12, -5}, {3, 12, -5}};

	private final Sheep sheep;
	private final SheepAccessor access;
	private final ItemDisplayElement[] body = new ItemDisplayElement[6];
	private final ItemDisplayElement[] wool = new ItemDisplayElement[6];
	private ModColor lastColor;
	private boolean lastSheared = true, lastAlive, lastHurt;

	private double lastX, lastZ;
	private float walkSpeed, walkPos;

	static SheepWoolRig attach(Sheep sheep) {
		SheepWoolRig rig = new SheepWoolRig(sheep);
		EntityAttachment.ofTicking(rig, sheep);
		return rig;
	}

	private SheepWoolRig(Sheep sheep) {
		this.sheep = sheep;
		this.access = (SheepAccessor) sheep;
		this.lastX = sheep.getX();
		this.lastZ = sheep.getZ();
		for (int i = 0; i < 6; i++) {
			body[i] = part();
			wool[i] = part();
		}
		body[1].setShadowRadius(0.7F);
		body[1].setShadowStrength(1.0F);
		update();
	}

	private ItemDisplayElement part() {
		ItemDisplayElement e = new ItemDisplayElement();
		e.setItemDisplayContext(ItemDisplayContext.FIXED);
		e.setInterpolationDuration(1);
		e.setTeleportDuration(1);
		e.setInvisible(true);
		e.setDisplaySize(1.5F, 1.5F);
		addElement(e);
		return e;
	}

	@Override
	protected void onTick() {
		update();
	}

	private void update() {
		ModColor color = SheepColors.get(sheep);
		// Shown until the entity is actually removed, so the death flop below plays out like vanilla's.
		boolean alive = color != null && !sheep.isRemoved();
		boolean sheared = sheep.isSheared();
		boolean hurt = sheep.hurtTime > 0 || sheep.deathTime > 0; // vanilla keeps the red overlay through the death animation
		if (color != lastColor || sheared != lastSheared || alive != lastAlive || hurt != lastHurt) {
			setItems(alive ? color : null, sheared, hurt);
			lastColor = color;
			lastSheared = sheared;
			lastAlive = alive;
			lastHurt = hurt;
		}
		if (!alive) return;

		// Walk animation, as LivingEntity.calculateEntityAnimation does client-side.
		double dx = sheep.getX() - lastX, dz = sheep.getZ() - lastZ;
		lastX = sheep.getX();
		lastZ = sheep.getZ();
		float f = Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0F, 1.0F);
		walkSpeed += (f - walkSpeed) * 0.4F;
		walkPos += walkSpeed;

		float scale = sheep.isBaby() ? 0.5F : 1.0F;
		float headScale = sheep.isBaby() ? 0.75F : 1.0F;
		float yaw = sheep.yBodyRot;
		Quaternionf yawQ = new Quaternionf().rotateY(-yaw * DEG);
		if (sheep.deathTime > 0) {
			// LivingEntityRenderer.setupRotations: keel over 90° about the body's Z over ~20 ticks.
			float flop = Math.min(1.0F, Mth.sqrt((sheep.deathTime - 1) / 20.0F * 1.6F));
			yawQ.rotateZ(-flop * (float) (Math.PI / 2));
		}

		// Head: vanilla SheepModel.setupAnim + grazing (Sheep.getHeadEatPositionScale/AngleScale).
		int eat = access.moredyes$eatAnimationTick();
		float eatPos = headEatPosition(eat), eatAngle = headEatAngle(eat);
		float headYaw = Mth.clamp(Mth.wrapDegrees(sheep.getYHeadRot() - yaw), -85.0F, 85.0F) * DEG;
		float headPitch = eat > 0 ? eatAngle : sheep.getXRot() * DEG;
		Quaternionf headRot = new Quaternionf().rotateY(-headYaw).rotateX(headPitch);
		float headY = 6.0F + eatPos * 9.0F;

		// Legs: QuadrupedModel leg swing.
		float a = Mth.cos(walkPos * 0.6662F) * 1.4F * walkSpeed;
		float b = Mth.cos(walkPos * 0.6662F + (float) Math.PI) * 1.4F * walkSpeed;
		// Body: vanilla's SheepModel poses it xRot = 90° (the box is authored lying along z).
		Quaternionf[] rots = {headRot, new Quaternionf().rotateX((float) (Math.PI / 2)), new Quaternionf().rotateX(a), new Quaternionf().rotateX(b),
				new Quaternionf().rotateX(b), new Quaternionf().rotateX(a)};

		for (int i = 0; i < 6; i++) {
			float[] p = PIVOTS[i];
			float py = i == 0 ? headY : p[1];
			float partScale = i == 0 ? headScale : scale;
			place(body[i], yawQ, scale, partScale, p[0], py, p[2], rots[i]);
			if (!sheared) place(wool[i], yawQ, scale, partScale, p[0], py, p[2], rots[i]);
		}
	}

	/** Position a part whose vanilla model pivot is (px, py, pz), with the given local rotation. */
	private static void place(
		ItemDisplayElement e, Quaternionf yawQ, float bodyScale, float partScale,
		float px, float py, float pz, Quaternionf local
	) {
		Vector3f t = new Vector3f(px, 24.0F - py, -pz).mul(bodyScale / 16.0F);
		yawQ.transform(t);
		e.setTranslation(t);
		e.setLeftRotation(new Quaternionf(yawQ).mul(local).mul(FIXED_FLIP));
		e.setScale(new Vector3f(partScale));
		e.startInterpolationIfDirty();
	}

	private void setItems(ModColor color, boolean sheared, boolean hurt) {
		String h = hurt ? "_hurt" : "";
		for (int i = 0; i < 6; i++) {
			String part = PARTS[i];
			if (color == null) {
				body[i].setItem(ItemStack.EMPTY);
				wool[i].setItem(ItemStack.EMPTY);
				continue;
			}
			body[i].setItem(stack((sheared ? "sheep/" + color.id() + "_sheared_" + part : "sheep/body_" + part) + h));
			wool[i].setItem(sheared ? ItemStack.EMPTY : stack("sheep/" + color.id() + "_" + part + h));
		}
	}

	private static ItemStack stack(String itemDefinition) {
		ItemStack stack = new ItemStack(Items.OAK_STAIRS);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, itemDefinition));
		return stack;
	}

	// Sheep.getHeadEatPositionScale / getHeadEatAngleScale without the partial tick.
	private static float headEatPosition(int tick) {
		if (tick <= 0) return 0.0F;
		if (tick >= 4 && tick <= 36) return 1.0F;
		if (tick < 4) return tick / 4.0F;
		return -(tick - 40) / 4.0F;
	}

	private static float headEatAngle(int tick) {
		if (tick > 4 && tick <= 36) {
			float f = (tick - 4) / 32.0F;
			return (float) (Math.PI / 5) + 0.21991149F * Mth.sin(f * 28.7F);
		}
		return tick > 0 ? (float) (Math.PI / 5) : 0.0F;
	}
}
