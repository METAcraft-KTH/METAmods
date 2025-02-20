package se.datasektionen.mc.metacraft_lib.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayerEntity.class)
public interface AccessorServerPlayerEntity {

	@Invoker
	static GameMode callGameModeFromNbt(@Nullable NbtCompound nbt, String key) {
		throw new IllegalStateException("Broken Mixin");
	}

}
