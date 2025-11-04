package nu.metacraft.lib.mixin;

import com.mojang.datafixers.DataFixer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;
import net.minecraft.world.level.storage.PlayerDataStorage;

@Mixin(PlayerDataStorage.class)
public interface AccessorPlayerSaveHandler {

	@Accessor
	File getPlayerDir();

	@Accessor
	DataFixer getFixerUpper();

}
