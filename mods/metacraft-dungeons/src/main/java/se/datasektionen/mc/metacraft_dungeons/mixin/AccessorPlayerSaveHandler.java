package se.datasektionen.mc.metacraft_dungeons.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.world.PlayerSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(PlayerSaveHandler.class)
public interface AccessorPlayerSaveHandler {

	@Accessor
	File getPlayerDataDir();

	@Accessor
	DataFixer getDataFixer();

}
