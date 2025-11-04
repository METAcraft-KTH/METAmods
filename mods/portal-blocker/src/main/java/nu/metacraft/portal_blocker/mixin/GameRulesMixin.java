package nu.metacraft.portal_blocker.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.GameRules;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(GameRules.class)
public class GameRulesMixin {

	@WrapOperation(
		method = "<clinit>",
		slice = @Slice(
				from = @At(
						value = "FIELD",
						target = "Lnet/minecraft/world/level/GameRules;RULE_PVP:Lnet/minecraft/world/level/GameRules$Key;"
				),
				to = @At(
						value = "FIELD",
						target = "Lnet/minecraft/world/level/GameRules;RULE_ALLOW_NETHER:Lnet/minecraft/world/level/GameRules$Key;"
				)
		),
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/level/GameRules;register(Ljava/lang/String;Lnet/minecraft/world/level/GameRules$Category;Lnet/minecraft/world/level/GameRules$Type;)Lnet/minecraft/world/level/GameRules$Key;"
		)
	)
	private static <T extends GameRules.Value<T>> GameRules.Key<T> clinit(
			String name, GameRules.Category category, GameRules.Type<T> type, Operation<GameRules.Key<T>> original
	) {
		assert name.equals("allowEnteringNetherUsingPortals");
		var t = ((GameRulesTypeAccessor<T>) type);
		t.setCallback(t.getCallback().andThen(
				(server, t1) -> {
					var state = PortalBlockerSettings.getInstance(server);
					boolean netherBlocked = state.isPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL);
					boolean isNetherAllowed = server.getGameRules().getRule(GameRules.RULE_ALLOW_NETHER).get();
					if (isNetherAllowed == netherBlocked) {
						state.setPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL, !isNetherAllowed);
					}
				}
		));
		return original.call(name, category, type);
	}

}
