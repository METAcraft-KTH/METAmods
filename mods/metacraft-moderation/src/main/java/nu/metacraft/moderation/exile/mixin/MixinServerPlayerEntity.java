package nu.metacraft.moderation.exile.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.moderation.exile.ExileData;
import nu.metacraft.moderation.exile.ExilePlayerData;
import nu.metacraft.zones.zone.Zone;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerEntity extends Player implements ExilePlayerData {

	@Shadow @Final public MinecraftServer server;

	@Unique
	private boolean canInteract = true;

	@Unique
	private final Set<Zone> currentZones = new HashSet<>();

	public MixinServerPlayerEntity(Level world, GameProfile profile) {
		super(world, profile);
	}

	@Override
	public Set<Zone> METAcraft_Moderation$getCurrentZones() {
		return currentZones;
	}

	@Inject(
		method = "tick",
		at = @At("HEAD")
	)
	public void tick(CallbackInfo ci) {
		if (!this.level().isClientSide()) {
			ExileData.getInstance(server).getExile((ServerPlayer) (Object) this).ifPresent(exile -> {
				exile.tick((ServerPlayer & ExilePlayerData) (Object) this);
			});
		}
	}

	public void METAcraft_Moderation$setCanInteract(boolean canInteract) {
		this.canInteract = canInteract;
	}

	public boolean METAcraft_Moderation$canInteract() {
		return canInteract;
	}
}
