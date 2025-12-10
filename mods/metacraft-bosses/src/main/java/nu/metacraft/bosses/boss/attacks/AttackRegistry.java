package nu.metacraft.bosses.boss.attacks;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import nu.metacraft.bosses.METAcraftBosses;
import nu.metacraft.bosses.boss.attacks.target.*;

public class AttackRegistry {

	public static final Registry<AttackType> REGISTRY = FabricRegistryBuilder.<AttackType>createSimple(
			ResourceKey.createRegistryKey(METAcraftBosses.getID("attacks"))
	).buildAndRegister();

	public static final AttackType TIMED = register("timed", new AttackType(TimedAttack.CODEC));
	public static final AttackType GIVE_ATTRIBUTE = register("give_attribute", new AttackType(GiveAttributeAttack.CODEC));
	public static final AttackType MULTI_ATTACK = register("multi_attack", new AttackType(MultiAttack.CODEC));
	public static final AttackType REMOVE_ALL_ATTACKS = register("remove_all_attacks", new AttackType(RemoveAllActiveAttacks.CODEC));
	public static final AttackType CUSTOM_DELAY = register("custom_delay", new AttackType(WithCustomDelay.CODEC));
	public static final AttackType SPAWN_ENTITIES = register("spawn_entities", new AttackType(SpawnSpecifiedEntities.CODEC));
	public static final AttackType SPAWN_ENTITIES_FOR_EACH_TARGET = register("spawn_entities_for_each_target", new AttackType(SpawnForEachTarget.CODEC));
	public static final AttackType PLACE_BLOCKS = register("place_blocks", new AttackType(PlaceBlocksAttack.CODEC));
	public static final AttackType DELAYED_ATTACK = register("delayed", new AttackType(DelayedAttack.CODEC));
	public static final AttackType SEND_MESSAGE = register("send_message", new AttackType(SendMessageAttack.CODEC));
	public static final AttackType SEND_TITLE = register("send_title", new AttackType(SendTitleAttack.CODEC));
	public static final AttackType CHANGE_MUSIC = register("change_music", new AttackType(ChangeMusicAttack.CODEC));
	public static final AttackType SET_HARDCORE = register("set_hardcore", new AttackType(SetHardcoreModeAttack.CODEC));
	public static final AttackType PLAY_SOUND = register("play_sound", new AttackType(PlaySoundAttack.CODEC));
	public static final AttackType MOVE_TOWARDS = register("move_towards", new AttackType(MoveTowardsAttack.CODEC));
	public static final AttackType TELEPORT = register("teleport", new AttackType(TeleportAttack.CODEC));
	public static final AttackType CONDITIONAL = register("conditional", new AttackType(ConditionalAttack.CODEC));
	public static final AttackType DOUBLE_TEAM = register("double_team", new AttackType(DoubleTeamAttack.CODEC));
	public static final AttackType STATUS_EFFECT = register("status_effect", new AttackType(StatusEffectAttack.CODEC));


	public static void init() {
		PTS.init();
	}

	private static AttackType register(String id, AttackType type) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(id), type);
	}

	public static class PTS {
		public static final Registry<PositionTargetSelectorType> REGISTRY = FabricRegistryBuilder.<PositionTargetSelectorType>createSimple(
				ResourceKey.createRegistryKey(METAcraftBosses.getID("attack_position_target_selectors"))
		).buildAndRegister();

		public static final PositionTargetSelectorType NEAREST_PLAYER = register(
				"nearest_player", new PositionTargetSelectorType(NearestPlayer.CODEC)
		);
		public static final PositionTargetSelectorType RANDOM_PLAYER = register(
				"random_player", new PositionTargetSelectorType(RandomPlayer.CODEC)
		);
		public static final PositionTargetSelectorType FIXED_POS = register(
				"fixed_pos", new PositionTargetSelectorType(FixedPos.CODEC)
		);
		public static final PositionTargetSelectorType MOVE_UP = register(
				"move_up", new PositionTargetSelectorType(MoveUp.CODEC)
		);
		public static final PositionTargetSelectorType MOVE_TO_GROUND = register(
				"move_to_ground", new PositionTargetSelectorType(MoveToGround.CODEC)
		);

		public static final PositionTargetSelectorType POSITION_REF = register(
				"position_ref", new PositionTargetSelectorType(PositionRefTarget.CODEC)
		);



		public static void init() {

		}

		private static PositionTargetSelectorType register(String id, PositionTargetSelectorType type) {
			return Registry.register(REGISTRY, Identifier.withDefaultNamespace(id), type);
		}
	}
}
