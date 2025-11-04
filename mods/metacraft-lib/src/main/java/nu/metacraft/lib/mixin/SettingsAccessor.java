package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Properties;
import net.minecraft.server.dedicated.Settings;

@Mixin(Settings.class)
public interface SettingsAccessor {

	@Accessor
	Properties getProperties();

}
