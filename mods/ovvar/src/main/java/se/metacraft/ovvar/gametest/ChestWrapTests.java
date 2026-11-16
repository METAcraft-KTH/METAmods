package se.metacraft.ovvar.gametest;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
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
import se.metacraft.ovvar.content.item.OvveItem;
import se.metacraft.ovvar.content.item.OvveTop;
import se.metacraft.ovvar.pack.ComboPack;
import se.metacraft.ovvar.pack.Combos;
import se.metacraft.ovvar.pack.EquipmentJson;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A real chestplate worn over an ovve whose top is up shows the top's sleeves and collar underneath
 * ({@code OvveTop#wrap}). With every patch, not just the dye's: a client with ovvar-client builds
 * {@code chest/<chapter>/<material>/<combo>} itself, for every material; a vanilla client is sent it once its
 * pack holds it (diamond, iron and netherite only, built on demand into the combination pack), and the
 * instant-only wrap until then. Building one never pushes a pack to anybody.
 */
public final class ChestWrapTests {

	/** More patches than the dye and trim can carry, one given-only, on a combination no pack holds yet. */
	private static final List<Placement> SEWN = List.of(
			place(Spot.BACK_TOP_LEFT, "tmeit-marshal"), place(Spot.BACK_BIG, "kth"), place(Spot.FRONT_TOP_LEFT, "itk"),
			place(Spot.SLEEVE_OUT_TOP_L, "metacraft"), place(Spot.SLEEVE_OUT_TOP_R, "data"), place(Spot.FRONT_LOW_RIGHT, "it"));

	@GameTest
	public void aModdedViewerGetsEveryPatchUnderEveryKnownChestplateAtOnce(GameTestHelper helper) {
		ServerPlayer wearer = helper.makeMockServerPlayerInLevel();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.Combo whole = Placement.combo(SEWN);
		Combos.setDrawsCombosForTest(viewer.getUUID(), true);
		try {
			for (String material : OvveTop.MATERIALS) {
				ItemStack client = wear(helper, wearer, Chapter.DATA, material, SEWN, viewer);
				ResourceKey<EquipmentAsset> expected = EquipmentJson.chestAsset(Chapter.DATA, material, whole);
				assertThat(asset(client).equals(expected), material + ": a modded viewer was sent " + asset(client) + ", not " + expected);
				assertThat(expected.identifier().getPath().equals(ComboAssets.chestPath("data", material, whole.key())), "the server's name is not the shared code's: " + expected);
				assertThat(!client.has(DataComponents.DYED_COLOR), material + ": a modded viewer was sent dye bits too");
			}
		} finally {
			Combos.setDrawsCombosForTest(viewer.getUUID(), false);
			forget(Chapter.DATA, whole);
		}
		helper.succeed();
	}

	@GameTest
	public void aVanillaViewerGetsTheInstantWrapUntilThePackHoldsTheWholeOne(GameTestHelper helper) {
		ServerPlayer wearer = helper.makeMockServerPlayerInLevel();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.Combo whole = Placement.combo(SEWN);
		Combos.KeyedCombo key = Combos.KeyedCombo.chest(Chapter.DATA, "iron", whole);
		try {
			forget(Chapter.DATA, whole);   // a run that failed halfway may have left it in combos.json
			ItemStack before = wear(helper, wearer, Chapter.DATA, "iron", SEWN, viewer);
			assertThat(asset(before).equals(EquipmentJson.chestAsset(Chapter.DATA, "iron")), "before the build a vanilla viewer was sent " + asset(before));
			ItemStack ovve = wearer.getItemBySlot(EquipmentSlot.LEGS);
			int dye = Looks.look(ovve, Piece.TOP, viewer.getUUID(), false).dye();
			DyedItemColor sent = before.get(DataComponents.DYED_COLOR);
			assertThat(dye != 0 && sent != null && sent.rgb() == dye, "before the build the instant patches were not in the dye: " + sent + " vs " + dye);
			assertThat(Combos.isKnown(key), "wearing iron over the combination did not queue its chest composite");

			Combos.markBuiltForTest(key);
			ItemStack after = wear(helper, wearer, Chapter.DATA, "iron", SEWN, viewer);
			assertThat(asset(after).equals(EquipmentJson.chestAsset(Chapter.DATA, "iron", whole)), "once built a vanilla viewer was sent " + asset(after));
			assertThat(!after.has(DataComponents.DYED_COLOR), "with the whole combination in the asset, dye bits were sent too");
		} finally {
			Combos.unmarkBuiltForTest(key);
			forget(Chapter.DATA, whole);
		}
		helper.succeed();
	}

	/**
	 * A vanilla viewer whose loaded pack is not one the server still knows (or a packet with no viewer at all) is
	 * never sent a wrap with patches, even when the current pack holds it: their pack may lack it, and a missing
	 * asset is an invisible chestplate. The plain wrap is in the Polymer pack, always there.
	 */
	@GameTest
	public void aViewerWhosePackIsUnknownGetsThePlainWrap(GameTestHelper helper) {
		ServerPlayer wearer = helper.makeMockServerPlayerInLevel();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.Combo whole = Placement.combo(SEWN);
		Combos.KeyedCombo key = Combos.KeyedCombo.chest(Chapter.FYSIK, "diamond", whole);
		Combos.markBuiltForTest(key);
		try {
			Combos.setLoadedForTest(viewer.getUUID(), -1);
			ItemStack client = wear(helper, wearer, Chapter.FYSIK, "diamond", SEWN, viewer);
			assertThat(asset(client).equals(EquipmentJson.chestAsset(Chapter.FYSIK, "diamond")), "a viewer on an unknown pack was sent " + asset(client));
			assertThat(client.has(DataComponents.DYED_COLOR), "the plain wrap lost its dye");
			ItemStack anonymous = PolymerItemUtils.getPolymerItemStack(wearer.getItemBySlot(EquipmentSlot.CHEST), context(viewer, false), helper.getLevel().registryAccess());
			assertThat(asset(anonymous).equals(EquipmentJson.chestAsset(Chapter.FYSIK, "diamond")), "a packet with no viewer was sent " + asset(anonymous));
		} finally {
			Combos.setLoadedForTest(viewer.getUUID(), null);
			Combos.unmarkBuiltForTest(key);
			forget(Chapter.FYSIK, whole);
		}
		helper.succeed();
	}

	/** When a pack loads, a wrapped chestplate is among what is sent again, so the wrap with every patch shows at once. */
	@GameTest
	public void aWrappedChestplateIsResentWhenAPackLoads(GameTestHelper helper) {
		ItemStack chest = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
		assertThat(!Combos.resentOnPackLoad(chest), "a plain chestplate is re-sent");
		chest.set(ModComponents.WRAPPED_TOP, UUID.randomUUID());
		assertThat(Combos.resentOnPackLoad(chest), "a wrapped chestplate is not re-sent");
		assertThat(Combos.resentOnPackLoad(new ItemStack(ModContent.ovve(Chapter.DATA))), "an ovve is not re-sent");
		helper.succeed();
	}

	/** Only diamond, iron and netherite go into the pack; the other materials are what they were for a vanilla viewer. */
	@GameTest
	public void onlyDiamondIronAndNetheriteAreQueuedForThePack(GameTestHelper helper) {
		ServerPlayer wearer = helper.makeMockServerPlayerInLevel();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.Combo whole = Placement.combo(SEWN);
		assertThat(OvveTop.PACK_MATERIALS.equals(List.of("diamond", "iron", "netherite")), "the pack's materials are " + OvveTop.PACK_MATERIALS);
		forget(Chapter.IT, whole);
		try {
			for (String material : OvveTop.MATERIALS) {
				ItemStack client = wear(helper, wearer, Chapter.IT, material, SEWN, viewer);
				assertThat(asset(client).equals(EquipmentJson.chestAsset(Chapter.IT, material)), material + ": a vanilla viewer was sent " + asset(client));
				boolean queued = Combos.isKnown(Combos.KeyedCombo.chest(Chapter.IT, material, whole));
				assertThat(queued == OvveTop.PACK_MATERIALS.contains(material), material + (queued ? " was queued for the pack" : " was not queued for the pack"));
			}
		} finally {
			forget(Chapter.IT, whole);
		}
		helper.succeed();
	}

	/** A wrap the pack has never been asked for: no combination, nothing queued, for a stand's or a mob's ovve either. */
	@GameTest
	public void aTopWithoutPatchesIsTheWrapAsBefore(GameTestHelper helper) {
		ServerPlayer wearer = helper.makeMockServerPlayerInLevel();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.setDrawsCombosForTest(viewer.getUUID(), true);
		try {
			ItemStack client = wear(helper, wearer, Chapter.DATA, "iron", List.of(), viewer);
			assertThat(asset(client).equals(EquipmentJson.chestAsset(Chapter.DATA, "iron")), "an unpatched top was sent " + asset(client));
			assertThat(EquipmentJson.chestAsset(Chapter.DATA, "iron", Placement.combo(List.of())).equals(EquipmentJson.chestAsset(Chapter.DATA, "iron")),
					"the empty combination is not the plain wrap");
		} finally {
			Combos.setDrawsCombosForTest(viewer.getUUID(), false);
		}
		helper.succeed();
	}

	/**
	 * Ruling: a chest composite must never cause a pack push, not even to the wearer or a viewer (a loading screen after an
	 * inventory change mid-fight). It is queued and built into the combination pack, and players get it with
	 * their next join pack.
	 */
	@GameTest(maxTicks = 400)
	public void wearingAChestplateOverANewCombinationQueuesABuildButPushesNobody(GameTestHelper helper) {
		ServerPlayer wearer = helper.makeMockServerPlayerInLevel();
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		Combos.Combo whole = Placement.combo(SEWN);
		Combos.KeyedCombo key = Combos.KeyedCombo.chest(Chapter.KEMI, "netherite", whole);
		Map<UUID, Long> pushes = new HashMap<>();
		helper.startSequence()
				.thenExecute(() -> {
					forget(Chapter.KEMI, whole);   // a run that failed halfway may have left it in combos.json
					assertThat(!Combos.isBuilt(key, null), "the sample is already in the pack; pick another");
					for (ServerPlayer p : List.of(wearer, viewer)) pushes.put(p.getUUID(), Combos.lastPushForTest(p.getUUID()));
					wear(helper, wearer, Chapter.KEMI, "netherite", SEWN, viewer);
					wear(helper, wearer, Chapter.KEMI, "netherite", SEWN, wearer);
					assertThat(Combos.isKnown(key), "the chest composite was not queued");
					assertThat(!Combos.claimedByForTest(wearer.getUUID()) && !Combos.claimedByForTest(viewer.getUUID()), "wearing a chestplate claimed a push");
					Combos.hurryForTest();
				})
				.thenWaitUntil(() -> assertThat(Combos.isBuilt(key, null), "the chest composite is not built yet"))
				.thenIdle(20)
				.thenExecute(() -> {
					// (other tests' players are left out: their own sewing may push them meanwhile, by the rules)
					for (ServerPlayer p : List.of(wearer, viewer)) {
						assertThat(!Combos.claimedByForTest(p.getUUID()), p.getName().getString() + " is owed a push after a chest-only build");
						assertThat(Combos.lastPushForTest(p.getUUID()) == pushes.getOrDefault(p.getUUID(), 0L), p.getName().getString() + " was pushed a pack after a chest-only build");
					}
					assertThat(ComboPack.current() != null, "no combination pack");
					Set<String> files = ComboAssetsParityTests.unzip(ComboPack.current().zip()).keySet();
					assertThat(files.contains(Combos.packPath(key)), "the built pack lacks " + Combos.packPath(key));
					forget(Chapter.KEMI, whole);
				})
				.thenSucceed();
	}

	/**
	 * The pack-size budget: a chest composite is one small file per (combination, material), only for the three
	 * pack materials and only for combinations somebody wore armour over. One top worn under all three adds at
	 * most 2.4 KB to the zip (measured 1.9 KB for this six-patch top: ~0.65 KB a file, half of it the long
	 * file name, which a zip writes twice).
	 */
	@GameTest
	public void aChestCompositeCostsLittleInThePack(GameTestHelper helper) {
		Combos.Combo whole = Placement.combo(SEWN);
		Combos.KeyedCombo top = new Combos.KeyedCombo(Chapter.DATA, Piece.TOP, whole);
		int alone = ComboPack.build(Set.of(top)).zip().length;
		java.util.Set<Combos.KeyedCombo> withChests = new java.util.HashSet<>(Set.of(top));
		for (String material : OvveTop.PACK_MATERIALS) withChests.add(Combos.KeyedCombo.chest(Chapter.DATA, material, whole));
		byte[] zip = ComboPack.build(withChests).zip();
		Map<String, String> files = ComboAssetsParityTests.unzip(zip);
		int added = zip.length - alone;
		assertThat(files.size() == 2 + OvveTop.PACK_MATERIALS.size(), "a top under three chestplates built " + files.keySet());
		assertThat(added <= 2_400, "three chest composites added " + added + " bytes to the pack; the budget is 2400");
		helper.succeed();
	}

	/** A chest composite's combos.json line reads back as the same key, and its file is the shared code's. */
	@GameTest
	public void aChestCompositeKeyReadsBackAndBuildsTheSharedJson(GameTestHelper helper) {
		Combos.KeyedCombo key = Combos.KeyedCombo.chest(Chapter.CL, "diamond", Placement.combo(SEWN));
		List<Combos.KeyedCombo> back = Combos.parseKeys(List.of(key.key()));
		assertThat(back.equals(List.of(key)), "'" + key.key() + "' read back as " + back);
		assertThat(key.key().startsWith("cl/chest/diamond:"), "the line is " + key.key());
		Map<String, String> files = ComboAssetsParityTests.unzip(ComboPack.build(Set.of(key)).zip());
		String path = Combos.packPath(key);
		assertThat(path.equals("assets/ovvar/equipment/" + ComboAssets.chestPath("cl", "diamond", key.combo().key()) + ".json"), "the pack path is " + path);
		assertThat(ComboAssets.jsonFor(ComboAssets.chestPath("cl", "diamond", key.combo().key())).orElseThrow().equals(files.get(path)), "the pack's chest composite is not the shared code's");
		assertThat(Combos.parseKeys(List.of("cl/chest/:" + key.combo().key(), "cl/chest/diamond/x:" + key.combo().key())).isEmpty(), "a broken chest line read");
		// a material the pack does not wrap (one dropped from PACK_MATERIALS later, or never in it) is not built forever
		assertThat(Combos.parseKeys(List.of("cl/chest/gold:" + key.combo().key(), "cl/chest/Iron:" + key.combo().key())).isEmpty(), "a chest line for a material the pack does not wrap read");
		helper.succeed();
	}

	// ---- helpers

	/**
	 * Dresses the wearer in a top-up ovve of the chapter with these top patches and a chestplate of the
	 * material over it, and returns the chestplate as the viewer's client would get it.
	 */
	private static ItemStack wear(GameTestHelper helper, ServerPlayer wearer, Chapter chapter, String material, List<Placement> top, ServerPlayer viewer) {
		ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
		OvveItem.setTopUp(ovve, true);
		if (!top.isEmpty()) Looks.setSewn(ovve, SpotPlacements.fromList(top).getOrThrow());
		wearer.setItemSlot(EquipmentSlot.LEGS, ovve);
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace((material.equals("gold") ? "golden" : material) + "_chestplate"));
		ItemStack chest = new ItemStack(item);
		assertThat(OvveTop.material(chest) != null && OvveTop.material(chest).equals(material), "no " + material + " chestplate: " + item);
		chest.set(ModComponents.WRAPPED_TOP, wearer.getUUID());
		wearer.setItemSlot(EquipmentSlot.CHEST, chest);
		MinecraftServer server = helper.getLevel().getServer();
		assertThat(server.getPlayerList().getPlayer(wearer.getUUID()) == wearer, "the wearer is not in the player list");
		return PolymerItemUtils.getPolymerItemStack(wearer.getItemBySlot(EquipmentSlot.CHEST), context(viewer, true), helper.getLevel().registryAccess());
	}

	/**
	 * The viewer's packet context as a real connection has it: a mock player's lacks the server, which the wrap
	 * needs to find the wearer. The rest is the mock connection's own. {@code profile} false: no viewer's profile.
	 */
	private static PacketContext context(ServerPlayer viewer, boolean profile) {
		PacketContext own = viewer.connection.getPacketContext();
		MinecraftServer server = viewer.level().getServer();
		return new PacketContext() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T get(ReadKey<T> key) {
				if (key == SERVER_INSTANCE) return (T) server;
				if (key == GAME_PROFILE) return profile ? (T) viewer.getGameProfile() : null;
				if (key == REGISTRY_ACCESS) return (T) server.registryAccess();
				return own.get(key);
			}

			@Override
			public <T> void set(Key<T> key, T value) {
				own.set(key, value);
			}
		};
	}

	private static ResourceKey<EquipmentAsset> asset(ItemStack client) {
		var equippable = client.get(DataComponents.EQUIPPABLE);
		return equippable == null ? null : equippable.assetId().orElse(null);
	}

	private static void forget(Chapter chapter, Combos.Combo whole) {
		for (String material : OvveTop.MATERIALS) Combos.forgetForTest(Combos.KeyedCombo.chest(chapter, material, whole));
	}

	private static Placement place(Spot spot, String patch) {
		Patches.Patch p = Patches.get(patch);
		if (!p.fits(spot)) throw new IllegalStateException(patch + " does not fit " + spot.id() + "; pick another for the sample");
		return new Placement(spot, p);
	}

	private static void assertThat(boolean condition, String message) {
		if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
	}
}
