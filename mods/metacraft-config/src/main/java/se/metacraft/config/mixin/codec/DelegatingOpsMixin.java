package se.metacraft.config.mixin.codec;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.DelegatingOps;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import se.metacraft.config.comments.ops.OpsWithComments;
import se.metacraft.config.comments.ops.RecordBuilderWithComments;

import java.util.Map;

@Mixin(DelegatingOps.class)
public abstract class DelegatingOpsMixin<T> implements OpsWithComments<T> {

	@Shadow @Final protected DynamicOps<T> delegate;

	@Override
	public DataResult<T> metacraft$withComments(T value, Map<T, Component> comments) {
		if (delegate instanceof OpsWithComments<T> withComments) {
			return withComments.metacraft$withComments(value, comments);
		}
		return DataResult.success(value);
	}

	@Override
	public DataResult<T> metacraft$withComments(T value, Int2ObjectMap<Component> comments) {
		if (delegate instanceof OpsWithComments<T> withComments) {
			return withComments.metacraft$withComments(value, comments);
		}
		return DataResult.success(value);
	}

	@Mixin(targets = "net.minecraft.resources.DelegatingOps$DelegateRecordBuilder")
	public abstract static class DelegateRecordBuilderMixin<T> implements RecordBuilderWithComments<T> {

		@Shadow @Final private RecordBuilder<T> original;

		@Override
		public RecordBuilderWithComments<T> metacraft$addComments(Map<T, Component> comments) {
			if (original instanceof RecordBuilderWithComments<T> withComments) {
				withComments.metacraft$addComments(comments);
			}
			return this;
		}

	}
}
