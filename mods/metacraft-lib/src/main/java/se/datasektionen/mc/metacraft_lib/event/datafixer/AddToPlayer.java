package se.datasektionen.mc.metacraft_lib.event.datafixer;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.loader.api.FabricLoader;

import java.util.stream.Stream;

public interface AddToPlayer {

	static Pair<String, TypeTemplate>[] append(Pair<String, TypeTemplate>[] fields, Schema schema) {
		Stream<Pair<String, TypeTemplate>> all = Stream.empty();
		for (var container : FabricLoader.getInstance().getEntrypointContainers("metacraft:player_datafixer", AddToPlayer.class)) {
			all = Stream.concat(all, container.getEntrypoint().appendFields(schema));
		}

		var toAdd = all.toArray(Pair[]::new);
		Pair<String, TypeTemplate>[] newArray = new Pair[fields.length+toAdd.length];
		System.arraycopy(fields, 0, newArray, 0, fields.length);
		System.arraycopy(toAdd, 0, newArray, fields.length, toAdd.length);
		return newArray;
	}

	Stream<Pair<String, TypeTemplate>> appendFields(Schema schema);

}
