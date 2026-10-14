package metacraft.ovvar.store;

import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Ownership;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Sewing and unpicking as the store sees them. An unowned ovve is sewn on the spot with the item
 * in hand, as it always was. An owned one is a view of its owner's wardrobe: a sew moves a patch
 * from the stash (or the item in hand) onto the design, and an unpick moves it off the design into
 * the stash or, on a survival server, back into the hand — either way one compare-and-set write
 * first, and only then the item, which is what makes two ovves of one owner (or two servers) not a
 * patch duplicator. Either way an owned ovve is its owner's to change and nobody else's
 * ({@link Ownership#editRefusal}).
 */
public final class OwnedSewing {
	private OwnedSewing() {}

	/** What an unpick did: the placement that came off, and whether it went to the stash or is the caller's to hand out as an item. */
	public record Unpicked(Placement placement, boolean toStash) {}

	/** Why this player may not sew or unpick on this server right now, or null if they may. */
	public static @Nullable String editingRefusal(ServerPlayer player) {
		StashConfig config = OvvarConfig.get().stash();
		if (config.minigameServer()) return "Ovvar are view-only on this server: sew on a survival server";
		if (!config.sewGameModes().contains(player.gameMode())) return "You cannot sew in " + player.gameMode().getName() + " mode";
		if (!config.ingameObjective().isEmpty()) {
			Objective objective = player.level().getServer().getScoreboard().getObjective(config.ingameObjective());
			if (objective != null) {
				ReadOnlyScoreInfo score = player.level().getServer().getScoreboard().getPlayerScoreInfo(player, objective);
				if (score != null && score.value() != 0) return "Not while a game is on";
			}
		}
		return null;
	}

	/**
	 * @param player	the player whose click this is, and who an owned ovve must belong to
	 *				  ({@code designs.edit_requires_owner}); null for the mod's own writes
	 * @param fromHand  the patch was a real item in the hand that the click consumed: it is banked and
	 *				  sewn in the same write, so it never needed to be in the stash
	 * @param onSewn	the change is on the ovve (and in the store, or queued for it)
	 * @param onRefused nothing changed; why, for the player
	 */
	public static void sew(@Nullable ServerPlayer player, ItemStack ovve, Placement placement, boolean fromHand, Runnable onSewn, Consumer<String> onRefused) {
		String ownerRefusal = Ownership.editRefusal(player, ovve);
		if (ownerRefusal != null) {
			onRefused.accept(ownerRefusal);
			return;
		}
		UUID owner = OvveItem.owner(ovve);
		if (owner == null || !(ovve.getItem() instanceof OvveItem item)) {
			if (Looks.sew(ovve, placement)) onSewn.run();
			else onRefused.accept("Cannot sew a patch on top of another patch!");
			return;
		}
		Chapter chapter = item.chapter;
		if (!Wardrobes.loaded(owner)) {
			Wardrobes.fetch(owner);
			onRefused.accept("The ovve's wardrobe is still loading, try again in a moment");
			return;
		}
		Wardrobe now = Wardrobes.current(owner);
		if (!fromHand && now.count(placement.patch()) == 0) {
			onRefused.accept("No " + placement.patch().name() + " in the stash");
			return;
		}
		if (!now.canSew(chapter, placement) && !(fromHand && now.add(placement.patch(), 1).canSew(chapter, placement))) {
			onRefused.accept("Cannot sew a patch on top of another patch!");
			return;
		}
		UnaryOperator<Wardrobe> change = w -> (fromHand ? w.add(placement.patch(), 1) : w).sew(chapter, placement).orElse(null);
		Wardrobes.update(owner, change, outcome -> {
			switch (outcome) {
				case OK -> {
					OvveItem.refresh(ovve);
					onSewn.run();
				}
				case REJECTED -> onRefused.accept("Cannot sew a patch on top of another patch!");
				case CONFLICT -> onRefused.accept("The wardrobe changed on another server, try again");
				case NOT_LOADED -> onRefused.accept("The ovve's wardrobe is still loading, try again in a moment");
				case UNREACHABLE -> {
					if (OvvarConfig.get().designs().sewWhenUnreachable()) {
						Wardrobes.queue(owner, change, "sew " + placement.key());
						OvveItem.refresh(ovve);
						onSewn.run();
					} else {
						onRefused.accept("The wardrobe store cannot be reached, try again later");
					}
				}
			}
		});
	}

	/**
	 * @param player	 the player whose click this is, and who an owned ovve must belong to
	 *				   ({@code designs.edit_requires_owner}); null for the mod's own writes
	 * @param toStash	an owned ovve's patch goes to the stash (true) or is the caller's to hand out once the
	 *				   store has let go of it (false, the vanilla feel on a survival server); unowned: always the caller's
	 * @param onUnpicked the patch is off the ovve
	 * @param onRefused  nothing changed; why, for the player
	 */
	public static void unpick(@Nullable ServerPlayer player, ItemStack ovve, Spot spot, boolean toStash, Consumer<Unpicked> onUnpicked, Consumer<String> onRefused) {
		String ownerRefusal = Ownership.editRefusal(player, ovve);
		if (ownerRefusal != null) {
			onRefused.accept(ownerRefusal);
			return;
		}
		UUID owner = OvveItem.owner(ovve);
		if (owner == null || !(ovve.getItem() instanceof OvveItem item)) {
			Placement there = Looks.at(ovve, spot);
			Patches.Patch patch = Looks.unpick(ovve, spot);
			if (patch != null) onUnpicked.accept(new Unpicked(there, false));
			else onRefused.accept("Nothing to unpick there");
			return;
		}
		Chapter chapter = item.chapter;
		if (!Wardrobes.loaded(owner)) {
			Wardrobes.fetch(owner);
			onRefused.accept("The ovve's wardrobe is still loading, try again in a moment");
			return;
		}
		// What is there according to the store's copy, never the ovve's own (it may be stale).
		Optional<Placement> there = Wardrobes.current(owner).at(chapter, spot);
		if (there.isEmpty()) {
			onRefused.accept("Nothing to unpick there");
			return;
		}
		Placement placement = there.get();
		UnaryOperator<Wardrobe> change = w -> w.unpick(chapter, spot).map(u -> toStash ? u : u.add(placement.patch(), -1)).orElse(null);
		Wardrobes.update(owner, change, outcome -> {
			switch (outcome) {
				case OK -> {
					OvveItem.refresh(ovve);
					onUnpicked.accept(new Unpicked(placement, toStash));
				}
				case REJECTED -> onRefused.accept("Nothing to unpick there");
				case CONFLICT -> onRefused.accept("The wardrobe changed on another server, try again");
				case NOT_LOADED -> onRefused.accept("The ovve's wardrobe is still loading, try again in a moment");
				case UNREACHABLE -> {
					if (OvvarConfig.get().designs().unpickWhenUnreachable()) {
						Wardrobes.queue(owner, change, "unpick " + placement.key());
						OvveItem.refresh(ovve);
						onUnpicked.accept(new Unpicked(placement, toStash));
					} else {
						onRefused.accept("The wardrobe store cannot be reached, try again later");
					}
				}
			}
		});
	}
}
