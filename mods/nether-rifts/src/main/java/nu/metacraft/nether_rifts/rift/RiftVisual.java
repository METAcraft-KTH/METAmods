package nu.metacraft.nether_rifts.rift;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRifts;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A rift's cracks, over the nether portal clients see in its blocks: one item display per merged panel and one
 * thin strip per outline run, all paper with a rift model dyed with a shader marker (#FE, stage, part). Each
 * model has a face either side of the portal's slab, just clear of it; the shared item shader in metacraft-shaders
 * draws them. A client whose core shaders are replaced sees the fallback sprites: the panel's is see-through
 * there, so it sees the plain portal with the edge strips round it.
 */
public final class RiftVisual extends ElementHolder {

	static final int BODY = 0xFA;
	static final int EDGE = 0xF9;
	private static final float EDGE_WIDTH = 0.25f;
	private static final float CLOSED = 0.01f;
	private static final int OPEN_TICKS = 8;
	private static final int PULSE_TICKS = 3;

	private record Piece(ItemDisplayElement display, int part, Vector3f scale) {}

	private final List<Piece> pieces = new ArrayList<>();
	private final List<BlockPos> blocks;
	private Stage stage;
	private int age = 0;
	private int pulseEndsAt = -1;

	private RiftVisual(RiftShape shape, Stage stage) {
		this.stage = stage;
		this.blocks = List.copyOf(shape.blocks());
		var axis = shape.axis();
		var origin = shape.centre();
		var any = shape.blocks().iterator().next();
		int plane = axis == Direction.Axis.X ? any.getZ() : any.getX();
		var turn = axis == Direction.Axis.X ? new Quaternionf() : new Quaternionf().rotateY(Mth.HALF_PI);
		var cells = PanelMerger.cells(shape);
		for (var r : PanelMerger.rectangles(cells)) {
			var centre = world(axis, plane, r.u0() + r.width() / 2f, r.v0() + r.height() / 2f);
			add(BODY, centre.subtract(origin), turn, new Vector3f(r.width(), r.height(), 1));
		}
		for (var e : PanelMerger.edges(cells)) {
			float mid = (e.from() + e.to()) / 2f;
			float length = e.to() - e.from();
			if (e.alongU()) {
				add(EDGE, world(axis, plane, mid, e.line()).subtract(origin), turn, new Vector3f(length, EDGE_WIDTH, 1));
			} else {
				var upright = new Quaternionf(turn).rotateZ(Mth.HALF_PI);
				add(EDGE, world(axis, plane, e.line(), mid).subtract(origin), upright, new Vector3f(length, EDGE_WIDTH, 1));
			}
		}
	}

	public static RiftVisual attach(ServerLevel level, Rift rift) {
		var visual = new RiftVisual(rift.shape(), rift.stage());
		ChunkAttachment.ofTicking(visual, level, rift.centre());
		return visual;
	}

	public int elementCount() {
		return pieces.size();
	}

	public void setStage(Stage stage) {
		this.stage = stage;
		for (var piece : pieces) {
			piece.display.setItem(item(piece.part, stage));
			scaleTo(piece.display, new Vector3f(piece.scale).mul(1.1f, 1.1f, 1f), PULSE_TICKS);
		}
		pulseEndsAt = age + PULSE_TICKS;
	}

	private static Vec3 world(Direction.Axis axis, int plane, float u, float v) {
		return axis == Direction.Axis.X ? new Vec3(u, v, plane + 0.5) : new Vec3(plane + 0.5, v, u);
	}

	private void add(int part, Vec3 offset, Quaternionf turn, Vector3f scale) {
		var display = new ItemDisplayElement(item(part, stage));
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setBrightness(new Brightness(15, 15));
		display.setOffset(offset);
		display.setLeftRotation(turn);
		display.setScale(new Vector3f(CLOSED, CLOSED, CLOSED));
		display.setViewRange(3f);
		addElement(display);
		pieces.add(new Piece(display, part, scale));
	}

	private static ItemStack item(int part, Stage stage) {
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, NetherRifts.getID(part == BODY ? "rift_panel" : "rift_edge"));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFE0000 | stage.marker() << 8 | part));
		return stack;
	}

	private static void scaleTo(ItemDisplayElement display, Vector3f scale, int ticks) {
		display.setInterpolationDuration(ticks);
		display.setStartInterpolation(0);
		display.setScale(scale);
	}

	@Override
	protected void onTick() {
		age++;
		if (age == 1 || age == pulseEndsAt) {
			for (var piece : pieces) {
				scaleTo(piece.display, piece.scale, age == 1 ? OPEN_TICKS : PULSE_TICKS);
			}
		}
		var attachment = getAttachment();
		if (attachment == null || !(attachment.getWorld() instanceof ServerLevel level)) {
			return;
		}
		// Clients make the portal's own particles; past stage 1 the rift adds soul fire, and ash once it's torn.
		if (age % 4 == 0 && stage != Stage.LIT) {
			int count = stage.raiding() ? 6 : 3;
			var random = level.getRandom();
			for (int i = 0; i < count; i++) {
				var at = Vec3.atCenterOf(blocks.get(random.nextInt(blocks.size())));
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.01);
				if (stage.raiding()) {
					level.sendParticles(ParticleTypes.ASH, at.x, at.y, at.z, 2, 0.6, 0.6, 0.6, 0.0);
				}
			}
		}
		if (age % 80 == 0) {
			var c = attachment.getPos();
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.4f, 0.6f);
		}
	}
}
