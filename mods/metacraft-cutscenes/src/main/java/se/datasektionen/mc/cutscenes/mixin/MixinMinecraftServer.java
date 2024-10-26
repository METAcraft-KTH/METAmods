package se.datasektionen.mc.cutscenes.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import se.datasektionen.mc.cutscenes.CutscenesConfig;
import se.datasektionen.mc.cutscenes.extension.MinecraftServerExtension;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer implements MinecraftServerExtension {

	@Unique
	private CutscenesConfig config;


	@Override
	public CutscenesConfig metacraft_cutscenes$getConfig() {
		return config;
	}

	@Override
	public void metacraft_cutscenes$setConfig(CutscenesConfig config) {
		this.config = config;
	}

}
