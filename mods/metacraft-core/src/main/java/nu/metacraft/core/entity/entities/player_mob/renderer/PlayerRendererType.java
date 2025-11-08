package nu.metacraft.core.entity.entities.player_mob.renderer;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.component.ResolvableProfile;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public enum PlayerRendererType implements StringRepresentable {
	FAKE_PLAYER("fake_player", FakePlayerRenderer::new),
	MANNEQUIN("mannequin", ((playerMob, resolvableProfile) -> new MannequinRenderer(playerMob)));

	public static final Codec<PlayerRendererType> CODEC = StringRepresentable.fromEnum(PlayerRendererType::values);

	private final String name;
	private final BiFunction<PlayerMob, ResolvableProfile, PlayerRenderer> rendererFactory;

	PlayerRendererType(String name, BiFunction<PlayerMob, ResolvableProfile, PlayerRenderer> rendererFactory) {
		this.name = name;
		this.rendererFactory = rendererFactory;
	}

	public PlayerRenderer createRenderer(PlayerMob playerMob, ResolvableProfile defaultSkin) {
		return rendererFactory.apply(playerMob, defaultSkin);
	}

	@Override
	public @NotNull String getSerializedName() {
		return name;
	}
}
