package se.datasektionen.mc.metacraft_core.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.datasektionen.mc.metacraft_core.entity.entities.player_mob.PlayerMob;
import se.datasektionen.mc.metacraft_core.extensions.EntityExtensions;
import se.datasektionen.mc.metacraft_core.music.ServerBossBarWithMusic;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtensions {

	@Shadow public abstract World getWorld();

	@Shadow public abstract int getId();

	@Shadow @Nullable public abstract MinecraftServer getServer();

	@Shadow public abstract DynamicRegistryManager getRegistryManager();

	@Unique
	private ServerBossBarWithMusic bossBar;

	@Unique
	private void removeBossBar() {
		if (this.bossBar != null) {
			this.bossBar.clearPlayers();
			this.bossBar = null;
			onBossBarRemoved();
		}
	}

	@Unique
	private void onBossBarRemoved() {
		if (this instanceof AccessorWitherEntity w) {
			w.getBossBar().setVisible(true);
		}
	}

	@Unique
	private void initialiseBossBar() {
		if (bossBar == null || !(this.getWorld() instanceof ServerWorld sw)) return;
		var tracker = EntityTrackerHelper.getEntityTrackers(sw).get(this.getId());
		if (tracker != null) {
			for (var player : EntityTrackerHelper.getListeners(tracker)) {
				this.bossBar.addPlayer(player.getPlayer());
			}
		}
		onBossBarAdded();
	}

	@Unique
	private void onBossBarAdded() {
		if (this instanceof AccessorWitherEntity w) {
			w.getBossBar().setVisible(false);
		}
	}

	@Inject(method = "readNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		metacraft_lib$loadBossBar(nbt);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (this.bossBar != null) {
			bossBar.updateFromEntity((Entity) (Object) this);
		}
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
		metacraft_lib$saveBossBar(nbt);
	}

	@Override
	public void metacraft_lib$loadBossBar(NbtCompound nbt) {
		if (nbt.contains(ServerBossBarWithMusic.BOSS_BAR)) {
			NbtCompound bossBar = nbt.getCompound(ServerBossBarWithMusic.BOSS_BAR);
			if (this.bossBar == null) {
				this.bossBar = ServerBossBarWithMusic.create();
				this.bossBar.readNBT(bossBar, getRegistryManager());
				this.bossBar.updateFromEntity((Entity) (Object) this);
				initialiseBossBar();
			} else {
				this.bossBar.readNBT(bossBar, getRegistryManager());
				this.bossBar.updateFromEntity((Entity) (Object) this);
			}
		} else {
			removeBossBar();
		}
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof PlayerMob p) {
			p.removeAllPlayerEntries();
		}
	}

	@Override
	public void metacraft_lib$saveBossBar(NbtCompound nbt) {
		if (bossBar != null) {
			nbt.put(ServerBossBarWithMusic.BOSS_BAR, bossBar.writeNBT(new NbtCompound(), this.getRegistryManager()));
		}
	}

	@Inject(method = "onStartedTrackingBy", at = @At("HEAD"))
	public void onStartTracking(ServerPlayerEntity player, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.addPlayer(player);
		}
	}

	@Inject(method = "onStoppedTrackingBy", at = @At("HEAD"))
	public void onStopTracking(ServerPlayerEntity player, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.removePlayer(player);
		}
	}

	@Override
	public Optional<ServerBossBarWithMusic> metacraft_lib$getBossBar() {
		return Optional.ofNullable(bossBar);
	}

	@Override
	public void metacraft_lib$setBossBar(ServerBossBarWithMusic bossBar) {
		if (this.bossBar != bossBar) {
			removeBossBar();
			this.bossBar = bossBar;
			initialiseBossBar();
		}
	}

	@Override
	public void metacraft_lib$updateBossBarReplaced() {
		if (!(this.getWorld() instanceof ServerWorld sw)) return;
		if (bossBar != null) {
			var tracker = EntityTrackerHelper.getEntityTrackers(sw).get(this.getId());
			if (tracker != null) {
				var players = EntityTrackerHelper.getListeners(tracker).stream().map(
						PlayerAssociatedNetworkHandler::getPlayer
				).collect(Collectors.toSet());
				List<ServerPlayerEntity> removals = new ArrayList<>();
				for (var prevPlayer : bossBar.getPlayers()) {
					if (!players.contains(prevPlayer)) {
						removals.add(prevPlayer);
					}
				}
				removals.forEach(bossBar::removePlayer);
				for (var newPlayer : players) {
					if (!bossBar.getPlayers().contains(newPlayer)) {
						bossBar.addPlayer(newPlayer);
					}
				}
			}
			this.bossBar.updateFromEntity((Entity) (Object) this);
			onBossBarAdded();
		} else {
			onBossBarRemoved();
		}
	}

	@Override
	public void metacraft_lib$setBossBarNoUpdate(ServerBossBarWithMusic bossBar) {
		this.bossBar = bossBar;
	}

}
