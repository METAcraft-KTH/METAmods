package se.metacraft.nether_rifts.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A hurt piglin's answer (angry at the attacker, and its piglins with it), for the War Drummer. */
@Mixin(PiglinAi.class)
public interface PiglinAiInvoker {

	@Invoker("maybeRetaliate")
	static void netherRifts$maybeRetaliate(ServerLevel level, AbstractPiglin body, LivingEntity attacker) {
		throw new AssertionError();
	}
}
