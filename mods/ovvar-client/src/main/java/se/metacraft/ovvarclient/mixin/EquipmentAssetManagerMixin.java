package se.metacraft.ovvarclient.mixin;

import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.ovvarclient.combo.ComboAssets;
import se.metacraft.ovvarclient.ClientCombos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@code ovvar:} equipment asset the resource packs do not hold is built from its name
 * ({@link ClientCombos}) instead of drawing nothing. Only missing ones: whatever a pack holds wins, so
 * with the server's pack loaded (no handshake, or an older server) nothing changes. Built assets are
 * kept until the next resource reload.
 */
@Mixin(EquipmentAssetManager.class)
public abstract class EquipmentAssetManagerMixin {
	@Shadow
	private Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> equipmentAssets;

	@Unique
	private final Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> ovvarClient$built = new ConcurrentHashMap<>();

	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void ovvarClient$forgetBuilt(CallbackInfo ci) {
		ovvarClient$built.clear();
		ClientCombos.markReady();
	}

	@Inject(method = "get", at = @At("RETURN"), cancellable = true)
	private void ovvarClient$buildMissing(ResourceKey<EquipmentAsset> id, CallbackInfoReturnable<EquipmentClientInfo> cir) {
		if (cir.getReturnValue() != EquipmentAssetManager.MISSING || !ComboAssets.NAMESPACE.equals(id.identifier().getNamespace())) return;
		cir.setReturnValue(ovvarClient$built.computeIfAbsent(id, key -> ClientCombos.buildOrFallBack(key, equipmentAssets)));
	}
}
