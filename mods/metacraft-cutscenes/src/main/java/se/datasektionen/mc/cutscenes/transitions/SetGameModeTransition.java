package se.datasektionen.mc.cutscenes.transitions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.GameMode;
import se.datasektionen.mc.cutscenes.util.IntervalMap;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfig;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;

public class SetGameModeTransition implements Transition, TransitionConfig {

	public static final MapCodec<SetGameModeTransition> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					GameMode.CODEC.fieldOf("game_mode").forGetter(t -> t.gamemode)
			).apply(instance, SetGameModeTransition::new)
	);

	private final GameMode gamemode;

	public SetGameModeTransition(GameMode gameMode) {
		this.gamemode = gameMode;
	}

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public void tick(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		cutscene.forAllPlayers(player -> {
			if (player.interactionManager.getGameMode() != gamemode) {
				player.changeGameMode(gamemode);
			}
		});
	}

	@Override
	public void deactivate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {

	}

	@Override
	public TransitionType<?> getType() {
		return TransitionRegistry.SET_GAME_MODE;
	}

	@Override
	public Transition create() {
		return this;
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SET_GAME_MODE;
	}
}
