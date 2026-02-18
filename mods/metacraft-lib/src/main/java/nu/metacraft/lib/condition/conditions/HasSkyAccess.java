package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.lib.condition.METAcraftConditions;

import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class HasSkyAccess implements LootItemCondition {

	public static final MapCodec<HasSkyAccess> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Heightmap.Types.CODEC.fieldOf("heightmap").forGetter(type -> type.heightmap)
			).apply(instance, HasSkyAccess::new)
	);

	private final Heightmap.Types heightmap;

	public HasSkyAccess(Heightmap.Types heightmap) {
		this.heightmap = heightmap;
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return METAcraftConditions.HAS_SKY_ACCESS;
	}

	@Override
	public boolean test(LootContext context) {
		var pos = context.getOptionalParameter(LootContextParams.ORIGIN);
		if (pos == null) return false;
		return context.getLevel().getHeight(heightmap, Mth.floor(pos.x()), Mth.floor(pos.z())) <= pos.y();
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Set.of(LootContextParams.ORIGIN);
	}
}
