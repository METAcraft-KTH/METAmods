package nu.metacraft.better_pets;

import com.mojang.authlib.GameProfile;
import nu.metacraft.core.gui.MultiplePlayerSelector;
import nu.metacraft.lib.util.helper.GameProfileHelper;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class TrustPlayerSelector extends MultiplePlayerSelector {
	public static final FontDescription MENU_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("better_pets", "pet_gui"));

	private final TameableExtension tameable;

	public TrustPlayerSelector(ServerPlayer player, TameableExtension tameable) {
		super(
				MenuType.GENERIC_9x5, player,
				tameable.metacraft$getTrustedPlayers().stream().map(
						id -> GameProfileHelper.getForUUID(id, player.level().getServer())
				).filter(Optional::isPresent).map(Optional::get).toList()
		);
		this.tameable = tameable;

		// Font magic
		// a = move cursor by -8
		//     (back by 8, aligns to vanilla GUI corner)
		// b = pet_gui.png
		//     (width: 176, so moves cursor by 176)
		// c = move cursor by -169
		//     (back by 169 = -8 + 176 + 1, resets cursor.
		//      + 1 is to count space between characters)
		//
		// See: https://github.com/METAcraft-KTH/resource-pack
		//
		var fontMagic = Component.literal("abc").withStyle(style ->
			style.withFont(MENU_FONT).withColor(ChatFormatting.WHITE)
		);
		setTitle(Component.empty().append(fontMagic).append(Component.translatableWithFallback("gui.metacraft.player_selector", "Player Selector")));

		setSlot(0, ItemStack.EMPTY);
		setSlot(1, ItemStack.EMPTY);
		setSlot(2, ItemStack.EMPTY);
		setSlot(3, ItemStack.EMPTY);

		setSlot(5, ItemStack.EMPTY);
		setSlot(6, ItemStack.EMPTY);
		setSlot(7, ItemStack.EMPTY);
		setSlot(8, ItemStack.EMPTY);

		setSlot(4, ItemStack.EMPTY);
		setSlot(13, ItemStack.EMPTY);
		setSlot(22, ItemStack.EMPTY);
		setSlot(31, ItemStack.EMPTY);
		setSlot(40, ItemStack.EMPTY);
	}

	@Override
	protected Comparator<GameProfile> customNonSelectedComparator() {
		return Comparator.<GameProfile>comparingInt(profile -> {
			var foundPlayer = getPlayer().level().getServer().getPlayerList().getPlayer(profile.id());
			if (foundPlayer == null) {
				return Integer.MAX_VALUE;
			}
			if (foundPlayer.level() != getPlayer().level()) {
				return Integer.MAX_VALUE-1;
			}
			return Math.round(foundPlayer.distanceTo(getPlayer()));
		}).thenComparing(getDefaultComparator(getPlayer().level().getServer()));
	}

	@Override
	protected boolean isValid(ServerPlayer player) {
		return getPlayer().distanceTo(player) < 16;
	}

	@Override
	protected void onSelected(GameProfile gameProfile) {
		tameable.metacraft$addTrustedPlayer(gameProfile.id());
	}

	@Override
	protected void onDeselected(GameProfile gameProfile) {
		tameable.metacraft$removeTrustedPlayer(gameProfile.id());
	}
}
