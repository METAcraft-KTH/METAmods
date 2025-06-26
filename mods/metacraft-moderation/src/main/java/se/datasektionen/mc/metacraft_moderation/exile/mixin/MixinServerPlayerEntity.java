package se.datasektionen.mc.metacraft_moderation.exile.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_moderation.exile.ExileData;
import se.datasektionen.mc.metacraft_moderation.exile.ExilePlayerData;
import se.datasektionen.mc.zones.zone.Zone;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ExilePlayerData {

	@Shadow @Final public MinecraftServer server;

	@Unique
	private boolean canInteract = true;

	@Unique
	private final Set<Zone> currentZones = new HashSet<>();

	public MixinServerPlayerEntity(World world, GameProfile profile) {
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
		if (!this.getWorld().isClient()) {
			ExileData.getInstance(server).getExile((ServerPlayerEntity) (Object) this).ifPresent(exile -> {
				exile.tick((ServerPlayerEntity & ExilePlayerData) (Object) this);
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
