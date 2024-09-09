package se.datasektionen.mc.resource_packs.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.resource_packs.ResourcePacks;
import se.datasektionen.mc.resource_packs.ServerPlayerEntityExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity implements ServerPlayerEntityExtension {

	@Unique
	private static final MapCodec<Set<UUID>> RESOURCE_PACKS = Uuids.SET_CODEC.optionalFieldOf("metacraft_resource_packs", Set.of());

	@Unique
	private final Set<UUID> resourcePacks = new HashSet<>();

	public MixinServerPlayerEntity(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	@Override
	public Set<UUID> metacraft$getResourcePacks() {
		return resourcePacks;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
	public void writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
		final var ops = getRegistryManager().getOps(NbtOps.INSTANCE);
		var builder = ops.mapBuilder();
		RESOURCE_PACKS.encode(resourcePacks, ops, builder);
		builder.build(nbt).resultOrPartial(ResourcePacks.LOGGER::error).ifPresent(result -> {
			if (result instanceof NbtCompound c && result != nbt) {
				nbt.copyFrom(c);
			}
		});
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
	public void readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		final var ops = getRegistryManager().getOps(NbtOps.INSTANCE);
		ops.getMap(nbt).ifSuccess(map -> {
			RESOURCE_PACKS.decode(ops, map).resultOrPartial(ResourcePacks.LOGGER::error).ifPresent(
					result -> {
						resourcePacks.clear();
						resourcePacks.addAll(result);
					}
			);
		});
	}

	@Inject(method = "copyFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
		resourcePacks.clear();
		resourcePacks.addAll(((ServerPlayerEntityExtension) oldPlayer).metacraft$getResourcePacks());
	}
}
