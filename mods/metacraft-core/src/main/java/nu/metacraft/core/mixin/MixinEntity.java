package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.entity.entities.MovingBlock;
import nu.metacraft.core.entity.entities.player_mob.PlayerMob;
import nu.metacraft.core.extensions.EntityExtensions;
import nu.metacraft.core.music.ManageableServerBossBar;
import nu.metacraft.lib.util.helper.EntityTrackerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtensions {

	@Shadow public abstract int getId();

	@Shadow public abstract DynamicRegistryManager getRegistryManager();

	@Shadow public abstract Box getBoundingBox();

	@Shadow private World world;

	@Shadow
	public abstract World getEntityWorld();

	@Unique
	private ManageableServerBossBar bossBar;

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
		if (bossBar == null || !(this.getEntityWorld() instanceof ServerWorld sw)) return;
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

	@Inject(method = "readData", at = @At("RETURN"))
	public void readNBT(ReadView nbt, CallbackInfo ci) {
		metacraft_lib$loadBossBar(nbt);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (this.bossBar != null) {
			bossBar.updateFromEntity((Entity) (Object) this);
		}
	}

	@Inject(method = "writeData", at = @At("RETURN"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		metacraft_lib$saveBossBar(nbt);
	}

	@Override
	public void metacraft_lib$loadBossBar(ReadView nbt) {
		nbt.read(ManageableServerBossBar.BOSS_BAR, ManageableServerBossBar.BossBarData.CODEC).ifPresentOrElse(data -> {
			if (this.bossBar == null) {
				this.bossBar = ManageableServerBossBar.create();
				this.bossBar.deserialize(data);
				this.bossBar.updateFromEntity((Entity) (Object) this);
				initialiseBossBar();
			} else {
				this.bossBar.deserialize(data);
				this.bossBar.updateFromEntity((Entity) (Object) this);
			}
		}, this::removeBossBar);
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	public void setRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof PlayerMob p) {
			p.removeAllPlayerEntries();
		}
		if (bossBar != null) {
			bossBar.onEntityRemoved((Entity) (Object) this, reason);
		}
	}

	@Override
	public void metacraft_lib$saveBossBar(WriteView nbt) {
		if (bossBar != null) {
			nbt.put(ManageableServerBossBar.BOSS_BAR, ManageableServerBossBar.BossBarData.CODEC, bossBar.serialize());
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
			if (this.bossBar.isMainEntity((Entity) (Object) this)) this.bossBar.removePlayer(player);
		}
	}

	@Override
	public Optional<ManageableServerBossBar> metacraft_lib$getBossBar() {
		return Optional.ofNullable(bossBar);
	}

	@Override
	public void metacraft_lib$setBossBar(ManageableServerBossBar bossBar) {
		if (this.bossBar != bossBar) {
			removeBossBar();
			this.bossBar = bossBar;
			initialiseBossBar();
		}
	}

	@Override
	public void metacraft_lib$updateBossBarReplaced() {
		if (!(this.getEntityWorld() instanceof ServerWorld sw)) return;
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
	public void metacraft_lib$setBossBarNoUpdate(ManageableServerBossBar bossBar) {
		this.bossBar = bossBar;
	}





	@Unique
	private long lastMovedByMovingBlock = -1;

	@Override
	public void metacraft$setLastMovedByMovingBlockTick(long lastMovedByMovingBlock) {
		this.lastMovedByMovingBlock = lastMovedByMovingBlock;
	}

	@Override
	public long metacraft$getLastMovedByMovingBlockTick() {
		return lastMovedByMovingBlock;
	}

	@ModifyExpressionValue(
			method = "getJumpVelocityMultiplier",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/block/Block;getJumpVelocityMultiplier()F",
					ordinal = 1
			)
	)
	protected float getJumpVelocityMultiplier(float g) {
		var selector = new Box(
				this.getBoundingBox().minX, this.getBoundingBox().minY, this.getBoundingBox().minZ,
				this.getBoundingBox().maxX, this.getBoundingBox().minY - 0.1, this.getBoundingBox().maxZ
		);
		for (var e : world.getOtherEntities((Entity) (Object) this, selector)) {
			if (e instanceof MovingBlock box) {
				return 1;
			}
		}
		return g;
	}

}
