package se.datasektionen.mc.metacraft_season_4.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableShoulderEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import se.datasektionen.mc.metacraft_season_4.entity.ai.Season4MemoryModules;

@Mixin(ParrotEntity.class)
public abstract class MixinParrotEntity extends TameableShoulderEntity {

	protected MixinParrotEntity(EntityType<? extends TameableShoulderEntity> entityType, World world) {
		super(entityType, world);
	}

	@ModifyConstant(
		method = "flapWings",
		constant = @Constant(
			doubleValue = 0.6
		)
	)
	private double flapWings(double constant) {
		if (this.getBrain().hasMemoryModule(Season4MemoryModules.STUNNED)) {
			return 1;
		}
		return constant;
	}

}
