package metacraft.ovvar.sewing;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;

/**
 * The sounds and particles of a stand or mannequin we put up and take down ourselves, as vanilla's
 * armour stand has them: placing one, breaking one, punching one. Without these the stash session's
 * stand and the wardrobe's mannequin pop in and out of the world in silence.
 */
final class StandEffects {
	private StandEffects() {}

	/** As an armour stand item places one. */
	static void appear(Entity figure) {
		figure.level().playSound(null, figure.getX(), figure.getY(), figure.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);
	}

	/** As a broken armour stand: its break sound and a puff of planks. Call before discarding it. */
	static void vanish(Entity figure) {
		if (!(figure.level() instanceof ServerLevel level)) return;
		level.playSound(null, figure.getX(), figure.getY(), figure.getZ(), SoundEvents.ARMOR_STAND_BREAK, figure.getSoundSource(), 1.0f, 1.0f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
				figure.getX(), figure.getY(2 / 3.0), figure.getZ(), 10,
				figure.getBbWidth() / 4.0f, figure.getBbHeight() / 4.0f, figure.getBbWidth() / 4.0f, 0.05);
	}

	/**
	 * As a punched armour stand. Ours are invulnerable, so vanilla gives up before its own: an armour
	 * stand gets the client's wobble and hit sound from the entity event, anything else the sound.
	 */
	static void hit(Entity figure) {
		if (!(figure.level() instanceof ServerLevel level)) return;
		if (figure instanceof ArmorStand) {
			level.broadcastEntityEvent(figure, EntityEvent.ARMORSTAND_WOBBLE);
		} else {
			level.playSound(null, figure.getX(), figure.getY(), figure.getZ(), SoundEvents.ARMOR_STAND_HIT, figure.getSoundSource(), 0.3f, 1.0f);
		}
	}
}
