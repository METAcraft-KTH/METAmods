package nu.metacraft.lib.config;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.api.SyntaxError;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import nu.metacraft.lib.METAcraftLib;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class JanksonHelper {

	private static final Jankson JANKSON = Jankson.builder().build();

	public static <T> Optional<T> load(Path configPath, Codec<T> codec) {
		return load(configPath, codec, ops -> ops);
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object) {
		save(configPath, codec, object, ops -> ops);
	}

	public static <T> Optional<T> load(Path configPath, Codec<T> codec, HolderLookup.Provider lookup) {
		return load(configPath, codec, lookup::createSerializationContext);
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object, HolderLookup.Provider lookup) {
		save(configPath, codec, object, lookup::createSerializationContext);
	}

	public static <T> Optional<T> load(Path configPath, Codec<T> codec, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		File file = configPath.toFile();
		if (!file.exists()) return Optional.empty();
		try (var reader = new BufferedInputStream(new FileInputStream(configPath.toFile()))) {  //The BufferedInputStream is to boost performance.
			var element = JANKSON.loadElement(reader);
			return codec.parse(opsFixer.apply(JanksonOps.INSTANCE), element).resultOrPartial(METAcraftLib.LOGGER::error);
		} catch (IOException e) {
			METAcraftLib.LOGGER.error(e);
		} catch (SyntaxError e) {
			METAcraftLib.LOGGER.error("Syntax error when parsing \"" + configPath + "\": " + e.getMessage());
		}
		return Optional.empty();
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		codec.encodeStart(opsFixer.apply(JanksonOps.INSTANCE), object).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(data -> {
			File file = configPath.toFile();
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException err) {
					err.printStackTrace();
				}
			}
			//The BufferedWriter is to boost performance.
			try (var outputStream = new BufferedWriter(new FileWriter(file))) {
				outputStream.write(data.toJson(JsonGrammar.JANKSON));
			} catch (IOException error) {
				error.printStackTrace();
			}
		});
	}

}
