package nu.metacraft.player_specific_scoreboards.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import nu.metacraft.player_specific_scoreboards.PlayerScoreboardExtension;
import nu.metacraft.player_specific_scoreboards.util.PlayerScoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements PlayerScoreboardExtension {

	@Unique
	private static final String SCOREBOARD = "metacraft:player_sidebar";

	@Shadow
	public ServerGamePacketListenerImpl connection;
	@Shadow
	@Final
	private MinecraftServer server;
	@Unique
	private boolean existsClientside = false;
	@Unique
	private PlayerScoreboard prevScoreboard = null;

	@Unique
	private PlayerScoreboard currentScoreboard = null;

	public ServerPlayerMixin(Level level, GameProfile gameProfile) {
		super(level, gameProfile);
	}

	@Unique
	private ClientboundSetObjectivePacket createSetObjectivePacket(
			Objective objective, PlayerScoreboard playerScoreboard
	) {
		if (objective != null && !Objects.equals(playerScoreboard, prevScoreboard)) {
			if (existsClientside) {
				if (playerScoreboard == null) {
					return new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE);
				} else {
					return new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_CHANGE);
				}
			} else {
				return new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD);
			}
		}
		return null;
	}

	@Override
	public void metacraft$setScoreboard(PlayerScoreboard playerScoreboard) {
		this.currentScoreboard = playerScoreboard;
		metacraft$updateScoreboard();
	}

	@Override
	public void metacraft$updateScoreboard() {
		Objective objective = null;
		if (currentScoreboard != null) {
			objective = currentScoreboard.createObjective();
		} else if (prevScoreboard != null) {
			objective = prevScoreboard.createObjective();
		}
		ClientboundSetObjectivePacket objectivePacket = createSetObjectivePacket(objective, currentScoreboard);
		if (currentScoreboard != null) {
			List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
			if (objectivePacket != null) {
				packets.add(objectivePacket);
			}
			packets.add(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
			Set<String> valuesToRemove = new HashSet<>();
			if (prevScoreboard != null) {
				for (var entry : prevScoreboard.entries()) {
					valuesToRemove.add(entry.getOwnerName());
				}
			}
			for (var entry : currentScoreboard.entries()) {
				valuesToRemove.remove(entry.getOwnerName());
				packets.add(entry.createSetValuePacket());
			}
			for (var oldEntry : valuesToRemove) {
				packets.add(new ClientboundResetScorePacket(oldEntry, PlayerScoreboard.SCOREBOARD_ID));
			}
			connection.send(new ClientboundBundlePacket(packets));
			existsClientside = true;
		} else {
			if (objectivePacket != null) {
				var displayObjective = this.server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
				if (displayObjective != null) {
					List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
					packets.add(objectivePacket);
					packets.add(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, displayObjective));
					connection.send(new ClientboundBundlePacket(packets));
				} else {
					connection.send(objectivePacket);
				}
			}
			existsClientside = false;
		}
		prevScoreboard = currentScoreboard;
	}

	@Override
	public PlayerScoreboard metacraft$getPrevScoreboard() {
		return prevScoreboard;
	}

	@Override
	public PlayerScoreboard metacraft$getCurrentScoreboard() {
		return currentScoreboard;
	}

	@Override
	public boolean metacraft$getExistsClientside() {
		return existsClientside;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void save(ValueOutput valueOutput, CallbackInfo ci) {
		valueOutput.storeNullable(SCOREBOARD, PlayerScoreboard.CODEC, currentScoreboard);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void load(ValueInput valueInput, CallbackInfo ci) {
		currentScoreboard = valueInput.read(SCOREBOARD, PlayerScoreboard.CODEC).orElse(null);
	}

	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void restoreFrom(ServerPlayer serverPlayer, boolean bl, CallbackInfo ci) {
		this.prevScoreboard = ((PlayerScoreboardExtension) serverPlayer).metacraft$getPrevScoreboard();
		this.currentScoreboard = ((PlayerScoreboardExtension) serverPlayer).metacraft$getCurrentScoreboard();
		this.existsClientside = ((PlayerScoreboardExtension) serverPlayer).metacraft$getExistsClientside();
	}
}
