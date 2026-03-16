package nu.metacraft.lib.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.throwables.MixinError;

import java.util.List;
import java.util.function.Function;

@Mixin(NbtPathArgument.NbtPath.class)
public interface NbtPathAccessor {

	@Accessor
	NbtPathArgument.Node[] getNodes();

	@Invoker
	int callEstimatePathDepth();

	@Invoker
	static int callApply(List<Tag> list, Function<Tag, Integer> function) {
		throw new MixinError("Failed to apply");
	}

	@Invoker
	List<Tag> callGetOrCreateParents(Tag tag) throws CommandSyntaxException;

}
