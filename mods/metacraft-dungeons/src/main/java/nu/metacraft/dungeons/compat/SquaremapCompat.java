package nu.metacraft.dungeons.compat;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.common.data.MapWorldInternal;

public class SquaremapCompat {

	public static void disableRenderer(RegistryKey<World> dim) {
		SquaremapProvider.get().getWorldIfEnabled(WorldIdentifier.parse(dim.getValue().toString())).ifPresent(world -> {
			if (world instanceof MapWorldInternal internal) {
				internal.renderManager().pauseRenders(true);
				if (internal.renderManager().isRendering()) {
					internal.renderManager().cancelRender();
				}
			}
		});
	}

}
