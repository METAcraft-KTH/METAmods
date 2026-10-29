package se.metacraft.skins;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import se.metacraft.config.util.JanksonOps;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Reads one skin folder ({@code skins/<id>/}: {@code skin.json5} plus a Blockbench export) into
 * the skin's definition and its files in the pack, cleaning the export up on the way: texture
 * references rewritten to {@code skins:item/<id>/<file name>}, a missing particle texture filled
 * in, {@code .png.mcmeta} files carried along, and missing display transforms warned about (never
 * invented). A Dyeable Hat also gets grey copies of its tinted textures ({@link GreyCopies}), a
 * dyed model and an item definition that switches between the two on the helmet's dye. Anything
 * wrong rejects the whole skin with the file at fault and the reason.
 * <p>
 * The definition is read by {@link SkinDefinition#CODEC}, but not through metacraft-config's
 * {@code JanksonHelper}: it logs why a file failed and returns nothing, and that reason is what
 * {@code /itemskins reload} and {@code /itemskins list} show. The models stay JSON trees edited in
 * place, as they must keep every field of the export, known to this mod or not.
 * Public only for {@link #NAMESPACE}.
 */
public final class SkinFolder {
	private SkinFolder() {}

	static final String DEFINITION = "skin.json5";
	/** The namespace of every skin's pack files and item model ({@code skins:<id>}). */
	public static final String NAMESPACE = "skins";

	private static final Jankson JANKSON = Jankson.builder().build();
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	/** What a resource path segment may hold; ids, model and texture names all become one. */
	private static final Pattern PATH_SEGMENT = Pattern.compile("[a-z0-9_.-]+");
	private static final List<String> SPEAR_ICON_CONTEXTS = List.of("gui", "ground", "fixed", "on_shelf");
	private static final double SPEAR_SWAP_ANIMATION_SCALE = 1.95;

	/**
	 * A skin that loaded: what later tickets need of its definition, and its files in the pack by
	 * path. {@code materials} is the Materials it's limited to, in vanilla order; empty if it fits every one.
	 * {@code dyeable}: it's a Dyeable Hat. {@code name} is the name without its formatting codes, {@code title} the name with them.
	 */
	record Loaded(String id, String name, Component title, Kind kind, Optional<List<Material>> materials, boolean dyeable, Optional<String> description,
				Release release, List<Perk> perks, Map<String, byte[]> files) {
		Fit fit() {
			return new Fit(kind, materials);
		}
	}

	static final class Rejected extends Exception {
		final Path file;

		Rejected(Path file, String reason) {
			super(reason, null, false, false);
			this.file = file;
		}
	}

	/** What a definition got as far as naming, for a rejected skin's line in {@code /itemskins list}. */
	static Optional<Kind> kindIfReadable(Path folder) {
		try {
			return Kind.CODEC.fieldOf("kind").codec().parse(JanksonOps.INSTANCE, JANKSON.load(Files.readString(folder.resolve(DEFINITION)))).result();
		} catch (IOException | SyntaxError | RuntimeException e) {
			return Optional.empty();
		}
	}

	static Loaded load(Path folder, Consumer<ReloadReport.Warning> warnings) throws Rejected {
		String id = folder.getFileName().toString();
		Path definitionFile = folder.resolve(DEFINITION);
		if (!PATH_SEGMENT.matcher(id).matches()) {
			throw new Rejected(folder, "the folder name is the skin id and may only hold a-z, 0-9, _, - and .");
		}
		if (!Files.isRegularFile(definitionFile)) throw new Rejected(definitionFile, "the folder has no " + DEFINITION);

		JsonObject json;
		try {
			json = JANKSON.load(Files.readString(definitionFile, StandardCharsets.UTF_8));
		} catch (SyntaxError e) {
			throw new Rejected(definitionFile, "broken json5: " + e.getCompleteMessage());
		} catch (IOException | RuntimeException e) {
			throw new Rejected(definitionFile, "broken json5: " + e.getMessage());
		}
		if (json == null) throw new Rejected(definitionFile, "broken json5: not an object");
		SkinDefinition definition = SkinDefinition.CODEC.parse(JanksonOps.INSTANCE, json).getOrThrow(reason -> new Rejected(definitionFile, reason));
		Kind kind = definition.kind();
		boolean dyeable = definition.look().dyeable();

		Map<String, byte[]> files = new TreeMap<>();
		Map<String, String> modelIds = new LinkedHashMap<>();
		for (var slot : definition.look().models().entrySet()) {
			modelIds.put(slot.getKey(), model(folder, id, kind, slot.getKey(), slot.getValue(), dyeable, definitionFile, files, modelIds, warnings));
		}
		files.put("assets/" + NAMESPACE + "/items/" + id + ".json", itemDefinition(kind, modelIds).getBytes(StandardCharsets.UTF_8));
		return new Loaded(id, SkinName.plain(definition.name()), SkinName.styled(definition.name(), definition.gradient()), kind, definition.materials(), dyeable,
				definition.description(), definition.release(), definition.perks(), files);
	}

	/**
	 * Cleans up one model of the Look and adds it and its textures to {@code files}; returns its model
	 * id. For a Dyeable Hat it also adds the dyed model and its grey copies, and puts the dyed model's
	 * id in {@code modelIds} under {@code "dyed"}.
	 */
	private static String model(Path folder, String id, Kind kind, String slot, String fileName, boolean dyeable, Path definitionFile,
								Map<String, byte[]> files, Map<String, String> modelIds, Consumer<ReloadReport.Warning> warnings) throws Rejected {
		if (!fileName.endsWith(".json") || !PATH_SEGMENT.matcher(fileName).matches()) {
			throw new Rejected(definitionFile, "look." + slot + " must name a .json file in the skin folder (a-z, 0-9, _, - and .), not \"" + fileName + "\"");
		}
		Path modelFile = folder.resolve(fileName);
		if (!Files.isRegularFile(modelFile)) throw new Rejected(definitionFile, "model file \"" + fileName + "\" (look." + slot + ") is not in the skin folder");
		com.google.gson.JsonObject model;
		try {
			model = JsonParser.parseString(Files.readString(modelFile, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | JsonParseException | IllegalStateException e) {
			throw new Rejected(modelFile, "not a model: " + e.getMessage());
		}

		if (model.has("textures")) {
			if (!model.get("textures").isJsonObject()) throw new Rejected(modelFile, "\"textures\" is not an object");
			com.google.gson.JsonObject textures = model.getAsJsonObject("textures");
			String first = null;
			for (String key : List.copyOf(textures.keySet())) {
				var value = textures.get(key);
				// 26.1+ also allows { sprite, force_translucent }; the sprite is what gets rewritten.
				com.google.gson.JsonObject holder = textures;
				String field = key;
				if (value.isJsonObject() && value.getAsJsonObject().has("sprite")) {
					holder = value.getAsJsonObject();
					field = "sprite";
					value = holder.get("sprite");
				}
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
					throw new Rejected(modelFile, "texture \"" + key + "\" is neither a name nor { sprite }");
				}
				String ref = value.getAsString();
				String rewritten = ref.startsWith("#") ? ref : texture(folder, id, key, ref, modelFile, files);
				holder.addProperty(field, rewritten);
				if (first == null) first = rewritten;
			}
			if (!textures.has("particle") && first != null) textures.addProperty("particle", first);
		}

		if (!model.has("parent")) warnAboutDisplay(id, kind, slot, model, modelFile, warnings);
		boolean tinted = !tintFaces(model).isEmpty();
		if (dyeable && !tinted) {
			throw new Rejected(modelFile, "marks no Tint faces (\"tintindex\") in its own elements, so the skin can't be dyeable"
					+ (model.has("parent") ? " (Tint faces in its parent don't count)" : ""));
		}
		if (!dyeable && tinted) {
			warnings.accept(new ReloadReport.Warning(id, modelFile, fileName + " marks Tint faces but the skin isn't dyeable; they keep their painted colours", false));
		}

		String base = fileName.substring(0, fileName.length() - ".json".length());
		files.put("assets/" + NAMESPACE + "/models/item/" + id + "/" + fileName, GSON.toJson(model).getBytes(StandardCharsets.UTF_8));
		if (dyeable) modelIds.put("dyed", dyedModel(folder, id, fileName, model, modelFile, files));
		return NAMESPACE + ":item/" + id + "/" + base;
	}

	/** The faces of the model's own elements that are marked Tint: a {@code tintindex} of 0 or more. A parent's don't count. */
	private static List<com.google.gson.JsonObject> tintFaces(com.google.gson.JsonObject model) {
		List<com.google.gson.JsonObject> tinted = new ArrayList<>();
		if (!(model.get("elements") instanceof JsonArray elements)) return tinted;
		for (var element : elements) {
			if (!(element instanceof com.google.gson.JsonObject e) || !(e.get("faces") instanceof com.google.gson.JsonObject faces)) continue;
			for (var face : faces.asMap().values()) {
				if (face instanceof com.google.gson.JsonObject f && f.get("tintindex") instanceof com.google.gson.JsonPrimitive index
						&& index.isNumber() && index.getAsDouble() >= 0) {
					tinted.add(f);
				}
			}
		}
		return tinted;
	}

	/**
	 * A Dyeable Hat's dyed model: a full copy of the cleaned-up {@code natural} model (not a child, so
	 * a texture that tinted and untinted faces share stays natural on the untinted ones) in which each
	 * Tint face points at a new texture key, {@code dyed_<key>}, holding the grey copy of its texture,
	 * with {@code tintindex} 0. Adds it and the grey copies (with any {@code .png.mcmeta}) to
	 * {@code files} under {@code dyed/}; returns its model id.
	 */
	private static String dyedModel(Path folder, String id, String fileName, com.google.gson.JsonObject natural, Path modelFile,
									Map<String, byte[]> files) throws Rejected {
		com.google.gson.JsonObject dyed = natural.deepCopy();
		com.google.gson.JsonObject textures = dyed.has("textures") ? dyed.getAsJsonObject("textures") : new com.google.gson.JsonObject();
		Map<String, String> dyedKeys = new LinkedHashMap<>();
		Map<String, byte[]> tintedPngs = new LinkedHashMap<>();
		for (com.google.gson.JsonObject face : tintFaces(dyed)) {
			if (!(face.get("texture") instanceof com.google.gson.JsonPrimitive ref) || !ref.isString() || !ref.getAsString().startsWith("#")) {
				throw new Rejected(modelFile, "a Tint face's texture must be a #reference to one of the model's textures, not " + face.get("texture"));
			}
			String key = ref.getAsString().substring(1);
			if (!dyedKeys.containsKey(key)) {
				com.google.gson.JsonElement entry = ownTexture(textures, key, modelFile);
				String name = sprite(entry).substring((NAMESPACE + ":item/" + id + "/").length());
				String dyedKey = "dyed_" + key;
				while (textures.has(dyedKey)) dyedKey += "_";
				com.google.gson.JsonElement grey = entry.deepCopy();
				String greyId = NAMESPACE + ":item/" + id + "/dyed/" + name;
				if (grey.isJsonObject()) grey.getAsJsonObject().addProperty("sprite", greyId);
				else grey = new com.google.gson.JsonPrimitive(greyId);
				textures.add(dyedKey, grey);
				dyedKeys.put(key, dyedKey);
				tintedPngs.put(name, files.get(texturePath(id, name)));
			}
			face.addProperty("texture", "#" + dyedKeys.get(key));
			face.addProperty("tintindex", 0);
		}

		Map<String, byte[]> greys;
		try {
			greys = GreyCopies.of(tintedPngs);
		} catch (GreyCopies.Unreadable e) {
			throw new Rejected(folder.resolve(e.name + ".png"), "a tinted texture the mod must make a grey copy of is " + e.getMessage());
		}
		for (var grey : greys.entrySet()) {
			String path = "assets/" + NAMESPACE + "/textures/item/" + id + "/dyed/" + grey.getKey() + ".png";
			files.put(path, grey.getValue());
			byte[] mcmeta = files.get(texturePath(id, grey.getKey()) + ".mcmeta");
			if (mcmeta != null) files.put(path + ".mcmeta", mcmeta);
		}
		files.put("assets/" + NAMESPACE + "/models/item/" + id + "/dyed/" + fileName, GSON.toJson(dyed).getBytes(StandardCharsets.UTF_8));
		return NAMESPACE + ":item/" + id + "/dyed/" + fileName.substring(0, fileName.length() - ".json".length());
	}

	/** The texture entry a Tint face's {@code #key} comes to, following {@code #} references within the model. */
	private static com.google.gson.JsonElement ownTexture(com.google.gson.JsonObject textures, String key, Path modelFile) throws Rejected {
		Set<String> seen = new java.util.HashSet<>();
		for (String at = key; seen.add(at) && textures.has(at); ) {
			com.google.gson.JsonElement entry = textures.get(at);
			String sprite = sprite(entry);
			if (!sprite.startsWith("#")) return entry;
			at = sprite.substring(1);
		}
		throw new Rejected(modelFile, "a Tint face's texture \"#" + key + "\" isn't one of the model's own textures, so it can't be dyed");
	}

	/** A cleaned-up texture entry's sprite: the name itself, or a {@code { sprite }}'s. */
	private static String sprite(com.google.gson.JsonElement entry) {
		return (entry.isJsonObject() ? entry.getAsJsonObject().get("sprite") : entry).getAsString();
	}

	private static String texturePath(String id, String name) {
		return "assets/" + NAMESPACE + "/textures/item/" + id + "/" + name + ".png";
	}

	/** Matches a texture reference to a .png in the folder by file name, copies it (and its .mcmeta), returns the new reference. */
	private static String texture(Path folder, String id, String key, String ref, Path modelFile, Map<String, byte[]> files) throws Rejected {
		String name = ref.substring(ref.lastIndexOf(':') + 1);
		name = name.substring(name.lastIndexOf('/') + 1);
		if (name.endsWith(".png")) name = name.substring(0, name.length() - ".png".length());
		Path png = folder.resolve(name + ".png");
		if (name.isEmpty() || !PATH_SEGMENT.matcher(name).matches() || !Files.isRegularFile(png)) {
			throw new Rejected(modelFile, "texture \"" + ref + "\" (\"" + key + "\") has no matching " + name + ".png in the skin folder"
					+ " (texture file names may only hold a-z, 0-9, _, - and .)");
		}
		String path = texturePath(id, name);
		try {
			files.put(path, Files.readAllBytes(png));
			Path mcmeta = folder.resolve(name + ".png.mcmeta");
			if (Files.isRegularFile(mcmeta)) files.put(path + ".mcmeta", Files.readAllBytes(mcmeta));
		} catch (IOException e) {
			throw new Rejected(png, "unreadable: " + e.getMessage());
		}
		return NAMESPACE + ":item/" + id + "/" + name;
	}

	/**
	 * Display transforms the client would otherwise draw untransformed. A model with a parent may
	 * inherit them, so only parentless models (every Blockbench export) are checked.
	 */
	private static void warnAboutDisplay(String id, Kind kind, String slot, com.google.gson.JsonObject model, Path modelFile,
										Consumer<ReloadReport.Warning> warnings) {
		if (slot.equals("in_hand")) return;   // the spear's icon model covers gui, ground, fixed and on_shelf
		Set<String> present = model.has("display") && model.get("display").isJsonObject() ? model.getAsJsonObject("display").keySet() : Set.of();
		List<String> prominent = new ArrayList<>(List.of("gui"));
		if (kind == Kind.HELMET) prominent.add("head");
		for (String context : prominent) {
			if (!present.contains(context)) {
				warnings.accept(new ReloadReport.Warning(id, modelFile, "no \"" + context + "\" display transform: it will look wrong"
						+ (context.equals("gui") ? " in inventories" : " on players' heads"), true));
			}
		}
		for (String context : List.of("fixed", "on_shelf")) {
			if (!present.contains(context)) {
				warnings.accept(new ReloadReport.Warning(id, modelFile, "no \"" + context + "\" display transform"
						+ (context.equals("fixed") ? " (item frames)" : " (shelves)"), false));
			}
		}
	}

	/**
	 * The item model definition at {@code assets/skins/items/<id>.json}. A spear's switches on display
	 * context, as vanilla's does; a Dyeable Hat's on whether the helmet is dyed, the dyed model taking
	 * the dye (vanilla's wolf armour works the same way).
	 */
	private static String itemDefinition(Kind kind, Map<String, String> models) {
		var root = new com.google.gson.JsonObject();
		if (kind.isSpear()) {
			var select = new com.google.gson.JsonObject();
			select.addProperty("type", "minecraft:select");
			select.addProperty("property", "minecraft:display_context");
			var iconCase = new com.google.gson.JsonObject();
			JsonArray when = new JsonArray();
			SPEAR_ICON_CONTEXTS.forEach(when::add);
			iconCase.add("when", when);
			iconCase.add("model", plainModel(models.get("icon")));
			JsonArray cases = new JsonArray();
			cases.add(iconCase);
			select.add("cases", cases);
			select.add("fallback", plainModel(models.get("in_hand")));
			root.add("model", select);
			root.addProperty("swap_animation_scale", SPEAR_SWAP_ANIMATION_SCALE);
		} else if (models.containsKey("dyed")) {
			var condition = new com.google.gson.JsonObject();
			condition.addProperty("type", "minecraft:condition");
			condition.addProperty("property", "minecraft:has_component");
			condition.addProperty("component", "minecraft:dyed_color");
			condition.add("on_false", plainModel(models.get("model")));
			var dyed = plainModel(models.get("dyed"));
			var dye = new com.google.gson.JsonObject();
			dye.addProperty("type", "minecraft:dye");
			dye.addProperty("default", -1);   // never used: on_true is only drawn with a colour
			JsonArray tints = new JsonArray();
			tints.add(dye);
			dyed.add("tints", tints);
			condition.add("on_true", dyed);
			root.add("model", condition);
		} else {
			root.add("model", plainModel(models.get("model")));
		}
		return GSON.toJson(root);
	}

	private static com.google.gson.JsonObject plainModel(String model) {
		var object = new com.google.gson.JsonObject();
		object.addProperty("type", "minecraft:model");
		object.addProperty("model", model);
		return object;
	}
}
