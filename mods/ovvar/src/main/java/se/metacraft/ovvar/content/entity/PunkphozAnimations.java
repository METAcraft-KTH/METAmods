package se.metacraft.ovvar.content.entity;

import de.tomalbrc.bil.api.Animator;
import net.minecraft.util.Mth;

/**
 * Plays vanilla {@code VillagerModel.setupAnim} on Punkphöz's model from the server. BIL bakes each animation into whole-model
 * frames, shows one animation at a time (the highest priority), and re-poses the model only while one plays, so the model's
 * three animations are:
 * <ul>
 * <li>{@value #IDLE}: the rest pose, always playing under the others, so a standing Punkphöz still turns his body and head and
 * comes back to rest after a walk or a shake.</li>
 * <li>{@value #WALK}: the legs' stride, {@code xRot = ±cos(walkAnimationPos * 0.6662) * 1.4 * walkAnimationSpeed * 0.5}, baked as
 * {@value #SPEED_BANDS} speed bands of one {@value #STRIDE_FRAMES}-frame stride each. It is not run on its clock: every tick
 * {@link #walkFrame} picks the frame for his walk state, so stride and swing follow his movement as vanilla's do.</li>
 * <li>{@value #SHAKE}: the unhappy "no", {@code head.xRot = 0.4}, {@code head.zRot = 0.3 * sin(0.45 * ticks)}, while his unhappy
 * counter runs. It outranks the walk. Its loop is 56 ticks, four periods of the roll to within a hundredth of one, so a shake
 * kept going by another ask wraps without a jump.</li>
 * </ul>
 * Head yaw and pitch are BIL's own: {@code LivingEntityHolder} turns the bone named {@code head} and everything under it.
 */
public final class PunkphozAnimations {

	public static final String IDLE = "idle";
	public static final String WALK = "walk";
	public static final String SHAKE = "shake";
	/** Frames per stride in {@value #WALK}: frame j of a band is stride phase j / 20. */
	public static final int STRIDE_FRAMES = 20;
	/** Speed bands in {@value #WALK}: band k swings at walkAnimationSpeed (k + 1) / 10. */
	public static final int SPEED_BANDS = 10;
	/** Below this walk speed vanilla's swing is under 2°, and he stands. */
	public static final float STILL = 0.05f;
	/** Vanilla's stride rate: radians of leg phase per unit of walkAnimationPos. */
	private static final float STRIDE_RATE = 0.6662f;

	private PunkphozAnimations() {}

	/** The {@value #WALK} frame for a walk speed and position: the nearest speed band, then the nearest stride phase in it. */
	public static int walkFrame(float speed, float position) {
		int band = Mth.clamp(Math.round(speed * SPEED_BANDS), 1, SPEED_BANDS) - 1;
		double turns = position * STRIDE_RATE / (2 * Math.PI);
		int phase = (int) Math.round((turns - Math.floor(turns)) * STRIDE_FRAMES) % STRIDE_FRAMES;
		return band * STRIDE_FRAMES + phase;
	}

	/** Keeps the idle under everything, and starts, steers or stops the walk and the shake for this tick. */
	static void tick(Punkphoz punkphoz) {
		Animator animator = punkphoz.getHolder().getAnimator();
		if (!animator.isPlaying(IDLE)) animator.playAnimation(IDLE, 0);
		if (punkphoz.getUnhappyCounter() > 0) {
			if (!animator.isPlaying(SHAKE)) animator.playAnimation(SHAKE, 2);
		} else if (animator.isPlaying(SHAKE)) {
			animator.stopAnimation(SHAKE);
		}
		float speed = punkphoz.walkAnimation.speed();
		if (speed >= STILL) {
			animator.playAnimation(WALK, 1);
			animator.setAnimationFrame(WALK, walkFrame(speed, punkphoz.walkAnimation.position()));
		} else if (animator.isPlaying(WALK)) {
			animator.stopAnimation(WALK);
		}
	}
}
