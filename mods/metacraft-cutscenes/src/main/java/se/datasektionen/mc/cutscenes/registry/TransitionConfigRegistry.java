package se.datasektionen.mc.cutscenes.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import se.datasektionen.mc.cutscenes.transitions.*;
import se.datasektionen.mc.cutscenes.transitions.config.*;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.cutscenes.transitions.entity.*;

public class TransitionConfigRegistry {

	public static final Registry<TransitionConfigType<?>> REGISTRY = FabricRegistryBuilder.<TransitionConfigType<?>>createSimple(
			RegistryKey.ofRegistry(Cutscenes.getID("transition_config"))
	).buildAndRegister();

	public static final Codec<TransitionConfig> CODEC = REGISTRY.getCodec().dispatch(
			TransitionConfig::getConfigType, TransitionConfigType::codec
	);

	public static final TransitionConfigType<MovingTransitionConfig> MOVING_TRANSITION_CONFIG = register(
			"moving", MovingTransitionConfig.CODEC
	);

	public static final TransitionConfigType<TeleportTransition> TELEPORT_TRANSITION = register(
			"teleport", TeleportTransition.CODEC
	);

	public static final TransitionConfigType<StatusEffectTransitionConfig> STATUS_EFFECT = register(
			"status_effect", StatusEffectTransitionConfig.CODEC
	);

	public static final TransitionConfigType<TitleTransitionConfig> TITLE = register(
			"title", TitleTransitionConfig.CODEC
	);

	public static final TransitionConfigType<MessageTransition> MESSAGE = register(
			"message", MessageTransition.CODEC
	);

	public static final TransitionConfigType<RunCommandTransition> COMMAND = register(
			"command", RunCommandTransition.CODEC
	);

	public static final TransitionConfigType<SpawnParticleTransitionConfig> PARTICLE = register(
			"particle", SpawnParticleTransitionConfig.CODEC
	);

	public static final TransitionConfigType<PlaySoundTransition> SOUND = register(
			"sound", PlaySoundTransition.CODEC
	);


	public static final TransitionConfigType<SpawnEntity> SPAWN_ENTITY = register(
			"spawn_entity", SpawnEntity.CODEC
	);

	public static final TransitionConfigType<MoveTo> ENTITY_MOVE_TO = register(
			"entity_move_to", MoveTo.CODEC
	);

	public static final TransitionConfigType<Attack> ENTITY_ATTACK = register(
			"entity_attack", Attack.CODEC
	);

	public static final TransitionConfigType<DisableAIConfig> DISABLE_ENTITY_AI = register(
			"entity_disable_ai", DisableAIConfig.CODEC
	);

	public static final TransitionConfigType<SetGameModeTransition> SET_GAME_MODE = register(
			"game_mode", SetGameModeTransition.CODEC
	);

	public static final TransitionConfigType<LookAt> LOOK_AT = register(
			"look_at", LookAt.CODEC
	);

	public static final TransitionConfigType<RotateHead.RotateHeadConfig> ROTATE_HEAD = register(
			"rotate_head", RotateHead.RotateHeadConfig.CODEC
	);






	public static void init() {

	}

	private static <T extends TransitionConfig> TransitionConfigType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.ofVanilla(id), new TransitionConfigType<>(codec));
	}

}
