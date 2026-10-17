package nu.metacraft.qol.void_anchor.rift;

import nu.metacraft.qol.Qol;
import nu.metacraft.qol.void_anchor.VoidAnchorConfig;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A crack in space hanging in the air, made of item displays showing the pack's rift models. What
 * they look like comes from the pack (the rift sprites and the item shader); the dyed colour of
 * each display's item tells the shader which part it is.
 *
 * <p>Every style has the main crack: lying flat under the player, the End's void showing through
 * it. It opens the way a crack does, first running out along its length as a thin line, then
 * prying open; it shuts by shrinking away. No two look quite alike: each picks one of the pack's
 * crack shapes, lies at its own angle, may be mirrored, and is a little longer or wider than the
 * last.
 *
 * <p>{@link RiftStyle#SHATTER} adds, below the crack, a bright core and glowing cracks running out
 * from its edge, all facing whoever looks at them, shooting out one after
 * another before the main crack pries open, as if space broke like glass; light (portal particles)
 * is pulled into the core.
 */
public final class Rift extends ElementHolder {

	/** A crack runs out along its length... */
	public static final int RUN_TICKS = 5;
	/** ...then pries open. */
	public static final int PRY_TICKS = 6;
	public static final int CLOSE_TICKS = 6;
	/** How many crack shapes the pack has: {@code metacraft:rift_0} and on, drawn by tools/gen_textures.py. */
	public static final int VARIANTS = 8;

	/** The dyed colours the item shader reads: the main crack, a glowing crack (the core's is fixed in its model; see item.vsh). */
	static final int VOID_CRACK = 0xFEFEFD;
	static final int GLOW_CRACK = 0xFEFEFC;

	private static final float CLOSED = 0.01f;
	private static final float THIN = 0.08f;
	/** How tall the rift a rescued player steps out of is, in blocks. */
	private static final float EXIT_SIZE = 2.0f;
	/** How far below the crack the shatter rift's burst sits, in rift sizes. */
	private static final float BURST_DROP = 0.4f;
	/** How many ray shapes the pack has: {@code metacraft:rift_ray_0} and on. */
	public static final int RAY_VARIANTS = 4;
	/** The shatter rift's glowing cracks out from its core. */
	private static final int RAYS = 5;

	/** One display: from closed it runs out to `run` (if any), then opens to `open`, moving to `at` (if any) as it does. */
	private record Piece(ItemDisplayElement display, int start, Vector3f run, Vector3f open, Vector3f at) {
		Piece(ItemDisplayElement display, int start, Vector3f run, Vector3f open) {
			this(display, start, run, open, null);
		}
	}

	private final List<Piece> pieces = new ArrayList<>();
	private final float size;
	private final RiftStyle style;
	private int age = 0;
	private int closingSince = -1;
	private int autoCloseAt = -1;

	/** An empty rift, its one piece added by the caller. */
	private Rift(float size, RiftStyle style, boolean bare) {
		this.size = size;
		this.style = style;
	}

	private Rift(float size, RiftStyle style) {
		this.size = size;
		this.style = style;
		var random = ThreadLocalRandom.current();
		float length = size * (0.85f + 0.3f * random.nextFloat());
		float width = size * (0.8f + 0.4f * random.nextFloat());
		int mainStart = 1;
		if (style == RiftStyle.SHATTER) {
			float core = size * 0.35f;
			var stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Qol.getID("rift_core"));
			// The burst sits below the crack, so the crack stays in front of it for a player above
			// and its light comes out round the crack from underneath.
			var below = new Vec3(0, -size * BURST_DROP, 0);
			var display = display(stack, new Quaternionf());
			display.setBillboardMode(Display.BillboardConstraints.CENTER);
			display.setOffset(below);
			// a touch toward the viewer, over the rays' inner ends
			display.setTranslation(new Vector3f(0, 0, size * 0.05f));
			pieces.add(new Piece(display, 1, null, new Vector3f(core, core, core)));
			// Rays out from the core's edge, each its own way round it, so none lies over another
			// or over the core. Each faces the camera wherever it is, stood up from lying flat; it
			// grows out from the rim, its inner end staying there.
			// just under the core's rim, so the core hides each ray's cut-off inner end
			float rim = core * 0.2f;
			float turn = random.nextFloat() * Mth.TWO_PI;
			for (int i = 0; i < RAYS; i++) {
				float angle = turn + i * Mth.TWO_PI / RAYS + (random.nextFloat() - 0.5f) * 0.5f;
				var out = new Vector3f(Mth.cos(angle), Mth.sin(angle), 0);
				// half a turn round: facing the camera, the sprite's wide end (its left) comes out on the far side
				var ray = crack(GLOW_CRACK, "rift_ray_" + random.nextInt(RAY_VARIANTS), new Quaternionf().rotateZ(angle + Mth.PI));
				ray.setBillboardMode(Display.BillboardConstraints.CENTER);
				ray.setOffset(below);
				ray.setRightRotation(new Quaternionf().rotateX(Mth.HALF_PI));
				// a hair apart in depth, so where they meet at the rim they don't fight over which is in front
				ray.setTranslation(new Vector3f(out).mul(rim).add(0, 0, 0.01f * i));
				// shorter than the crack is long, so the crack stays the thing to look at
				float reach = size * (0.45f + 0.25f * random.nextFloat());
				var at = new Vector3f(out).mul(rim + reach / 2).add(0, 0, 0.01f * i);
				pieces.add(new Piece(ray, 2 + i, null, new Vector3f(reach, reach * 0.4f, 1f), at));
			}
			mainStart = 3 + RAYS;
		}
		// the main crack; turning it over mirrors it (the model is a flat quad with both faces, so it stays in place)
		var lie = new Quaternionf().rotateY(random.nextFloat() * Mth.TWO_PI);
		var main = crack(VOID_CRACK, random.nextBoolean() ? lie.rotateX(Mth.PI) : lie);
		pieces.add(new Piece(main, mainStart, new Vector3f(length, 1f, width * THIN), new Vector3f(length, 1f, width)));
	}

	private ItemDisplayElement crack(int part, Quaternionf turn) {
		return crack(part, "rift_" + ThreadLocalRandom.current().nextInt(VARIANTS), turn);
	}

	private ItemDisplayElement crack(int part, String model, Quaternionf turn) {
		var stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Qol.getID(model));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(part));
		return display(stack, turn);
	}

	private ItemDisplayElement display(ItemStack stack, Quaternionf turn) {
		var display = new ItemDisplayElement(stack);
		display.setItemDisplayContext(ItemDisplayContext.NONE);
		display.setBrightness(new Brightness(15, 15));
		display.setScale(new Vector3f(CLOSED, CLOSED, CLOSED));
		display.setLeftRotation(turn);
		display.setViewRange(4f);
		addElement(display);
		return display;
	}

	/**
	 * The rift a rescued player steps out of: a crack stood up on top of their anchor, facing where
	 * they arrive, already open by the time the white-out clears.
	 */
	public static Rift openExit(ServerLevel level, BlockPos anchor, Vec3 arrival) {
		var rift = new Rift(EXIT_SIZE, RiftStyle.CRACK, true);
		var centre = new Vec3(anchor.getX() + 0.5, anchor.getY() + 1 + EXIT_SIZE / 2, anchor.getZ() + 0.5);
		// stand the flat crack up (its length vertical), then turn it to face the arrival spot
		float face = (float) Mth.atan2(arrival.x - centre.x, arrival.z - centre.z);
		var standing = new Quaternionf().rotateY(face).rotateZ(Mth.HALF_PI).rotateX(Mth.HALF_PI);
		float width = EXIT_SIZE * 0.6f;
		rift.pieces.add(new Piece(rift.crack(VOID_CRACK, standing), 1,
				new Vector3f(EXIT_SIZE, 1f, width * THIN), new Vector3f(EXIT_SIZE, 1f, width)));
		ChunkAttachment.ofTicking(rift, level, centre);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
		return rift;
	}

	/** Opens a rift in the configured style. */
	public static Rift open(ServerLevel level, Vec3 centre, float size) {
		return open(level, centre, size, VoidAnchorConfig.getInstance().riftStyle());
	}

	public static Rift open(ServerLevel level, Vec3 centre, float size, RiftStyle style) {
		var rift = new Rift(size, style);
		ChunkAttachment.ofTicking(rift, level, centre);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5f, 1.6f);
		if (style == RiftStyle.SHATTER) {
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.5f);
		}
		return rift;
	}

	/** Shuts the rift by itself after this many more ticks. */
	public Rift closeAfter(int ticks) {
		autoCloseAt = age + ticks;
		return this;
	}

	public void close() {
		if (closingSince >= 0) {
			return;
		}
		closingSince = age;
		for (var piece : pieces) {
			scaleTo(piece.display, new Vector3f(CLOSED, CLOSED, CLOSED), CLOSE_TICKS);
		}
	}

	/** Ticks from opening until every piece is fully open. */
	public int openTicks() {
		return pieces.stream().mapToInt(p -> p.start + RUN_TICKS + (p.run != null ? PRY_TICKS : 0)).max().orElse(0);
	}

	public boolean isClosing() {
		return closingSince >= 0;
	}

	@Override
	protected void onTick() {
		age++;
		var attachment = getAttachment();
		if (closingSince < 0) {
			// The spawn packets carried the closed scale; each piece opens from it in its turn.
			for (var piece : pieces) {
				if (age == piece.start) {
					if (piece.at != null) {
						piece.display.setTranslation(piece.at);
					}
					scaleTo(piece.display, piece.run != null ? piece.run : piece.open, RUN_TICKS);
					if (style == RiftStyle.SHATTER && piece.run == null && piece.start > 1 && attachment != null) {
						var c = attachment.getPos();
						attachment.getWorld().playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS,
								0.7f, 0.6f + 0.25f * piece.start / 2);
					}
				} else if (piece.run != null && age == piece.start + RUN_TICKS) {
					scaleTo(piece.display, piece.open, PRY_TICKS);
				}
			}
		}
		if (age == autoCloseAt) {
			close();
		}
		if (attachment == null) {
			return;
		}
		if (closingSince < 0 && age % 2 == 0) {
			var c = attachment.getPos();
			if (style == RiftStyle.SHATTER) {
				// portal particles fly to where they were sent from: light pulled into the core
				attachment.getWorld().sendParticles(ParticleTypes.PORTAL, c.x, c.y - size * BURST_DROP, c.z, 10, 0, 0, 0, size * 0.35);
			} else {
				attachment.getWorld().sendParticles(
						ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.2, c.z, 6, size * 0.35, 0.1, size * 0.35, 0.02
				);
			}
		}
		if (closingSince >= 0 && age - closingSince >= CLOSE_TICKS) {
			destroy();
		}
	}

	private static void scaleTo(ItemDisplayElement display, Vector3f scale, int ticks) {
		display.setInterpolationDuration(ticks);
		display.setStartInterpolation(0);
		display.setScale(scale);
	}

}
