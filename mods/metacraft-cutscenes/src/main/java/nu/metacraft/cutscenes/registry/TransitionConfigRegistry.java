package nu.metacraft.cutscenes.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import nu.metacraft.cutscenes.transitions.*;
import nu.metacraft.cutscenes.transitions.config.*;
import nu.metacraft.cutscenes.transitions.entity.*;
import nu.metacraft.cutscenes.Cutscenes;

public class TransitionConfigRegistry {

	public static final Registry<TransitionConfigType<?>> REGISTRY = FabricRegistryBuilder.<TransitionConfigType<?>>createSimple(
			ResourceKey.createRegistryKey(Cutscenes.getID("transition_config"))
	).buildAndRegister();

	public static final Codec<TransitionConfig> CODEC = REGISTRY.byNameCodec().dispatch(
			TransitionConfig::getConfigType, TransitionConfigType::codec
	);

	public static final TransitionConfigType<MovingTransitionConfig> MOVING_TRANSITION_CONFIG = register(
			"moving", MovingTransitionConfig.CODEC
	);

	public static final TransitionConfigType<SmoothCameraPathConfig> CAMERA_PATH = register(
			"camera_path", SmoothCameraPathConfig.CODEC
	);

	public static final TransitionConfigType<PlayerSpecificCameraPathTransition.Config> PLAYER_CAMERA_PATH = register(
			"player_camera_path", PlayerSpecificCameraPathTransition.Config.CODEC
	);

	public static final TransitionConfigType<SmoothEntityPathConfig> SMOOTH_ENTITY_PATH = register(
			"smooth_entity_path", SmoothEntityPathConfig.CODEC
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

	public static final TransitionConfigType<MoveTo.Config> ENTITY_MOVE_TO = register(
			"entity_move_to", MoveTo.Config.CODEC
	);

	public static final TransitionConfigType<PathFindTo> ENTITY_FUZZY_FIND_TO = register(
			"entity_fuzzy_move_to", PathFindTo.CODEC
	);

	public static final TransitionConfigType<Attack> ENTITY_ATTACK = register(
			"entity_attack", Attack.CODEC
	);

	public static final TransitionConfigType<DisableAIConfig> DISABLE_ENTITY_AI = register(
			"entity_disable_ai", DisableAIConfig.CODEC
	);

	public static final TransitionConfigType<DropItem.DropSpecificStack> ENTITY_DROP_STACK = register(
			"entity_drop_stack", DropItem.DropSpecificStack.CODEC
	);

	public static final TransitionConfigType<DropItem.DropFromSlot> ENTITY_DROP_SLOT = register(
			"entity_drop_slot", DropItem.DropFromSlot.CODEC
	);

	public static final TransitionConfigType<SetCurrentItem> ENTITY_SET_CURRENT_HAND = register(
			"entity_set_current_hand", SetCurrentItem.CODEC
	);

	public static final TransitionConfigType<SetPose.Config> ENTITY_SET_POSE = register(
			"entity_set_pose", SetPose.Config.CODEC
	);

	public static final TransitionConfigType<RemoveEntity> ENTITY_REMOVE = register(
			"entity_remove", RemoveEntity.CODEC
	);


	public static final TransitionConfigType<SetGameModeTransition.Config> SET_GAME_MODE = register(
			"gamemode", SetGameModeTransition.Config.CODEC
	);

	public static final TransitionConfigType<LookAt> LOOK_AT = register(
			"look_at", LookAt.CODEC
	);

	public static final TransitionConfigType<RotateHead.RotateHeadConfig> ROTATE_HEAD = register(
			"rotate_head", RotateHead.RotateHeadConfig.CODEC
	);

	public static final TransitionConfigType<Sleep> SLEEP = register(
			"sleep", Sleep.CODEC
	);

	public static final TransitionConfigType<Sneak> SNEAK = register(
			"sneak", Sneak.CODEC
	);

	public static final TransitionConfigType<SwingHand> SWING_HAND = register(
			"swing_hand", SwingHand.CODEC
	);

	public static final TransitionConfigType<HideOtherPlayersTransition> HIDE_OTHER_PLAYERS = register(
			"hide_other_players", HideOtherPlayersTransition.CODEC
	);

	public static final TransitionConfigType<MusicTransition> MUSIC = register(
			"music", MusicTransition.CODEC
	);

	public static final TransitionConfigType<SetTimeTransition> SET_TIME = register(
			"set_time", SetTimeTransition.CODEC
	);

	public static final TransitionConfigType<SetWeatherTransition> SET_WEATHER = register(
			"set_weather", SetWeatherTransition.CODEC
	);

	public static final TransitionConfigType<SetGameRuleTransition.Config> SET_GAME_RULE = register(
			"set_game_rule", SetGameRuleTransition.Config.CODEC
	);

	public static final TransitionConfigType<PlaceStructure> PLACE_STRUCTURE = register(
			"place_structure", PlaceStructure.CODEC
	);

	public static final TransitionConfigType<ConditionalTransition.Config> CONDITIONAL = register(
			"conditional", ConditionalTransition.Config.CODEC
	);

	public static final TransitionConfigType<LoadIntoCutscene> LOAD_CHUNKS = register(
			"load_chunks", LoadIntoCutscene.CODEC
	);

	public static final TransitionConfigType<AddPlayerDummies> PLAYER_DUMMIES = register(
			"player_dummies", AddPlayerDummies.CODEC
	);







	public static void init() {

	}

	private static <T extends TransitionConfig> TransitionConfigType<T> register(String id, MapCodec<T> codec) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(id), new TransitionConfigType<>(codec));
	}

}
