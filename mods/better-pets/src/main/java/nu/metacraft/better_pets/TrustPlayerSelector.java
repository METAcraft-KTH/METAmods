package nu.metacraft.better_pets;

import com.mojang.authlib.GameProfile;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import nu.metacraft.core.gui.MultiplePlayerSelector;
import nu.metacraft.lib.util.helper.GameProfileHelper;

import java.util.Comparator;
import java.util.Optional;

public class TrustPlayerSelector extends MultiplePlayerSelector {
	public static final StyleSpriteSource MENU_FONT = new StyleSpriteSource.Font(Identifier.of("metacraft", "pet_gui"));

	private final TameableExtension tameable;

	public TrustPlayerSelector(ServerPlayerEntity player, TameableExtension tameable) {
		super(
				ScreenHandlerType.GENERIC_9X5, player,
				tameable.metacraft$getTrustedPlayers().stream().map(
						id -> GameProfileHelper.getForUUID(id, player.getEntityWorld().getServer())
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
		var fontMagic = Text.literal("abc").styled(style ->
			style.withFont(MENU_FONT).withColor(Formatting.WHITE)
		);
		setTitle(Text.empty().append(fontMagic).append(Text.translatableWithFallback("gui.metacraft.player_selector", "Player Selector")));

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
			var foundPlayer = getPlayer().getEntityWorld().getServer().getPlayerManager().getPlayer(profile.id());
			if (foundPlayer == null) {
				return Integer.MAX_VALUE;
			}
			if (foundPlayer.getEntityWorld() != getPlayer().getEntityWorld()) {
				return Integer.MAX_VALUE-1;
			}
			return Math.round(foundPlayer.distanceTo(getPlayer()));
		}).thenComparing(getDefaultComparator(getPlayer().getEntityWorld().getServer()));
	}

	@Override
	protected boolean isValid(ServerPlayerEntity player) {
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
