package nu.metacraft.zones.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import nu.metacraft.zones.ZoneManager;
import nu.metacraft.zones.util.LocationPredicateAccess;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

@Mixin(LocationPredicate.class)
public class LocationPredicateMixin implements LocationPredicateAccess {

	@Unique
	private Optional<String> zone = Optional.empty();

	@Unique
	private static final String ZONE = "metacraft:zone";

	@Inject(
		method = "matches",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/advancements/criterion/LocationPredicate;canSeeSky:Ljava/util/Optional;"
		),
		cancellable = true
	)
	public void test(
			ServerLevel world, double x, double y, double z, CallbackInfoReturnable<Boolean> cir
	) {
		if (zone.isPresent()) {
			var zone = this.zone.get();
			var manager = ZoneManager.getInstance(world.getServer());
			if (manager.containsZone(zone)) {
				if (!manager.getZone(zone).contains(world.dimension(), BlockPos.containing(x, y, z))) {
					cir.setReturnValue(false);
				}
			}
		}
	}

	@ModifyExpressionValue(
		method = "<clinit>",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
		)
	)
	private static Codec<LocationPredicate> clinit(Codec<LocationPredicate> original) {
		MapCodec<LocationPredicate> codec = ((MapCodec.MapCodecCodec<LocationPredicate>) original).codec();
		return new MapCodec<LocationPredicate>() {
			@Override
			public <T> RecordBuilder<T> encode(LocationPredicate input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				var z = ((LocationPredicateAccess) (Object) input).metacraft$getZone();
				z.ifPresent(s -> prefix.add(ZONE, ops.createString(s)));
				return codec.encode(input, ops, prefix);
			}

			@Override
			public <T> DataResult<LocationPredicate> decode(DynamicOps<T> ops, MapLike<T> input) {
				var z = input.get(ZONE);
				var result = codec.decode(ops, input);
				if (z != null) {
					return ops.getStringValue(z).flatMap(
							zone -> result.map(predicate -> {
								((LocationPredicateAccess) (Object) predicate).metacraft$setZone(zone);
								return predicate;
							})
					);
				} else {
					return result;
				}
			}

			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return codec.keys(ops);
			}
		}.codec();
	}

	@Override
	public Optional<String> metacraft$getZone() {
		return zone;
	}

	@Override
	public void metacraft$setZone(String zone) {
		this.zone = Optional.ofNullable(zone);
	}
}
