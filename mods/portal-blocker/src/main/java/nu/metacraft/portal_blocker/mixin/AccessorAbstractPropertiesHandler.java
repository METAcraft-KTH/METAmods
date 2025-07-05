package nu.metacraft.portal_blocker.mixin;

import net.minecraft.server.dedicated.AbstractPropertiesHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Properties;

@Mixin(AbstractPropertiesHandler.class)
public interface AccessorAbstractPropertiesHandler {

	@Accessor
	Properties getProperties();

}
