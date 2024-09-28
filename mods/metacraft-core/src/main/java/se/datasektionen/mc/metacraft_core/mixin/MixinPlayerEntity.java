package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_core.util.METAcraftCoreData;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

	protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "remove", at = @At("RETURN"))
	public void remove(RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayerEntity p && !reason.shouldDestroy()) {
			var point = ((ServerPlayerEntityExtensions) p).metacraft_lib$getMusicPoint();
			if (point != null) {
				point.discard();
			}
		}
	}

	@Inject(method = "damageArmor", at = @At("HEAD"), cancellable = true)
	public void noBreakArmor(DamageSource source, float amount, CallbackInfo ci) {
		MinecraftServer server = getServer();
		if (server == null) {
			return;
		}
		METAcraftCoreData data = METAcraftCoreData.getInstance(server);
		if (data.isDisableArmorDamage()) {
			ci.cancel();
		}
	}
}
