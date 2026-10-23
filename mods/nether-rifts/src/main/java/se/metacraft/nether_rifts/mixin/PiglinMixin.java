package se.metacraft.nether_rifts.mixin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.ServerLevelAccessor;
import se.metacraft.nether_rifts.mob.WarDrummer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A piglin spawning naturally in the nether now and then brings a War Drummer along (see {@link WarDrummer#joinNaturalSpawn}). */
@Mixin(Piglin.class)
public abstract class PiglinMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void netherRifts$naturalDrummer(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
		WarDrummer.joinNaturalSpawn(level, (Piglin) (Object) this, reason);
	}
}
