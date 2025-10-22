package nu.metacraft.better_pets;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import eu.pb4.sgui.api.elements.AnimatedGuiElementBuilder;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nu.metacraft.core.gui.MultiplePlayerSelector;
import nu.metacraft.lib.util.helper.GameProfileHelper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TrustPlayerSelector extends MultiplePlayerSelector {

	private final TameableExtension tameable;


	//Letters from: https://minecraft-heads.com/custom-heads/tag/font-quartz
	private static final ProfileComponent E = withTexture(
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmYzY2Y4ZGQ4MmYzODE5ODMxYmRhNjcxNjU0MGVkZGMyZDcxNTNjODViY2M3OGU0MzI1OTU2NTA0ZjY3NWYifX19",
			"bf3cf8dd82f3819831bda6716540eddc2d7153c85bcc78e4325956504f675f"
	);
	private static final ProfileComponent R = withTexture(
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzM5ODg2YTkxNDgxOTM2NDg1OTgzNzAyNGM0YmNmYTg1M2Q4NmJkODJiZTU0MTdkNjhjMDU3Yjg0MzMifX19",
			"339886a914819364859837024c4bcfa853d86bd82be5417d68c057b8433"
	);
	private static final ProfileComponent T = withTexture(
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2E2ZTUzYmZiN2MxM2ZlZGJkZmU4OTY3NmY4MWZjMmNhNzk3NDYzNGE2ODQxNDFhZDFmNTE2NGYwZWRmNGEyIn19fQ==",
			"ca6e53bfb7c13fedbdfe89676f81fc2ca7974634a684141ad1f5164f0edf4a2"
	);

	private static final List<ProfileComponent> TRUSTED = List.of(
			T,
			R,
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzMyNjg0NjkzNWE5NjI2MWY0NGY0NDg1ODA4OTFkYjk5ZGZiODExOTU5MWNlNWI2MWQzMDQ2YTNhMzgwZDMifX19",
					"3326846935a96261f44f448580891db99dfb8119591ce5b61d3046a3a380d3"
			),
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjM4ZDI3NTk1NjlkNTE1ZDI0NTRkNGE3ODkxYTk0Y2M2M2RkZmU3MmQwM2JmZGY3NmYxZDQyNzdkNTkwIn19fQ==",
					"f38d2759569d515d2454d4a7891a94cc63ddfe72d03bfdf76f1d4277d590"
			),
			T,
			E,
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGE2YTdkMWI3NjI5MTkzOTQyZjhlMTQ2YzZkMWQyZGIxOTFkMjdmODExZDIxZTI5YTJlNGNmYmFiZGEwODgifX19",
					"da6a7d1b7629193942f8e146c6d1d2db191d27f811d21e29a2e4cfbabda088"
			)
	);

	private static final List<ProfileComponent> NEARBY = List.of(
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmM0ZDc1Yjk5ZmFlYzVlMTJhZDJmOTJkYTYxYWM0OTI2MjZlNTA1YjZhODRmNmQ2OWJiZjI0OTllN2I4NDQyNyJ9fX0=",
					"bc4d75b99faec5e12ad2f92da61ac492626e505b6a84f6d69bbf2499e7b84427"
			),
			E,
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDJjZDVhMWI1Mjg4Y2FhYTIxYTZhY2Q0Yzk4Y2VhZmQ0YzE1ODhjOGIyMDI2Yzg4YjcwZDNjMTU0ZDM5YmFiIn19fQ==",
					"42cd5a1b5288caaa21a6acd4c98ceafd4c1588c8b2026c88b70d3c154d39bab"
			),
			R,
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ1MDMxZTg3MTM5YTgxNzY4ZTE0ZmQzZjBhNDdhNjcyMTNmMDYyNTQ2ZmExN2E4YTg2MjIyYmUxOTc4YTNmIn19fQ==",
					"bd5031e87139a81768e14fd3f0a47a67213f062546fa17a8a86222be1978a3f"
			),
			withTexture(
					"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODlmZjhjNzQ0OTUwNzI5ZjU4Y2I0ZTY2ZGM2OGVhZjYyZDAxMDZmOGE1MzE1MjkxMzNiZWQxZDU1ZTMifX19",
					"89ff8c744950729f58cb4e66dc68eaf62d0106f8a531529133bed1d55e3"
			)
	);

	private static ProfileComponent withTexture(String value, String signature) {
		PropertyMap map = new PropertyMap(
				ImmutableMultimap.of(
						"textures", new Property("textures", value, signature)
				)
		);
		return GameProfileHelper.staticComponentBuilder().withProperties(map).build();
	}

	private static GuiElementInterface createMovingLetter(
			Text name, List<ProfileComponent> part, int index, int interval
	) {
		var builder = new AnimatedGuiElementBuilder();
		for (int i = 0; i < part.size(); i++) {
			if (index >= part.size()) {
				index = 0;
			}
			builder.setItem(Items.PLAYER_HEAD).setComponent(
					DataComponentTypes.PROFILE, part.get(index++)
			).setName(name).saveItemStack();
		}
		return builder.setInterval(interval).build();
	}

	public TrustPlayerSelector(ServerPlayerEntity player, TameableExtension tameable) {
		super(
				ScreenHandlerType.GENERIC_9X5, player,
				tameable.metacraft$getTrustedPlayers().stream().map(
						id -> GameProfileHelper.getForUUID(id, player.getEntityWorld().getServer())
				).filter(Optional::isPresent).map(Optional::get).toList()
		);
		this.tameable = tameable;
		setTitle(Text.translatableWithFallback("gui.metacraft.player_selector", "Player Selector"));

		int interval = 30;
		Text text = Text.literal("Nearby");
		setSlot(0, createMovingLetter(text, NEARBY, 0, interval));
		setSlot(1, createMovingLetter(text, NEARBY, 1, interval));
		setSlot(2, createMovingLetter(text, NEARBY, 2, interval));
		setSlot(3, createMovingLetter(text, NEARBY, 3, interval));

		text = Text.literal("Trusted");
		setSlot(5, createMovingLetter(text, TRUSTED, 0, interval));
		setSlot(6, createMovingLetter(text, TRUSTED, 1, interval));
		setSlot(7, createMovingLetter(text, TRUSTED, 2, interval));
		setSlot(8, createMovingLetter(text, TRUSTED, 3, interval));
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
