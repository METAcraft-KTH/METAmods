package se.metacraft.skins.ui;

import se.metacraft.skins.ApplyResult;
import se.metacraft.skins.BoxResult;
import se.metacraft.skins.OwnershipChange;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Optional;

/**
 * What a player is told about their skins, in chat and on the action bar, with a sound only they
 * hear. Shared by the commands and the menus, which word every refusal through {@link #whyNot}.
 * The wording is placeholder.
 */
public final class Feedback {
	private Feedback() {}

	/** A Claim, or an admin's grant: the same message either way. */
	public static void claimed(ServerPlayer player, String skinName) {
		tell(player, Component.literal("✨ You now own " + skinName + "! Open /itemskins to apply it.").withStyle(ChatFormatting.GOLD));
		sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
	}

	/** An admin's grant of every skin: "✨ An admin gave you 12 skins! Open /itemskins to apply them." */
	public static void grantedAll(ServerPlayer player, int count) {
		tell(player, Component.literal("✨ An admin gave you " + count + " skin" + (count == 1 ? "" : "s") + "! Open /itemskins to apply "
				+ (count == 1 ? "it" : "them") + ".").withStyle(ChatFormatting.GOLD));
		sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
	}

	/** An admin's revoke of every skin: "All 12 of your skins were removed by an admin." */
	public static void revokedAllByAdmin(ServerPlayer player, int count) {
		tell(player, Component.literal((count == 1 ? "Your only skin was" : "All " + count + " of your skins were") + " removed by an admin.")
				.withStyle(ChatFormatting.YELLOW));
	}

	public static void revokedByAdmin(ServerPlayer player, String skinName) {
		tell(player, Component.literal(skinName + " was removed from your skins by an admin.").withStyle(ChatFormatting.YELLOW));
	}

	public static void applied(ServerPlayer player, String skinName, String itemName, Optional<String> replaced) {
		tell(player, Component.literal("✔ " + skinName + " applied to your " + itemName + replaced.map(r -> " (replaced " + r + ")").orElse(""))
				.withStyle(ChatFormatting.GREEN));
		sound(player, SoundEvents.SMITHING_TABLE_USE);
	}

	public static void removed(ServerPlayer player, String skinName) {
		tell(player, Component.literal(skinName + " removed. The item it was on is plain again.").withStyle(ChatFormatting.YELLOW));
	}

	/** A Dyeable Hat's saved tint cleared: "<Skin>'s tint cleared. It's back in its starting colour." */
	public static void tintCleared(ServerPlayer player, String skinName) {
		tell(player, Component.literal(skinName + "'s tint cleared. It's back in its starting colour.").withStyle(ChatFormatting.YELLOW));
	}

	/** A confirm screen cancelled: "<action> cancelled. You keep <kept>." ("Claim", "the token"; "Extract", "the skin"). */
	public static void cancelled(ServerPlayer player, String action, String kept) {
		tell(player, Component.literal(action + " cancelled. You keep " + kept + ".").withStyle(ChatFormatting.GRAY));
	}

	/** An Extract: the token is in their inventory, or at their feet ({@code dropped}) when it was full. */
	public static void extracted(ServerPlayer player, String skinName, boolean dropped) {
		tell(player, Component.literal("You extracted " + skinName + ". Its Skin Token is " + (dropped ? "at your feet: your inventory is full." : "in your inventory."))
				.withStyle(ChatFormatting.GOLD));
		sound(player, SoundEvents.ITEM_PICKUP);
	}

	/** A Roll settled: "You rolled <Skin>!", in chat only. */
	public static void rolled(ServerPlayer player, Component skinName) {
		player.sendSystemMessage(Component.literal("You rolled ").withStyle(ChatFormatting.GOLD).append(skinName)
				.append(Component.literal("!").withStyle(ChatFormatting.GOLD)));
	}

	/** The claim menu opening: the client shows nothing of a right-click with a token on its own. */
	public static void pageTurn(ServerPlayer player) {
		sound(player, SoundEvents.BOOK_PAGE_TURN);
	}

	/** Whom a refusal is worded for. */
	public sealed interface Audience {
		/**
		 * The player whose skins they are, told in the first person: {@code verb} is what they tried
		 * ("apply", "claim"), {@code unchanged} what is left as it was ("nothing changed", "you keep the token").
		 */
		record Owner(String verb, String unchanged) implements Audience {}

		/** An admin acting on {@code player}'s skins; {@code dormantReason} is why the skin is Dormant, if it is. */
		record Admin(String player, Optional<String> dormantReason) implements Audience {}
	}

	/**
	 * Why a grant, revoke, remove, Claim or Extract was refused (anything but {@code DONE}), worded for
	 * {@code to}. {@code skin} is the skin as they know it: its name for a player, its id for an admin.
	 */
	public static String whyNot(OwnershipChange result, String skin, Audience to) {
		return switch (to) {
			case Audience.Owner(var verb, var unchanged) -> switch (result) {
				case ALREADY_OWNED -> "You already own " + skin + ".";
				case NOT_OWNED, UNKNOWN_SKIN, DORMANT_SKIN -> "You can't " + verb + " " + skin + " right now, so " + unchanged + ".";
				case NOT_APPLIED -> skin + " is on no item.";
				case STILL_APPLIED -> skin + " is on an item. Remove it from its item first, then extract it.";
				case NO_TOKEN -> "That token isn't in your inventory any more.";
				case ITEM_AWAY -> skin + " is on an item you don't have with you. Bring it back first; " + unchanged + ".";
				case DAMAGED_FILE -> "Your skins file is damaged, so " + unchanged + ". Ask an admin to fix it.";
				case SAVE_FAILED -> "Your skins couldn't be saved, so " + unchanged + ". Try again later.";
				case DONE -> throw new IllegalArgumentException("not a refusal: " + result);
			};
			case Audience.Admin(var player, var dormantReason) -> switch (result) {
				case ALREADY_OWNED -> player + " already owns " + skin + ".";
				case NOT_OWNED -> player + " doesn't own " + skin + ".";
				case NOT_APPLIED -> skin + " is on no item.";
				case UNKNOWN_SKIN -> "There's no skin " + skin + ". /itemskins list shows them all.";
				case DORMANT_SKIN -> skin + " is Dormant: " + dormantReason.orElse("its definition didn't load") + ".";
				case DAMAGED_FILE -> player + "'s skins file is damaged and is never overwritten; fix it first. /itemskins info " + player + " says what's wrong.";
				case SAVE_FAILED -> "Couldn't save " + player + "'s skins file, so nothing changed. The server log says why.";
				case DONE, NO_TOKEN, STILL_APPLIED, ITEM_AWAY -> throw new IllegalArgumentException("not an admin's refusal: " + result);
			};
		};
	}

	/** Why a Roll, or opening a box, was refused (anything but {@code DONE}), told to the player holding the box. */
	public static String whyNot(BoxResult result) {
		return switch (result) {
			case BOX_GONE -> "Your Skin Box is gone.";
			case DORMANT_BOX, UNKNOWN_BOX -> "This Skin Box can't be opened right now.";
			case NOTHING_TO_ROLL -> "This box has nothing to roll. Your box is kept.";
			case DONE -> throw new IllegalArgumentException("not a refusal: " + result);
		};
	}

	/** Why {@code /itemskins givebox} was refused, told to the admin; {@code dormantReason} is why the box is Dormant, if it is. */
	public static String whyNotGiven(BoxResult result, String box, Optional<String> dormantReason) {
		return switch (result) {
			case UNKNOWN_BOX -> "There's no Skin Box " + box + ". /itemskins list shows them all.";
			case DORMANT_BOX -> box + " is Dormant, so it can't be given: " + dormantReason.orElse("its file didn't load") + ".";
			case DONE, BOX_GONE, NOTHING_TO_ROLL -> throw new IllegalArgumentException("not a give's refusal: " + result);
		};
	}

	/** Why an apply was refused (anything but {@code APPLIED}), told to the player; {@code skin} and {@code item} are named as they see them. */
	public static String whyNot(ApplyResult.Outcome outcome, String skin, String item) {
		Audience applying = new Audience.Owner("apply", "nothing changed");
		return switch (outcome) {
			case ALREADY_APPLIED -> "That " + item + " already has " + skin + ".";
			case NO_ITEM, DOESNT_FIT -> "That " + item + " isn't there any more.";
			case NOT_OWNED -> whyNot(OwnershipChange.NOT_OWNED, skin, applying);
			case UNKNOWN_SKIN -> whyNot(OwnershipChange.UNKNOWN_SKIN, skin, applying);
			case DORMANT_SKIN -> whyNot(OwnershipChange.DORMANT_SKIN, skin, applying);
			case DAMAGED_FILE -> whyNot(OwnershipChange.DAMAGED_FILE, skin, applying);
			case SAVE_FAILED -> whyNot(OwnershipChange.SAVE_FAILED, skin, applying);
			case APPLIED -> throw new IllegalArgumentException("not a refusal: " + outcome);
		};
	}

	/** Something the player asked for didn't happen: red text and the villager's "no". */
	public static void failed(ServerPlayer player, String why) {
		tell(player, Component.literal(why).withStyle(ChatFormatting.RED));
		sound(player, SoundEvents.VILLAGER_NO);
	}

	private static void tell(ServerPlayer player, Component message) {
		player.sendSystemMessage(message);
		player.sendOverlayMessage(message);
	}

	private static void sound(ServerPlayer player, SoundEvent sound) {
		sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), 1.0f, 1.0f);
	}

	/** A sound only this player hears, at their position. */
	static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), volume, pitch,
				player.getRandom().nextLong()));
	}
}
