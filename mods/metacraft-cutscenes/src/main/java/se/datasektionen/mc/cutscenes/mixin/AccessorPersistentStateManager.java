package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryOps;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PersistentStateManager.class)
public interface AccessorPersistentStateManager {

	@Invoker
	<T extends PersistentState> NbtCompound callEncode(PersistentStateType<T> type, PersistentState state, RegistryOps<NbtElement> ops);

}
