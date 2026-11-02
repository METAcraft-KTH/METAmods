package se.metacraft.player_bosses.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.metacraft.player_bosses.state.PlayerBossState;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {

	public ServerPlayerMixin(Level level, GameProfile gameProfile) {
		super(level, gameProfile);
	}

	@Shadow public abstract ServerLevel level();

	@ModifyReturnValue(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"))
	public TeleportTransition getRespawnTarget(TeleportTransition original) {
		return PlayerBossState.getInstance(this.level()).filter(PlayerBossState::hasBoss).map(
			state -> state.getPlayerSpawnPoint(this.level(), this)
		).orElse(original);
	}

	@Inject(
		method = "hurtServer",
		at = @At("HEAD")
	)
	public void hurtServer(
		ServerLevel level, DamageSource source,
		float damage, CallbackInfoReturnable<Boolean> cir
	) {
		CommandSourceStack commandSourceStack;
		var sourceEntity = source.getEntity();
		if (sourceEntity != null) {
			commandSourceStack = new CommandSourceStack(
				CommandSource.NULL, sourceEntity.position(),
				sourceEntity.getRotationVector(), level,
				LevelBasedPermissionSet.GAMEMASTER, level.getServer(), sourceEntity
			);
		} else {
			var pos = source.getSourcePosition();
			commandSourceStack = new CommandSourceStack(
				CommandSource.NULL, pos != null ? pos : this.position(),
				Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER,
				Component.literal("Unattributed Attack"), level.getServer()
			);
		}
		PlayerBossState.getInstance(level).ifPresent(state -> {
			if (state.isBoss((ServerPlayer) (Object) this)) {
				state.getConfig().onAttackingBossCommand().ifPresent(command -> {
					level.getServer().getCommands().performPrefixedCommand(
						commandSourceStack, command
					);
				});
			}
		});
	}

}
