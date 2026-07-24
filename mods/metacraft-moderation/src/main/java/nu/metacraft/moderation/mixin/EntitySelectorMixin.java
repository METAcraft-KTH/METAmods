package nu.metacraft.moderation.mixin;

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import nu.metacraft.moderation.EntitySelectorExtension;
import nu.metacraft.moderation.ModerationPlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin implements EntitySelectorExtension {

	@Unique
	private boolean metacraft$alwaysIncludeModerators = false;

	@ModifyVariable(
			method = {"findPlayers", "findEntities"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/commands/arguments/selector/EntitySelector;isWorldLimited()Z"
			),
			name = "predicate"
	)
	public Predicate<Entity> findPlayers(Predicate<Entity> predicate) {
		if (metacraft$alwaysIncludeModerators) {
			return predicate;
		} else {
			return predicate.and(e -> {
				if (e instanceof ModerationPlayerData player) {
					return player.METAcraft_Moderation$getModerationMode().map(
							mode -> mode.getDef().visibleToEntitySelectors()
					).orElse(true);
				}
				return true;
			});
		}
	}

	@Override
	public void metacraft$setAlwaysIncludeModerators(boolean canExcludeModerators) {
		this.metacraft$alwaysIncludeModerators = canExcludeModerators;
	}
}
