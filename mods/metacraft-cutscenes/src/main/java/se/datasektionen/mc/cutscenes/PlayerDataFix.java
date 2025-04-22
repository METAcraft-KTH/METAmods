package se.datasektionen.mc.cutscenes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import se.datasektionen.mc.metacraft_lib.event.datafixer.AddToPlayer;

import java.util.stream.Stream;

public class PlayerDataFix implements AddToPlayer {
	@Override
	public Stream<Pair<String, TypeTemplate>> appendFields(Schema schema) {
		return Stream.of(
				Pair.of(
						"cutscene", CutsceneDataFixer.CUTSCENE.in(schema)
				)
		);
	}
}
