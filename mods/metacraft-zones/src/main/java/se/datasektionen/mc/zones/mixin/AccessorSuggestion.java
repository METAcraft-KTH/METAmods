package se.datasektionen.mc.zones.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Suggestion.class, remap = false)
public interface AccessorSuggestion {

	@Accessor
	@Mutable
	void setText(String text);

}
