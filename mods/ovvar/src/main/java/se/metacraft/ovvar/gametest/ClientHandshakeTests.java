package se.metacraft.ovvar.gametest;

import com.mojang.authlib.GameProfile;
import se.metacraft.ovvar.Ovvar;
import se.metacraft.ovvar.combo.ComboAssets;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.Looks;
import se.metacraft.ovvar.content.ModComponents;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.Piece;
import se.metacraft.ovvar.content.Placement;
import se.metacraft.ovvar.content.Spot;
import se.metacraft.ovvar.content.SpotPlacements;
import se.metacraft.ovvar.pack.ComboPack;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.resource_packs.EarlyPackPacketsCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The ovvar-client handshake, server side, with the channel faked: a player whose client registered
 * {@code ovvar:draws_combos/v<protocol>} gets no combination pack on join, is sent whole combinations and is
 * never claimed for or pushed one; everyone else, and a client on another protocol, gets what they always did.
 */
public final class ClientHandshakeTests {

	/** Only this server's own protocol's channel is a handshake; another version's, or none, is a vanilla client. */
	@GameTest
	public void theHandshakeIsThisProtocolsChannelOnly(GameTestHelper helper) {
		Identifier own = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "draws_combos/v" + ComboAssets.PROTOCOL);
		assertThat(Combos.DRAWS_COMBOS_CHANNEL.equals(own), "the channel is " + Combos.DRAWS_COMBOS_CHANNEL);
		assertThat(Combos.handshake(Set.of(own, Identifier.fromNamespaceAndPath("fabric", "x"))::contains), "this protocol's channel did not handshake");
		Set<Identifier> older = Set.of(Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "draws_combos/v" + (ComboAssets.PROTOCOL - 1)));
		Set<Identifier> newer = Set.of(Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "draws_combos/v" + (ComboAssets.PROTOCOL + 1)));
		assertThat(!Combos.handshake(older::contains), "an older client's channel handshook");
		assertThat(!Combos.handshake(newer::contains), "a newer client's channel handshook");
		assertThat(!Combos.handshake(Set.<Identifier>of()::contains), "a client with no channels handshook");
		helper.succeed();
	}

	/** The join packs: the combination pack for a vanilla client, not for one that handshook. */
	@GameTest
	public void aHandshakenPlayerGetsNoCombinationPackOnJoinOthersDo(GameTestHelper helper) {
		assertThat(ComboPack.ACTIVE, "metacraft-resource-packs is not in the test runtime");
		assertThat(ComboPack.current() != null, "the startup build left no combination pack");
		GameProfile modded = new GameProfile(UUID.randomUUID(), "Modded");
		GameProfile vanilla = new GameProfile(UUID.randomUUID(), "Vanilla");
		Combos.setDrawsCombosForTest(modded.id(), true);
		try {
			assertThat(!joinPacks(helper, modded).contains(ComboPack.ID), "a player who handshook was sent the combination pack");
			assertThat(joinPacks(helper, vanilla).contains(ComboPack.ID), "a vanilla player was not sent the combination pack");
		} finally {
			Combos.setDrawsCombosForTest(modded.id(), false);
		}
		assertThat(joinPacks(helper, modded).contains(ComboPack.ID), "after the handshake was undone the pack still was not sent");
		helper.succeed();
	}

	/**
	 * More patches than the dye holds, one of them given-only, on a combination the pack does not have: a
	 * player who handshook is sent all of it as the asset (no dye, no trim, nothing owed), is never claimed
	 * for, and /ovvar reload sends them nothing; the combination is still queued for everyone else's pack.
	 * A vanilla viewer still waits for the pack.
	 */
	@GameTest
	public void aHandshakenPlayerIsSentWholeCombinationsAndNeverPushed(GameTestHelper helper) {
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.TBAS));
		List<Placement> sewn = List.of(new Placement(Spot.LEG_OUT_TOP_R, Patches.get("tmeit-marshal")), new Placement(Spot.LEG_OUT_MID_R, Patches.get("kth")),
				new Placement(Spot.LEG_FRONT_TOP_R, Patches.get("spiken")), new Placement(Spot.LEG_FRONT_MID_R, Patches.get("tmeit")),
				new Placement(Spot.LEG_OUT_TOP_L, Patches.get("metacraft")), new Placement(Spot.LEG_OUT_MID_L, Patches.get("jgs")),
				new Placement(Spot.LEG_FRONT_TOP_L, Patches.get("maid")));
		Looks.setSewn(ovve, SpotPlacements.fromList(sewn).getOrThrow());
		Combos.Combo whole = Placement.combo(sewn);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Combos.setDrawsCombosForTest(player.getUUID(), true);
		try {
			assertThat(!Combos.isBuilt(Chapter.TBAS, Piece.BOTTOM, whole, null), "the sample combination is already in the pack; pick another");
			Looks.Look look = Looks.look(ovve, Piece.BOTTOM, player.getUUID());
			assertThat(look.combo().key().equals(whole.key()), "sent " + look.combo().key() + ", not the whole combination");
			assertThat(look.dye() == 0 && look.feetDye() == 0 && look.trim() == null, "a client that draws combinations was sent dye bits or a trim");
			assertThat(look.complete(), "a client that draws combinations was owed a pack");
			Looks.claimIfNeeded(player, ovve);
			assertThat(Combos.isKnown(Chapter.TBAS, Piece.BOTTOM, whole), "a combination sewn by a client that draws it was not queued for the others' pack");
			assertThat(!Combos.claimedByForTest(player.getUUID()), "a client that draws combinations was claimed for a push");
			assertThat(Combos.reload(player).contains("ovvar-client"), "/ovvar reload would push a pack: " + Combos.reload(player));

			// A mob's garment is drawn from the dye for everyone, this client too.
			ItemStack mob = ovve.copy();
			mob.set(ModComponents.MOB_OVVE, Unit.INSTANCE);
			Looks.Look mobLook = Looks.look(mob, Piece.BOTTOM, player.getUUID());
			Looks.Look mobVanilla = Looks.look(mob, Piece.BOTTOM, UUID.randomUUID());
			assertThat(mobLook.equals(mobVanilla), "a mob's garment looks different to a client that draws combinations: " + mobLook + " vs " + mobVanilla);

			// A vanilla viewer of the same ovve: the pack has to catch up.
			Looks.Look vanilla = Looks.look(ovve, Piece.BOTTOM, UUID.randomUUID(), false);
			assertThat(!vanilla.complete() && vanilla.combo().isEmpty(), "a vanilla viewer was sent a combination the pack does not hold: " + vanilla);
		} finally {
			Combos.setDrawsCombosForTest(player.getUUID(), false);
			Combos.forgetForTest(Chapter.TBAS, Piece.BOTTOM, whole);
		}
		helper.succeed();
	}

	/** The pack ids metacraft-resource-packs would push this profile while joining. */
	private static List<UUID> joinPacks(GameTestHelper helper, GameProfile profile) {
		List<ClientboundResourcePackPushPacket> packets = new ArrayList<>();
		EarlyPackPacketsCallback.EVENT.invoker().addPackets(helper.getLevel().getServer(), profile, packets::add);
		return packets.stream().map(ClientboundResourcePackPushPacket::id).toList();
	}

	private static void assertThat(boolean condition, String message) {
		if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
	}
}
