package se.metacraft.nether_rifts.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An interaction's box size, which a Chained Ghast's parts send to clients that see them as interactions. */
@Mixin(Interaction.class)
public interface InteractionAccessor {

	@Accessor("DATA_WIDTH_ID")
	static EntityDataAccessor<Float> netherRifts$width() {
		throw new AssertionError();
	}

	@Accessor("DATA_HEIGHT_ID")
	static EntityDataAccessor<Float> netherRifts$height() {
		throw new AssertionError();
	}
}
