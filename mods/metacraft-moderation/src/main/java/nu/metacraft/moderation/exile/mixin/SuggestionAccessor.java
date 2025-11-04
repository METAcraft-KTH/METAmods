package nu.metacraft.moderation.exile.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Suggestion.class, remap = false)
public interface SuggestionAccessor {

	@Accessor
	@Mutable
	void setText(String text);

}
