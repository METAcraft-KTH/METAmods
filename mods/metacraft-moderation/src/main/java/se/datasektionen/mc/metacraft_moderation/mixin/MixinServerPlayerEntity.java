package se.datasektionen.mc.metacraft_moderation.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.datasektionen.mc.metacraft_moderation.ModerationData;
import se.datasektionen.mc.metacraft_moderation.ModerationPlayerData;
import se.datasektionen.mc.metacraft_moderation.moderator_mode.ModerationModeState;

import java.util.HashMap;
import java.util.Locale;
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

	public MixinServerPlayerEntity(World world, GameProfile profile) {
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
				this.state, (ServerPlayerEntity) (Object) this
		);
		skipSaveState = false;
		this.state = moderationMode;
	}


	@Inject(method = "writeCustomData", at = @At("RETURN"))
	public void toNBT(WriteView nbt, CallbackInfo ci) {
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

		nbt.put(METACRAFT_MODERATION, NbtCompound.CODEC, moderationNBT);
	}

	@Inject(method = "readCustomData", at = @At("RETURN"))
	public void fromNBT(ReadView nbt, CallbackInfo ci) {
		var moderationNBT = nbt.getReadView(METACRAFT_MODERATION);
		if (!skipSaveState) {
			moderationNBT.read(MODERATION_STATE, NbtCompound.CODEC).ifPresent(
					data -> {
						state = ModerationModeState.createFromNBT(ModerationData.getInstance(server), data);
						state.updatePlayer((ServerPlayerEntity) (Object) this);

						//TODO Remove these before season 5, they are purely for backwards compatibility.
						if (nbt.read(PlayerDataHelper.STAT_HANDLER, Identifier.CODEC).isEmpty() && state.getDef().shouldHaveSeparatePlayerData()) {
							PlayerDataHelper.setStatHandler((ServerPlayerEntity) (Object) this, ModerationModeState.getFromDef(state.getDef()), false);
						}
						if (nbt.read(PlayerDataHelper.ADVANCEMENT_TRACKER, Identifier.CODEC).isEmpty() && state.getDef().shouldHaveSeparatePlayerData()) {
							PlayerDataHelper.setAdvancementTracker((ServerPlayerEntity) (Object) this, ModerationModeState.getFromDef(state.getDef()), false);
						}
						if (nbt.read(PlayerDataHelper.ANNOUNCE_ADVANCEMENTS, Identifier.CODEC).isEmpty() && !state.getDef().announceAdvancements()) {
							PlayerDataHelper.setAnnounceAdvancements((ServerPlayerEntity) (Object) this, false);
						}
					}
			);
		}
		defaultModeratorMode = moderationNBT.getOptionalString(DEFAULT_MODERATOR_MODE).map(
				mode -> mode.toLowerCase(Locale.ROOT)
		).orElse(null);

		if (!skipSaveState) {
			moderationNBT.read(MODERATOR_MODE_NBT_MAP, NbtCompound.CODEC).ifPresent(moderatorModeNBTMap -> {
				savedNBT.clear();
				for (String key : moderatorModeNBTMap.getKeys()) {
					moderatorModeNBTMap.getCompound(key).ifPresent(
							data -> savedNBT.put(key.toLowerCase(Locale.ROOT), PlayerDataHelper.updatePlayerData(data, getServer().getDataFixer()))
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
