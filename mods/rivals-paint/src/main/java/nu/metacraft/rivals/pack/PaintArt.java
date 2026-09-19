package nu.metacraft.rivals.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import nu.metacraft.rivals.PaintColor;
import nu.metacraft.rivals.Rivals;
import nu.metacraft.rivals.paint.PaintStates;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Paint art for connected paint (spec §5). No edge tiles: the shader draws the border. Per colour
 * sixteen 16×16 textures of one flat colour, the four connection bits in the low nibble of red and
 * the paint marker in alpha; uniform sprites survive mipmapping exactly, which is what makes reading
 * bits back out of a texel safe. Six one-quad models (one per attach direction), and a variants
 * blockstate for each donor block mapping every client state in use to its model and texture; donor
 * states we do not use — the waterlogged multiface ones, the powered redstone wire, the pale moss
 * carpet's own base — point at the empty model, so they draw nothing at all.
 *
 * <p>Each quad's {@code uv} is flipped per face so the sprite's u and v axes line up with the paint's
 * own in-plane axes: the gloss shader takes its in-face coordinate from the sprite (a block display has no
 * world position to read), so the UVs are what tell it which side of a cell is which. See {@link #uv}.
 *
 * <p>A variants file cannot do multipart, so the two kinds of client state take two kinds of model: a
 * connected cell gets a wrapper that hangs its (colour, bits) texture on one of the six shared face
 * quads, and a splat mask gets one model listing a quad per painted face, all on the all-connected
 * texture — the splat fallback has no neighbours to speak of anyway.
 */
public final class PaintArt {
	public static final int SIZE = 16;
	/** Connection-bit patterns per colour: one texture (and one wrapper per face) each. */
	public static final int BITS = 16;
	/**
	 * The alpha every paint texel carries, and nothing else does: the marker the gloss shaders key on.
	 * No texture in either atlas an item pipeline draws (blocks, items) and no chunk texture has an alpha
	 * in the 233..237 the shaders' window admits — the nearest that exist are 232 (nether_portal) and 238
	 * (frosted_ice), neither of them in a paint-carrying atlas.
	 */
	public static final int PAINT_ALPHA = 235;
	private static final Direction[] DIRECTIONS = Direction.values();
	/** How far off the attach face a quad sits, in sixteenths, as in vanilla's own multiface models. */
	private static final double OFFSET = 0.1;
	private static final int BLOCK_FACE_COUNT = 6;

	private PaintArt() {}

	public static String textureName(PaintColor color, int bits) {
		return "paint_" + color.id + "_" + bits;
	}

	public static String modelName(Direction attach) {
		return "paint_face_" + attach.getSerializedName();
	}

	/** The wrapper model that hangs one colour's (bits) texture on the face quad for {@code attach}. */
	public static String wrapperName(PaintColor color, int bits, Direction attach) {
		return textureName(color, bits) + "_" + attach.getSerializedName();
	}

	/** The model for a splat's whole face mask: a quad per painted face, all on the all-connected texture. */
	public static String maskName(PaintColor color, int faceMask) {
		return "paint_" + color.id + "_mask" + faceMask;
	}

	/** The red channel with the bits in its low nibble. */
	public static int encodeRed(int rgb, int bits) {
		return (rgb >> 16 & 0xF0) | (bits & 15);
	}

	public static Map<String, byte[]> packFiles() {
		Map<String, byte[]> files = new LinkedHashMap<>();
		for (PaintColor color : PaintColor.values()) {
			for (int bits = 0; bits < 16; bits++) {
				files.put("assets/" + Rivals.MOD_ID + "/textures/block/" + textureName(color, bits) + ".png", uniform(color.rgb, bits));
			}
		}
		for (Direction attach : DIRECTIONS) {
			files.put("assets/" + Rivals.MOD_ID + "/models/block/" + modelName(attach) + ".json", json(faceModel(attach)));
		}
		for (PaintColor color : PaintColor.values()) {
			for (int bits = 0; bits < 16; bits++) {
				for (Direction attach : DIRECTIONS) {
					files.put("assets/" + Rivals.MOD_ID + "/models/block/" + wrapperName(color, bits, attach) + ".json",
							json(wrapperModel(color, bits, attach)));
				}
			}
			// The masks paint actually hands out: every face combination with at least two faces.
			for (int mask = 1; mask < 1 << BLOCK_FACE_COUNT; mask++) {
				if (Integer.bitCount(mask) < 2) continue;
				files.put("assets/" + Rivals.MOD_ID + "/models/block/" + maskName(color, mask) + ".json", json(maskModel(color, mask)));
			}
		}
		files.put("assets/" + Rivals.MOD_ID + "/models/block/paint_none.json", json(emptyModel()));
		for (Block donor : PaintStates.DONORS) {
			files.put("assets/minecraft/blockstates/" + BuiltInRegistries.BLOCK.getKey(donor).getPath() + ".json",
					blockstate(donor).getBytes(StandardCharsets.UTF_8));
		}
		return files;
	}

	/** One flat colour over the whole tile: the marker in alpha, the bits in red, the colour in the rest. */
	static byte[] uniform(int rgb, int bits) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		int argb = PAINT_ALPHA << 24 | encodeRed(rgb, bits) << 16 | (rgb & 0xFFFF);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) image.setRGB(x, y, argb);
		}
		return png(image);
	}

	/** PNG bytes: the one encoder the paint art uses. */
	static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", out)) throw new IOException("no PNG writer available");
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("could not encode paint art", e);
		}
	}

	/** One paper-thin element against the attach face, both sides textured with {@code #paint}, no tint. */
	static JsonObject element(Direction attach) {
		double plane = attach.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? OFFSET : SIZE - OFFSET;
		Direction.Axis axis = attach.getAxis();
		JsonObject element = new JsonObject();
		element.add("from", corner(axis, plane, 0));
		element.add("to", corner(axis, plane, SIZE));
		JsonObject faces = new JsonObject();
		for (Direction side : sides(axis)) faces.add(side.getSerializedName(), face(side));
		element.add("faces", faces);
		return element;
	}

	/** A corner of the quad: {@code plane} on the attach axis, {@code span} on the other two. */
	private static JsonArray corner(Direction.Axis axis, double plane, double span) {
		JsonArray corner = new JsonArray();
		for (Direction.Axis a : Direction.Axis.values()) corner.add(number(a == axis ? plane : span));
		return corner;
	}

	/** The two sides of a quad on {@code axis}: both of the axis's directions, so the paint is visible from either. */
	private static Direction[] sides(Direction.Axis axis) {
		return switch (axis) {
			case X -> new Direction[] {Direction.WEST, Direction.EAST};
			case Y -> new Direction[] {Direction.UP, Direction.DOWN};
			case Z -> new Direction[] {Direction.NORTH, Direction.SOUTH};
		};
	}

	private static JsonObject face(Direction side) {
		JsonArray uv = new JsonArray();
		for (int value : uv(side)) uv.add(value);
		JsonObject face = new JsonObject();
		face.add("uv", uv);
		face.addProperty("texture", "#paint");
		return face;
	}

	/**
	 * The {@code uv} array for one face, flipped so that the sprite's own u axis runs along the paint's
	 * +u and its v axis along the paint's +v — the axes
	 * {@link nu.metacraft.rivals.paint.ConnectedPaintBlock#inPlane} names the connection bits after (an
	 * attach on Y: u = +x, v = +z; on X: u = +z, v = +y; on Z: u = +x, v = +y). The gloss shader reads its
	 * in-face coordinate off the sprite, so this table <em>is</em> the orientation: get it wrong and a
	 * cell's border opens on the wrong side.
	 *
	 * <p>Vanilla's own convention, from the 26.3 client: {@code CuboidFace.UVs.getVertexU/V} give vertex 0
	 * (minU, minV), 1 (minU, maxV), 2 (maxU, maxV), 3 (maxU, minV) — straight out of the {@code uv} array,
	 * with no sorting — and {@code FaceInfo}'s per-facing vertex table puts those four corners at
	 * particular box extents. Reading the two together, u and v run along:
	 *
	 * <ul>
	 * <li>{@code up}: u = +x, v = +z — already the paint's own axes, so no flip;</li>
	 * <li>{@code down}: u = +x, v = −z — flip v;</li>
	 * <li>{@code north}: u = −x, v = −y — flip both;</li>
	 * <li>{@code south}: u = +x, v = −y — flip v;</li>
	 * <li>{@code west}: u = +z, v = −y — flip v;</li>
	 * <li>{@code east}: u = −z, v = −y — flip both.</li>
	 * </ul>
	 */
	public static int[] uv(Direction side) {
		return switch (side) {
			case UP -> new int[] {0, 0, SIZE, SIZE};
			case DOWN, SOUTH, WEST -> new int[] {0, SIZE, SIZE, 0};
			case NORTH, EAST -> new int[] {SIZE, SIZE, 0, 0};
		};
	}

	/** Whole sixteenths as integers, so the JSON reads like vanilla's own models. */
	private static Number number(double value) {
		return value == Math.rint(value) ? (Number) (int) value : (Number) value;
	}

	/** The shared quad for one attach direction; the wrapper models fill in {@code #paint}. */
	static JsonObject faceModel(Direction attach) {
		JsonObject model = new JsonObject();
		model.addProperty("ambientocclusion", false);
		JsonArray elements = new JsonArray();
		elements.add(element(attach));
		model.add("elements", elements);
		return model;
	}

	static JsonObject wrapperModel(PaintColor color, int bits, Direction attach) {
		JsonObject model = new JsonObject();
		model.addProperty("parent", Rivals.MOD_ID + ":block/" + modelName(attach));
		model.add("textures", textures(color, bits));
		return model;
	}

	static JsonObject maskModel(PaintColor color, int faceMask) {
		JsonObject model = new JsonObject();
		model.addProperty("ambientocclusion", false);
		model.add("textures", textures(color, 15));
		JsonArray elements = new JsonArray();
		for (Direction attach : DIRECTIONS) {
			if ((faceMask & 1 << attach.ordinal()) != 0) elements.add(element(attach));
		}
		model.add("elements", elements);
		return model;
	}

	/**
	 * The empty model a donor state paint does not use points at: no geometry, and a {@code particle}
	 * only because every model needs one — vanilla gives even {@code block/air.json} a particle texture,
	 * and a model without one is a "missing texture references" line in the client's log on every join.
	 *
	 * <p>The particle texture is borrowed from the first colour's all-connected paint, so the one place
	 * it shows is the one place an unused donor state can make particles: breaking a stray waterlogged
	 * sculk vein or a redstone dust a player placed in an arena puffs that colour's paint crumbs. A
	 * harmless tell, and a cheaper one than shipping a texture nothing else uses.
	 */
	static JsonObject emptyModel() {
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", Rivals.MOD_ID + ":block/" + textureName(PaintColor.values()[0], 15));
		JsonObject model = new JsonObject();
		model.add("textures", textures);
		model.add("elements", new JsonArray());
		return model;
	}

	/** The texture slots of a paint model: {@code paint} for the quads, {@code particle} for everything else. */
	private static JsonObject textures(PaintColor color, int bits) {
		String texture = Rivals.MOD_ID + ":block/" + textureName(color, bits);
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", texture);
		textures.addProperty("paint", texture);
		return textures;
	}

	/** The variant key of a client state, e.g. "attached=false,disarmed=false,east=true,north=false,powered=false,south=false,west=true". */
	public static String variantKey(BlockState state) {
		return state.getProperties().stream().sorted(Comparator.comparing(Property::getName))
				.map(p -> p.getName() + "=" + valueName(state, p)).collect(Collectors.joining(","));
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/**
	 * A donor's whole blockstate file. Every state of the block gets a variant — the ones paint hands
	 * out point at their paint model, the rest at the empty one — because a variants file the client
	 * cannot match against a state it is shown is a missing-model cube.
	 */
	static String blockstate(Block donor) {
		Set<BlockState> used = new HashSet<>(PaintStates.all());
		JsonObject variants = new JsonObject();
		for (BlockState state : donor.getStateDefinition().getPossibleStates()) {
			JsonObject variant = new JsonObject();
			variant.addProperty("model", Rivals.MOD_ID + ":block/" + modelFor(state, used));
			variants.add(variantKey(state), variant);
		}
		JsonObject root = new JsonObject();
		root.add("variants", variants);
		return root.toString();
	}

	private static String modelFor(BlockState state, Set<BlockState> used) {
		if (!used.contains(state)) return "paint_none";
		PaintStates.Entry entry = PaintStates.entry(state);
		Direction face = entry.face();
		return face != null ? wrapperName(entry.color(), entry.bits(), face) : maskName(entry.color(), entry.faceMask());
	}

	private static byte[] json(JsonObject model) {
		return model.toString().getBytes(StandardCharsets.UTF_8);
	}
}
