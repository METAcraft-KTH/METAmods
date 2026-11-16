package se.metacraft.ovvar.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keyring: is the player still digging? A held button sends nothing between START and STOP. */
@Mixin(ServerPlayerGameMode.class)
public interface KeyringDiggingAccessor {
	@Accessor("isDestroyingBlock")
	boolean isOvvarDestroyingBlock();

	@Accessor("hasDelayedDestroy")
	boolean isOvvarDelayedDestroy();
}
