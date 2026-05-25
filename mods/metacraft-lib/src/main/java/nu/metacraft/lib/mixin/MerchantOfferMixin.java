package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.extensions.MerchantOfferExtensions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantOffer.class)
public class MerchantOfferMixin implements MerchantOfferExtensions {
	@Shadow
	private int uses;
	@Shadow
	private int maxUses;

	@Unique
	private int maxUsesPerPlayer;
	@Unique
	private Object2IntMap<UUID> usesPerPlayer;

	@Inject(
			at = @At("RETURN"),
			method = "<init>(Lnet/minecraft/world/item/trading/ItemCost;Ljava/util/Optional;Lnet/minecraft/world/item/ItemStack;IIZIIFI)V"
	)
	public void init(
			ItemCost baseCostA, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<ItemCost> costB,
			ItemStack result, int _uses, int maxUses, boolean rewardExp, int specialPriceDiff, int demand,
			float priceMultiplier, int xp, CallbackInfo ci
	) {
		this.maxUsesPerPlayer = -1;
		this.usesPerPlayer = new Object2IntOpenHashMap<>(0);
	}

	@Unique
	private static final Codec<Map<UUID, Integer>> USES_PER_PLAYER_CODEC = Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.INT);

	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
	private static Codec<MerchantOffer> wrapCodec(Codec<MerchantOffer> original) {
		MapCodec<MerchantOffer> codec = ((MapCodec.MapCodecCodec<MerchantOffer>) original).codec();
		return new MapCodec<MerchantOffer>() {
			@Override
			public <T> RecordBuilder<T> encode(MerchantOffer input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				var maxUsesPerPlayer = ((MerchantOfferExtensions) input).metacraft$getMaxUsesPerPlayer();
				var usesPerPlayer = ((MerchantOfferExtensions) input).metacraft$getUsesPerPlayer();
				if (maxUsesPerPlayer != -1) {
					prefix.add("maxUsesPerPlayer", ops.createInt(maxUsesPerPlayer));
				}
				if (!usesPerPlayer.isEmpty()) {
					prefix.add("usesPerPlayer", USES_PER_PLAYER_CODEC.encodeStart(ops, usesPerPlayer));
				}
				return codec.encode(input, ops, prefix);
			}

			@Override
			public <T> DataResult<MerchantOffer> decode(DynamicOps<T> ops, MapLike<T> input) {
				var maxUsesPerPlayer = input.get("maxUsesPerPlayer");
				var usesPerPlayer = input.get("usesPerPlayer");
				var result = codec.decode(ops, input);
				return result.map(tradeOffer -> {
					if (maxUsesPerPlayer != null) {
						((MerchantOfferExtensions) tradeOffer).metacraft$setMaxUsesPerPlayer(ops.getNumberValue(maxUsesPerPlayer, -1).intValue());
					}
					if (usesPerPlayer != null) {
						var mapResult = USES_PER_PLAYER_CODEC.parse(ops, usesPerPlayer).resultOrPartial(METAcraftLib.LOGGER::error);
						mapResult.ifPresent(map -> {
							var existingMap = ((MerchantOfferExtensions) tradeOffer).metacraft$getUsesPerPlayer();
							existingMap.clear();
							existingMap.putAll(map);
						});
					}
					return tradeOffer;
				});
			}

			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return codec.keys(ops);
			}
		}.codec();
	}

	public int metacraft$getMaxUsesPerPlayer() {
		return this.maxUsesPerPlayer;
	}

	public void metacraft$setMaxUsesPerPlayer(int maxUsesPerPlayer) {
		this.maxUsesPerPlayer = maxUsesPerPlayer;
	}

	public Object2IntMap<UUID> metacraft$getUsesPerPlayer() {
		return this.usesPerPlayer;
	}

	@Override
	public void metacraft$setUses(int uses) {
		this.uses = uses;
	}

	public void metacraft$setMaxUses(int maxUses) {
		this.maxUses = maxUses;
	}
}
