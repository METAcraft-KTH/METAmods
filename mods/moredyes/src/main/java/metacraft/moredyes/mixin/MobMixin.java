package metacraft.moredyes.mixin;

import metacraft.moredyes.color.ModColor;
import metacraft.moredyes.sheep.SheepColors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin extends LivingEntity {
    protected MobMixin() {
        super(null, null);
    }

    /** Death loot for a sheep in our colour comes from our per-colour tables (wool only if unshorn). */
    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void moredyes$sheepLoot(ServerLevel level, DamageSource source, boolean playerKill, CallbackInfo ci) {
        if (!((Object) this instanceof Sheep sheep)) return;
        ModColor color = SheepColors.get(sheep);
        if (color == null) return;
        this.dropFromLootTable(level, source, playerKill, SheepColors.deathLootTable(color, sheep.isSheared()));
        ci.cancel();
    }
}
