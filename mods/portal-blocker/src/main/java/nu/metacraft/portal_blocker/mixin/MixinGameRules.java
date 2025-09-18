package nu.metacraft.portal_blocker.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.GameRules;
import nu.metacraft.portal_blocker.PortalBlockerSettings;
import nu.metacraft.portal_blocker.PortalState;
import nu.metacraft.portal_blocker.portal_type.PortalTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(GameRules.class)
public class MixinGameRules {

	@WrapOperation(
		method = "<clinit>",
		slice = @Slice(
				from = @At(
						value = "FIELD",
						target = "Lnet/minecraft/world/GameRules;PVP:Lnet/minecraft/world/GameRules$Key;"
				),
				to = @At(
						value = "FIELD",
						target = "Lnet/minecraft/world/GameRules;ALLOW_ENTERING_NETHER_USING_PORTALS:Lnet/minecraft/world/GameRules$Key;"
				)
		),
		at = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/world/GameRules;register(Ljava/lang/String;Lnet/minecraft/world/GameRules$Category;Lnet/minecraft/world/GameRules$Type;)Lnet/minecraft/world/GameRules$Key;"
		)
	)
	private static <T extends GameRules.Rule<T>> GameRules.Key<T> clinit(
			String name, GameRules.Category category, GameRules.Type<T> type, Operation<GameRules.Key<T>> original
	) {
		assert name.equals("allowEnteringNetherUsingPortals");
		var t = ((AccessorGameRulesType<T>) type);
		t.setChangeCallback(t.getChangeCallback().andThen(
				(server, t1) -> {
					var state = PortalBlockerSettings.getInstance(server);
					boolean netherBlocked = state.isPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL);
					boolean isNetherAllowed = server.getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS).get();
					if (isNetherAllowed == netherBlocked) {
						state.setPortalBlockedGlobally(PortalTypeRegistry.NETHER, PortalState.BlockingType.TRAVEL, !isNetherAllowed);
					}
				}
		));
		return original.call(name, category, type);
	}

}
