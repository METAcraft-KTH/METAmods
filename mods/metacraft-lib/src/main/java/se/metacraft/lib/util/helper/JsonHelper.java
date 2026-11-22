package se.metacraft.lib.util.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import se.metacraft.lib.METAcraftLib;

import java.io.*;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import net.minecraft.core.HolderLookup;

public class JsonHelper {

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
		try (var reader = new BufferedReader(new FileReader(file))) { //The BufferedReader is to boost performance.
			JsonElement element = JsonParser.parseReader(reader);
			return codec.parse(opsFixer.apply(JsonOps.INSTANCE), element);
		} catch (JsonSyntaxException e) {
			return DataResult.error(() -> "Syntax error when parsing \"" + configPath + "\": " + e.getMessage());
		} catch (JsonIOException | IOException e) {
			return DataResult.error(e::getMessage);
		}
	}

	public static <T> void saveUnsafely(
		Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer
	) throws IOException {
		var data = codec.encodeStart(opsFixer.apply(JsonOps.INSTANCE), object).getOrThrow(IOException::new);
		File file = configPath.toFile();
		if (!file.exists()) {
			file.createNewFile();
		}
		//The BufferedWriter is to boost performance.
		try (var writer = new JsonWriter(new BufferedWriter(new FileWriter(file)))) {
			writer.setIndent("\t");
			Streams.write(data, writer);
		}
	}

	public static <T> void save(
		Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer
	) {
		try {
			saveUnsafely(configPath, codec, object, opsFixer);
		} catch (IOException err) {
			METAcraftLib.LOGGER.error(err.getMessage(), err);
		}
	}

}
