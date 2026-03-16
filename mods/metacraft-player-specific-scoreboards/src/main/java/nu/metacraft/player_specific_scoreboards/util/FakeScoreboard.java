package nu.metacraft.player_specific_scoreboards.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class FakeScoreboard extends Scoreboard {

	private static final FakeScoreboard INSTANCE = new FakeScoreboard();

	public static FakeScoreboard getInstance() {
		return INSTANCE;
	}

	@Override
	public @NonNull Objective addObjective(
			@NonNull String string,
			@NonNull ObjectiveCriteria objectiveCriteria,
			@NonNull Component component,
			ObjectiveCriteria.@NonNull RenderType renderType,
			boolean bl,
			@Nullable NumberFormat numberFormat
	) {
		return new Objective(
				this, "", ObjectiveCriteria.DUMMY,
				Component.literal(""), ObjectiveCriteria.RenderType.INTEGER,
				false, BlankFormat.INSTANCE
		);
	}

	@Override
	public @NonNull ScoreAccess getOrCreatePlayerScore(@NonNull ScoreHolder scoreHolder, @NonNull Objective objective, boolean bl) {
		return new ScoreAccess() {
			@Override
			public int get() {
				return 0;
			}

			@Override
			public void set(int i) {

			}

			@Override
			public boolean locked() {
				return false;
			}

			@Override
			public void unlock() {

			}

			@Override
			public void lock() {

			}

			@Override
			public @Nullable Component display() {
				return null;
			}

			@Override
			public void display(@Nullable Component component) {

			}

			@Override
			public void numberFormatOverride(@Nullable NumberFormat numberFormat) {

			}
		};
	}

	@Override
	protected void loadPlayerScore(Scoreboard.@NonNull PackedScore packedScore) {

	}

	@Override
	public void setDisplayObjective(@NonNull DisplaySlot displaySlot, @Nullable Objective objective) {

	}

	@Override
	public @NonNull PlayerTeam addPlayerTeam(@NonNull String string) {
		return new PlayerTeam(this, string);
	}

	@Override
	protected void loadPlayerTeam(PlayerTeam.@NonNull Packed packed) {

	}

}
