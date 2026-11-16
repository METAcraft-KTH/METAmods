package se.metacraft.ovvar.clienttest;

/**
 * Holds every humanoid's idle arm sway (ArmSwayMixin) while a test compares two frames of a still scene.
 * A mannequin is not still: HumanoidModel bobs its arms by age (AnimationUtils.bobModelPart, a slow sine
 * in zRot and xRot), so two frames a few ticks apart differ along the arms by an amount that depends on
 * where in that sine they fall, which is only the timing of the run. Test source set only.
 */
public final class StillArms {
	private StillArms() {}

	private static volatile boolean held;

	public static boolean held() {
		return held;
	}

	public static void hold(boolean hold) {
		held = hold;
	}
}
