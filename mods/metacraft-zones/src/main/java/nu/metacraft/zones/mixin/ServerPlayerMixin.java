package nu.metacraft.zones.mixin;

import net.minecraft.server.level.ServerPlayer;
import nu.metacraft.zones.PlayerZoneMessageExtension;
import nu.metacraft.zones.zone.Zone;
import nu.metacraft.zones.zone.data.MessageZoneData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements PlayerZoneMessageExtension {

	@Unique
	private final Map<Zone, MessageZoneData.MessageEntry> zones = new HashMap<>();

	@Override
	public void metacraft$addZoneMessage(Zone zone, MessageZoneData.MessageEntry message) {
		zones.put(zone, message);
	}

	@Override
	public void metacraft$removeZoneMessage(Zone zone) {
		zones.remove(zone);
	}

	@Override
	public MessageZoneData.MessageEntry metacraft$getZoneMessage(Zone zone) {
		return zones.get(zone);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		zones.values().removeIf(
				zone -> zone.tryRunCommand((ServerPlayer) (Object) this)
		);
	}
}
