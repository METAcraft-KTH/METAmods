package metacraft.ovvar.content;

import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.store.DesignStoreConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Whose ovve this is, and what that means for everybody else. An owned ovve is a view of its
 * owner's wardrobe ({@link metacraft.ovvar.store.Wardrobes}), so handing one to a friend must not
 * make it theirs to wear or theirs to change: with {@code designs.others_ovve} at {@code block}
 * (the default) a foreign ovve does not go in the legs slot, and with
 * {@code designs.edit_requires_owner} on (the default) nobody but the owner sews on it or shears a
 * patch off it. {@code rebind} instead makes a given ovve the new holder's, and {@code allow} is
 * how it was before: anyone may wear it, still showing its owner's design.
 */
public final class Ownership {
	private Ownership() {}

	/**
	 * Is wearing this stack out of the question for this player: an ovve owned by somebody else,
	 * with {@code others_ovve} at {@code block}. The one question the equip paths ask, on either
	 * side, so it needs no server.
	 */
	public static boolean blocksWearing(@Nullable Player player, ItemStack stack) {
		if (player == null || !(stack.getItem() instanceof OvveItem)) return false;
		if (OvvarConfig.get().designs().othersOvve() != DesignStoreConfig.OthersOvve.BLOCK) return false;
		UUID owner = OvveItem.owner(stack);
		return owner != null && !owner.equals(player.getUUID());
	}

	/** Why this player may not wear this ovve, for them to read, or null if they may. */
	public static @Nullable String wearRefusal(ServerPlayer player, ItemStack ovve) {
		if (!blocksWearing(player, ovve)) return null;
		return "That ovve belongs to " + ownerName(player.level().getServer(), OvveItem.owner(ovve));
	}

	/**
	 * Why this player may not sew on this ovve or unpick from it, or null if they may. An unowned
	 * ovve is anybody's to sew on (it binds to the first player holding it); a system caller (no
	 * player) is never refused.
	 */
	public static @Nullable String editRefusal(@Nullable ServerPlayer player, ItemStack ovve) {
		if (player == null || !OvvarConfig.get().designs().editRequiresOwner()) return null;
		UUID owner = OvveItem.owner(ovve);
		if (owner == null || owner.equals(player.getUUID())) return null;
		return "That ovve belongs to " + ownerName(player.level().getServer(), owner) + "; only they can change it";
	}

	/** The owner's name: the player if they are here, else the server's name cache, else "someone else". */
	public static String ownerName(@Nullable MinecraftServer server, @Nullable UUID owner) {
		if (server == null || owner == null) return "someone else";
		ServerPlayer online = server.getPlayerList().getPlayer(owner);
		if (online != null) return online.getName().getString();
		return server.services().nameToIdCache().get(owner).map(NameAndId::name).orElse("someone else");
	}

	/** A refusal in red chat, the way sewing refusals are told (the action bar is overwritten too fast). */
	public static void refuse(ServerPlayer player, String why) {
		player.sendSystemMessage(Component.literal(why).withColor(TextColor.RED.getValue()));
	}
}
