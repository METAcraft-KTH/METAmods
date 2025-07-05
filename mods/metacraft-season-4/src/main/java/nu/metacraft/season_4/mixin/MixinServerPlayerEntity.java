package nu.metacraft.season_4.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
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
import nu.metacraft.season_4.extensions.ServerPlayerEntityExtensions;
import nu.metacraft.season_4.end.EndBossPlayerState;

@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtensions {

	public MixinServerPlayerEntity(World world, GameProfile profile) {
		super(world, profile);
	}

	@Shadow public abstract void sendMessage(Text message, boolean overlay);

	@Shadow public abstract ServerWorld getWorld();

	@Unique
	private static final String CAMPUS_LODESTONE_BACK = "CampusLodestoneBack";

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

	@Inject(method = "writeCustomData", at = @At("HEAD"))
	public void writeNBT(WriteView nbt, CallbackInfo ci) {
		if (this.campusLodestoneBackWorld != null && this.campusLodestoneBackPos != null) {
			NbtCompound backData = new NbtCompound();
			backData.putString("world", this.campusLodestoneBackWorld.getValue().toString());
			backData.putInt("x", this.campusLodestoneBackPos.getX());
			backData.putInt("y", this.campusLodestoneBackPos.getY());
			backData.putInt("z", this.campusLodestoneBackPos.getZ());
			nbt.put(CAMPUS_LODESTONE_BACK, NbtCompound.CODEC, backData);
		}
	}

	@Inject(method = "readCustomData", at = @At("HEAD"))
	public void readNbt(ReadView nbt, CallbackInfo ci) {
		nbt.read(CAMPUS_LODESTONE_BACK, NbtCompound.CODEC).ifPresent(backData -> {
			Identifier worldKey = Identifier.tryParse(backData.getString("world", ""));
			if (worldKey != null) {
				this.campusLodestoneBackWorld = RegistryKey.of(RegistryKeys.WORLD, worldKey);
			}
			this.campusLodestoneBackPos = new BlockPos(
					backData.getInt("x", 0),
					backData.getInt("y", 0),
					backData.getInt("z", 0)
			);
		});
	}

	@ModifyReturnValue(method = "getRespawnTarget", at = @At("RETURN"))
	public TeleportTarget getRespawnTarget(TeleportTarget original) {
		return EndBossPlayerState.getInstance(this.getWorld()).filter(EndBossPlayerState::hasBoss).map(
				state -> state.getPlayerSpawnPoint(this.getWorld(), this)
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
