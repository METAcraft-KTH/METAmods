package se.datasektionen.mc.simplecustomfeatures.objects;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.Registry;

public interface ObjectType<T extends BaseObject<R>, R> {

	MapCodec<T> getCodec();

	Registry<R> getRegistry();

}
