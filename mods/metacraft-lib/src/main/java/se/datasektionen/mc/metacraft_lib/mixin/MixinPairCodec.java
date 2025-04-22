package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PairCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PairCodec.class, remap = false)
public class MixinPairCodec {

	@WrapOperation(
			method = "encode(Lcom/mojang/datafixers/util/Pair;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
			at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encode(Ljava/lang/Object;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;")
	)
	public <T> DataResult<T> encode(
			Codec<?> instance, Object o, DynamicOps<T> dynamicOps,
			Object rest, Operation<DataResult<T>> original
	) { //Hacky fix for a strange class cast exception with player datafixers from 1.21.4 to 1.21.5.
		return runSafely(instance, o, dynamicOps, rest, original);
	}

	@Unique
	private static <T> DataResult<T> runSafely(
			Codec<?> instance, Object o, DynamicOps<T> dynamicOps,
			Object rest, Operation<DataResult<T>> original
	) {
		try {
			return original.call(instance, o, dynamicOps, rest);
		} catch (ClassCastException e) {
			if (o instanceof Pair<?,?> p) {
				var attempt1 = runSafely(instance, p.getFirst(), dynamicOps, rest, original);
				if (attempt1.isError()) {
					var attempt2 = runSafely(instance, p.getSecond(), dynamicOps, rest, original);
					if (attempt2.isError()) {
						var result = attempt1.hasResultOrPartial() ? attempt1.resultOrPartial() : attempt2.resultOrPartial();
						if (result.isPresent()) {
							return attempt1.mapError(err -> DataResult.appendMessages(err, attempt2.error().get().message())).setPartial(result.get());
						} else {
							return attempt1.mapError(err -> DataResult.appendMessages(err, attempt2.error().get().message()));
						}
					}
					return attempt2;
				}
				return attempt1;
			}
			return DataResult.error(e::getMessage);
		}
	}

}
