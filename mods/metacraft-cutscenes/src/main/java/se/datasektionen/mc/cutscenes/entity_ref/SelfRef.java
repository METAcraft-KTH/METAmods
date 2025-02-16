package se.datasektionen.mc.cutscenes.entity_ref;

import com.mojang.serialization.MapCodec;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;

import java.util.Optional;
import java.util.stream.Stream;

public final class SelfRef implements EntityRef {

	private static final SelfRef INSTANCE = new SelfRef();

	public static SelfRef getInstance() {
		return INSTANCE;
	}

	public static final MapCodec<SelfRef> CODEC = MapCodec.unit(INSTANCE);

	private SelfRef() {}

	@Override
	public Stream<? extends Entity> get(@Nullable ServerPlayerEntity player, CutsceneInstance cutsceneInstance) {
		return Optional.ofNullable(player).map(Stream::of).orElse(cutsceneInstance.getPlayers().stream());
	}

	@Override
	public EntityRefType<?> getType() {
		return EntityRefRegistry.SELF;
	}
}
