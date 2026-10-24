package se.metacraft.bosses.boss.attacks;

public abstract class InstantAttack implements Attack {
	@Override
	public final void activate(BossContext<?> ctx) {
		trigger(ctx);
	}

	@Override
	public final void tick(BossContext<?> ctx) {

	}

	@Override
	public final void deactivate(BossContext<?> ctx) {

	}

	@Override
	public final boolean isInstant() {
		return true;
	}

	public abstract void trigger(BossContext<?> ctx);
}
