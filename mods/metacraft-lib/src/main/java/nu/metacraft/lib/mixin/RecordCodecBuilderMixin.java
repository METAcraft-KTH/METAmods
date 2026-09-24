package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.config.comments.RecordCodecBuilderExtension;
import nu.metacraft.lib.config.comments.codecs.MapCodecWithComments;
import nu.metacraft.lib.util.helper.CodecAccessHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Mixin(RecordCodecBuilder.class)
public class RecordCodecBuilderMixin implements RecordCodecBuilderExtension {

	@Unique
	private final Map<Object, String> comments = new HashMap<>();

	@Inject(method = "<init>", at = @At("RETURN"))
	public void init(Function<?, ?> getter, Function<?, ?> encoder, MapDecoder<?> decoder, CallbackInfo ci) {
		if (comments.isEmpty() && decoder instanceof MapCodecWithComments<?> c) {
			comments.putAll(c.comments());
			if (comments.isEmpty()) {
				c.getSelfComment().ifPresent(comment -> {
					var list = CodecAccessHelper.getKnownKeys(c.getCodec()).toList();
					if (list.size() == 1) {
						comments.put(list.getFirst(), comment);
					}
				});
			}
		}
	}

	@ModifyReturnValue(method = "build", at = @At("RETURN"))
	private static <O> MapCodec<O> build(
		MapCodec<O> original, @Local(name = "builder") RecordCodecBuilder<O, O> builder
	) {
		return new MapCodecWithComments<>(original, comments(builder), Optional.empty());
	}

	@Unique
	private static Map<Object, String> comments(RecordCodecBuilder<?, ?> builder) {
		return ((RecordCodecBuilderExtension) (Object) builder).metacraft$comments();
	}

	@ModifyExpressionValue(
		method = "dependent",
		at = @At(
			value = "NEW",
			target = "(Ljava/util/function/Function;Ljava/util/function/Function;Lcom/mojang/serialization/MapDecoder;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;"
		)
	)
	public RecordCodecBuilder<?, ?> dependent(RecordCodecBuilder<?, ?> original) {
		comments(original).putAll(comments);
		return original;
	}

	@Override
	public Map<Object, String> metacraft$comments() {
		return comments;
	}

	@Mixin(RecordCodecBuilder.Instance.class)
	public static class Instance {
		@Unique
		private static Map<Object, String> comments(RecordCodecBuilder<?, ?> builder) {
			return ((RecordCodecBuilderExtension) (Object) builder).metacraft$comments();
		}

		@ModifyExpressionValue(
			method = "lambda$lift1$2",
			at = @At(
				value = "NEW",
				target = "(Ljava/util/function/Function;Ljava/util/function/Function;Lcom/mojang/serialization/MapDecoder;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;"
			)
		)
		public RecordCodecBuilder<?, ?> onNewBuilder(
			RecordCodecBuilder<?, ?> original,
			@Local(name = "f") RecordCodecBuilder<?, ?> f,
			@Local(name = "a") RecordCodecBuilder<?, ?> a
		) {
			var comments = comments(original);
			comments.putAll(comments(f));
			comments.putAll(comments(a));
			return original;
		}

		@ModifyExpressionValue(
			method = "ap2",
			at = @At(
				value = "NEW",
				target = "(Ljava/util/function/Function;Ljava/util/function/Function;Lcom/mojang/serialization/MapDecoder;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;"
			)
		)
		public RecordCodecBuilder<?, ?> onNewBuilder(
			RecordCodecBuilder<?, ?> original,
			@Local(name = "function") RecordCodecBuilder<?, ?> function,
			@Local(name = "fa") RecordCodecBuilder<?, ?> fa,
			@Local(name = "fb") RecordCodecBuilder<?, ?> fb
		) {
			var comments = comments(original);
			comments.putAll(comments(function));
			comments.putAll(comments(fa));
			comments.putAll(comments(fb));
			return original;
		}

		@ModifyExpressionValue(
			method = "ap3",
			at = @At(
				value = "NEW",
				target = "(Ljava/util/function/Function;Ljava/util/function/Function;Lcom/mojang/serialization/MapDecoder;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;"
			)
		)
		public RecordCodecBuilder<?, ?> onNewBuilder(
			RecordCodecBuilder<?, ?> original,
			@Local(name = "function") RecordCodecBuilder<?, ?> function,
			@Local(name = "f1") RecordCodecBuilder<?, ?> f1,
			@Local(name = "f2") RecordCodecBuilder<?, ?> f2,
			@Local(name = "f3") RecordCodecBuilder<?, ?> f3
		) {
			var comments = comments(original);
			comments.putAll(comments(function));
			comments.putAll(comments(f1));
			comments.putAll(comments(f2));
			comments.putAll(comments(f3));
			return original;
		}

		@ModifyExpressionValue(
			method = "ap4",
			at = @At(
				value = "NEW",
				target = "(Ljava/util/function/Function;Ljava/util/function/Function;Lcom/mojang/serialization/MapDecoder;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;"
			)
		)
		public RecordCodecBuilder<?, ?> onNewBuilder(
			RecordCodecBuilder<?, ?> original,
			@Local(name = "function") RecordCodecBuilder<?, ?> function,
			@Local(name = "f1") RecordCodecBuilder<?, ?> f1,
			@Local(name = "f2") RecordCodecBuilder<?, ?> f2,
			@Local(name = "f3") RecordCodecBuilder<?, ?> f3,
			@Local(name = "f4") RecordCodecBuilder<?, ?> f4
		) {
			var comments = comments(original);
			comments.putAll(comments(function));
			comments.putAll(comments(f1));
			comments.putAll(comments(f2));
			comments.putAll(comments(f3));
			comments.putAll(comments(f4));
			return original;
		}

		@ModifyExpressionValue(
			method = "map",
			at = @At(
				value = "NEW",
				target = "(Ljava/util/function/Function;Ljava/util/function/Function;Lcom/mojang/serialization/MapDecoder;)Lcom/mojang/serialization/codecs/RecordCodecBuilder;"
			)
		)
		public RecordCodecBuilder<?, ?> onNewBuilder(
			RecordCodecBuilder<?, ?> original,
			@Local(name = "unbox") RecordCodecBuilder<?, ?> unbox
		) {
			var comments = comments(original);
			comments.putAll(comments(unbox));
			return original;
		}
	}
}
