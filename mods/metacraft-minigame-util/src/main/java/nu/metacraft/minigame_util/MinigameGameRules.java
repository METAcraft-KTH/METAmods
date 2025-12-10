package nu.metacraft.minigame_util;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.*;
import nu.metacraft.core.METAcraftCore;
import org.jetbrains.annotations.NotNull;

import java.util.function.ToIntFunction;

public class MinigameGameRules {

	public static final GameRule<@NotNull Boolean> REPLACE_BROKEN_BLOCKS = registerBoolean(
			"replace_broken_blocks", GameRuleCategory.PLAYER, false
	);

	public static void init() {

	}

	private static GameRule<@NotNull Boolean> registerBoolean(String id, GameRuleCategory gameRuleCategory, boolean defaultValue) {
		return register(
				id, gameRuleCategory, GameRuleType.BOOL, BoolArgumentType.bool(),
				Codec.BOOL, defaultValue, FeatureFlagSet.of(), GameRuleTypeVisitor::visitBoolean,
				(b) -> b ? 1 : 0
		);
	}

	private static <T> GameRule<@NotNull T> register(
			String id, GameRuleCategory gameRuleCategory,
			GameRuleType gameRuleType, ArgumentType<T> argumentType,
			Codec<T> codec, T object, FeatureFlagSet featureFlagSet,
			net.minecraft.world.level.gamerules.GameRules.VisitorCaller<@NotNull T> visitorCaller, ToIntFunction<T> toIntFunction
	) {
		return Registry.register(
				BuiltInRegistries.GAME_RULE, METAcraftCore.getID(id),
				new GameRule<>(
						gameRuleCategory, gameRuleType, argumentType, visitorCaller,
						codec, toIntFunction, object, featureFlagSet
				)
		);
	}
}
