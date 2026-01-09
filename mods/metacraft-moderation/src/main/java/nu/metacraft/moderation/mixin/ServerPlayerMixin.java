package nu.metacraft.moderation.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.lib.util.helper.PlayerDataHelper;
import nu.metacraft.moderation.ModerationData;
import nu.metacraft.moderation.ModerationPlayerData;
import nu.metacraft.moderation.moderator_mode.ModerationModeState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements ModerationPlayerData {

	@Shadow @Final
	private MinecraftServer server;

	@Unique
	private static final String METACRAFT_MODERATION = "metacraft-moderation";
	@Unique
	private static final String MODERATION_STATE = "state";

	@Unique
	private static final String MODERATOR_MODE_NBT_MAP = "player_data";


	@Unique
	private static final String DEFAULT_MODERATOR_MODE = "default_mode";

	@Unique
	private Map<String, CompoundTag> savedNBT = new HashMap<>();

	@Unique
	private ModerationModeState state;

	@Unique
	private String defaultModeratorMode;

	@Unique
	private boolean skipSaveState = false;

	public ServerPlayerMixin(Level world, GameProfile profile) {
		super(world, profile);
	}

	@Override
	public Optional<ModerationModeState> METAcraft_Moderation$getModerationMode() {
		return Optional.ofNullable(state);
	}

	@Override
	public void METAcraft_Moderation$setModerationMode(ModerationModeState moderationMode) {
		skipSaveState = true;
		Optional.ofNullable(moderationMode).orElse(ModerationModeState.NULL).applyToPlayer(
				this.state, (ServerPlayer) (Object) this
		);
		skipSaveState = false;
		this.state = moderationMode;
	}


	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void toNBT(ValueOutput nbt, CallbackInfo ci) {
		CompoundTag moderationNBT = new CompoundTag();
		if (state != null && !skipSaveState) {
			moderationNBT.put(MODERATION_STATE, state.toNBT());
		}

		if (!skipSaveState) {
			CompoundTag moderatorModeNBTMap = new CompoundTag();
			for (var entry : savedNBT.entrySet()) {
				moderatorModeNBTMap.put(entry.getKey(), entry.getValue());
			}
			moderationNBT.put(MODERATOR_MODE_NBT_MAP, moderatorModeNBTMap);
		}

		if (defaultModeratorMode != null) {
			moderationNBT.putString(DEFAULT_MODERATOR_MODE, defaultModeratorMode);
		}

		nbt.store(METACRAFT_MODERATION, CompoundTag.CODEC, moderationNBT);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void fromNBT(ValueInput nbt, CallbackInfo ci) {
		ValueInput moderationNBT;
		if (nbt.contains(METACRAFT_MODERATION)) {
			moderationNBT = nbt.childOrEmpty(METACRAFT_MODERATION);
		} else {
			moderationNBT = nbt.childOrEmpty("METAcraft-Moderation");
		}
		if (!skipSaveState) {
			Optional<CompoundTag> stateData;
			if (moderationNBT.contains(MODERATION_STATE)) {
				stateData = moderationNBT.read(MODERATION_STATE, CompoundTag.CODEC);
			} else {
				stateData = moderationNBT.read("ModerationState", CompoundTag.CODEC);
			}
			stateData.ifPresent(
					data -> {
						state = ModerationModeState.createFromNBT(ModerationData.getInstance(server), data);
						state.updatePlayer((ServerPlayer) (Object) this);
					}
			);
		}
		defaultModeratorMode = moderationNBT.getString(DEFAULT_MODERATOR_MODE).or(
				() -> moderationNBT.getString("DefaultModeratorMode")
		).map(
				mode -> mode.toLowerCase(Locale.ROOT)
		).orElse(null);

		if (!skipSaveState) {
			Optional<CompoundTag> nbtMap;
			if (moderationNBT.contains(MODERATOR_MODE_NBT_MAP)) {
				nbtMap = moderationNBT.read(MODERATOR_MODE_NBT_MAP, CompoundTag.CODEC);
			} else {
				nbtMap = moderationNBT.read("ModeratorModeNBTMap", CompoundTag.CODEC);
			}
			nbtMap.ifPresent(moderatorModeNBTMap -> {
				savedNBT.clear();
				for (String key : moderatorModeNBTMap.keySet()) {
					moderatorModeNBTMap.getCompound(key).ifPresent(
							data -> savedNBT.put(key.toLowerCase(Locale.ROOT), PlayerDataHelper.updatePlayerData(data, level().getServer().getFixerUpper()))
					);
				}
			});
		}
	}

	@Override
	public void METAcraft_Moderation$setDefaultModerationMode(String mode) {
		defaultModeratorMode = mode;
	}

	@Override
	public Optional<String> METAcraft_Moderation$getDefaultModerationMode() {
		return Optional.ofNullable(defaultModeratorMode);
	}

	@Override
	public Map<String, CompoundTag> METAcraft_Moderation$getSavedNBT() {
		return savedNBT;
	}

	@Inject(method = "restoreFrom", at = @At("HEAD"))
	public void copyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
		state = ((ModerationPlayerData) oldPlayer).METAcraft_Moderation$getModerationMode().orElse(null);
		savedNBT = ((ModerationPlayerData) oldPlayer).METAcraft_Moderation$getSavedNBT();
		defaultModeratorMode = ((ModerationPlayerData) oldPlayer).METAcraft_Moderation$getDefaultModerationMode().orElse(null);
	}

}
