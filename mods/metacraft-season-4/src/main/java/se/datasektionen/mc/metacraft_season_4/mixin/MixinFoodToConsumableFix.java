package se.datasektionen.mc.metacraft_season_4.mixin;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.datafixer.fix.FoodToConsumableFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

@Mixin(FoodToConsumableFix.class)
public class MixinFoodToConsumableFix {

	@Unique
	private static Optional<Pair<String, List<Dynamic<?>>>> getDescriptionAndLore(Dynamic<?> dynamic) {
		var lore = dynamic.get("minecraft:lore").result();
		if (lore.isPresent()) {
			List<Dynamic<?>> currentLore = lore.get().asList(e -> e);
			var top = currentLore.removeFirst();
			var actualTop = top.asString();
			if (actualTop.isError()) return Optional.empty();
			return Optional.of(Pair.of(actualTop.getOrThrow(), currentLore));
		}
		return Optional.empty();
	}

	@Unique
	private static String getTranslationKey(Dynamic<?> instrument) {
		var soundIDObject = instrument.get("sound_event");
		if (soundIDObject.asString().isError()) {
			soundIDObject = soundIDObject.get("sound_id");
		}

		String soundID = soundIDObject.asString("error:error");
		if (!soundID.contains(":")) {
			soundID = "minecraft:" + soundID;
		}
		return "instrument." + String.join(".", soundID.split(":")).replace("/", ".");
	}

	@Unique
	private static <T> DataResult<Dynamic<T>> parseJson(DynamicOps<T> ops, String json) {
		try {
			return DataResult.success(new Dynamic<>(
					ops, JsonOps.INSTANCE.convertTo(ops, JsonParser.parseString(json))
			));
		} catch (JsonParseException parse) {
			return DataResult.error(parse::getMessage);
		}
	}

	//Lambda inside makeRule.
	@ModifyReturnValue(method = "method_62817", at = @At("RETURN"))
	private static <T> Dynamic<T> fixInstruments(Dynamic<T> dynamic) {
		Optional<Dynamic<T>> instrument = dynamic.get("minecraft:instrument").result();
		return instrument.map(value -> {
			if (value.asString().isSuccess()) return dynamic;
			Dynamic<T> resultDynamic = dynamic;

			Dynamic<T> description = null;
			var descAndLore = getDescriptionAndLore(dynamic);
			if (descAndLore.isPresent()) {
				var desc = parseJson(dynamic.getOps(), descAndLore.get().getFirst()).resultOrPartial();
				if (desc.isPresent()) {
					description = desc.get();
					resultDynamic = resultDynamic.set("minecraft:lore", dynamic.createList(descAndLore.get().getSecond().stream()));
				}
			}
			if (description == null) {
				description = parseJson(dynamic.getOps(), getTranslationKey(value)).resultOrPartial().orElse(null);
				if (description == null) {
					description = new Dynamic<>(dynamic.getOps());
					description.set("text", dynamic.createString("Error, unable to update goat horn description!"));
				}
			}

			return resultDynamic.set("minecraft:instrument", value.set(
					"description", description
			).set(
					"use_duration", dynamic.createFloat(
							value.get("use_duration").get().result().map(
									duration -> duration.asNumber(7*20).floatValue() / 20.0f
							).orElse(7.0f)
					)
			));
		}).orElse(dynamic);
	}

}
