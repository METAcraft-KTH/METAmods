package metacraft.ovvar.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.danse.api.BodyLayerModels;
import de.tomalbrc.danse.util.MinecraftSkinParser;
import de.tomalbrc.danse.util.MinecraftSkinParser.BodyPart;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A body layer's model ({@code BodyLayerModels.shell}, our Danse fork) must show each texel on the
 * same cell of the stand-in as Danse's own pixel armour does, or an ovve on a gesturing player
 * would be scrambled. Danse's side is read with its own sampler; the shell's side is its UVs
 * sampled the way vanilla maps a face's [u1 v1 u2 v2] onto the face (north: u1 at +x, v1 at the
 * top; south: u1 at −x; east: u1 at +z; west: u1 at −z; up: u1 at −x, v1 at −z; down: u1 at −x,
 * v1 at +z). A test texture with a unique colour per texel makes every mismatch readable.
 */
public final class DanseShellParityTests {

	/** The per-pixel generator's face order; the tint indices run through it. */
	private static final List<Direction> MODEL_FACES = List.of(
			Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);

	private static final List<BodyPart> PARTS = List.of(
			BodyPart.HEAD, BodyPart.BODY, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG);

	@GameTest
	public void aShellShowsEveryTexelWhereDansesPixelsDo(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		BufferedImage texture = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) texture.setRGB(x, y, 0xFF000000 | (x << 8) | y);

		for (BodyPart part : PARTS) {
			List<Integer> danse = new ArrayList<>();
			for (Direction d : MinecraftSkinParser.DIRECTIONS) {
				MinecraftSkinParser.extractTextureRGB(texture, MinecraftSkinParser.NOTCH_TEXTURE_MAP, part,
						MinecraftSkinParser.Layer.INNER, d, c -> danse.add(c.color() & 0xFFFFFF));
			}
			JsonObject faces = faces(BodyLayerModels.shell(part, Identifier.parse("ovvar:test"), 0, 0f));
			Vec3i size = de.tomalbrc.danse.util.Util.sizeFor(part.getName());
			int px = size.getX(), py = size.getY(), pz = size.getZ();

			int base = 0;
			for (Direction face : MODEL_FACES) {
				// the generator's per-face index is i + j·a: N/S (x, y), E/W (y, z), U/D (x, z)
				int a = face.getAxis() == Direction.Axis.X ? py : px;
				int b = face.getAxis() == Direction.Axis.Z ? py : pz;
				float[] uv = uv(faces, face);
				for (int j = 0; j < b; j++) {
					for (int i = 0; i < a; i++) {
						double ni = (i + 0.5) / a, nj = (j + 0.5) / b;
						double s, t;   // fraction from u1 and from v1, vanilla's convention
						switch (face) {
							case NORTH -> { s = 1 - ni; t = 1 - nj; }      // i = x, j = y
							case SOUTH -> { s = ni; t = 1 - nj; }
							case EAST -> { s = 1 - nj; t = 1 - ni; }       // i = y, j = z
							case WEST -> { s = nj; t = 1 - ni; }
							case UP -> { s = ni; t = nj; }                 // i = x, j = z
							default -> { s = ni; t = 1 - nj; }             // DOWN
						}
						int col = (int) Math.floor((uv[0] + s * (uv[2] - uv[0])) * 4);
						int row = (int) Math.floor((uv[1] + t * (uv[3] - uv[1])) * 2);
						int shell = texture.getRGB(col, row) & 0xFFFFFF;
						int pixel = danse.get(base + i + j * a);
						if (shell != pixel) {
							helper.fail(part + " " + face + " cell (" + i + "," + j + "): the shell shows texel ("
									+ col + "," + row + ") but Danse draws (" + (pixel >> 8) + "," + (pixel & 0xFF) + ")");
							return;
						}
					}
				}
				base += a * b;
			}
			if (base != danse.size()) {
				helper.fail(part + ": " + base + " cells on the model, " + danse.size() + " pixels from Danse");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * A shell over a texture cropped to the part's own rectangle ({@code BodyLayerModels.region})
	 * shows exactly what the full-layout shell shows: the crop is what keeps a pack of many patch
	 * layers from filling the items atlas with transparent 256×128 sheets.
	 */
	@GameTest
	public void aCroppedShellShowsWhatTheFullShellShows(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		BufferedImage full = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) full.setRGB(x, y, 0xFF000000 | (x << 8) | y);
		for (BodyPart part : PARTS) {
			for (int vShift : isLeft(part) ? new int[]{0, -16} : new int[]{0}) {
				int[] r = BodyLayerModels.region(part, vShift);
				BufferedImage crop = full.getSubimage(r[0], r[1], r[2], r[3]);
				JsonObject whole = faces(BodyLayerModels.shell(part, Identifier.parse("ovvar:test"), vShift, 0f));
				JsonObject cropped = faces(BodyLayerModels.shell(part, Identifier.parse("ovvar:test"), vShift, 0f, r));
				for (Direction face : MODEL_FACES) {
					// off texel edges: a face is 3 to 12 texels wide, and none of these land on a boundary
					for (double s : new double[]{0.13, 0.52, 0.87}) {
						for (double t : new double[]{0.13, 0.52, 0.87}) {
							int a = sample(full, uv(whole, face), s, t), b = sample(crop, uv(cropped, face), s, t);
							if (a != b) {
								helper.fail(part + " vShift " + vShift + " " + face + " at (" + s + "," + t + "): full shell "
										+ Integer.toHexString(a) + ", cropped shell " + Integer.toHexString(b));
								return;
							}
						}
					}
				}
			}
		}
		helper.succeed();
	}

	/** The texel a face shows at fraction (s, t) from its u1/v1 corner; UVs are 0–16 over the whole image. */
	private static int sample(BufferedImage image, float[] uv, double s, double t) {
		int col = (int) Math.floor((uv[0] + s * (uv[2] - uv[0])) / 16 * image.getWidth());
		int row = (int) Math.floor((uv[1] + t * (uv[3] - uv[1])) / 16 * image.getHeight());
		return image.getRGB(col, row) & 0xFFFFFF;
	}

	private static boolean isLeft(BodyPart part) {
		return part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG;
	}

	@GameTest
	public void vShiftMovesEveryFaceUpByThatManyTexels(GameTestHelper helper) {
		if (!danse()) { helper.succeed(); return; }
		JsonObject plain = faces(BodyLayerModels.shell(BodyPart.LEFT_ARM, Identifier.parse("ovvar:test"), 0, 0f));
		JsonObject up = faces(BodyLayerModels.shell(BodyPart.LEFT_ARM, Identifier.parse("ovvar:test"), -16, 0f));
		for (Direction face : MODEL_FACES) {
			float[] a = uv(plain, face), b = uv(up, face);
			// 16 skin texels of a 32-texel-tall layout = 8 of the 16 UV units
			if (a[0] != b[0] || a[2] != b[2] || a[1] - 8 != b[1] || a[3] - 8 != b[3]) {
				helper.fail(face + ": vShift −16 gave " + java.util.Arrays.toString(b) + " from " + java.util.Arrays.toString(a));
				return;
			}
		}
		helper.succeed();
	}

	private static JsonObject faces(String model) {
		return JsonParser.parseString(model).getAsJsonObject().getAsJsonArray("elements")
				.get(0).getAsJsonObject().getAsJsonObject("faces");
	}

	private static float[] uv(JsonObject faces, Direction face) {
		JsonArray uv = faces.getAsJsonObject(face.getName()).getAsJsonArray("uv");
		return new float[]{uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat()};
	}

	private static boolean danse() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("danse");
	}
}
