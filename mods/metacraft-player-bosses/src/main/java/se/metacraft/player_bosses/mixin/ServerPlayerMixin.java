package se.metacraft.player_bosses.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
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

}
