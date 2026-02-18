package nu.metacraft.dungeons.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.dungeons.dungeons.DungeonData;
//import xyz.jpenilla.squaremap.common.config.ConfigManager;
//import xyz.jpenilla.squaremap.common.config.WorldConfig;

//FIXME Squaremap
/*@Pseudo
@Mixin(ConfigManager.class)
public class ConfigManagerMixin {

	@ModifyReturnValue(method = "worldConfig", at = @At("RETURN"))
	public WorldConfig worldConfig(WorldConfig config, @Local(argsOnly = true) ServerLevel world) {
		if (DungeonData.getIfPresent(world).isPresent()) {
			config.MAP_ENABLED = false;
		}
		return config;
	}

}*/
