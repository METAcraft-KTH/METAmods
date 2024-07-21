package se.datasektionen.mc.metacraft_moderation.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_moderation.ModerationData;
import se.datasektionen.mc.metacraft_moderation.ModerationPlayerData;
import se.datasektionen.mc.metacraft_moderation.moderator_mode.ModerationModeState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ModerationPlayerData {

	@Shadow @Final public MinecraftServer server;

	@Unique
	private static final String METACRAFT_MODERATION = "METAcraft-Moderation";
	@Unique
	private static final String MODERATION_STATE = "ModerationState";

	@Unique
	private static final String MODERATOR_MODE_NBT_MAP = "ModeratorModeNBTMap";


	@Unique
	private static final String DEFAULT_MODERATOR_MODE = "DefaultModeratorMode";

	@Unique
	private Map<String, NbtCompound> savedNBT = new HashMap<>();

	@Unique
	private ModerationModeState state;

	@Unique
	private String defaultModeratorMode;

	@Unique
	private boolean skipSaveState = false;

	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Override
	public Optional<ModerationModeState> METAcraft_Moderation$getModerationMode() {
		return Optional.ofNullable(state);
	}

	@Override
	public void METAcraft_Moderation$setModerationMode(ModerationModeState moderationMode) {
		skipSaveState = true;
		Optional.ofNullable(moderationMode).orElse(ModerationModeState.NULL).applyToPlayer(
				this.state, (ServerPlayerEntity) (Object) this
		);
		skipSaveState = false;
		this.state = moderationMode;
	}


	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void toNBT(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound moderationNBT = new NbtCompound();
		if (state != null && !skipSaveState) {
			moderationNBT.put(MODERATION_STATE, state.toNBT());
		}

		if (!skipSaveState) {
			NbtCompound moderatorModeNBTMap = new NbtCompound();
			for (var entry : savedNBT.entrySet()) {
				moderatorModeNBTMap.put(entry.getKey(), entry.getValue());
			}
			moderationNBT.put(MODERATOR_MODE_NBT_MAP, moderatorModeNBTMap);
		}

		if (defaultModeratorMode != null) {
			moderationNBT.putString(DEFAULT_MODERATOR_MODE, defaultModeratorMode);
		}

		nbt.put(METACRAFT_MODERATION, moderationNBT);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void fromNBT(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound moderationNBT = nbt.getCompound(METACRAFT_MODERATION);
		if (moderationNBT.contains(MODERATION_STATE) && !skipSaveState) {
			state = ModerationModeState.createFromNBT(ModerationData.getInstance(server), moderationNBT.getCompound(MODERATION_STATE));
			state.updatePlayer((ServerPlayerEntity) (Object) this);
		}
		if (moderationNBT.contains(DEFAULT_MODERATOR_MODE)) {
			defaultModeratorMode = moderationNBT.getString(DEFAULT_MODERATOR_MODE);
		} else {
			defaultModeratorMode = null;
		}

		if (moderationNBT.contains(MODERATOR_MODE_NBT_MAP) && !skipSaveState) {
			savedNBT.clear();
			NbtCompound moderatorModeNBTMap = moderationNBT.getCompound(MODERATOR_MODE_NBT_MAP);
			for (String key : moderatorModeNBTMap.getKeys()) {
				savedNBT.put(key, moderatorModeNBTMap.getCompound(key));
			}
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
	public Map<String, NbtCompound> METAcraft_Moderation$getSavedNBT() {
		return savedNBT;
	}

	@Inject(method = "copyFrom", at = @At("HEAD"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		state = ((ModerationPlayerData) oldPlayer).METAcraft_Moderation$getModerationMode().orElse(null);
		savedNBT = ((ModerationPlayerData) oldPlayer).METAcraft_Moderation$getSavedNBT();
		defaultModeratorMode = ((ModerationPlayerData) oldPlayer).METAcraft_Moderation$getDefaultModerationMode().orElse(null);
	}

}
