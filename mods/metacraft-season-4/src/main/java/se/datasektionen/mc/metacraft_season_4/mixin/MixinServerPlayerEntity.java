package se.datasektionen.mc.metacraft_season_4.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_season_4.extensions.ServerPlayerEntityExtensions;
import se.datasektionen.mc.metacraft_season_4.end.EndBossPlayerState;

@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	@Shadow public abstract void sendMessage(Text message, boolean overlay);

	@Shadow public abstract ServerWorld getServerWorld();

	@Unique
	private static final String CAMPUS_LODESTONE_BACK = "CampusLodestoneBack";

	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Unique
	private boolean attackedThroughFriendlyFire = false;


	@Unique
	@Nullable
	private RegistryKey<World> campusLodestoneBackWorld;

	@Unique
	@Nullable
	private BlockPos campusLodestoneBackPos;

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		this.campusLodestoneBackWorld = ((MixinServerPlayerEntity) (Object) oldPlayer).campusLodestoneBackWorld;
		this.campusLodestoneBackPos = ((MixinServerPlayerEntity) (Object) oldPlayer).campusLodestoneBackPos;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
	public void writeNBT(NbtCompound nbt, CallbackInfo ci) {
		if (this.campusLodestoneBackWorld != null && this.campusLodestoneBackPos != null) {
			NbtCompound backData = new NbtCompound();
			backData.putString("world", this.campusLodestoneBackWorld.getValue().toString());
			backData.putInt("x", this.campusLodestoneBackPos.getX());
			backData.putInt("y", this.campusLodestoneBackPos.getY());
			backData.putInt("z", this.campusLodestoneBackPos.getZ());
			nbt.put(CAMPUS_LODESTONE_BACK, backData);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
	public void readNbt(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(CAMPUS_LODESTONE_BACK, NbtElement.COMPOUND_TYPE)) {
			NbtCompound backData = nbt.getCompound(CAMPUS_LODESTONE_BACK);
			Identifier worldKey = Identifier.tryParse(backData.getString("world"));
			if (worldKey != null) {
				this.campusLodestoneBackWorld = RegistryKey.of(RegistryKeys.WORLD, worldKey);
			}
			this.campusLodestoneBackPos = new BlockPos(
				backData.getInt("x"),
				backData.getInt("y"),
				backData.getInt("z")
			);
		}
	}

	@ModifyReturnValue(method = "getRespawnTarget", at = @At("RETURN"))
	public TeleportTarget getRespawnTarget(TeleportTarget original) {
		return EndBossPlayerState.getInstance(this.getServerWorld()).filter(EndBossPlayerState::hasBoss).map(
				state -> state.getPlayerSpawnPoint(this.getServerWorld(), this)
		).orElse(original);
	}

	@ModifyReturnValue(method = "shouldDamagePlayer", at = @At("RETURN"))
	public boolean shouldDamagePlayer(boolean original) {
		if (attackedThroughFriendlyFire) return true;
		return original;
	}

	@Override
	@Nullable
	public RegistryKey<World> metacraft_core$getCampusLodestoneBackWorld() {
		return this.campusLodestoneBackWorld;
	}

	@Override
	@Nullable
	public BlockPos metacraft_core$getCampusLodestoneBackPos() {
		return this.campusLodestoneBackPos;
	}

	@Override
	public void metacraft_core$setCampusLodestoneBackPos(RegistryKey<World> world, BlockPos pos) {
		this.campusLodestoneBackWorld = world;
		this.campusLodestoneBackPos = pos;
	}

	@Override
	public void metacraft_core$unsetCampusLodestoneBackPos() {
		this.campusLodestoneBackWorld = null;
		this.campusLodestoneBackPos = null;
	}


	@Override
	public void metacraft_season_4$setAttackedThroughFriendlyFire(boolean state) {
		this.attackedThroughFriendlyFire = state;
	}
}
