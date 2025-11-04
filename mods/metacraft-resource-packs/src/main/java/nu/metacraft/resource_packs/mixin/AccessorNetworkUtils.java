package nu.metacraft.resource_packs.mixin;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.util.HttpUtil;

@Mixin(HttpUtil.class)
public interface AccessorNetworkUtils {

	@Invoker
	static HashCode callHashFile(Path path, HashFunction hashFunction) throws IOException {
		throw new IllegalStateException("Mixin Error");
	}

}
