package se.datasektionen.mc.metacraft_lib.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryWrapper;
import se.datasektionen.mc.metacraft_lib.METAcraftLib;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class JsonHelper {

	public static <T> Optional<T> load(Path configPath, Codec<T> codec) {
		return load(configPath, codec, ops -> ops);
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object) {
		save(configPath, codec, object, ops -> ops);
	}

	public static <T> Optional<T> load(Path configPath, Codec<T> codec, RegistryWrapper.WrapperLookup lookup) {
		return load(configPath, codec, lookup::getOps);
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object, RegistryWrapper.WrapperLookup lookup) {
		save(configPath, codec, object, lookup::getOps);
	}

	public static <T> Optional<T> load(Path configPath, Codec<T> codec, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		File file = configPath.toFile();
		if (!file.exists()) return Optional.empty();
		try (var reader = new BufferedReader(new FileReader(file))) { //The BufferedReader is to boost performance.
			JsonElement element = JsonParser.parseReader(reader);
			return codec.parse(opsFixer.apply(JsonOps.INSTANCE), element).resultOrPartial(METAcraftLib.LOGGER::error);
		} catch (IOException error) {
			error.printStackTrace();
		}
		return Optional.empty();
	}

	public static <T> void save(Path configPath, Codec<T> codec, T object, UnaryOperator<DynamicOps<JsonElement>> opsFixer) {
		codec.encodeStart(opsFixer.apply(JsonOps.INSTANCE), object).resultOrPartial(METAcraftLib.LOGGER::error).ifPresent(data -> {
			File file = configPath.toFile();
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException err) {
					err.printStackTrace();
				}
			}
			//The BufferedWriter is to boost performance.
			try (var writer = new JsonWriter(new BufferedWriter(new FileWriter(file)))) {
				writer.setIndent("\t");
				Streams.write(data, writer);
			} catch (IOException error) {
				error.printStackTrace();
			}
		});
	}

}
