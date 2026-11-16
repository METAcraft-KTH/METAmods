package se.metacraft.ovvarclient;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.metacraft.ovvarclient.combo.ComboAssets;

import java.util.Map;
import java.util.Optional;

/**
 * The ovvar patch combinations this client draws itself: every {@code ovvar:<chapter>/<piece>/<combo>}
 * (and {@code ovvar:chest/<chapter>/<material>/<combo>}, the top under a chestplate with every patch)
 * equipment asset its packs lack is built from the name ({@link ComboAssets#jsonFor}, the code the server
 * builds its combination pack with), so an ovvar server that saw the handshake ({@link OvvarClient}) never
 * pushes this client that pack. The textures the definitions name are all in the server's Polymer pack.
 */
public final class ClientCombos {
	private ClientCombos() {}

	static final Logger LOGGER = LoggerFactory.getLogger("ovvar-client");

	/** Set once the asset manager's mixin has run (a resource reload went through it): only then may the client handshake. */
	private static volatile boolean ready;

	public static boolean ready() {
		return ready;
	}

	public static void markReady() {
		ready = true;
	}

	/** An {@code ovvar:} asset's definition from its path, or empty if the path is not a combination this build can read. */
	public static Optional<EquipmentClientInfo> build(String path) {
		return ComboAssets.jsonFor(path).flatMap(json -> EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result());
	}

	/**
	 * What to draw for an {@code ovvar:} asset the packs do not hold: the combination (or chestplate wrap with
	 * the top's combination, {@code chest/<chapter>/<material>/<combo>}) built from its name or, for a name this
	 * build cannot read (which a server on the same protocol never sends), the garment or wrap one level up
	 * without its patches rather than nothing. Only while that garment is loaded: without the
	 * server's Polymer pack none of the textures exist, and the asset stays missing, as on vanilla. Anything
	 * that is not a combination's name ({@code feet/...}, a base garment) is left missing too. Never throws:
	 * this runs while rendering.
	 */
	public static EquipmentClientInfo buildOrFallBack(ResourceKey<EquipmentAsset> id, Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> loaded) {
		String path = id.identifier().getPath();
		int slash = path.lastIndexOf('/');
		String[] parts = path.split("/", -1);
		boolean chest = parts.length == 4 && parts[0].equals(ComboAssets.CHEST);
		if (parts.length != 3 && !chest) return EquipmentAssetManager.MISSING;
		EquipmentClientInfo garment = loaded.get(ResourceKey.create(EquipmentAssets.ROOT_ID, id.identifier().withPath(path.substring(0, slash))));
		if (garment == null) return EquipmentAssetManager.MISSING;
		try {
			Optional<EquipmentClientInfo> built = build(path);
			if (built.isPresent()) return built.get();
		} catch (RuntimeException e) {
			LOGGER.warn("[ovvar-client] building equipment asset {} failed", id.identifier(), e);
		}
		LOGGER.warn("[ovvar-client] cannot build equipment asset {}; drawing it without its patches", id.identifier());
		return garment;
	}
}
