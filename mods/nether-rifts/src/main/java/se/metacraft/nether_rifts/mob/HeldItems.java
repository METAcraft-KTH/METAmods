package se.metacraft.nether_rifts.mob;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.component.AnimationComponent;
import de.tomalbrc.bil.core.element.PerPlayerItemDisplayElement;
import de.tomalbrc.bil.core.holder.base.AbstractAnimationHolder;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import de.tomalbrc.bil.core.holder.wrapper.DisplayWrapper;
import de.tomalbrc.bil.core.holder.wrapper.Locator;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.core.model.Pose;
import de.tomalbrc.bil.util.Utils;
import eu.pb4.polymer.virtualentity.api.data.InteractionEntityData;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows what a BIL mob holds in its hands and wears on its head, as a vanilla mob does: BIL draws no equipment. The
 * model marks the spots with empty groups (no cubes), as Blockbench's modded entity format has no locators:
 * <ul>
 * <li>{@code item_right}, {@code item_left}: inside the right / left arm bone, at the bottom-centre of the fist (the
 * middle of the arm's bottom face). The item is held 2 px in front of that, where vanilla holds it on its 4 px deep
 * arms. The main hand is on the right arm and the off hand on the left, the other way round for a left-handed mob.
 * (A model may put them elsewhere: the Chained Ghast's artist put item_right in the spear piglin's left fist and
 * item_left on the crossbow piglin's body.)
 * <li>{@code item_head}: inside the head bone, at the centre of the head (the middle of the head cube: 4 px above
 * the neck for an 8 px head). Vanilla draws head items (carved pumpkins, blocks, banners) about that point.
 * </ul>
 * A marker's rotation at 0 holds the item as a vanilla mob does with its arm hanging straight down (the item's
 * third-person hand view), or wears it as a vanilla mob does (its head view, at vanilla's 0.625 scale); the group's
 * rotation turns the item from there, about the marker. A missing marker leaves that slot unshown.
 *
 * <p>BIL keeps an empty group as a model node, but makes it no bone and gives it no scale (0: its poses keep their
 * place but lose their turn), so a marker's pose is worked out from its bone's: the bone's pose in the playing
 * animation, then the marker's rest place and rotation in it. Animating a marker itself does nothing. Each marker gets
 * an item display, posed through the holder as BIL poses its bones (body yaw, head look for what's in the head bone,
 * death tilt, scale), so it moves with its bone. A worn helmet isn't shown here: vanilla draws that as armour fitted
 * to the head, not as an item, and so does {@link ArmourPieces}. Skulls show as their item model, not vanilla's skull.
 */
public final class HeldItems {

	public static final String RIGHT = "item_right";
	public static final String LEFT = "item_left";
	public static final String HEAD = "item_head";

	/**
	 * From a hand marker to the item: vanilla's ItemInHandLayer (2 px forward, then the arm's turn to the hand view),
	 * less the half turn an item display gives its item.
	 */
	private static final Matrix4fc IN_HAND = new Matrix4f().translate(0, 0, -2 / 16f).rotateZ(Mth.PI).rotateX(-Mth.HALF_PI);
	/** From the head marker to the item: vanilla's CustomHeadLayer (0.625 scale), less the item display's half turn. */
	private static final Matrix4fc ON_HEAD = new Matrix4f().rotateY(Mth.PI).scale(0.625f);

	/** The slots shown, and their markers. */
	public enum Spot {
		RIGHT_HAND(RIGHT, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, IN_HAND),
		LEFT_HAND(LEFT, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, IN_HAND),
		HEAD(HeldItems.HEAD, ItemDisplayContext.HEAD, ON_HEAD);

		public final String marker;
		final ItemDisplayContext context;
		final Matrix4fc offset;

		Spot(String marker, ItemDisplayContext context, Matrix4fc offset) {
			this.marker = marker;
			this.context = context;
			this.offset = offset;
		}

		/** What the mob shows there: worn armour isn't drawn as an item. */
		ItemStack stack(LivingEntity mob) {
			return switch (this) {
				case RIGHT_HAND -> mob.getItemHeldByArm(HumanoidArm.RIGHT);
				case LEFT_HAND -> mob.getItemHeldByArm(HumanoidArm.LEFT);
				case HEAD -> {
					var stack = mob.getItemBySlot(EquipmentSlot.HEAD);
					yield ArmourPieces.isWorn(stack, EquipmentSlot.HEAD) ? ItemStack.EMPTY : stack;
				}
			};
		}
	}

	/** One marker's display, the bone it's on, and what it last showed. */
	private static final class Shown {
		final Spot spot;
		final DisplayWrapper<PerPlayerItemDisplayElement> display;
		/** Tracks the bone's pose in the animations; null on a model whose groups above the marker are all empty. */
		@Nullable final Locator bone;
		ItemStack stack = ItemStack.EMPTY;
		@Nullable Pose bonePose;
		@Nullable Pose itemPose;

		Shown(Spot spot, DisplayWrapper<PerPlayerItemDisplayElement> display, @Nullable Locator bone) {
			this.spot = spot;
			this.display = display;
			this.bone = bone;
		}
	}

	private final LivingEntityHolder<?> holder;
	private final LivingEntity mob;
	private final List<Shown> shown = new ArrayList<>();

	/** Adds a display to the holder for each marker its model has; none without markers. */
	public HeldItems(LivingEntityHolder<?> holder) {
		this.holder = holder;
		this.mob = holder.getParent();
		var model = holder.getModel();
		for (var spot : Spot.values()) {
			var node = marker(model, spot.marker);
			if (node == null) {
				continue;
			}
			var element = new PerPlayerItemDisplayElement(ItemStack.EMPTY);
			// as BIL's bones, so it moves in step with them
			element.setInterpolationDuration(2);
			element.setTeleportDuration(2);
			element.setItemDisplayContext(spot.context);
			var bone = bone(node);
			shown.add(new Shown(spot, new DisplayWrapper<>(element, node, model.defaultPose().get(node.uuid())),
					bone == null ? null : Locator.of(bone, model.defaultPose().get(bone.uuid()))));
			holder.addAdditionalDisplay(element);
		}
	}

	/** The model's node named {@code name}, or null. */
	@Nullable
	public static Node marker(Model model, String name) {
		for (var node : model.nodeMap().values()) {
			if (node.name().equals(name)) {
				return node;
			}
		}
		return null;
	}

	/** The group a marker is on: the nearest above it that BIL gives a scale (one with cubes), or null. */
	@Nullable
	public static Node bone(Node marker) {
		var node = marker.parent();
		while (node != null && node.transform().localScale() == 0) {
			node = node.parent();
		}
		return node;
	}

	/**
	 * A marker's pose in model space, from its bone's ({@link #bone}; null for none): the bone's pose without the scale
	 * BIL fits its model with, then each group's rest place and rotation down to the marker, as BIL chains them.
	 */
	public static Matrix4f markerPose(Node marker, @Nullable Matrix4fc bonePose) {
		var bone = bone(marker);
		var path = new ArrayList<Node>();
		for (var node = marker; node != bone; node = node.parent()) {
			path.addFirst(node);
		}
		// BIL turns a model's root half round
		var pose = bone == null ? new Matrix4f().rotateY(Mth.PI) : new Matrix4f(bonePose).scale(1 / bone.transform().localScale());
		for (var node : path) {
			pose.translate(node.transform().origin()).rotate(Utils.createQuaternion(node.transform().rotation()));
		}
		return pose;
	}

	/** The item's pose in model space, from its marker's. */
	public static Matrix4f itemTransform(Matrix4fc markerPose, Spot spot) {
		return new Matrix4f(markerPose).mul(spot.offset);
	}

	/** Whether the model has no markers, so nothing is shown. */
	public boolean isEmpty() {
		return shown.isEmpty();
	}

	/** The display showing a spot's item, or null without its marker. */
	@Nullable
	public ItemDisplayElement display(Spot spot) {
		for (var s : shown) {
			if (s.spot == spot) {
				return s.display.element();
			}
		}
		return null;
	}

	/**
	 * Shows the mob's items where its markers are now. Call it each tick after the animations advance (the holder's
	 * {@code onTick}, as {@link Holder} does), so the items move with this tick's bones. Changes only are sent.
	 */
	public void tick() {
		if (shown.isEmpty()) {
			return;
		}
		var animator = (AnimationComponent) holder.getAnimator();
		for (var s : shown) {
			var stack = s.spot.stack(mob);
			if (!ItemStack.matches(stack, s.stack)) {
				s.stack = stack.copy();
				s.display.element().setItem(s.stack.copy());
			}
			// the bone's pose in the playing animation (BIL gives a .bbmodel's animations every node), else its last
			Pose bonePose = null;
			if (s.bone != null) {
				var found = animator.findPose(null, s.bone);
				bonePose = found != null && found.pose() != null ? found.pose() : s.bone.getLastPose(null);
			}
			if (bonePose != s.bonePose || s.itemPose == null) {
				s.bonePose = bonePose;
				s.itemPose = Pose.of(itemTransform(markerPose(s.display.node(), bonePose == null ? null : bonePose.matrix()), s.spot));
			}
			var pose = s.itemPose;
			if (holder.getScale() != 1) {
				// BIL scales the pose's own scale vector in place
				pose = new Pose(new Vector3f(pose.translation()), new Vector3f(pose.scale()), new Quaternionf(pose.leftRotation()),
						new Quaternionf(pose.rightRotation()), pose.matrix());
			}
			holder.updateElement(null, s.display, pose);
		}
	}

	/**
	 * Sets every display of a holder (bones, held items, armour) to be seen as far as the mob is tracked. A display's
	 * view range is in units of 64 blocks (scaled by the client's entity distance), and BIL leaves its bones at 1: the
	 * mob would vanish at 64 blocks though clients keep it to its type's tracking range.
	 */
	public static void setViewRange(AbstractAnimationHolder holder, EntityType<?> type) {
		float range = viewRange(type);
		for (var element : holder.getElements()) {
			if (element instanceof DisplayElement display) {
				display.setViewRange(range);
			}
		}
	}

	/** The view range that covers a type's tracking range: at least vanilla's 1. */
	public static float viewRange(EntityType<?> type) {
		return Math.max(1f, type.clientTrackingRange() * 16 / 64f);
	}

	/**
	 * A living entity holder that shows its mob's held items ({@link HeldItems}) and worn armour ({@link ArmourPieces});
	 * use it in place of LivingEntityHolder. Polymer ticks it from the entity tracker, after every mob has ticked, so the
	 * armour's colours come after the mob's hurt tint.
	 *
	 * <p>Clients hit the mob's {@link HitStandIn}, so the crosshair shows its attack indicator over it as over a vanilla
	 * mob. BIL's interaction goes to them zero wide, never picked, but keeps its height: the bones ride on top of it, and
	 * the mob's name shows over it.
	 */
	public static class Holder<T extends LivingEntity & AnimatedEntity> extends LivingEntityHolder<T> {

		private final HeldItems heldItems = new HeldItems(this);
		private final ArmourPieces armour = new ArmourPieces(this);
		private final HitStandIn hitStandIn;

		public Holder(T parent, Model model) {
			super(parent, model);
			setViewRange(this, parent.getType());
			hitboxInteraction.setWidth(0);
			hitStandIn = HitStandIn.attach(parent);
		}

		public HitStandIn hitStandIn() {
			return hitStandIn;
		}

		// BIL sends the interaction the mob's size on both: it goes out zero wide again after

		@Override
		public void onDimensionsUpdated(EntityDimensions dimensions) {
			super.onDimensionsUpdated(dimensions);
			sendPacket(unpickable());
			hitStandIn.resize(dimensions.height());
		}

		@Override
		protected void startWatchingExtraPackets(ServerGamePacketListenerImpl player, Consumer<Packet<ClientGamePacketListener>> packetConsumer) {
			super.startWatchingExtraPackets(player, packetConsumer);
			packetConsumer.accept(unpickable());
		}

		/**
		 * BIL sets its zero-wide interaction alight, and a client draws flames over a zero-wide entity without end
		 * (a crash): the stand-in burns instead.
		 */
		@Override
		protected void updateOnFire(boolean onFire) {
		}

		private ClientboundSetEntityDataPacket unpickable() {
			return new ClientboundSetEntityDataPacket(hitboxInteraction.getEntityId(), List.of(SynchedEntityData.DataValue.create(InteractionEntityData.WIDTH, 0f)));
		}

		public HeldItems heldItems() {
			return heldItems;
		}

		public ArmourPieces armour() {
			return armour;
		}

		@Override
		protected void onTick() {
			super.onTick();
			heldItems.tick();
			armour.tick();
		}
	}
}
