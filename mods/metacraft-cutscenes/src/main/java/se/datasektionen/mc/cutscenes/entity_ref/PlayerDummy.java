package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;

import java.util.stream.Stream;

public class PlayerDummy implements EntityRef {

	private static final PlayerDummy INSTANCE = new PlayerDummy();

	public static final MapCodec<PlayerDummy> CODEC = MapCodec.unit(INSTANCE);

	public static PlayerDummy getInstance() {
		return INSTANCE;
	}

	private PlayerDummy() {}

	@Override
	public Stream<Entity> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		if (player != null) {
			return cutsceneInstance.getPlayerDummyFor(player).stream();
		}
		return Stream.of();
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.PLAYER_DUMMY;
	}
}
