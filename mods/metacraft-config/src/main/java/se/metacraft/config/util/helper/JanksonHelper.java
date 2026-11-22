package se.metacraft.config.util.helper;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.api.SyntaxError;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import se.metacraft.lib.METAcraftLib;
import se.metacraft.config.util.JanksonOps;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class JanksonHelper {

	private static final Jankson JANKSON = Jankson.builder().build();

	public static <T> DataResult<T> load(Path configPath, Codec<T> codec) {
		return load(configPath, codec, ops -> ops);
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object) {
		save(configPath, codec, object, ops -> ops);
	}

	public static <T> DataResult<T> load(Path configPath, Codec<T> codec, HolderLookup.Provider lookup) {
		return load(configPath, codec, lookup::createSerializationContext);
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object, HolderLookup.Provider lookup) {
		save(configPath, codec, object, lookup::createSerializationContext);
	}

	public static <T> DataResult<T> load(Path configPath, Codec<T> codec, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		File file = configPath.toFile();
		if (!file.exists()) return DataResult.error(() -> "File does not exist");
		try (var reader = new BufferedInputStream(new FileInputStream(configPath.toFile()))) {  //The BufferedInputStream is to boost performance.
			var element = JANKSON.loadElement(reader);
			return codec.parse(opsFixer.apply(JanksonOps.INSTANCE), element);
		} catch (IOException e) {
			return DataResult.error(e::getMessage);
		} catch (SyntaxError e) {
			return DataResult.error(() -> "Syntax error when parsing \"" + configPath + "\": " + e.getMessage());
		}
	}

	public static <T> void saveUnsafely(
		Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer
	) throws IOException {
		var data = codec.encodeStart(opsFixer.apply(JanksonOps.INSTANCE), object).getOrThrow(IOException::new);
		File file = configPath.toFile();
		if (!file.exists()) {
			file.createNewFile();
		}
		//The BufferedWriter is to boost performance.
		try (var outputStream = new BufferedWriter(new FileWriter(file))) {
			outputStream.write(data.toJson(JsonGrammar.JANKSON));
		}
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		try {
			saveUnsafely(configPath, codec, object, opsFixer);
		} catch (IOException err) {
			METAcraftLib.LOGGER.error(err.getMessage(), err);
		}
	}

}
