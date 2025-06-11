package se.datasektionen.mc.metacraft_season_4.boss;

import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import se.metacraft.bosses.boss.attacks.AttackRegistry;
import se.metacraft.bosses.boss.attacks.AttackType;

public class Season4Attacks {


	public static final RegistryEntry<AttackType> DEVIN_DISGUISE = registerEntry("devin_disguise", new AttackType(DevinDisguiseAttack.CODEC));
	public static final AttackType AVOLINE_MULTI_TNT = register("avoline_multi_tnt", new AttackType(AvolineMultiTNT.CODEC));
	public static final AttackType PLAYER_SHUFFLE = register("player_shuffle", new AttackType(PlayerShuffleAttack.CODEC));
	public static final AttackType TICK_SPEED_CHANGING = register("tick_speed_changing", new AttackType(ChangingTickSpeedAttack.CODEC));
	public static final AttackType TICK_SPEED_CHANGE = register("tick_speed_change", new AttackType(ChangeTickSpeed.CODEC));
	public static final AttackType INVENTORY_SHUFFLE = register("inventory_shuffle", new AttackType(InventoryShuffleAttack.CODEC));
	public static final AttackType PLAYER_SHADOWS = register("player_shadows", new AttackType(PlayerShadows.CODEC));

	public static void init() {

	}


	private static AttackType register(String id, AttackType type) {
		return Registry.register(AttackRegistry.REGISTRY, Identifier.ofVanilla(id), type);
	}

	private static RegistryEntry<AttackType> registerEntry(String id, AttackType type) {
		return Registry.registerReference(AttackRegistry.REGISTRY, Identifier.ofVanilla(id), type);
	}

}
