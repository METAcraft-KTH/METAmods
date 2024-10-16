package se.datasektionen.mc.cutscenes.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.transitions.*;
import se.datasektionen.mc.cutscenes.transitions.entity.*;

public class TransitionRegistry {

	public static final Registry<TransitionType<?>> REGISTRY = FabricRegistryBuilder.<TransitionType<?>>createSimple(
			RegistryKey.ofRegistry(Cutscenes.getID("transition"))
	).buildAndRegister();

	public static final Codec<Transition> CODEC = REGISTRY.getCodec().dispatch(
			Transition::getType, TransitionType::codec
	);

	public static final TransitionType<MovingTransition> MOVING_TRANSITION = register(
			"moving", MovingTransition.CODEC
	);

	public static final TransitionType<TeleportTransition> TELEPORT_TRANSITION = register(
			"teleport", TeleportTransition.CODEC
	);

	public static final TransitionType<StatusEffectTransition> STATUS_EFFECT = register(
			"status_effect", StatusEffectTransition.CODEC
	);

	public static final TransitionType<TitleTransition> TITLE = register(
			"title", TitleTransition.CODEC
	);

	public static final TransitionType<MessageTransition> MESSAGE = register(
			"message", MessageTransition.CODEC
	);

	public static final TransitionType<RunCommandTransition> COMMAND = register(
			"command", RunCommandTransition.CODEC
	);

	public static final TransitionType<SpawnParticleTransition> PARTICLE = register(
			"particle", SpawnParticleTransition.CODEC
	);

	public static final TransitionType<PlaySoundTransition> SOUND = register(
			"sound", PlaySoundTransition.CODEC
	);

	public static final TransitionType<SpawnEntity> SPAWN_ENTITY = register(
			"spawn_entity", SpawnEntity.CODEC
	);

	public static final TransitionType<MoveTo> ENTITY_MOVE_TO = register(
			"entity_move_to", MoveTo.CODEC
	);

	public static final TransitionType<Attack> ENTITY_ATTACK = register(
			"entity_attack", Attack.CODEC
	);

	public static final TransitionType<DisableAI> DISABLE_ENTITY_AI = register(
			"entity_disable_ai", DisableAI.CODEC
	);

	public static final TransitionType<SetGameModeTransition> SET_GAME_MODE = register(
			"gamemode", SetGameModeTransition.CODEC
	);

	public static final TransitionType<LookAt> LOOK_AT = register(
			"look_at", LookAt.CODEC
	);

	public static final TransitionType<RotateHead> ROTATE_HEAD = register(
			"rotate_head", RotateHead.CODEC
	);

	public static final TransitionType<HideOtherPlayersTransition> HIDE_OTHER_PLAYERS = register(
			"hide_other_players", HideOtherPlayersTransition.CODEC
	);

	public static final TransitionType<MusicTransition> MUSIC = register(
			"music", MusicTransition.CODEC
	);

	public static final TransitionType<SetTimeTransition> SET_TIME = register(
			"set_time", SetTimeTransition.CODEC
	);





	public static void init() {
		TransitionConfigRegistry.init();
	}

	private static <T extends Transition> TransitionType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), new TransitionType<>(codec));
	}

}
