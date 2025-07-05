package nu.metacraft.lib.condition.conditions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.context.ContextParameter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import nu.metacraft.lib.condition.METAcraftConditions;

import java.util.Set;

public class HasSkyAccess implements LootCondition {

	public static final MapCodec<HasSkyAccess> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Heightmap.Type.CODEC.fieldOf("heightmap").forGetter(type -> type.heightmap)
			).apply(instance, HasSkyAccess::new)
	);

	private final Heightmap.Type heightmap;

	public HasSkyAccess(Heightmap.Type heightmap) {
		this.heightmap = heightmap;
	}

	@Override
	public LootConditionType getType() {
		return METAcraftConditions.HAS_SKY_ACCESS;
	}

	@Override
	public boolean test(LootContext context) {
		var pos = context.get(LootContextParameters.ORIGIN);
		if (pos == null) return false;
		return context.getWorld().getTopY(heightmap, MathHelper.floor(pos.getX()), MathHelper.floor(pos.getZ())) <= pos.getY();
	}

	@Override
	public Set<ContextParameter<?>> getAllowedParameters() {
		return Set.of(LootContextParameters.ORIGIN);
	}
}
