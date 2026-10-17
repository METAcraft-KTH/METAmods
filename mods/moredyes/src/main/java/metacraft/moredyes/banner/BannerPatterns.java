package metacraft.moredyes.banner;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import metacraft.moredyes.MoreDyes;
import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.color.ModColors;
import metacraft.moredyes.sign.SignColors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Banner patterns in our colours, for every pattern the server knows — vanilla or datapack — with
 * no build step.
 *
 * <p>How it works: the client renders a banner layer as {@code texture × dyeColour}, and white dye's
 * tint is {@code #F9FFFE}, so a layer applied with WHITE shows the texture's own colour. For each
 * source pattern {@code ns:path} and each of our colours we register a derived pattern
 * {@code moredyes:<color>/<ns>/<path>} while the dynamic registries load, and when Polymer builds
 * the resource pack we multiply the source mask by the colour and add the result under the derived
 * pattern's asset id. Applying a derived pattern with WHITE (what our dyes do in vanilla's loom,
 * {@link metacraft.moredyes.mixin.LoomMenuMixin}) is a banner in our colour that any vanilla client
 * renders correctly.
 *
 * <p>A banner "in" one of our colours (what our banner recipes make, see {@link #colouredBanner}) is
 * the same trick on vanilla's full-cover {@code minecraft:base}: a white banner whose first layer is
 * {@code moredyes:<color>/minecraft/base}, named for the colour. Vanilla's loom, banner copying and
 * shield decorating carry it like any layer; washing in a cauldron stops at it, as it stops at a
 * banner's own colour (see {@link metacraft.moredyes.mixin.CauldronInteractionsMixin}). Like a banner's own
 * colour it is not one of its patterns: such a banner takes six patterns on top (seven layers) where
 * vanilla stops at six layers ({@link #layerCap}).
 */
public final class BannerPatterns {
	/** derived pattern id -> (colour, source pattern id). Rebuilt on every registry setup. */
	private static final Map<Identifier, Derived> DERIVED = new ConcurrentHashMap<>();
	private static @Nullable MinecraftServer server;

	/** Vanilla's full-cover pattern, the one a banner's own colour is drawn with. */
	public static final Identifier BASE = Identifier.withDefaultNamespace("base");
	/** Patterns on top of a banner's colour, as vanilla allows on a banner of a vanilla colour. */
	public static final int MAX_PATTERNS = 6;

	public record Derived(ModColor color, Identifier source) {}

	private BannerPatterns() {}

	/** The name of a banner in this colour, which the generated lang file spells out. */
	public static String bannerNameKey(ModColor color) {
		return "block." + MoreDyes.MOD_ID + "." + color.id() + "_banner";
	}

	/**
	 * A banner in this colour, as the banner recipe makes it: a white banner under our full-cover
	 * layer, applied with WHITE so it shows in the colour, and named for it.
	 */
	public static ItemStack colouredBanner(ModColor color, HolderLookup.Provider registries) {
		Holder<BannerPattern> base = registries.lookupOrThrow(Registries.BANNER_PATTERN)
				.getOrThrow(ResourceKey.create(Registries.BANNER_PATTERN, derivedId(color, BASE)));
		ItemStack stack = new ItemStack(Items.BANNER.white());
		stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.of(new BannerPatternLayers.Layer(base, DyeColor.WHITE))));
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback(bannerNameKey(color), color.name() + " Banner"));
		return stack;
	}

	/** Whether this layer is one of our colours over the whole banner (a coloured banner's first layer). */
	public static boolean isColouredBase(BannerPatternLayers.Layer layer) {
		return baseColour(layer) != null;
	}

	/**
	 * The colour of our full-cover layer, or null for any other layer. Read off the pattern's id rather
	 * than {@link #derived}, which a client in the same JVM (singleplayer, client tests) rebuilds empty when
	 * it loads the server's registries.
	 */
	public static @Nullable ModColor baseColour(BannerPatternLayers.Layer layer) {
		Identifier id = layer.pattern().unwrapKey().map(ResourceKey::identifier).orElse(null);
		if (id == null || !id.getNamespace().equals(MoreDyes.MOD_ID)) return null;
		for (ModColor color : ModColors.all()) {
			if (id.equals(derivedId(color, BASE))) return color;
		}
		return null;
	}

	/**
	 * How many patterns a banner carries on top of its colour. A banner in one of our colours carries
	 * that colour as its first layer; like a vanilla banner's own colour, it is not a pattern.
	 */
	public static int patternsOnTop(BannerPatternLayers layers) {
		List<BannerPatternLayers.Layer> list = layers.layers();
		return list.size() - (!list.isEmpty() && isColouredBase(list.getFirst()) ? 1 : 0);
	}

	/**
	 * The most layers vanilla lets a banner have at a loom and in a copy: six, and one more for a banner
	 * whose first layer is our colour, so that it too takes six patterns on top.
	 */
	public static int layerCap(BannerPatternLayers layers) {
		return MAX_PATTERNS + layers.layers().size() - patternsOnTop(layers);
	}

	/**
	 * What a vanilla client is shown in a loom's banner slot. The client's loom screen counts the layers
	 * of the banner there and lists no patterns from six on, so a banner in our colour with five patterns
	 * on top (six layers) would look full to it. For that banner alone the client is shown the five
	 * patterns on a plain banner of the nearest vanilla colour, without our colour's layer, and so lists
	 * the patterns for a sixth; the server keeps the real banner, and the result is the real banner with
	 * the pattern added. Any other stack is returned as it is.
	 */
	public static ItemStack loomClientView(ItemStack stack) {
		BannerPatternLayers layers = stack.get(DataComponents.BANNER_PATTERNS);
		if (layers == null || layers.layers().size() < MAX_PATTERNS || patternsOnTop(layers) >= MAX_PATTERNS) return stack;
		ModColor base = baseColour(layers.layers().getFirst());
		if (base == null) return stack;
		ItemStack view = stack.transmuteCopy(Items.BANNER.pick(SignColors.nearestDye(base)));
		view.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(List.copyOf(layers.layers().subList(1, layers.layers().size()))));
		return view;
	}

	/**
	 * The pattern in our colour that stands for {@code source} (a vanilla or datapack pattern), to be
	 * applied with WHITE; null when there is none, e.g. for a pattern that is itself derived.
	 */
	public static @Nullable Holder<BannerPattern> derivedOf(HolderGetter<BannerPattern> patterns, ModColor color, Holder<BannerPattern> source) {
		return source.unwrapKey()
				.flatMap(key -> patterns.get(ResourceKey.create(Registries.BANNER_PATTERN, derivedId(color, key.identifier()))))
				.<Holder<BannerPattern>>map(ref -> ref)
				.orElse(null);
	}

	public static Identifier derivedId(ModColor color, Identifier source) {
		return Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID,
				color.id() + "/" + source.getNamespace() + "/" + source.getPath());
	}

	public static @Nullable Derived derived(Identifier id) {
		return DERIVED.get(id);
	}

	public static String translationKey(Identifier derivedId) {
		return "block." + MoreDyes.MOD_ID + ".banner." + derivedId.getPath().replace('/', '.');
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			MoreDyes.LOGGER.info("[{}] {} derived banner patterns registered", MoreDyes.MOD_ID, DERIVED.size());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(BannerPatterns::generateTextures);
	}

	/**
	 * Called from {@link metacraft.moredyes.mixin.RegistryLoadTaskMixin} with every banner pattern
	 * the data packs loaded; returns them plus one derived entry per colour per pattern.
	 */
	public static List<RegistryLoadTask.PendingRegistration<BannerPattern>> derive(
			List<RegistryLoadTask.PendingRegistration<BannerPattern>> loaded) {
		DERIVED.clear();
		List<RegistryLoadTask.PendingRegistration<BannerPattern>> out = new ArrayList<>(loaded);
		Set<Identifier> present = new HashSet<>();
		for (var reg : loaded) present.add(reg.key().identifier());
		for (var reg : loaded) {
			Identifier id = reg.key().identifier();
			if (id.getNamespace().equals(MoreDyes.MOD_ID)) continue; // never derive from derived
			if (reg.value().right().isPresent()) continue;		   // failed to load; nothing to derive
			for (ModColor color : ModColors.all()) {
				Identifier derived = derivedId(color, id);
				if (!present.add(derived)) continue;
				out.add(newPending(ResourceKey.create(Registries.BANNER_PATTERN, derived),
						new BannerPattern(derived, translationKey(derived))));
				DERIVED.put(derived, new Derived(color, id));
			}
		}
		MoreDyes.LOGGER.info("[{}] derived {} banner patterns from {} loaded", MoreDyes.MOD_ID, DERIVED.size(), loaded.size());
		return out;
	}

	private static RegistryLoadTask.PendingRegistration<BannerPattern> newPending(ResourceKey<BannerPattern> key, BannerPattern value) {
		// Constructor made accessible by moredyes.accesswidener.
		return new RegistryLoadTask.PendingRegistration<>(key, Either.left(value), RegistrationInfo.BUILT_IN);
	}

	/** Multiply every source mask by its colour and add the results to the pack. */
	private static void generateTextures(ResourcePackBuilder builder) {
		MinecraftServer srv = server;
		if (srv == null) {
			MoreDyes.LOGGER.error("[{}] resource pack built before the server started; banner patterns will be missing", MoreDyes.MOD_ID);
			return;
		}
		Registry<BannerPattern> registry = srv.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		Map<String, String> lang = new LinkedHashMap<>();
		int ok = 0, failed = 0;
		for (var entry : registry.entrySet()) {
			Identifier id = entry.getKey().identifier();
			Derived d = DERIVED.get(id);
			if (d == null) continue;
			BannerPattern source = registry.getValue(d.source());
			Identifier srcAsset = source != null ? source.assetId() : d.source();
			boolean any = false;
			for (String kind : new String[]{"banner", "shield"}) {
				byte[] mask = findMask(builder, srv, kind, srcAsset);
				if (mask == null) {
					MoreDyes.LOGGER.error("[{}] no {} mask texture found for pattern {} (asset {}); {} will not render",
							MoreDyes.MOD_ID, kind, d.source(), srcAsset, id);
					continue;
				}
				try {
					byte[] tinted = multiply(mask, d.color().rgb());
					builder.addData("assets/" + MoreDyes.MOD_ID + "/textures/entity/" + kind + "/" + id.getPath() + ".png", tinted);
					any = true;
				} catch (IOException e) {
					MoreDyes.LOGGER.error("[{}] failed to tint {} mask for {}", MoreDyes.MOD_ID, kind, d.source(), e);
				}
			}
			if (any) ok++; else failed++;
			// the base layer reads like vanilla's own ("Fully Red Field") in a coloured banner's tooltip
			lang.put(translationKey(id) + ".white", d.source().equals(BASE)
					? "Fully " + d.color().name() + " Field"
					: d.color().name() + " " + titleCase(d.source().getPath()));
		}
		JsonObject json = new JsonObject();
		lang.forEach(json::addProperty);
		// Own namespace so it merges with, rather than replaces, the generated moredyes lang file.
		builder.addStringData("assets/" + MoreDyes.MOD_ID + "_banners/lang/en_us.json", json.toString());
		MoreDyes.LOGGER.info("[{}] generated {} banner pattern textures ({} without a mask)", MoreDyes.MOD_ID, ok, failed);
	}

	/**
	 * Mask sources, in order: vanilla masks bundled in our jar; whatever Polymer's builder can see
	 * (mod assets, included packs, the vanilla client jar); the server's enabled data packs, since
	 * datapacks that add patterns usually ship their textures alongside.
	 */
	private static byte @Nullable [] findMask(ResourcePackBuilder builder, MinecraftServer srv, String kind, Identifier asset) {
		String texturePath = "textures/entity/" + kind + "/" + asset.getPath() + ".png";
		if (asset.getNamespace().equals("minecraft")) {
			try (InputStream in = MoreDyes.class.getResourceAsStream(
					"/assets/" + MoreDyes.MOD_ID + "/masks/" + kind + "/minecraft/" + asset.getPath() + ".png")) {
				if (in != null) return in.readAllBytes();
			} catch (IOException ignored) {
			}
		}
		byte[] data = builder.getDataOrSource("assets/" + asset.getNamespace() + "/" + texturePath);
		if (data != null) return data;
		Identifier location = Identifier.fromNamespaceAndPath(asset.getNamespace(), texturePath);
		for (PackResources pack : srv.getPackRepository().openAllSelected()) {
			try (pack) {
				IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, location);
				if (supplier != null) {
					try (InputStream in = supplier.get()) {
						return in.readAllBytes();
					}
				}
			} catch (IOException ignored) {
			}
		}
		return null;
	}

	static byte[] multiply(byte[] png, int rgb) throws IOException {
		BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(png));
		if (src == null) throw new IOException("not a PNG");
		int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getRGB(x, y);
				int a = (p >>> 24), r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
				out.setRGB(x, y, (a << 24) | ((r * cr / 255) << 16) | ((g * cg / 255) << 8) | (b * cb / 255));
			}
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(out, "png", bytes);
		return bytes.toByteArray();
	}

	static String titleCase(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder sb = new StringBuilder();
		for (String word : name.split("_")) {
			if (word.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return sb.toString();
	}
}
