package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityExtensions {

	@Shadow public abstract int getId();

	@Shadow public abstract RegistryAccess registryAccess();

	@Shadow public abstract AABB getBoundingBox();

	@Shadow private Level level;

	@Shadow
	public abstract Level level();

	@Unique
	private ManageableServerBossBar bossBar;

	@Unique
	private void removeBossBar() {
		if (this.bossBar != null) {
			this.bossBar.removeAllPlayers();
			this.bossBar = null;
			onBossBarRemoved();
		}
	}

	@Unique
	private void onBossBarRemoved() {
		if (this instanceof WitherBossAccessor w) {
			w.getBossEvent().setVisible(true);
		}
	}

	@Unique
	private void initialiseBossBar() {
		if (bossBar == null || !(this.level() instanceof ServerLevel sw)) return;
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
		if (this instanceof WitherBossAccessor w) {
			w.getBossEvent().setVisible(false);
		}
	}

	@Inject(method = "load", at = @At("RETURN"))
	public void readNBT(ValueInput nbt, CallbackInfo ci) {
		metacraft_lib$loadBossBar(nbt);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (this.bossBar != null) {
			bossBar.updateFromEntity((Entity) (Object) this);
		}
	}

	@Inject(method = "saveWithoutId", at = @At("RETURN"))
	public void writeNBT(ValueOutput nbt, CallbackInfo ci) {
		metacraft_lib$saveBossBar(nbt);
	}

	@Override
	public void metacraft_lib$loadBossBar(ValueInput nbt) {
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
	public void metacraft_lib$saveBossBar(ValueOutput nbt) {
		if (bossBar != null) {
			nbt.store(ManageableServerBossBar.BOSS_BAR, ManageableServerBossBar.BossBarData.CODEC, bossBar.serialize());
		}
	}

	@Inject(method = "startSeenByPlayer", at = @At("HEAD"))
	public void onStartTracking(ServerPlayer player, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.addPlayer(player);
		}
	}

	@Inject(method = "stopSeenByPlayer", at = @At("HEAD"))
	public void onStopTracking(ServerPlayer player, CallbackInfo ci) {
		if (this.bossBar != null) {
			this.bossBar.removePlayer(player);
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
		if (!(this.level() instanceof ServerLevel sw)) return;
		if (bossBar != null) {
			var tracker = EntityTrackerHelper.getEntityTrackers(sw).get(this.getId());
			if (tracker != null) {
				var players = EntityTrackerHelper.getListeners(tracker).stream().map(
						ServerPlayerConnection::getPlayer
				).collect(Collectors.toSet());
				List<ServerPlayer> removals = new ArrayList<>();
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
			method = "getBlockJumpFactor",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/Block;getJumpFactor()F",
					ordinal = 1
			)
	)
	protected float getJumpVelocityMultiplier(float g) {
		var selector = new AABB(
				this.getBoundingBox().minX, this.getBoundingBox().minY, this.getBoundingBox().minZ,
				this.getBoundingBox().maxX, this.getBoundingBox().minY - 0.1, this.getBoundingBox().maxZ
		);
		for (var e : level.getEntities((Entity) (Object) this, selector)) {
			if (e instanceof MovingBlock box) {
				return 1;
			}
		}
		return g;
	}

}
