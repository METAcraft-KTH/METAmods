package nu.metacraft.moderation.mixin;

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import nu.metacraft.moderation.ModerationPlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin {

	@ModifyVariable(
			method = {"findPlayers", "findEntities"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/commands/arguments/selector/EntitySelector;isWorldLimited()Z"
			)
	)
	public Predicate<Entity> findPlayers(Predicate<Entity> original) {
		return original.and(e -> {
			if (e instanceof ModerationPlayerData player) {
				return player.METAcraft_Moderation$getModerationMode().map(
						mode -> mode.getDef().visibleToEntitySelectors()
				).orElse(true);
			}
			return true;
		});
	}

}
