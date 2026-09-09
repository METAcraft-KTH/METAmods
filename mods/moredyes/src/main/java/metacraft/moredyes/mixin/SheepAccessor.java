package metacraft.moredyes.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.sheep.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Sheep.class)
public interface SheepAccessor {
    @Accessor("DATA_WOOL_ID")
    static EntityDataAccessor<Byte> moredyes$woolId() {
        throw new AssertionError();
    }

    @Accessor("eatAnimationTick")
    int moredyes$eatAnimationTick();
}
