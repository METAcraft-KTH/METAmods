package nu.metacraft.dungeons.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.common.data.MapWorldInternal;

public class SquaremapCompat {

	public static void disableRenderer(ResourceKey<Level> dim) {
		SquaremapProvider.get().getWorldIfEnabled(WorldIdentifier.parse(dim.location().toString())).ifPresent(world -> {
			if (world instanceof MapWorldInternal internal) {
				internal.renderManager().pauseRenders(true);
				if (internal.renderManager().isRendering()) {
					internal.renderManager().cancelRender();
				}
			}
		});
	}

}
