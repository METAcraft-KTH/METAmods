package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRefType;
import se.datasektionen.mc.metacraft_core.util.RefContext;

import java.util.stream.Stream;

public class PlayerDummy implements EntityRef {

	private static final PlayerDummy INSTANCE = new PlayerDummy();

	public static final MapCodec<PlayerDummy> CODEC = MapCodec.unit(INSTANCE);

	public static PlayerDummy getInstance() {
		return INSTANCE;
	}

	private PlayerDummy() {}

	@Override
	public Stream<Entity> get(RefContext ctx) {
		var player = ctx.getPlayer();
		var cutscene = CutsceneInstance.getCutscene(ctx);
		if (player.isPresent() && cutscene.isPresent()) {
			return cutscene.get().getPlayerDummyFor(player.get()).stream();
		}
		return Stream.of();
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefs.PLAYER_DUMMY;
	}
}
