package se.metacraft.trample_rules;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import org.jetbrains.annotations.NotNull;

public class TrampleRules implements ModInitializer {

	public static final String NAMESPACE = "trample_rules";

	/** Whether players trample farmland (vanilla behaviour: yes). */
	public static final GameRule<@NotNull Boolean> PLAYER_CROP_TRAMPLE = registerBoolean(
			"player_crop_trample", GameRuleCategory.PLAYER
	);

	/** Whether mobs trample farmland (vanilla behaviour: yes, if mobGriefing is on). */
	public static final GameRule<@NotNull Boolean> MOB_TRAMPLE = registerBoolean(
			"mob_trample", GameRuleCategory.MOBS
	);

	@Override
	public void onInitialize() {
		// Rules are registered when this class loads.
	}

	private static GameRule<@NotNull Boolean> registerBoolean(String id, GameRuleCategory category) {
		return Registry.register(
				BuiltInRegistries.GAME_RULE, Identifier.fromNamespaceAndPath(NAMESPACE, id),
				new GameRule<>(
						category, GameRuleType.BOOL, BoolArgumentType.bool(),
						GameRuleTypeVisitor::visitBoolean, Codec.BOOL,
						(b) -> b ? 1 : 0, true, FeatureFlagSet.of()
				)
		);
	}

}
