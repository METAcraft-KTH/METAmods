package se.metacraft.config.mixin;

import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.impl.serializer.CommentSerializer;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.config.extensions.JanksonObjectExtension;
import se.metacraft.config.util.BuilderWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

@Mixin(JsonObject.class)
public class JsonObjectMixin implements JanksonObjectExtension {

	@Unique
	private final List<String> tailComments = new ArrayList<>();

	@Override
	public void metacraft$addTailComment(String tailComment) {
		tailComments.add(tailComment);
	}

	@Inject(
		method = "toJson(Ljava/io/Writer;Lblue/endless/jankson/JsonGrammar;I)V",
		at = @At(
			value = "JUMP",
			opcode = Opcodes.IFNE
		),
		slice = @Slice(
			from = @At(
				value = "FIELD",
				target = "Lblue/endless/jankson/JsonGrammar;printWhitespace:Z",
				opcode = Opcodes.GETFIELD,
				ordinal = 4
			),
			to = @At(
				value = "INVOKE",
				target = "Ljava/util/List;size()I",
				ordinal = 4
			)
		)
	)
	public void toJson(
		Writer w, JsonGrammar grammar, int depth, CallbackInfo ci,
		@Local(name = "effectiveDepth") int effectiveDepth // This is valid, not sure why IntelliJ is complaining.
	) throws IOException {
		for (var comment : tailComments) {
			for (int i = 0; i < effectiveDepth+1; i++) {
				w.append("\t");
			}
			var builderWriter = new BuilderWriter();
			CommentSerializer.print(builderWriter, comment, effectiveDepth, grammar);
			var result = builderWriter.result();
			while (result.endsWith("\t")) {
				result = result.substring(0, result.length()-1);
			}
			w.append(result);
		}
	}
}
