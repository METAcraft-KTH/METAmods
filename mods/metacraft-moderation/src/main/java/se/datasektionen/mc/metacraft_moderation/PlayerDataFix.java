package se.datasektionen.mc.metacraft_moderation;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import net.minecraft.datafixer.TypeReferences;
import se.datasektionen.mc.metacraft_lib.event.datafixer.AddToPlayer;

import java.util.stream.Stream;

public class PlayerDataFix implements AddToPlayer {

	@Override
	public Stream<Pair<String, TypeTemplate>> appendFields(Schema schema) {
		return Stream.of(
				Pair.of(
						"METAcraft-Moderation", DSL.optionalFields(
								"ModeratorModeNBTMap", DSL.compoundList(
										TypeReferences.PLAYER.in(schema)
								)
						)
				)
		);
	}
}
