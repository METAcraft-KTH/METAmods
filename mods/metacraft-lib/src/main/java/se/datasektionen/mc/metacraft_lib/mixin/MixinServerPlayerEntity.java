package se.datasektionen.mc.metacraft_lib.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_lib.METAcraftData;
import se.datasektionen.mc.metacraft_lib.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_lib.util.helper.EntityTrackerHelper;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	@Shadow public ServerPlayNetworkHandler networkHandler;

	@Shadow public abstract ServerWorld getServerWorld();

	@Shadow public abstract void sendMessage(Text message, boolean overlay);


	@Unique
	private String customName;

	@Unique
	private boolean showInGUI = true;

	@Unique
	private static final String CUSTOM_PLAYER_NAME = "CustomPlayerName";

	@Unique
	private static final String CUSTOM_PLAYER_NAME_SHOW_IN_GUI = "CustomPlayerNameShowInGUI";


	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		customName = ((ServerPlayerEntityExtensions) oldPlayer).METAcraft_Moderation$getCustomName();
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		if (customName != null) {
			nbt.putString(CUSTOM_PLAYER_NAME, customName);
			nbt.putBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI, showInGUI);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void readNBT(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(CUSTOM_PLAYER_NAME)) {
			boolean show = nbt.contains(CUSTOM_PLAYER_NAME_SHOW_IN_GUI) ? nbt.getBoolean(CUSTOM_PLAYER_NAME_SHOW_IN_GUI) : showInGUI;
			METAcraft_Moderation$setCustomName(nbt.getString(CUSTOM_PLAYER_NAME), show);
		} else {
			METAcraft_Moderation$setCustomName(null, true);
		}
	}

	@Override
	public void METAcraft_Moderation$setCustomName(String customName, boolean showInGUI) {
		this.customName = customName;
		this.showInGUI = showInGUI;
		if (showInGUI) {
			METAcraftData.getInstance(getServer()).setName(getUuid(), customName);
		}
		var tracker = EntityTrackerHelper.getEntityTrackers(this.getServerWorld()).get(this.getId());
		if (tracker == null) {
			return;
		}
		for (var player : this.getWorld().getPlayers()) {
			if (player != this) {
				((ServerPlayerEntity) player).networkHandler.sendPacket(
						new PlayerRemoveS2CPacket(ImmutableList.of(this.getUuid()))
				);
				tracker.stopTracking((ServerPlayerEntity) player);
				((ServerPlayerEntity) player).networkHandler.sendPacket(
						PlayerListS2CPacket.entryFromPlayer(ImmutableList.of((ServerPlayerEntity) (Object) this))
				);
				tracker.updateTrackedStatus((ServerPlayerEntity) player);
			}
		}
	}

	@Override
	public boolean METAcraft_Moderation$showInGUI() {
		return showInGUI;
	}

	@Override
	public String METAcraft_Moderation$getCustomName() {
		return customName;
	}

}
