package metacraft.ovvar.gametest;

import metacraft.ovvar.Motd;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.ServerConfig;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Ownership;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.content.SpotPlacements;
import metacraft.ovvar.datagen.Tex;
import metacraft.ovvar.store.DesignStoreConfig;
import metacraft.ovvar.store.FileBackend;
import metacraft.ovvar.store.JdbcBackend;
import metacraft.ovvar.store.OwnedSewing;
import metacraft.ovvar.store.StashConfig;
import metacraft.ovvar.store.Wardrobe;
import metacraft.ovvar.store.WardrobeBackend;
import metacraft.ovvar.store.Wardrobes;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.pack.Combos;
import metacraft.ovvar.pack.EquipmentJson;
import metacraft.ovvar.pack.Trims;
import metacraft.ovvar.pack.WardrobeArt;
import metacraft.ovvar.pack.WardrobeFont;
import metacraft.ovvar.pack.WardrobePreview;
import metacraft.ovvar.pack.WardrobePreview.Angle;
import metacraft.ovvar.sewing.WardrobeAction;
import metacraft.ovvar.sewing.WardrobeGui;
import metacraft.ovvar.sewing.WardrobeMannequin;
import metacraft.ovvar.sewing.StashSession;
import eu.pb4.sgui.api.elements.GuiElement;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The wardrobe store: both backends refuse a write that names the wrong version, the cache in
 * front of them turns a lost race into a refetch, a patch moves between the stash and the design
 * and never multiplies, two ovves of one owner are one design, and an unpick hands the patch out
 * once no matter how many ovves show it; and the ownership rules: somebody else's ovve is not worn,
 * sewn on or unpicked, the owner's own is, and the MOTD says which server this is. The tests that swap the server's backend for a temporary
 * one take turns ({@link #BUSY}: game tests in a batch run together) and put a throwaway one back —
 * never the run dir's configured store (a live JDBC backend, in a deployed run dir, whose rows are
 * real): every mock player these tests spawn joins for real and is fetched on join, so the
 * configured store must never be the thing listening when that happens.
 */
public final class WardrobeTests {
	/**
	 * How many texels at the right-hand end of a garment texture's marker row are datagen's own data
	 * rather than art: the marker, the kind texel, the layer texel and the four-colour debug palette.
	 * A test walking a whole texture for stray pixels skips them by position, never by colour.
	 */
	private static final int MARKER_ROW_DATA = 7;

	/** Held by whichever sequence test is using the server's wardrobe store right now. */
	private static final AtomicBoolean BUSY = new AtomicBoolean();

	static {
		// The moment the server is up — before the first test spawns a mock player and well before
		// any test claims BUSY — detach the store from whatever config/ovvar.json names. Fabric-api's
		// GameTest has no batch() to force these onto one worker (javap confirms), so without this a
		// stray on-join fetch for a fixed test UUID can hit a live JDBC store with real rows before
		// any test-owned backend is in place.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> Wardrobes.use(server, idleBackend()));
	}

	private static WardrobeBackend idleBackend() {
		try {
			return new FileBackend(Files.createTempDirectory("ovvar-wardrobes-idle"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Runs a store test's step; whatever it throws (a failed assertion or anything else) the lock
	 * and the throwaway backend come back first, so a broken test never leaves {@link #BUSY} held
	 * forever and turns every other store test's wait into a permanent "another store test is
	 * running" failure instead of the retry it is meant to be.
	 */
	private static void guarded(MinecraftServer server, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException | Error e) {
			release(server);
			throw e;
		}
	}

	private static final Chapter CHAPTER = Chapter.values()[0];
	private static final Patches.Patch ITK_PATCH = Patches.get("itk"), NYCKELN_PATCH = Patches.get("nyckeln");
	private static final Placement ITK = new Placement(Spot.FRONT_TOP_LEFT, ITK_PATCH);
	private static final Placement NYCKELN = new Placement(Spot.BACK_TOP_RIGHT, NYCKELN_PATCH);
	/** A cell the doll only shows from one side: the outer face of the wearer's right sleeve. */
	private static final Placement SLEEVE = new Placement(Spot.SLEEVE_OUT_TOP_R, ITK_PATCH);

	// ---- the record

	@GameTest
	public void patchesMoveBetweenStashAndDesign(GameTestHelper helper) {
		Wardrobe none = Wardrobe.NONE;
		if (none.sew(CHAPTER, ITK).isPresent()) helper.fail("sewn a patch that is not in the stash");
		Wardrobe one = none.add(ITK_PATCH, 1);
		if (one.count(ITK_PATCH) != 1) helper.fail("count after add: " + one.count(ITK_PATCH));
		Wardrobe sewn = one.sew(CHAPTER, ITK).orElseThrow();
		if (sewn.count(ITK_PATCH) != 0) helper.fail("the stash still holds the sewn patch");
		if (!ITK.equals(sewn.at(CHAPTER, Spot.FRONT_TOP_LEFT).orElse(null))) helper.fail("the patch is not on the design");
		if (sewn.sew(CHAPTER, ITK).isPresent()) helper.fail("sewn the same patch twice from an empty stash");
		if (sewn.add(ITK_PATCH, 1).sew(CHAPTER, ITK).isPresent()) helper.fail("sewn over an occupied spot");
		Wardrobe back = sewn.unpick(CHAPTER, Spot.FRONT_TOP_LEFT).orElseThrow();
		if (back.count(ITK_PATCH) != 1 || back.design(CHAPTER).isPresent()) helper.fail("unpick did not move the patch back: " + back);
		if (back.unpick(CHAPTER, Spot.FRONT_TOP_LEFT).isPresent()) helper.fail("unpicked an empty spot");
		if (!back.sameContents(one)) helper.fail("a sew and an unpick do not cancel out: " + back + " vs " + one);
		helper.succeed();
	}

	// ---- the backends

	@GameTest
	public void fileBackendStoresWithVersions(GameTestHelper helper) throws IOException {
		storesWithVersions(helper, new FileBackend(Files.createTempDirectory("ovvar-wardrobes")));
	}

	@GameTest
	public void jdbcBackendStoresWithVersions(GameTestHelper helper) throws IOException {
		storesWithVersions(helper, new JdbcBackend(h2()));
	}

	/**
	 * The same against a real database when one is named: {@code -Dovvar.test.jdbc.url=jdbc:postgresql://host/db}
	 * (plus {@code ovvar.test.jdbc.user} / {@code .password}); passes trivially otherwise.
	 */
	@GameTest
	public void realDatabaseStoresWithVersions(GameTestHelper helper) throws IOException {
		String url = System.getProperty("ovvar.test.jdbc.url");
		if (url == null) {
			helper.succeed();
			return;
		}
		storesWithVersions(helper, new JdbcBackend(new DesignStoreConfig.Jdbc(url, System.getProperty("ovvar.test.jdbc.user", ""),
				System.getProperty("ovvar.test.jdbc.password", ""), "", "ovve_wardrobes_test_" + Long.toHexString(System.nanoTime()), "", 5, 5)));
	}

	private static DesignStoreConfig.Jdbc h2() {
		return new DesignStoreConfig.Jdbc("jdbc:h2:mem:ovvar_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1",
				"sa", "", "", "ovve_wardrobes", "org.h2.Driver", 5, 5);
	}

	private static void storesWithVersions(GameTestHelper helper, WardrobeBackend backend) throws IOException {
		UUID owner = UUID.randomUUID();
		if (backend.load(owner).isPresent()) helper.fail("a fresh store has a wardrobe");
		Wardrobe v1 = Wardrobe.NONE.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElseThrow().withVersion(1);
		if (!backend.store(owner, v1, 0)) helper.fail("first write refused");
		if (backend.store(owner, v1, 0)) helper.fail("a second insert of the same owner went through");
		Wardrobe v2 = v1.add(NYCKELN_PATCH, 3).sew(CHAPTER, NYCKELN).orElseThrow().withVersion(2);
		if (!backend.store(owner, v2, 1)) helper.fail("update from version 1 refused");
		if (backend.store(owner, v2.withVersion(3), 1)) helper.fail("update from a stale version went through");
		Optional<Wardrobe> loaded = backend.load(owner);
		if (!v2.equals(loaded.orElse(null))) helper.fail("loaded " + loaded + ", wanted " + v2);
		Wardrobe empty = Wardrobe.NONE.withVersion(3);
		if (!backend.store(owner, empty, 2)) helper.fail("writing an empty wardrobe refused");
		if (!empty.equals(backend.load(owner).orElse(null))) helper.fail("empty wardrobe did not round-trip");
		backend.close();
		helper.succeed();
	}

	// ---- the cache

	@GameTest(maxTicks = 1200)
	public void wardrobesUpdateIsCompareAndSet(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		FileBackend behind = new FileBackend(dir);   // the same files, written "from another server"
		UUID owner = UUID.randomUUID();
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElse(null), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "sew outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					if (Wardrobes.current(owner).version() != 1) helper.fail("cached version " + Wardrobes.current(owner).version() + ", wanted 1");
					// Another server unpicks and sews on: the store is at version 5 with only the Nyckeln on.
					try {
						Wardrobe theirs = Wardrobe.NONE.add(ITK_PATCH, 1).add(NYCKELN_PATCH, 1).sew(CHAPTER, NYCKELN).orElseThrow().withVersion(5);
						if (!behind.store(owner, theirs, 1)) helper.fail("behind-the-back write refused");
					} catch (IOException e) {
						throw new GameTestAssertException(Component.literal(e.toString()), 0);
					}
					outcome.set(null);
					Wardrobes.update(owner, w -> w.unpick(CHAPTER, Spot.FRONT_TOP_LEFT).orElse(null), outcome::set);
				}))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.CONFLICT, "stale write outcome " + outcome.get()))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner) && Wardrobes.current(owner).version() == 5, "cache not refetched to version 5"))
				.thenExecute(() -> guarded(server, () -> {
					if (Wardrobes.current(owner).at(CHAPTER, Spot.FRONT_TOP_LEFT).isPresent()) helper.fail("the itk survived the refetch");
					if (Wardrobes.current(owner).count(ITK_PATCH) != 1) helper.fail("the itk is not back in the stash after the refetch");
					outcome.set(null);
					Wardrobes.update(owner, w -> w.sew(CHAPTER, ITK).orElse(null), outcome::set);
				}))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK && Wardrobes.current(owner).version() == 6, "retry after the refetch: " + outcome.get()))
				.thenExecute(() -> release(server))
				.thenSucceed();
	}

	@GameTest(maxTicks = 1200)
	public void twoOvvesShareOneDesign(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		ItemStack a = new ItemStack(ModContent.ovve(CHAPTER)), b = new ItemStack(ModContent.ovve(CHAPTER));
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					// Their first tick in a player's inventory binds them; nothing to adopt, both are plain.
					OvveItem.syncDesign(player, a);
					OvveItem.syncDesign(player, b);
					if (!owner.equals(OvveItem.owner(a)) || !owner.equals(OvveItem.owner(b))) helper.fail("not bound on pickup");
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElse(null), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "sew outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					OvveItem.syncDesign(player, a);
					OvveItem.syncDesign(player, b);
					if (!ITK.equals(Looks.at(a, Spot.FRONT_TOP_LEFT)) || !ITK.equals(Looks.at(b, Spot.FRONT_TOP_LEFT))) helper.fail("the sew did not reach both ovves");
					outcome.set(null);
					Wardrobes.update(owner, w -> w.unpick(CHAPTER, Spot.FRONT_TOP_LEFT).orElse(null), outcome::set);
				}))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "unpick outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					OvveItem.syncDesign(player, a);
					OvveItem.syncDesign(player, b);
					if (Looks.at(a, Spot.FRONT_TOP_LEFT) != null || Looks.at(b, Spot.FRONT_TOP_LEFT) != null) helper.fail("the unpick did not reach both ovves");
					release(server);
				}))
				.thenSucceed();
	}

	/** The dupe rule: with the patch showing on two ovves, unpicking it on the second gives nothing. */
	@GameTest(maxTicks = 1200)
	public void unpickGivesThePatchOnceAcrossOvves(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		UUID owner = UUID.randomUUID();
		ItemStack a = new ItemStack(ModContent.ovve(CHAPTER)), b = new ItemStack(ModContent.ovve(CHAPTER));
		OvveItem.setOwner(a, owner);
		OvveItem.setOwner(b, owner);
		List<OwnedSewing.Unpicked> given = new ArrayList<>();
		List<String> refused = new ArrayList<>();
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElse(null), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "sew outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					OvveItem.refresh(a);
					OvveItem.refresh(b);
					if (!ITK.equals(Looks.at(b, Spot.FRONT_TOP_LEFT))) helper.fail("b does not show the ITK");
					// Into the hand (a survival server): the store lets go of it first, and only once.
					OwnedSewing.unpick(null, a, Spot.FRONT_TOP_LEFT, false, given::add, refused::add);
				}))
				.thenWaitUntil(() -> assertThat(given.size() + refused.size() == 1, "first unpick not answered"))
				.thenExecute(() -> guarded(server, () -> {
					if (given.size() != 1 || !ITK.equals(given.get(0).placement()) || given.get(0).toStash()) helper.fail("first unpick: given " + given + ", refused " + refused);
					if (Wardrobes.current(owner).count(ITK_PATCH) != 0) helper.fail("an unpick into the hand also left one in the stash");
					// b still carries the old copy; the unpick asks the store, not the item.
					if (!ITK.equals(Looks.at(b, Spot.FRONT_TOP_LEFT))) helper.fail("b was refreshed before being asked");
					OwnedSewing.unpick(null, b, Spot.FRONT_TOP_LEFT, false, given::add, refused::add);
				}))
				.thenWaitUntil(() -> assertThat(given.size() + refused.size() == 2, "second unpick not answered"))
				.thenExecute(() -> guarded(server, () -> {
					if (given.size() != 1 || refused.size() != 1) helper.fail("second unpick: given " + given + ", refused " + refused);
					release(server);
				}))
				.thenSucceed();
	}

	// ---- ownership: whose ovve this is

	/** Somebody else's ovve does not go on, and one forced into the slot comes off on the next tick. */
	@GameTest
	public void foreignOvveCannotBeWorn(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
		OvveItem.setOwner(ovve, UUID.randomUUID());
		if (player.isEquippableInSlot(ovve, EquipmentSlot.LEGS)) helper.fail("the armour slot took a foreign ovve");
		if (player.canEquipWithDispenser(ovve)) helper.fail("a dispenser could put a foreign ovve on");
		if (Ownership.wearRefusal(player, ovve) == null) helper.fail("no refusal to read for a foreign ovve");
		// Forced in (/item replace, another mod): the wearer's tick takes it off, into their inventory.
		player.setItemSlot(EquipmentSlot.LEGS, ovve);
		ovve.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
		if (!player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) helper.fail("the tick left a foreign ovve on");
		if (!player.getInventory().contains(stack -> stack.getItem() instanceof OvveItem)) helper.fail("the evicted ovve went nowhere");
		helper.succeed();
	}

	@GameTest
	public void ownerCanWearTheirOvve(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
		OvveItem.setOwner(ovve, player.getUUID());
		if (!player.isEquippableInSlot(ovve, EquipmentSlot.LEGS)) helper.fail("the owner could not put their own ovve on");
		if (Ownership.wearRefusal(player, ovve) != null) helper.fail("refused the owner: " + Ownership.wearRefusal(player, ovve));
		player.setItemSlot(EquipmentSlot.LEGS, ovve);
		ovve.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
		if (player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) helper.fail("the tick took the owner's own ovve off");
		helper.succeed();
	}

	@GameTest
	public void unownedOvveBindsToTheFirstWearer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
		if (!player.isEquippableInSlot(ovve, EquipmentSlot.LEGS)) helper.fail("an unowned ovve would not go on");
		player.setItemSlot(EquipmentSlot.LEGS, ovve);
		ovve.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
		if (!player.getUUID().equals(OvveItem.owner(ovve))) helper.fail("an unowned ovve did not bind to its wearer");
		if (player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) helper.fail("the ovve it just bound to came off again");
		helper.succeed();
	}

	/** rebind: a given ovve becomes the holder's. allow: anyone wears it, still showing its owner's design. */
	@GameTest
	public void othersOvveRebindAndAllowStillWork(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		DesignStoreConfig designs = OvvarConfig.get().designs();
		try {
			ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
			UUID someoneElse = UUID.randomUUID();
			OvveItem.setOwner(ovve, someoneElse);
			OvvarConfig.modify(config -> config.designs(designs.othersOvve(DesignStoreConfig.OthersOvve.ALLOW)));
			if (!player.isEquippableInSlot(ovve, EquipmentSlot.LEGS)) helper.fail("allow: a foreign ovve still would not go on");
			if (Ownership.wearRefusal(player, ovve) != null) helper.fail("allow: still refused");
			OvveItem.syncDesign(player, ovve);
			if (!someoneElse.equals(OvveItem.owner(ovve))) helper.fail("allow: the ovve changed hands");

			OvvarConfig.modify(config -> config.designs(designs.othersOvve(DesignStoreConfig.OthersOvve.REBIND)));
			OvveItem.syncDesign(player, ovve);
			if (!player.getUUID().equals(OvveItem.owner(ovve))) helper.fail("rebind: the ovve did not become the holder's");
			if (!player.isEquippableInSlot(ovve, EquipmentSlot.LEGS)) helper.fail("rebind: the rebound ovve would not go on");
		} finally {
			OvvarConfig.modify(config -> config.designs(designs));
		}
		helper.succeed();
	}

	/** rebind is a pickup too: with bind_on_pickup off nothing binds and nothing changes hands. */
	@GameTest
	public void rebindStillNeedsBindOnPickup(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		DesignStoreConfig designs = OvvarConfig.get().designs();
		try {
			OvvarConfig.modify(config -> config.designs(
					designs.othersOvve(DesignStoreConfig.OthersOvve.REBIND).bindOnPickup(false)));
			ItemStack theirs = new ItemStack(ModContent.ovve(CHAPTER));
			UUID someoneElse = UUID.randomUUID();
			OvveItem.setOwner(theirs, someoneElse);
			OvveItem.syncDesign(player, theirs);
			if (!someoneElse.equals(OvveItem.owner(theirs))) helper.fail("rebound an ovve with bind_on_pickup off");
			ItemStack nobodys = new ItemStack(ModContent.ovve(CHAPTER));
			OvveItem.syncDesign(player, nobodys);
			if (OvveItem.owner(nobodys) != null) helper.fail("bound an unowned ovve with bind_on_pickup off");
		} finally {
			OvvarConfig.modify(config -> config.designs(designs));
		}
		helper.succeed();
	}

	/** Shears on somebody else's ovve change nothing: not the store, not the stash, not the ovve. */
	@GameTest(maxTicks = 1200)
	public void foreignOvveCannotBeUnpicked(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		UUID owner = UUID.randomUUID();
		ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
		OvveItem.setOwner(ovve, owner);
		List<OwnedSewing.Unpicked> given = new ArrayList<>();
		List<String> refused = new ArrayList<>();
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElse(null), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "sew outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					OvveItem.refresh(ovve);
					OwnedSewing.unpick(stranger, ovve, Spot.FRONT_TOP_LEFT, false, given::add, refused::add);
				}))
				.thenWaitUntil(() -> assertThat(given.size() + refused.size() == 1, "the unpick was not answered"))
				.thenExecute(() -> guarded(server, () -> {
					if (!given.isEmpty()) helper.fail("a stranger unpicked a patch: " + given);
					if (!refused.get(0).contains("belongs to")) helper.fail("refusal text: " + refused.get(0));
					Wardrobe now = Wardrobes.current(owner);
					if (now.version() != 1) helper.fail("the store moved on a refused unpick: version " + now.version());
					if (now.count(ITK_PATCH) != 0) helper.fail("the stash changed on a refused unpick");
					if (!ITK.equals(Looks.at(ovve, Spot.FRONT_TOP_LEFT))) helper.fail("the ovve lost its patch anyway");
					release(server);
				}))
				.thenSucceed();
	}

	/** Nor may a stranger sew on it, even holding the patch. */
	@GameTest(maxTicks = 1200)
	public void foreignOvveCannotBeSewn(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		UUID owner = UUID.randomUUID();
		ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
		OvveItem.setOwner(ovve, owner);
		List<String> sewn = new ArrayList<>();
		List<String> refused = new ArrayList<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> OwnedSewing.sew(stranger, ovve, NYCKELN, true, () -> sewn.add("sewn"), refused::add))
				.thenWaitUntil(() -> assertThat(sewn.size() + refused.size() == 1, "the sew was not answered"))
				.thenExecute(() -> guarded(server, () -> {
					if (!sewn.isEmpty()) helper.fail("a stranger sewed on somebody else's ovve");
					if (!refused.get(0).contains("belongs to")) helper.fail("refusal text: " + refused.get(0));
					if (Wardrobes.current(owner).version() != 0) helper.fail("the store moved on a refused sew");
					release(server);
				}))
				.thenSucceed();
	}

	/** The owner themselves sews and unpicks as before. */
	@GameTest(maxTicks = 1200)
	public void ownerCanUnpickAndSew(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
		OvveItem.setOwner(ovve, owner);
		List<String> sewn = new ArrayList<>();
		List<OwnedSewing.Unpicked> given = new ArrayList<>();
		List<String> refused = new ArrayList<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> OwnedSewing.sew(player, ovve, ITK, true, () -> sewn.add("sewn"), refused::add))
				.thenWaitUntil(() -> assertThat(sewn.size() + refused.size() == 1, "the sew was not answered"))
				.thenExecute(() -> guarded(server, () -> {
					if (sewn.isEmpty()) helper.fail("the owner could not sew on their own ovve: " + refused);
					if (!ITK.equals(Looks.at(ovve, Spot.FRONT_TOP_LEFT))) helper.fail("the sew did not reach the ovve");
					// The store's own copy, not the ovve's, is what unpick reads: re-seed it straight from the
					// (isolated) backend rather than trust the cache to still show what the sew callback just
					// wrote — a mock player join anywhere else in the suite can fetch this same fixed test
					// UUID in between and clobber the shared cache with whatever it finds.
					Wardrobes.refresh(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner) && Wardrobes.current(owner).at(CHAPTER, Spot.FRONT_TOP_LEFT).isPresent(),
						"the re-seeded wardrobe does not show the ITK"))
				.thenExecute(() -> guarded(server, () ->
						OwnedSewing.unpick(player, ovve, Spot.FRONT_TOP_LEFT, true, given::add, refused::add)))
				.thenWaitUntil(() -> assertThat(given.size() + refused.size() == 1, "the unpick was not answered"))
				.thenExecute(() -> guarded(server, () -> {
					if (given.isEmpty()) helper.fail("the owner could not unpick from their own ovve: " + refused);
					if (Wardrobes.current(owner).count(ITK_PATCH) != 1) helper.fail("the unpicked patch is not in the stash");
					release(server);
				}))
				.thenSucceed();
	}

	// ---- the config file's _help

	/**
	 * {@code config/ovvar.json} and its {@code designs}, {@code stash} and {@code server} blocks
	 * each carry a {@code _help} object with an entry for every key they write, plus {@code _about};
	 * this is the only comment JSON gets, so a key silently missing its line is worth failing on.
	 */
	@GameTest
	public void configHelpCoversEveryKey(GameTestHelper helper) {
		// The plain factory-default config, not nudged off default anywhere: server/designs/stash
		// (and jdbc inside designs) use optionalFieldOf(key).xmap(...) precisely so a block equal to
		// its own default is still written (and so is its _help), unlike the scalar keys inside each
		// block, which optionalFieldOf(key, default) still omits when they equal that default.
		OvvarConfig config = new OvvarConfig(true, 6, ServerConfig.DEFAULT, DesignStoreConfig.DEFAULT, StashConfig.DEFAULT);
		JsonElement json = OvvarConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, config)
				.getOrThrow(message -> new IllegalStateException("config does not encode: " + message));
		JsonObject root = json.getAsJsonObject();
		assertHelpCoversKeys(helper, root, OvvarConfig.HELP, "root");
		assertHelpCoversKeys(helper, root.getAsJsonObject("server"), ServerConfig.HELP, "server");
		JsonObject designs = root.getAsJsonObject("designs");
		assertHelpCoversKeys(helper, designs, DesignStoreConfig.HELP, "designs");
		assertHelpCoversKeys(helper, designs == null ? null : designs.getAsJsonObject("jdbc"), DesignStoreConfig.Jdbc.HELP, "designs.jdbc");
		assertHelpCoversKeys(helper, root.getAsJsonObject("stash"), StashConfig.HELP, "stash");
		helper.succeed();
	}

	private static void assertHelpCoversKeys(GameTestHelper helper, JsonObject block, Map<String, String> help, String name) {
		if (block == null) { helper.fail(name + " block was not written at all"); return; }
		if (!block.has("_help")) helper.fail(name + " has no _help");
		JsonObject written = block.getAsJsonObject("_help");
		if (!written.has("_about")) helper.fail(name + "._help has no _about");
		for (String key : block.keySet()) {
			if (key.equals("_help")) continue;
			if (!written.has(key)) helper.fail(name + "._help is missing an entry for " + key);
		}
		for (String key : help.keySet()) {
			if (!written.has(key)) helper.fail(name + "._help does not match its HELP map: missing " + key);
		}
	}

	// ---- the wardrobe screen

	/** The title carries the ovvar:wardrobe font and the chapter's own glyph codepoint. */
	@GameTest
	public void wardrobeTitleCarriesTheChapterGlyph(GameTestHelper helper) {
		for (Chapter tab : Chapter.values()) {
			Component title = WardrobeGui.title(tab, Wardrobe.NONE, Angle.FRONT);
			char glyph = WardrobeArt.chapterChar(tab);
			boolean foundGlyph = false, foundFont = false;
			for (Component part : allParts(title)) {
				if (WardrobeArt.FONT.equals(part.getStyle().getFont() instanceof net.minecraft.network.chat.FontDescription.Resource r ? r.id() : null)) {
					foundFont = true;
					if (part.getString().indexOf(glyph) >= 0) foundGlyph = true;
				}
			}
			if (!foundFont) helper.fail(tab + ": no part of the title uses the wardrobe font");
			if (!foundGlyph) helper.fail(tab + ": no part of the title carries its glyph U+" + Integer.toHexString(glyph));
		}
		helper.succeed();
	}

	private static List<Component> allParts(Component component) {
		List<Component> out = new ArrayList<>();
		out.add(component);
		for (Component sibling : component.getSiblings()) out.addAll(allParts(sibling));
		return out;
	}

	/**
	 * <b>Where the title's glyphs really land.</b> The whole screen — the background, the tab
	 * highlight, the paper doll, every patch on it, the empty-state notices, the page counter and the
	 * stats — is one string of codepoints in one container title: a {@code space} provider walks the
	 * cursor and each bitmap glyph draws at the cursor and advances it. Until now nothing checked
	 * that arithmetic. A glyph anchored from the wrong corner, an advance that does not match the
	 * texture, or one glyph too many appended, all come out as a picture somewhere it does not belong
	 * on somebody's screen — and as nothing at all in any test here.
	 *
	 * <p>So the title is walked the way the client walks it: the advances read out of the very font
	 * JSON the pack ships, each glyph's own advance, one cursor. Then:
	 * <ul>
	 *   <li>every glyph is inside the container's {@value WardrobeArt#WIDTH}×{@value WardrobeArt#HEIGHT};
	 *   <li>nothing is drawn on the action row's buttons — the one row that is all slots and no
	 *	   picture, so anything landing there is a pale square over a button (the page counter's own
	 *	   two columns, which the design puts in that row's spare middle, are the exception);
	 *   <li>every glyph lands at its own declared place, bar the three that move by design: the tab
	 *	   highlight follows the active tab along the tab row, and the stats and page rows are laid
	 *	   out from a computed x along their own row;
	 *   <li>the cursor comes back to where it started, which is what lets the chapter's name follow
	 *	   as ordinary text — and is the first thing a wrong advance breaks.
	 * </ul>
	 *
	 * <p>Every position here is worked out by the server and sent in the title, so this also settles
	 * what a client on an out-of-date pack can and cannot do: the codepoints it is sent may name the
	 * wrong <em>picture</em> (adding a cell or a patch renumbers the preview glyphs), but never a
	 * wrong <em>place</em>. A picture on the action row cannot come from this font at all.
	 *
	 * <p>The page counter's exception is carried but not exercised: with
	 * {@value metacraft.ovvar.sewing.WardrobeGui#POCKET} pocket slots and {@link Patches#all} shorter
	 * than that, {@code pageCount} is always 1 and the counter is never drawn. The exception is here
	 * for the catalogue that outgrows the pocket.
	 */
	@GameTest
	public void theTitlesGlyphsLandWhereTheScreenMeansThem(GameTestHelper helper) {
		Map<Character, Integer> advances = spaceAdvances();
		Map<Character, WardrobeFont.Glyph> byChar = new java.util.LinkedHashMap<>();
		for (WardrobeFont.Glyph glyph : WardrobeFont.glyphs()) byChar.put(glyph.codepoint(), glyph);
		// The states that change what the title carries: nothing sewn and nothing stashed (both
		// notices), a design on every angle, a stash big enough to page (the page counter), and a
		// look at somebody else's (the look-only notice).
		List<Placement> sewn = List.of(new Placement(Spot.BACK_BIG, Patches.get("itk")), new Placement(Spot.SEAT, Patches.get("pung")),
				new Placement(Spot.SLEEVE_OUT_TOP_R, Patches.get("spiken")), new Placement(Spot.LEG_OUT_MID_L, Patches.get("maid")));
		Wardrobe bare = Wardrobe.NONE;
		Wardrobe dressed = Wardrobe.NONE.withDesign(CHAPTER, SpotPlacements.fromList(sewn).getOrThrow());
		Wardrobe stocked = dressed;
		for (Patches.Patch patch : Patches.all()) stocked = stocked.add(patch, 3);
		int checked = 0;
		for (Chapter chapter : Chapter.values()) {
			for (Angle angle : Angle.values()) {
				for (Wardrobe wardrobe : List.of(bare, dressed, stocked)) {
					for (boolean own : new boolean[]{true, false}) {
						for (int tab = 0; tab < WardrobeGui.TAB_COLS; tab++) {
							checked++;
							walkTitle(helper, WardrobeGui.title(chapter, wardrobe, angle, tab, own, 0), chapter, advances, byChar);
						}
					}
				}
			}
		}
		if (checked == 0) helper.fail("no title walked");
		helper.succeed();
	}

	/** The font's own space advances, read out of the generated JSON rather than written out again here. */
	private static Map<Character, Integer> spaceAdvances() {
		Map<Character, Integer> out = new java.util.LinkedHashMap<>();
		for (JsonElement provider : WardrobeArt.fontJson().getAsJsonArray("providers")) {
			JsonObject object = provider.getAsJsonObject();
			if (!object.get("type").getAsString().equals("space")) continue;
			for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("advances").entrySet()) {
				out.put(entry.getKey().charAt(0), entry.getValue().getAsInt());
			}
		}
		return out;
	}

	private static void walkTitle(GameTestHelper helper, Component title, Chapter chapter,
			Map<Character, Integer> advances, Map<Character, WardrobeFont.Glyph> byChar) {
		String string = title.getString();
		int x = WardrobeFont.TITLE_X, at = 0;
		for (; at < string.length(); at++) {
			char c = string.charAt(at);
			Integer advance = advances.get(c);
			if (advance != null) {
				x += advance;
				continue;
			}
			Chapter background = null;
			for (Chapter other : Chapter.values()) if (WardrobeArt.chapterChar(other) == c) background = other;
			if (background != null) {
				if (x != 0) helper.fail("the " + background.id + " background is drawn at x " + x + ", not at the container's own left edge");
				x += WardrobeArt.WIDTH + 1;
				continue;
			}
			WardrobeFont.Glyph glyph = byChar.get(c);
			if (glyph == null) break;   // the chapter's name, appended last, in the ordinary font
			checkGlyphPlace(helper, glyph, x, chapter);
			x += glyph.advance();
		}
		// Everything after the glyphs is the title's own text, and the cursor is back where it began
		// — the property every position on this screen is measured from.
		String rest = string.substring(at);
		if (!rest.equals(WardrobeGui.titleText(chapter))) {
			helper.fail("after the glyphs the title reads '" + rest + "', wanted '" + WardrobeGui.titleText(chapter) + "'");
		}
		if (x != WardrobeFont.TITLE_X) {
			helper.fail("the glyphs left the cursor at x " + x + ", not back at " + WardrobeFont.TITLE_X + ": an advance does not match its texture");
		}
	}

	/** One glyph, drawn at cursor {@code x}: inside the screen, off the action row's buttons, and at its own place. */
	private static void checkGlyphPlace(GameTestHelper helper, WardrobeFont.Glyph glyph, int x, Chapter chapter) {
		int y = glyph.top(), x1 = x + glyph.width(), y1 = y + glyph.height();
		String where = glyph.name() + " (" + chapter.id + ") at " + x + "," + y + " " + glyph.width() + "x" + glyph.height();
		if (x < 0 || y < 0 || x1 > WardrobeArt.WIDTH || y1 > WardrobeArt.HEIGHT) {
			helper.fail(where + " is outside the container's " + WardrobeArt.WIDTH + "x" + WardrobeArt.HEIGHT);
		}
		// The action row is buttons, not picture. Only the page counter belongs there, in the two
		// columns the design keeps free for it.
		boolean pageCounter = glyph.name().startsWith("page/");
		for (int col = 0; col < 9; col++) {
			if (pageCounter && (col == 5 || col == 6)) continue;
			int boxX = WardrobeFont.ITEM_X + WardrobeFont.PITCH * col, boxY = WardrobeFont.ITEM_Y + WardrobeFont.PITCH * WardrobeGui.ACTION_ROW;
			boolean overlaps = x < boxX + WardrobeFont.ICON && boxX < x1 && y < boxY + WardrobeFont.ICON && boxY < y1;
			if (overlaps) helper.fail(where + " is drawn on the action row's slot at column " + col + ", which is a button");
		}
		// And at its own place, bar the three that move along a row by design.
		boolean moves = pageCounter || glyph.name().startsWith("stats/") || glyph == WardrobeFont.ACTIVE_TAB;
		if (!moves && x != glyph.x()) helper.fail(where + " is drawn at x " + x + ", but its own place is " + glyph.x());
		if (moves && y != glyph.top()) helper.fail(where + " moved off its own row");
	}

	/** A wardrobe with 3 stash kinds and 2 sewn placements fills exactly that many collection and preview slots. */
	@GameTest(maxTicks = 1200)
	public void wardrobeSlotsFollowTheWardrobe(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();

		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				// Two of each: one of each sewn on, one of each left in the stash, so both the
				// collection and the preview have something to fill (the catalogue has two patches).
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 2).add(NYCKELN_PATCH, 2)
						.sew(CHAPTER, ITK).orElseThrow()
						.sew(CHAPTER, NYCKELN).orElse(null), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "setup outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					WardrobeGui gui = WardrobeGui.forTest(player, CHAPTER, Angle.FRONT);
					int filledCollection = 0;
					for (int i = 0; i < 20; i++) {
						int slot = 9 + (i / 5) * 9 + (i % 5);
						if (!isEmpty(gui, slot)) filledCollection++;
					}
					if (filledCollection != 2) helper.fail("collection slots filled: " + filledCollection + ", wanted 2 (one kind per patch left in the stash)");
					if (isEmpty(gui, WardrobeGui.previewSlot(ITK.spot()))) helper.fail("no preview item at the ITK's slot");
					// The Nyckeln is sewn on the back, so its tooltip is on the back view, not this one.
					WardrobeGui back = WardrobeGui.forTest(player, CHAPTER, Angle.BACK);
					if (isEmpty(back, WardrobeGui.previewSlot(Angle.BACK, NYCKELN.spot()))) helper.fail("no preview item at the Nyckeln's slot on the back view");
					if (gui.getGuiElement(WardrobeGui.previewSlot(ITK.spot())).getGuiCallback() != GuiElement.EMPTY_CALLBACK) {
						helper.fail("a preview item has a click callback; it should be hover-only");
					}
					// survival mode: every action is present
					for (int slot : new int[]{45, 46, 47, 48, 52, 53}) {
						if (isEmpty(gui, slot)) helper.fail("action slot " + slot + " missing on a survival server");
					}
					if (!isEmpty(gui, 49)) helper.fail("finish-sewing button present with no session running");
				}))
				.thenExecute(() -> guarded(server, () -> {
					// With a session running, "finish sewing" (col 4 of the action row, slot 49) appears.
					StashConfig sessions = OvvarConfig.get().stash();
					try {
						StashConfig withSessions = new StashConfig(sessions.minigameServer(), sessions.sewGameModes(), sessions.ingameObjective(),
								sessions.bankOnPickup(), sessions.bankInCreative(), sessions.unpickToStash(), sessions.withdraw(), true,
								sessions.stashClick(), sessions.anyStand(), sessions.sessionReach(), sessions.sessionSeconds(), sessions.explainInChat());
						OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(), withSessions));
						StashSession.start(player, NYCKELN_PATCH, why -> helper.fail("could not start a session: " + why));
						WardrobeGui withSession = WardrobeGui.forTest(player, CHAPTER, Angle.FRONT);
						if (isEmpty(withSession, 49)) helper.fail("finish-sewing button missing with a session running");
						StashSession.end(player, null);
					} finally {
						OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(), sessions));
					}
					release(server);
				}))
				.thenSucceed();
	}

	/** On a minigame server the take-out and sew actions are grey panes carrying the reason; the help book still explains why. */
	@GameTest(maxTicks = 1200)
	public void wardrobeActionsRespectTheMode(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		StashConfig stash = OvvarConfig.get().stash();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> guarded(server, () -> {
					try {
						OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(),
								new StashConfig(true, stash.sewGameModes(), stash.ingameObjective(), stash.bankOnPickup(), stash.bankInCreative(),
										stash.unpickToStash(), stash.withdraw(), stash.sessions(), stash.stashClick(), stash.anyStand(),
										stash.sessionReach(), stash.sessionSeconds(), stash.explainInChat())));
						WardrobeGui gui = WardrobeGui.forTest(player, CHAPTER, Angle.FRONT);
						// Not gone, and not a pane of grey glass either: the action's own icon, dimmed
						// and slashed, named "(not here)" and carrying the reason.
						Map<Integer, WardrobeAction> refused = Map.of(45, WardrobeAction.TAKE_OUT, 47, WardrobeAction.SEW, 48, WardrobeAction.SEE_3D);
						for (var entry : refused.entrySet()) {
							int slot = entry.getKey();
							WardrobeAction what = entry.getValue();
							if (isEmpty(gui, slot)) helper.fail(what + " is missing instead of dimmed on a minigame server");
							ItemStack item = gui.getGuiElement(slot).getItemStack();
							Identifier model = item.get(DataComponents.ITEM_MODEL);
							if (!what.model(false).equals(model)) helper.fail(what + " wears the model " + model + ", wanted " + what.model(false));
							if (!item.getHoverName().getString().contains("(not here)")) {
								helper.fail(what + " is not named \"… (not here)\": " + item.getHoverName().getString());
							}
							if (!lore(item).contains(StashConfig.LOOK_ONLY)) helper.fail(what + " does not carry the reason: " + lore(item));
							if (gui.getGuiElement(slot).getGuiCallback() != GuiElement.EMPTY_CALLBACK) {
								helper.fail(what + " is refused but still clickable");
							}
						}
						// And an action that is allowed wears the plain icon, with no reason on it.
						ItemStack allowed = gui.getGuiElement(46).getItemStack();
						if (!WardrobeAction.PUT_IN.model(true).equals(allowed.get(DataComponents.ITEM_MODEL))) {
							helper.fail("an allowed action does not wear its plain icon: " + allowed.get(DataComponents.ITEM_MODEL));
						}
						if (isEmpty(gui, 52)) helper.fail("no help book on a minigame server");
						GuiElement help = gui.getGuiElement(52);
						var itemLore = help.getItemStack().get(net.minecraft.core.component.DataComponents.LORE);
						boolean sawLookOnly = itemLore != null && itemLore.lines().stream()
								.anyMatch(line -> line.getString().toLowerCase(java.util.Locale.ROOT).contains("look"));
						if (!sawLookOnly) helper.fail("help book does not explain the server is look-only");
					} finally {
						OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(), stash));
					}
					release(server);
				}))
				.thenSucceed();
	}

	/**
	 * The screen says what it is: a tab per owned ovve, the one on show glinting and saying
	 * "(showing)" and the others saying a click switches to them; exactly one piece toggle, at the
	 * far end of the tab row, named for the half on show and the half a click brings up; and an
	 * empty stash and an unsewn half each saying what would be there.
	 */
	@GameTest(maxTicks = 1200)
	public void wardrobeTabsAndEmptyStatesExplainThemselves(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		Chapter shown = CHAPTER, other = Chapter.values()[1];
		for (Chapter chapter : List.of(shown, other)) {
			ItemStack ovve = new ItemStack(ModContent.ovve(chapter));
			OvveItem.setOwner(ovve, owner);
			player.getInventory().add(ovve);
		}
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> guarded(server, () -> {
					WardrobeGui gui = WardrobeGui.forTest(player, shown, Angle.FRONT);

					ItemStack active = gui.getGuiElement(0).getItemStack();
					if (active.getItem() != ModContent.ovve(shown)) helper.fail("the first tab is " + active.getItem() + ", wanted the shown chapter's ovve");
					if (!Boolean.TRUE.equals(active.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) helper.fail("the tab on show does not glint");
					if (!lore(active).contains("(showing)")) helper.fail("the tab on show does not say \"(showing)\": " + lore(active));
					ItemStack sleeping = gui.getGuiElement(1).getItemStack();
					if (Boolean.TRUE.equals(sleeping.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) helper.fail("a tab that is not on show glints");
					if (!lore(sleeping).toLowerCase(java.util.Locale.ROOT).contains("click to switch")) helper.fail("a tab does not say a click switches to it: " + lore(sleeping));

					// Nothing but tabs between them and the two rotation buttons at the far end.
					for (int col = 2; col < 9; col++) {
						boolean rotator = col == WardrobeGui.ROTATE_LEFT_COL || col == WardrobeGui.ROTATE_RIGHT_COL;
						if (!rotator && !isEmpty(gui, col)) helper.fail("something else is in the tab row at col " + col);
					}

					// An empty stash and an unsewn half each say so across the whole panel, in the glyph
					// layer: no item anywhere in either panel, and the notice in the title instead.
					for (int slot = 9; slot < 45; slot++) {
						if (!isEmpty(gui, slot)) helper.fail("slot " + slot + " has an item in it with an empty stash and nothing sewn");
					}
					String title = gui.getTitle().getString();
					if (title.indexOf(WardrobeFont.NO_PATCHES.codepoint()) < 0) helper.fail("no \"no patches yet\" notice in the title of an empty stash");
					if (title.indexOf(WardrobeFont.NOTHING_SEWN.codepoint()) < 0) helper.fail("no \"nothing sewn yet\" notice in the title of an unsewn half");
					if (!title.contains(WardrobeFont.at(WardrobeFont.NO_PATCHES))) helper.fail("the empty-stash notice is not placed over the collection panel");
					if (!title.contains(WardrobeFont.at(WardrobeFont.NOTHING_SEWN))) helper.fail("the unsewn notice is not placed over the preview panel");
					release(server);
				}))
				.thenSucceed();
	}

	/**
	 * The empty-state notices are art over their panel, and only when the panel is empty: a stash
	 * with a patch in it, or a half with a patch sewn on it, loses its notice.
	 */
	@GameTest
	public void wardrobeNoticesOnlyShowWhileThePanelIsEmpty(GameTestHelper helper) {
		char noPatches = WardrobeFont.NO_PATCHES.codepoint(), nothingSewn = WardrobeFont.NOTHING_SEWN.codepoint();
		String bare = WardrobeGui.title(CHAPTER, Wardrobe.NONE, Angle.FRONT).getString();
		if (bare.indexOf(noPatches) < 0 || bare.indexOf(nothingSewn) < 0) helper.fail("an empty wardrobe is missing a notice");

		String stashed = WardrobeGui.title(CHAPTER, Wardrobe.NONE.add(ITK_PATCH, 1), Angle.FRONT).getString();
		if (stashed.indexOf(noPatches) >= 0) helper.fail("the \"no patches yet\" notice is still drawn over a stash with a patch in it");
		if (stashed.indexOf(nothingSewn) < 0) helper.fail("the \"nothing sewn yet\" notice went missing while nothing is sewn");

		Wardrobe sewn = Wardrobe.NONE.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElseThrow();
		String worn = WardrobeGui.title(CHAPTER, sewn, Angle.FRONT).getString();
		if (worn.indexOf(nothingSewn) >= 0) helper.fail("the \"nothing sewn yet\" notice is still drawn over a sewn-on half");
		if (worn.indexOf(noPatches) < 0) helper.fail("the \"no patches yet\" notice went missing while the stash is empty");

		// Both notices are pixel text this font can actually draw, and neither is wider than its panel.
		for (WardrobeFont.Glyph notice : List.of(WardrobeFont.NO_PATCHES, WardrobeFont.NOTHING_SEWN)) {
			var art = notice.art().get();
			if (art.width != notice.width() || art.height != notice.height()) helper.fail(notice.name() + " art is " + art.width + "x" + art.height);
			if (notice.x() + notice.width() > WardrobeArt.WIDTH) helper.fail(notice.name() + " runs past the right edge of the screen");
		}
		helper.succeed();
	}

	/** Every lore line of a stack, joined — what a player reads when they hover it. */
	private static String lore(ItemStack stack) {
		var lore = stack.get(DataComponents.LORE);
		if (lore == null) return "";
		return String.join(" | ", lore.lines().stream().map(Component::getString).toList());
	}

	/**
	 * The pocket pages when a stash holds more kinds of patch than its twenty slots: every kind is
	 * on exactly one page, the two ends of the bottom row belong to the arrows from the moment
	 * there is a second page, and the counter says which page of how many.
	 *
	 * <p>The arithmetic is tested as arithmetic because it has to be: the catalogue has two patches
	 * in it, so no wardrobe this server can build has 45 kinds to page through. What a real
	 * wardrobe can show — one page, no arrows, no counter — is checked against the screen itself.
	 */
	@GameTest
	public void wardrobePocketPagesTheStash(GameTestHelper helper) {
		if (WardrobeGui.POCKET != 20) helper.fail("the pocket is " + WardrobeGui.POCKET + " slots; these numbers assume 20");
		// Twenty kinds still fit; the twenty-first costs two slots to the arrows, so pages hold 18.
		if (WardrobeGui.pageCount(0) != 1) helper.fail("an empty stash is " + WardrobeGui.pageCount(0) + " pages");
		if (WardrobeGui.pageCount(20) != 1) helper.fail("20 kinds are " + WardrobeGui.pageCount(20) + " pages, wanted 1");
		if (WardrobeGui.perPage(20) != 20) helper.fail("20 kinds on one page should use all 20 slots");
		if (WardrobeGui.pageCount(21) != 2) helper.fail("21 kinds are " + WardrobeGui.pageCount(21) + " pages, wanted 2");
		if (WardrobeGui.perPage(21) != 18) helper.fail("a paging pocket holds " + WardrobeGui.perPage(21) + " kinds, wanted 18");
		if (WardrobeGui.pageCount(45) != 3) helper.fail("45 kinds are " + WardrobeGui.pageCount(45) + " pages, wanted 3");
		if (WardrobeGui.pageCount(54) != 3) helper.fail("54 kinds are " + WardrobeGui.pageCount(54) + " pages, wanted 3");
		if (WardrobeGui.pageCount(55) != 4) helper.fail("55 kinds are " + WardrobeGui.pageCount(55) + " pages, wanted 4");

		// Every kind on exactly one page, in order, for every size worth trying.
		for (int kinds : new int[]{1, 19, 20, 21, 37, 45, 100}) {
			int pages = WardrobeGui.pageCount(kinds), per = WardrobeGui.perPage(kinds);
			List<Integer> seen = new ArrayList<>();
			for (int page = 0; page < pages; page++) {
				int slots = WardrobeGui.pocketSlots(pages > 1).size();
				if (slots != per) helper.fail(kinds + " kinds: a page has " + slots + " slots for " + per + " kinds");
				for (int i = 0; i + page * per < kinds && i < slots; i++) seen.add(page * per + i);
			}
			for (int i = 0; i < kinds; i++) {
				if (!seen.contains(i)) helper.fail(kinds + " kinds: kind " + i + " is on no page");
			}
			if (seen.size() != kinds) helper.fail(kinds + " kinds: " + seen.size() + " slots filled, so a kind is on two pages");
			if (seen.stream().distinct().count() != kinds) helper.fail(kinds + " kinds: a kind appears twice");
		}

		// The arrows take the ends of the bottom row, and only while there is more than one page.
		if (WardrobeGui.pocketSlots(false).contains(WardrobeGui.PAGE_NEXT) != true) helper.fail("one page: the bottom-right slot should still hold a patch");
		if (WardrobeGui.pocketSlots(true).contains(WardrobeGui.PAGE_NEXT)) helper.fail("paging: the bottom-right slot is the next-page arrow, not a patch");
		if (WardrobeGui.pocketSlots(true).contains(WardrobeGui.PAGE_PREVIOUS)) helper.fail("paging: the bottom-left slot is the previous-page arrow, not a patch");

		// The counter: nothing on one page, "page N/M" beyond it.
		if (!WardrobeFont.pages(0, 1).getString().isEmpty()) helper.fail("a single page still draws a counter");
		String counter = WardrobeFont.pages(1, 3).getString();
		if (counter.indexOf(WardrobeFont.PAGE_LABEL.codepoint()) < 0) helper.fail("the counter has no \"page\" in it");
		if (counter.indexOf(WardrobeFont.PAGE_OF.codepoint()) < 0) helper.fail("the counter has no \"/\" in it");
		helper.succeed();
	}

	/** A stash of the two kinds there are fits one page: no arrows, no counter, both kinds on show. */
	@GameTest(maxTicks = 1200)
	public void wardrobePocketShowsOnePageWithoutArrows(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 3).add(NYCKELN_PATCH, 1), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "setup outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					WardrobeGui gui = WardrobeGui.forTest(player, CHAPTER, Angle.FRONT);
					int filled = 0;
					for (int slot : WardrobeGui.pocketSlots(false)) if (!isEmpty(gui, slot)) filled++;
					if (filled != 2) helper.fail(filled + " pocket slots filled, wanted one per kind (2)");
					if (!isEmpty(gui, WardrobeGui.PAGE_NEXT) && gui.getGuiElement(WardrobeGui.PAGE_NEXT).getItemStack()
							.get(DataComponents.ITEM_MODEL) == WardrobeAction.PAGE_NEXT.model(true)) {
						helper.fail("a next-page arrow on a stash that fits one page");
					}
					if (gui.getTitle().getString().indexOf(WardrobeFont.PAGE_LABEL.codepoint()) >= 0) {
						helper.fail("a page counter on a stash that fits one page");
					}
					// Sorted by name, so a kind keeps its slot however the counts change.
					String first = gui.getGuiElement(WardrobeGui.pocketSlots(false).get(0)).getItemStack().getHoverName().getString();
					if (!first.equals("ITK")) helper.fail("the first slot holds " + first + ", wanted the first kind by name");
					release(server);
				}))
				.thenSucceed();
	}

	/** The tab row only ever lists chapters the player owns an ovve of. */
	@GameTest
	public void foreignOvveHasNoWardrobeTab(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Chapter owned = CHAPTER, foreign = Chapter.values()[1];
		ItemStack mine = new ItemStack(ModContent.ovve(owned));
		OvveItem.setOwner(mine, player.getUUID());
		player.getInventory().add(mine);
		ItemStack theirs = new ItemStack(ModContent.ovve(foreign));
		OvveItem.setOwner(theirs, UUID.randomUUID());
		player.getInventory().add(theirs);
		List<Chapter> tabs = WardrobeGui.ownedChapters(player);
		if (!tabs.contains(owned)) helper.fail("the player's own chapter is missing from the tabs: " + tabs);
		if (tabs.contains(foreign)) helper.fail("a foreign ovve's chapter is shown as a tab: " + tabs);
		helper.succeed();
	}

	/**
	 * Every chapter gets a background PNG and a font entry; the real checked-in template (and so
	 * every tint) is {@code WardrobeArt.WIDTH x WardrobeArt.HEIGHT} (176x126: exactly the
	 * GENERIC_9x6 container's own rows, nothing more); the font's space provider is exactly the
	 * better-pets-style {@code {a: -8, c: -169}} advances.
	 */
	@GameTest
	public void wardrobeArtGeneratesFontAndBackgroundPerChapter(GameTestHelper helper) {
		JsonObject font = WardrobeArt.fontJson();
		JsonObject space = font.getAsJsonArray("providers").get(0).getAsJsonObject();
		if (!space.get("type").getAsString().equals("space")) helper.fail("the first provider is not the space provider: " + space);
		JsonObject advances = space.getAsJsonObject("advances");
		if (advances.get("a").getAsInt() != -8 || advances.get("c").getAsInt() != -169) {
			helper.fail("space advances are not {a: -8, c: -169}: " + advances);
		}
		// And the bit set of +/-1 ... +/-128 that puts everything else where it goes, each advance
		// cancelling out against its opposite so a glyph run leaves the cursor where it found it.
		WardrobeFont.spaceAdvances().forEach((c, advance) -> {
			if (!advances.has(String.valueOf(c))) helper.fail("the font is missing the space for " + advance);
			else if (advances.get(String.valueOf(c)).getAsInt() != advance) helper.fail("the space for " + advance + " advances " + advances.get(String.valueOf(c)));
		});
		for (int move : new int[]{1, 7, 93, -158, 255}) {
			int sum = 0;
			for (char c : WardrobeFont.move(move).toCharArray()) sum += advances.get(String.valueOf(c)).getAsInt();
			if (sum != move) helper.fail("a move of " + move + " px advances " + sum);
		}
		List<String> backgroundFiles = new ArrayList<>();
		for (Chapter tab : Chapter.values()) backgroundFiles.add("ovvar:wardrobe/" + tab.id + ".png");
		List<String> staticFiles = new ArrayList<>();
		for (WardrobeFont.Glyph glyph : WardrobeFont.glyphs()) staticFiles.add(glyph.textureRef());
		int backgrounds = 0, furniture = 0;
		for (JsonElement provider : font.getAsJsonArray("providers")) {
			JsonObject o = provider.getAsJsonObject();
			if (!o.get("type").getAsString().equals("bitmap")) continue;
			String file = o.get("file").getAsString();
			if (backgroundFiles.contains(file)) {
				backgrounds++;
				if (o.get("height").getAsInt() != WardrobeArt.HEIGHT) helper.fail("bitmap provider height is not " + WardrobeArt.HEIGHT + ": " + o);
			} else if (staticFiles.contains(file)) {
				furniture++;
			} else {
				helper.fail("a bitmap provider nothing claims: " + o);
			}
		}
		if (backgrounds != Chapter.values().length) helper.fail("expected one background per chapter (" + Chapter.values().length + "), font has " + backgrounds);
		if (furniture != WardrobeFont.glyphs().size()) helper.fail("the font lists " + furniture + " of its " + WardrobeFont.glyphs().size() + " glyphs");
		if (WardrobeFont.glyphs().size() < WardrobePreview.glyphCount()) helper.fail("the previews are not all registered with the font");
		// Every static glyph's art is the size the font promises for it, and the ascent puts its top
		// where the layout says (a cell-sized highlight on the tab row's own frame ring, and so on).
		for (WardrobeFont.Glyph glyph : WardrobeFont.glyphs()) {
			if (WardrobeFont.ascent(glyph.top()) != WardrobeFont.TITLE_Y + 7 - glyph.top()) helper.fail("the ascent formula moved under " + glyph.name());
		}

		var template = WardrobeArt.readTemplate();
		if (template.getWidth() != WardrobeArt.WIDTH || template.getHeight() != WardrobeArt.HEIGHT) {
			helper.fail("the real template is " + template.getWidth() + "x" + template.getHeight() + ", wanted " + WardrobeArt.WIDTH + "x" + WardrobeArt.HEIGHT);
		}
		for (Chapter tab : Chapter.values()) {
			var tinted = WardrobeArt.tint(template, WardrobeArt.colour(tab));
			if (tinted.getWidth() != WardrobeArt.WIDTH || tinted.getHeight() != WardrobeArt.HEIGHT) {
				helper.fail(tab + " background is " + tinted.getWidth() + "x" + tinted.getHeight() + ", wanted " + WardrobeArt.WIDTH + "x" + WardrobeArt.HEIGHT);
			}
		}
		helper.succeed();
	}

	/**
	 * <b>The rule that takes the whole font down with it.</b> A bitmap provider whose ascent is
	 * greater than its height is refused by the client, and a font with one bad provider in it is
	 * dropped entirely — every glyph of it — so the wardrobe falls back to a plain chest with a
	 * title of missing-glyph boxes. That is what shipped in v3: the stats readout is 5 px tall at
	 * the top of the header, where the ascent is 6.
	 *
	 * <p>So: every provider of the generated font, against every rule the client applies to it —
	 * ascent no greater than height, a texture that the same pack build actually wrote, of the
	 * height the provider claims, and one codepoint per glyph and no other.
	 */
	@GameTest
	public void wardrobeFontIsOneTheClientWillLoad(GameTestHelper helper) {
		JsonObject font = WardrobeArt.fontJson();
		Map<String, int[]> packed = WardrobeArt.packFiles();
		if (packed.isEmpty()) helper.fail("the resource pack was never built, so there is nothing to check the font against");
		Map<String, String> byChar = new java.util.LinkedHashMap<>();
		int providers = 0;
		for (JsonElement provider : font.getAsJsonArray("providers")) {
			JsonObject o = provider.getAsJsonObject();
			if (!o.get("type").getAsString().equals("bitmap")) continue;
			providers++;
			String file = o.get("file").getAsString();
			int ascent = o.get("ascent").getAsInt(), height = o.get("height").getAsInt();
			if (ascent > height) helper.fail("ascent " + ascent + " is higher than height " + height + " for " + file + ": the client refuses the whole font");
			int[] size = packed.get(WardrobeArt.texturePath(file));
			if (size == null) helper.fail("the font points at " + file + ", which this pack build never wrote");
			else if (size[1] != height) helper.fail(file + " is " + size[1] + " px tall, the provider says " + height);
			for (JsonElement row : o.getAsJsonArray("chars")) {
				String chars = row.getAsString();
				if (chars.length() != 1) helper.fail(file + " claims " + chars.length() + " codepoints in a row; one glyph, one codepoint");
				String already = byChar.put(chars, file);
				if (already != null) helper.fail("U+" + Integer.toHexString(chars.charAt(0)) + " is claimed by both " + already + " and " + file);
			}
		}
		if (providers != Chapter.values().length + WardrobeFont.glyphs().size()) {
			helper.fail(providers + " bitmap providers, wanted " + (Chapter.values().length + WardrobeFont.glyphs().size()));
		}
		// And no glyph's codepoint collides with the space provider's, which would draw a picture
		// where a space should be (or the other way round).
		for (char space : WardrobeFont.spaceAdvances().keySet()) {
			if (byChar.containsKey(String.valueOf(space))) helper.fail("U+" + Integer.toHexString(space) + " is both a space and " + byChar.get(String.valueOf(space)));
		}
		// The same rule at the source, so a new glyph cannot be added in a place that breaks it.
		for (WardrobeFont.Glyph glyph : WardrobeFont.glyphs()) {
			if (WardrobeFont.ascent(glyph.top()) > glyph.height()) {
				helper.fail(glyph.name() + " wants ascent " + WardrobeFont.ascent(glyph.top()) + " with height " + glyph.height());
			}
			// Padding a glyph to satisfy that must not have moved its art: the top row is still the
			// first row of the art, which is what every position on this screen is measured from.
			var art = glyph.art().get();
			if (art.height != glyph.height()) helper.fail(glyph.name() + " art is " + art.height + " px tall, the font says " + glyph.height());
		}
		helper.succeed();
	}

	/**
	 * "Bad overlap of textures": the background's drawn boxes must lie on the slot frames' own ring
	 * and never inside the 16×16 an item's icon fills, or every tab icon has a stitch line through
	 * it. Checked against the real checked-in template, every slot of the grid; and the corners of
	 * the boxes the template does draw are where the grid says (a box one px off would pass the
	 * overlap check by sitting in the gutter, so both are asserted).
	 */
	@GameTest
	public void wardrobeTemplateBoxesAreOnTheSlotGrid(GameTestHelper helper) {
		var template = WardrobeArt.readTemplate();
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				int x0 = WardrobeFont.ITEM_X + WardrobeFont.PITCH * col, y0 = WardrobeFont.ITEM_Y + WardrobeFont.PITCH * row;
				for (int y = y0; y < y0 + WardrobeFont.ICON; y++) {
					for (int x = x0; x < x0 + WardrobeFont.ICON; x++) {
						if ((template.getRGB(x, y) & 0xFF) >= 250) {
							helper.fail("stitching at (" + x + "," + y + ") is inside the icon of slot (" + row + "," + col + ")");
						}
					}
				}
			}
		}
		// The tab row and the action row are drawn as one box per cell: their frame rings carry it.
		for (int col = 0; col < 9; col++) {
			for (int row : new int[]{0, 5}) {
				int x = WardrobeFont.cellX(col), y = WardrobeFont.cellY(row);
				if ((template.getRGB(x, y) & 0xFF) < 250) helper.fail("no box corner at the cell ring of slot (" + row + "," + col + "), (" + x + "," + y + ")");
			}
		}
		helper.succeed();
	}

	/**
	 * The title's own text is the chapter's name and nothing else, which fits the screen at the
	 * vanilla font's widest; the counts are pixel glyphs right-aligned in the spare header width,
	 * as many of them as fit beside that name, and the help item's lore has them all whatever
	 * happens. ("Text going over the limit": v2 put all of it in the title's text.)
	 */
	@GameTest
	public void wardrobeTitleTextFitsAndTheStatsAreGlyphs(GameTestHelper helper) {
		for (Chapter chapter : Chapter.values()) {
			String text = WardrobeGui.titleText(chapter);
			if (text.length() > 28) helper.fail(chapter + "'s title text is " + text.length() + " characters: \"" + text + "\"");
			if (WardrobeFont.TITLE_X + text.length() * WardrobeFont.TITLE_CHAR > WardrobeArt.WIDTH) {
				helper.fail(chapter + "'s title text can run off the right of the screen");
			}
			// Nothing of the counts is left in the text.
			for (String word : List.of("earned", "sewn", "stash")) {
				if (text.contains(word)) helper.fail(chapter + "'s title text still spells out the counts: \"" + text + "\"");
			}
		}

		Wardrobe wardrobe = Wardrobe.NONE.add(ITK_PATCH, 3).sew(CHAPTER, ITK).orElseThrow();
		String title = WardrobeGui.title(CHAPTER, wardrobe, Angle.FRONT).getString();
		for (WardrobeFont.Glyph label : List.of(WardrobeFont.EARNED, WardrobeFont.SEWN, WardrobeFont.STASH)) {
			if (title.indexOf(label.codepoint()) < 0) helper.fail("the title does not carry the " + label.name() + " readout");
		}
		// The digits of each count are there, and the row stays inside the header's right margin.
		if (title.indexOf(WardrobeFont.digit(3).codepoint()) < 0) helper.fail("the title does not carry the count 3 (earned) as a digit glyph");
		List<WardrobeFont.Glyph> row = WardrobeFont.statsRow(3, 1, 2, WardrobeGui.titleText(CHAPTER).length());
		if (row.isEmpty()) helper.fail("no room for any of the counts beside " + CHAPTER + "'s name");
		if (WardrobeFont.statsX(row, 0) < WardrobeFont.TITLE_X + WardrobeGui.titleText(CHAPTER).length() * WardrobeFont.TITLE_CHAR) {
			helper.fail("the stats readout starts on top of the title's own text");
		}
		int end = WardrobeFont.statsX(row, row.size() - 1) + row.get(row.size() - 1).width();
		if (end > WardrobeFont.HEADER_RIGHT) helper.fail("the stats readout ends at " + end + ", past the header's margin");

		// The longest chapter name there is still leaves room for at least one count.
		if (WardrobeFont.statsRow(1, 1, 1, WardrobeGui.titleChars()).isEmpty()) {
			helper.fail("the longest chapter name (" + WardrobeGui.titleChars() + " chars) leaves no room for any count");
		}
		helper.succeed();
	}

	/** The tab being shown gets the highlight glyph, placed at its own column; no tab, no highlight. */
	@GameTest
	public void wardrobeTitleHighlightsTheTabOnShow(GameTestHelper helper) {
		char highlight = WardrobeFont.ACTIVE_TAB.codepoint();
		for (int col = 0; col < WardrobeGui.TAB_COLS; col++) {
			String title = WardrobeGui.title(CHAPTER, Wardrobe.NONE, Angle.FRONT, col, true, 0).getString();
			if (title.indexOf(highlight) < 0) helper.fail("no active-tab highlight in the title for tab " + col);
			String at = WardrobeFont.at(WardrobeFont.ACTIVE_TAB, WardrobeFont.cellX(col));
			if (!title.contains(at)) helper.fail("the highlight for tab " + col + " is not placed at x " + WardrobeFont.cellX(col));
		}
		if (WardrobeGui.title(CHAPTER, Wardrobe.NONE, Angle.FRONT, -1, true, 0).getString().indexOf(highlight) >= 0) {
			helper.fail("a player on no tab of their own still gets a highlight");
		}
		helper.succeed();
	}

	// ---- the preview: a rendered picture of the player's own ovve

	/**
	 * Four sides, and every cell is seen from exactly one of them: the doll is built up out of a
	 * bare-ovve glyph per (chapter, angle) plus one glyph per (patch, cell), so the pack holds a
	 * fixed number of glyphs however much anybody sews. The count logged is the formula.
	 */
	@GameTest
	public void wardrobePreviewGlyphsAreFixedAndCoverEveryVisibleCell(GameTestHelper helper) {
		int bare = WardrobePreview.bareGlyphCount();
		if (bare != Chapter.values().length * Angle.values().length) {
			helper.fail("bare ovve glyphs: " + bare + ", wanted " + Chapter.values().length + " x " + Angle.values().length);
		}
		// One glyph per patch that fits a cell the doll can show, per angle that draws it, and none
		// for a cell it cannot.
		int wanted = 0, hidden = 0;
		for (Spot spot : Spot.values()) {
			List<Angle> angles = WardrobePreview.anglesOf(spot);
			if (angles.isEmpty()) {
				hidden++;
				continue;
			}
			// A cell on a box's SIDE face is on one face of one box, and a face is seen from one side
			// only. A cell on its TOP face is seen from none of the four, so the front and the back
			// both draw it foreshortened — two angles, and angleOf names the front one.
			int sides = spot.top() ? 2 : 1;
			if (angles.size() != sides) helper.fail(spot + " is drawn from " + angles + ", wanted " + sides + " angle(s)");
			if (WardrobePreview.angleOf(spot) != angles.getFirst()) helper.fail(spot + ": angleOf says " + WardrobePreview.angleOf(spot) + ", anglesOf starts " + angles);
			if (spot.top() && !angles.equals(List.of(Angle.FRONT, Angle.BACK))) {
				helper.fail(spot + " is a top-face cell drawn from " + angles + ", wanted the front and the back views");
			}
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				Placement placement = new Placement(spot, patch);
				for (Angle angle : angles) {
					wanted++;
					if (WardrobePreview.patchGlyph(placement, angle) == null) helper.fail("no glyph for " + placement.key() + " on the " + angle + " view");
				}
			}
		}
		if (WardrobePreview.patchGlyphCount() != wanted) {
			helper.fail("patch glyphs: " + WardrobePreview.patchGlyphCount() + ", wanted " + wanted);
		}
		// The inner faces are the only cells the doll never shows, and there are none of those.
		if (hidden != 0) helper.fail(hidden + " cell(s) are on no view at all");
		helper.succeed();
	}

	/**
	 * <b>Every</b> cell's glyph draws the patch's own art, and the right way round — not the cloth
	 * beside it, which is what "text missing on back, it shows on front" would be.
	 *
	 * <p>The art cannot be compared pixel for pixel: the doll resamples every face ×1.5 and shades
	 * it, and the patch layer gives up the pixel the figure's outline owns. What survives all three
	 * is <em>chromaticity</em> — shading multiplies the channels, so their ratios are kept — and the
	 * <em>order</em> of the colours across a row, which resampling stretches but never reorders. So:
	 * every colour of the art is in the glyph, the glyph has no colour the art does not (it is the
	 * patch layer alone, and cloth would show up here), and the colours run left to right in the
	 * art's own order, reversed exactly where the model mirrors that face.
	 *
	 * <p>"The art", for a patch bigger than its cell, is the part of it that lands on the cell's own
	 * face: datagen wraps what hangs over the cell round the box, so those columns belong to the face
	 * next door and this cell's glyph is right not to draw them.
	 * {@link WardrobePreview#shownArt} is that window — and the seat's two legs in the order the view
	 * puts them.
	 */
	@GameTest
	public void wardrobePreviewDrawsEveryCellsOwnPatchArt(GameTestHelper helper) {
		for (Patches.Patch patch : Patches.all()) {
			List<Integer> artColours = colours(patchArt(patch));
			if (artColours.size() < 2) helper.fail(patch.id() + "'s art is one flat colour; this test cannot tell it from cloth");
			for (Spot spot : Spot.values()) {
				if (!patch.fits(spot)) continue;
				// The PNG this cell shows, which need not be the catalogue's own size.
				Patches.Art chosen = Patches.artFor(patch, spot);
				Tex art = patchArt(chosen);
				List<Angle> angles = WardrobePreview.anglesOf(spot);
				if (angles.isEmpty()) helper.fail(spot + " is on no view, so " + patch.id() + " sewn there could never be seen");
				for (Angle angle : angles) {
				WardrobeFont.Glyph glyph = WardrobePreview.patchGlyph(new Placement(spot, patch), angle);
				if (glyph == null) {
					helper.fail("no glyph for " + patch.id() + " on " + spot.id());
					continue;
				}
				Tex drawn = glyph.art().get();
				List<Integer> got = colours(drawn);
				String where = patch.id() + " on " + spot.id() + " (" + angle + " view)";
				// The art this cell can show: an oversize patch's hang-over is drawn on the face next door.
				Tex shown = WardrobePreview.shownArt(spot, chosen, art, angle);
				List<Integer> shownColours = colours(shown);
				if (shownColours.isEmpty()) {
					helper.fail(where + ": no part of the art lands on the cell's own face");
					continue;
				}
				for (int colour : shownColours) {
					if (!near(got, colour)) helper.fail(where + ": the art's colour " + hex(colour) + " is not in the glyph, which has " + hex(got));
				}
				for (int colour : got) {
					if (!near(shownColours, colour)) helper.fail(where + ": the glyph has " + hex(colour) + ", which the art does not — cloth, or the wrong crop");
				}
				// And the way round: a row of the art, collapsed to the order its colours run in, must
				// read the same way across the glyph — on every cell, left or right. A left cell is
				// mirrored twice and so not at all: datagen mirrors its art because the armour model
				// mirrors that limb, and the doll mirrors the limb for the same reason, which puts the
				// art back the way it was drawn. A patch reads correctly on both sleeves, and that is
				// the whole point of datagen pre-mirroring it.
				List<Integer> want = run(shown, middleRow(shown), BAND);
				if (!readsAs(drawn, want)) {
					helper.fail(where + ": the art reads " + hex(want) + " across, the glyph reads " + hex(run(drawn, middleRow(drawn), BAND)));
				}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * The seat's halves sit on the leg the art was drawn for — pinned to the <b>art</b>, not to
	 * {@link Spot#seatHalf}, which is the cut itself and would agree with a swap of its own making.
	 *
	 * <p>Seat art is drawn as seen from behind, where the wearer's left leg is the one at the
	 * viewer's left, so the art's left half belongs on the wearer's left leg. The model draws that
	 * leg as a mirror image off the right leg's strips, so the {@code _l} texture must hold the
	 * art's left half flipped in x and {@code _r} the right half as it is. Both are checked whole
	 * and then again at one distinctive pixel — a pixel of the art where the two halves disagree,
	 * which is exactly the pixel a swap would move to the other leg.
	 *
	 * <p>The instant path cannot be drawn without a client, so what is pinned here is its constant:
	 * {@code OVVAR_SEAT_COLUMN_RIGHT} in {@code ovvar.glsl} must be {@link Spot#seatColumn}'s RIGHT
	 * value, which is the whole of what the shader's seat branch decides.
	 */
	@GameTest
	public void seatHalvesSitOnTheLegTheArtWasDrawnFor(GameTestHelper helper) {
		int seats = 0;
		for (Patches.Patch patch : Patches.all()) {
			if (!patch.seat()) continue;
			seats++;
			Tex art = patchArt(Patches.artFor(patch, Spot.SEAT));
			int half = art.width / 2;
			Tex artLeft = art.crop(0, 0, half, art.height), artRight = art.crop(half, 0, half, art.height);
			int[] tell = firstDifference(artLeft, artRight);
			if (tell == null) {
				helper.fail(patch.id() + "'s two halves are identical, so this test could not tell a swap from a correct cut");
				continue;
			}
			// What the pack really draws on each leg: the cell out of the generated placement texture.
			Tex onRight = seatCell(patch, Spot.Side.RIGHT), onLeft = seatCell(patch, Spot.Side.LEFT);
			if (!same(onRight, artRight)) {
				helper.fail(patch.id() + ": the wearer's right leg (_r) is not the art's right half — the seat halves are swapped");
			}
			if (!same(onLeft, artLeft.flipX())) {
				helper.fail(patch.id() + ": the wearer's left leg (_l) is not the art's left half mirrored — the seat halves are swapped");
			}
			// The distinctive pixel, said plainly: on the wearer's left leg, at the column the model's
			// mirroring puts it in, the art's LEFT half is what shows.
			int wanted = artLeft.get(tell[0], tell[1]), got = onLeft.get(half - 1 - tell[0], tell[1]);
			if (got != wanted) {
				helper.fail(patch.id() + ": at art pixel (" + tell[0] + ", " + tell[1] + ") the wearer's left leg shows " + Integer.toHexString(got)
						+ ", but the art's left half has " + Integer.toHexString(wanted) + " there (and its right half " + Integer.toHexString(artRight.get(tell[0], tell[1])) + ")");
			}
		}
		if (seats == 0) helper.fail("no seat patch in the catalogue to check");
		// And the instant path's half of the same convention.
		String glsl = resource("/assets/ovvar/shaders/include/ovvar.glsl");
		var matcher = java.util.regex.Pattern.compile("OVVAR_SEAT_COLUMN_RIGHT\\s*=\\s*([0-9.]+)").matcher(glsl);
		if (!matcher.find()) {
			helper.fail("ovvar.glsl no longer declares OVVAR_SEAT_COLUMN_RIGHT, so nothing pins the instant path's seat halves");
		} else {
			int shader = (int) Double.parseDouble(matcher.group(1));
			if (shader != Spot.seatColumn(Spot.Side.RIGHT)) {
				helper.fail("ovvar.glsl draws the art's column " + shader + " on the unmirrored leg, Spot.seatColumn says " + Spot.seatColumn(Spot.Side.RIGHT));
			}
		}
		helper.succeed();
	}

	/**
	 * A seat patch taller than the seat's own row keeps every row of it. The art is centred on the
	 * two cells the way oversize plain art is centred on its cell, so the extra rows hang onto the
	 * cloth below (and above) — and the rails at the very top and bottom of the art are exactly what
	 * a path that clipped the art to the cell would lose, on both legs. Also that the paper doll
	 * keeps them: they must be in the reference art the audit compares the glyph against, and their
	 * colours in the glyph the doll really draws.
	 */
	@GameTest
	public void aTallSeatPatchKeepsTheRowsThatHangOverTheSeat(GameTestHelper helper) {
		int tall = 0;
		for (Patches.Patch patch : Patches.all()) {
			if (!patch.seat() || patch.height() <= Spot.PX) continue;
			tall++;
			Patches.Art chosen = Patches.artFor(patch, Spot.SEAT);
			Tex art = patchArt(chosen);
			int half = art.width / 2, last = art.height - 1;
			if (chosen.offsetY(Spot.SEAT) >= 0) {
				helper.fail(patch.id() + " is " + patch.height() + " px tall but sits inside the seat's row; nothing hangs over");
			}
			for (Spot.Side side : new Spot.Side[]{Spot.Side.RIGHT, Spot.Side.LEFT}) {
				Tex leg = seatCell(patch, side);
				if (leg.height != patch.height()) {
					helper.fail(patch.id() + " on the " + side + " leg is " + leg.height + " px tall, wanted " + patch.height());
					continue;
				}
				// The half this leg wears, as drawn (the texture holds the left leg's mirrored).
				Tex wanted = side == Spot.Side.LEFT ? art.crop(0, 0, half, art.height).flipX() : art.crop(half, 0, half, art.height);
				for (int row : new int[]{0, last}) {
					boolean any = false;
					for (int x = 0; x < half; x++) {
						int pixel = wanted.get(x, row);
						if (Tex.a(pixel) == 0) continue;
						any = true;
						if (leg.get(x, row) != pixel) {
							helper.fail(patch.id() + ": the " + side + " leg is missing art pixel (" + x + ", " + row + "), the "
									+ (row == 0 ? "top" : "bottom") + " rail — the art was clipped to the seat's own row");
						}
					}
					if (!any) helper.fail(patch.id() + "'s row " + row + " is empty, so this test could not tell whether it was drawn");
				}
			}
			// And on the paper doll.
			Tex shown = WardrobePreview.shownArt(Spot.SEAT, chosen, art);
			if (shown.height != art.height) helper.fail(patch.id() + ": the doll's reference art is " + shown.height + " px tall, wanted " + art.height);
			WardrobeFont.Glyph glyph = WardrobePreview.patchGlyph(new Placement(Spot.SEAT, patch));
			if (glyph == null) {
				helper.fail("no preview glyph for " + patch.id() + " on the seat");
				continue;
			}
			List<Integer> got = colours(glyph.art().get());
			for (int row : new int[]{0, shown.height - 1}) {
				List<Integer> rails = run(shown, row);
				if (rails.isEmpty()) helper.fail(patch.id() + ": the doll drops the whole of row " + row + ", which hangs over the seat");
				for (int colour : rails) {
					if (!near(got, colour)) {
						helper.fail(patch.id() + ": row " + row + " of the art has " + hex(colour) + ", which the seat's glyph has not — the doll clipped it to the cell");
					}
				}
			}
		}
		if (tall == 0) helper.fail("no seat patch taller than the seat's own row to check");
		helper.succeed();
	}

	/**
	 * One leg's half of a seat patch, out of the generated placement texture the client draws it
	 * with: the art's own rectangle, which for a seat patch taller than the seat's row starts above
	 * the cell and ends below it.
	 */
	private static Tex seatCell(Patches.Patch patch, Spot.Side side) {
		String name = "patch/seat/" + patch.id() + (side == Spot.Side.LEFT ? "_l" : "_r");
		Patches.Art art = Patches.artFor(patch, Spot.SEAT);
		int x = Spot.SEAT.u * Spot.DETAIL, y = Spot.SEAT.v * Spot.DETAIL + art.offsetY(Spot.SEAT);
		return generated(Piece.BOTTOM, name).crop(x, y, Spot.PX, art.height());
	}

	/** A generated equipment layer texture, off the runtime classpath (datagen has to have run). */
	private static Tex generated(Piece piece, String name) {
		String path = "/assets/" + metacraft.ovvar.Ovvar.MOD_ID + "/textures/entity/equipment/" + piece.layer + "/" + name + ".png";
		try (var in = WardrobeTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path + " — run ./gradlew runDatagen");
			return Tex.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String resource(String path) {
		try (var in = WardrobeTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path);
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean same(Tex a, Tex b) {
		if (a.width != b.width || a.height != b.height) return false;
		for (int y = 0; y < a.height; y++) for (int x = 0; x < a.width; x++) if (a.get(x, y) != b.get(x, y)) return false;
		return true;
	}

	/** The first pixel, reading rows, where two same-sized images disagree; null if they never do. */
	private static int @org.jspecify.annotations.Nullable [] firstDifference(Tex a, Tex b) {
		for (int y = 0; y < a.height; y++) for (int x = 0; x < a.width; x++) if (a.get(x, y) != b.get(x, y)) return new int[]{x, y};
		return null;
	}

	/** A patch's catalogue art: {@code art/ovvar/patches/<id>.png}, the size the catalogue declares. */
	private static Tex patchArt(Patches.Patch patch) {
		return patchArt(patch.art());
	}

	/**
	 * One of a patch's arts — which one a place shows is {@link Patches#artFor}'s to say, and where
	 * its pixels come from (a PNG, or a 16 px one scaled down) is {@link Tex#art(Patches.Art)}'s.
	 */
	private static Tex patchArt(Patches.Art art) {
		return Tex.art(art);
	}

	/**
	 * Is {@code colour} one of {@code colours}, to within a level of quantisation per channel?
	 * Shading multiplies the channels and rounds, which can carry a ratio over a bucket boundary —
	 * a sleeve, shaded twice over, does exactly that — so the comparison allows one level. Cloth is
	 * nowhere near a patch's colours at this tolerance; the chapter's cerise against the ITK
	 * patch's orange is four levels of green apart.
	 */
	private static boolean near(List<Integer> colours, int colour) {
		return near(colours, colour, LEVEL);
	}

	/** Levels of quantisation the shaded glyph may have moved a colour by; {@link #near}'s tolerance. */
	private static final int LEVEL = 1;

	/**
	 * And the tolerance a row's <em>bands</em> are read at ({@link #run}), which has to be twice
	 * that: two art colours a level apart are one band already, and the shading can carry either of
	 * them another level — towards the other, and then a boundary the art still holds is one the
	 * doll cannot be asked to hold. IT's 8×8 on a shoulder is exactly that, three neighbouring
	 * violets of which the middle one rounds onto its neighbour's bucket on the doll.
	 */
	private static final int BAND = 2 * LEVEL;

	private static boolean near(List<Integer> colours, int colour, int levels) {
		for (int other : colours) {
			int dr = Math.abs((other >> 8 & 0xF) - (colour >> 8 & 0xF));
			int dg = Math.abs((other >> 4 & 0xF) - (colour >> 4 & 0xF));
			int db = Math.abs((other & 0xF) - (colour & 0xF));
			if (dr <= levels && dg <= levels && db <= levels) return true;
		}
		return false;
	}

	/**
	 * A colour with its brightness divided out, quantised: what a pixel still has in common with
	 * itself after the doll has shaded it (shading multiplies every channel by the same amount).
	 */
	private static int chroma(int argb) {
		int r = Tex.r(argb), g = Tex.g(argb), b = Tex.b(argb);
		int max = Math.max(r, Math.max(g, b));
		if (max == 0) return 0;
		return (r * 15 / max) << 8 | (g * 15 / max) << 4 | b * 15 / max;
	}

	/** Every chromaticity the visible pixels of {@code tex} use, in first-seen order. */
	private static List<Integer> colours(Tex tex) {
		List<Integer> out = new ArrayList<>();
		for (int y = 0; y < tex.height; y++) {
			for (int x = 0; x < tex.width; x++) {
				if (Tex.a(tex.get(x, y)) == 0) continue;
				int chroma = chroma(tex.get(x, y));
				if (!out.contains(chroma)) out.add(chroma);
			}
		}
		return out;
	}

	/** The row nearest the middle with anything on it — a tall patch loses its top and bottom rows to the box. */
	private static int middleRow(Tex tex) {
		for (int away = 0; away <= tex.height; away++) {
			for (int y : new int[]{tex.height / 2 + away, tex.height / 2 - away}) {
				if (y >= 0 && y < tex.height && !run(tex, y).isEmpty()) return y;
			}
		}
		return tex.height / 2;
	}

	/**
	 * One row's colours left to right, each run of the same one collapsed to a single entry —
	 * "the same" to the tolerance the comparison itself uses ({@link #near}), because two colours a
	 * single level of quantisation apart are two the shaded glyph cannot be asked to tell apart: the
	 * run has to be read at the resolution it is compared at, or a row of art holding both would
	 * read as one entry longer than the same row on the doll.
	 */
	private static List<Integer> run(Tex tex, int y) {
		return run(tex, y, LEVEL);
	}

	/** The same, collapsing at a tolerance of the caller's choosing ({@link #LEVEL} or {@link #BAND}). */
	private static List<Integer> run(Tex tex, int y, int levels) {
		List<Integer> out = new ArrayList<>();
		for (int x = 0; x < tex.width; x++) {
			if (Tex.a(tex.get(x, y)) == 0) continue;
			int chroma = chroma(tex.get(x, y));
			if (out.isEmpty() || !near(List.of(out.get(out.size() - 1)), chroma, levels)) out.add(chroma);
		}
		return out;
	}

	/**
	 * Does any row of {@code drawn} read as {@code want}? Allowing an end to be missing: the pixel
	 * the figure's own outline owns is taken off the patch layer, which can cost the first or last
	 * colour of a row on a cell at the edge of its face.
	 */
	private static boolean readsAs(Tex drawn, List<Integer> want) {
		for (int y = 0; y < drawn.height; y++) {
			List<Integer> got = run(drawn, y, BAND);
			if (got.isEmpty()) continue;
			for (int from = 0; from <= 1; from++) {
				for (int to = want.size(); to >= want.size() - 1; to--) {
					if (from <= to && sameRun(got, want.subList(from, to))) return true;
				}
			}
		}
		return false;
	}

	/**
	 * Does {@code got} read the colours of {@code want}, in that order? A band of the glyph's row may
	 * be split in two on the way: the doll shades the panel's right half, a cell can straddle that
	 * edge, and two art colours a single level apart — one entry of the art's run, since the run is
	 * collapsed at the tolerance it is compared at — round to two levels apart on the two sides of
	 * it. So an entry of {@code got} that matches nothing may be passed over; what may never happen
	 * is a colour of the art turning up out of order, which is what a mirrored or mis-cropped face
	 * would look like. Every colour in play is already known to be one of the art's own (the
	 * chromaticity sets are compared exactly), so there is nothing else for a spare entry to be.
	 */
	private static boolean sameRun(List<Integer> got, List<Integer> want) {
		int at = 0;
		for (int colour : got) {
			if (at < want.size() && near(List.of(want.get(at)), colour)) at++;
		}
		return at == want.size();
	}

	private static String hex(List<Integer> colours) {
		return colours.stream().map(WardrobeTests::hex).toList().toString();
	}

	private static String hex(int chroma) {
		return "#" + Integer.toHexString(chroma);
	}

	/**
	 * A placement is drawn by its own glyph, in the title, over the bare ovve of the angle that
	 * shows its cell — and by nothing at all at the other three angles.
	 */
	@GameTest
	public void wardrobeTitleStacksTheBareOvveAndEveryPlacement(GameTestHelper helper) {
		Placement sleeve = new Placement(Spot.SLEEVE_OUT_MID_R, NYCKELN_PATCH);
		Wardrobe wardrobe = Wardrobe.NONE.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElseThrow()
				.add(sleeve.patch(), 1).sew(CHAPTER, sleeve).orElseThrow();
		for (Angle angle : Angle.values()) {
			String title = WardrobeGui.title(CHAPTER, wardrobe, angle).getString();
			char barely = WardrobePreview.bareGlyph(CHAPTER, angle).codepoint();
			if (title.indexOf(barely) < 0) helper.fail("the " + angle + " title does not draw the bare ovve");
			for (Placement placement : List.of(ITK, sleeve)) {
				WardrobeFont.Glyph glyph = WardrobePreview.patchGlyph(placement);
				boolean shown = WardrobePreview.angleOf(placement.spot()) == angle;
				boolean drawn = title.indexOf(glyph.codepoint()) >= 0;
				if (shown && !drawn) helper.fail(placement.key() + " is not drawn on the " + angle + " view, which shows its cell");
				if (!shown && drawn) helper.fail(placement.key() + " is drawn on the " + angle + " view, which cannot show its cell");
				if (shown && !title.contains(WardrobeFont.at(glyph))) helper.fail(placement.key() + " is not placed where the doll draws it");
			}
			// The chest is seen from the front and the sleeve's outer face from the wearer's right.
			if (angle == Angle.FRONT && WardrobePreview.angleOf(ITK.spot()) != Angle.FRONT) helper.fail("the chest is not on the front view");
			if (WardrobePreview.angleOf(sleeve.spot()) != Angle.RIGHT) helper.fail("a right sleeve's outer face is not on the right-side view");
		}
		helper.succeed();
	}

	/** Every glyph's art is the size the font promises, and every patch glyph lands inside the preview panel. */
	@GameTest
	public void wardrobePreviewArtFitsThePanel(GameTestHelper helper) {
		for (WardrobeFont.Glyph glyph : WardrobeFont.glyphs()) {
			var art = glyph.art().get();
			if (art.width != glyph.width() || art.height != glyph.height()) {
				helper.fail(glyph.name() + " art is " + art.width + "x" + art.height + ", the font says " + glyph.width() + "x" + glyph.height());
			}
			if (!glyph.name().startsWith("preview/")) continue;
			if (glyph.x() < WardrobePreview.PANEL_X || glyph.x() + glyph.width() > WardrobePreview.PANEL_X + WardrobePreview.PANEL_W
					|| glyph.top() < WardrobePreview.PANEL_Y || glyph.top() + glyph.height() > WardrobePreview.PANEL_Y + WardrobePreview.PANEL_H) {
				helper.fail(glyph.name() + " is drawn outside the preview panel, at (" + glyph.x() + "," + glyph.top() + ") " + glyph.width() + "x" + glyph.height());
			}
		}
		helper.succeed();
	}

	/** The rotation buttons are at the end of the tab row and cycle the angle through all four sides. */
	@GameTest(maxTicks = 1200)
	public void wardrobeRotationButtonsCycleTheAngle(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// The screen only builds its rows once the player's wardrobe is in the cache — before that
		// it is the "loading" screen — and the fetch goes to whichever throwaway backend is in.
		Wardrobes.fetch(player.getUUID());
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(player.getUUID()), "player not loaded"))
				.thenExecute(() -> rotationButtons(helper, player))
				.thenSucceed();
	}

	private static void rotationButtons(GameTestHelper helper, ServerPlayer player) {
		for (Angle angle : Angle.values()) {
			WardrobeGui gui = WardrobeGui.forTest(player, CHAPTER, angle);
			if (gui.angle() != angle) helper.fail("the screen is at " + gui.angle() + ", wanted " + angle);
			for (int slot : new int[]{WardrobeGui.ROTATE_LEFT, WardrobeGui.ROTATE_RIGHT}) {
				if (isEmpty(gui, slot)) helper.fail("no rotation button at slot " + slot);
				String name = gui.getGuiElement(slot).getItemStack().getHoverName().getString();
				if (!name.toLowerCase(java.util.Locale.ROOT).contains("turn")) helper.fail("the button at " + slot + " reads \"" + name + "\"");
			}
			String lore = lore(gui.getGuiElement(WardrobeGui.ROTATE_RIGHT).getItemStack());
			if (!lore.contains(angle.label)) helper.fail("the right-turn button does not say which side is showing: " + lore);
			if (!lore.contains(angle.turned(1).label)) helper.fail("the right-turn button does not say which side it brings round: " + lore);
		}
		// Four steps either way come back to where they started, and no two sides are the same.
		Angle at = Angle.FRONT;
		for (int i = 0; i < 4; i++) at = at.turned(1);
		if (at != Angle.FRONT) helper.fail("turning right four times does not come back to the front");
		for (int i = 0; i < 4; i++) at = at.turned(-1);
		if (at != Angle.FRONT) helper.fail("turning left four times does not come back to the front");
		if (Angle.FRONT.turned(1) == Angle.FRONT.turned(-1)) helper.fail("turning left and right are the same move");
	}

	/**
	 * Every cell the doll shows has a hover slot over the very px it is drawn on: the slot is
	 * inside the preview panel (rows 1-4, cols 5-8) and the cell's rectangle has its centre in that
	 * slot's own 18x18 box — which is the whole of what makes a tooltip point at the part of the
	 * picture it is about. And the three angles that do not show a cell have no slot for it.
	 */
	@GameTest
	public void wardrobePreviewSlotsHoldTheCellTheyAreAbout(GameTestHelper helper) {
		for (Spot spot : Spot.values()) {
		for (Angle angle : WardrobePreview.anglesOf(spot)) {
			int[] rect = WardrobePreview.cellRect(angle, spot);
			if (rect == null) {
				helper.fail(spot + ": the " + angle + " view shows it but has no rectangle for it");
				continue;
			}
			if (rect[0] < 0 || rect[1] < 0 || rect[0] + rect[2] > WardrobePreview.PANEL_W || rect[1] + rect[3] > WardrobePreview.PANEL_H) {
				helper.fail(spot + ": its rectangle " + rect[0] + "," + rect[1] + " " + rect[2] + "x" + rect[3] + " is not inside the preview panel");
			}
			int slot = WardrobeGui.previewSlot(angle, spot);
			int row = slot / 9, col = slot % 9;
			if (row < 1 || row > 4 || col < 5 || col > 8) {
				helper.fail(spot + ": slot " + slot + " (row " + row + ", col " + col + ") is not one of the preview's own");
				continue;
			}
			// The panel is exactly the 4x4 block of slot cells, so a slot's box in the panel's own px:
			int boxX = (col - 5) * WardrobeFont.PITCH, boxY = (row - 1) * WardrobeFont.PITCH;
			int cx = rect[0] + rect[2] / 2, cy = rect[1] + rect[3] / 2;
			if (cx < boxX || cx >= boxX + WardrobeFont.PITCH || cy < boxY || cy >= boxY + WardrobeFont.PITCH) {
				helper.fail(spot + ": the cell's centre (" + cx + ", " + cy + ") is outside slot " + slot + "'s box at (" + boxX + ", " + boxY + ")");
			}
			// And no slot on a view that does not draw the cell at all. (A cell on a box's top face
			// is drawn from two, the front and the back, so it is hoverable on both — each at the
			// slot that view really draws it in.)
			for (Angle other : Angle.values()) {
				if (WardrobePreview.anglesOf(spot).contains(other)) continue;
				if (WardrobeGui.previewSlot(other, spot) >= 0) helper.fail(spot + " has a slot on the " + other + " view, which does not show it");
			}
		}
		}
		helper.succeed();
	}

	/**
	 * A top-face placement is written on the cell's own rows and on no other — in particular not on
	 * the <b>marker row</b> just above them, which is where the playtest thought it had found the
	 * shoulders' art. It had not: that reading was a bounding box taken over the whole texture, which
	 * unions the art with the data texels datagen writes at the right-hand end of the marker row
	 * (hence its x reaching 127 as well as its y reaching 31). This says it per row instead, which a
	 * bounding box cannot fudge: for a cell-sized patch the art is exactly the cell's 8 rows and 8
	 * columns, row 31 is clear of everything but those data texels, and the cell's last row is drawn.
	 */
	@GameTest
	public void aTopFacePlacementSitsOnTheCellsOwnRows(GameTestHelper helper) {
		int D = Spot.DETAIL;
		Patches.Patch cellSized = Patches.all().stream().filter(p -> !p.seat() && p.width() == Spot.PX && p.height() == Spot.PX)
				.findFirst().orElse(null);
		if (cellSized == null) {
			helper.fail("no cell-sized patch in the catalogue, so nothing here can say where a cell's own rows are");
			return;
		}
		int tops = 0;
		for (Spot spot : Spot.values()) {
			if (!spot.top() || !cellSized.fits(spot)) continue;
			tops++;
			Tex tex = generated(spot.piece, "patch/" + spot.id() + "/" + cellSized.id());
			int first = spot.v * D, last = first + spot.pxHeight() - 1;
			if (first != 32 || last != 39) {
				helper.fail(spot.id() + "'s rows are " + first + ".." + last + ", and the playtest's numbers were about 32..39");
			}
			for (int y = 0; y < tex.height; y++) {
				int from = -1, to = -1;
				for (int x = 0; x < tex.width; x++) {
					if (y == tex.height / 2 - 1 && x >= tex.width - MARKER_ROW_DATA) continue;   // marker, kind, layer, debug palette
					if (Tex.a(tex.get(x, y)) == 0) continue;
					if (from < 0) from = x;
					to = x;
				}
				boolean onTheCell = y >= first && y <= last;
				if (!onTheCell && from >= 0) {
					helper.fail(cellSized.id() + " on " + spot.id() + " draws on row " + y + " (x " + from + ".." + to
							+ "), which is not one of the cell's own " + first + ".." + last
							+ (y == tex.height / 2 - 1 ? " — the marker row" : ""));
				}
				if (onTheCell && from < 0) {
					helper.fail(cellSized.id() + " on " + spot.id() + " leaves row " + y + " of the cell empty");
				}
				if (onTheCell && (from < spot.u * D || to >= spot.u * D + spot.px())) {
					helper.fail(cellSized.id() + " on " + spot.id() + " draws row " + y + " from x " + from + " to " + to
							+ ", outside the cell's columns " + spot.u * D + ".." + (spot.u * D + spot.px() - 1));
				}
			}
		}
		if (tops != 2) helper.fail("wanted the two shoulders to check, found " + tops + " top-face cell(s) the patch fits");
		helper.succeed();
	}

	/**
	 * The dev switch that paints the boxes' top faces ({@code OVVAR_DEBUG_TOP_FACES}) ships OFF, and
	 * the palette it paints from is in every texture of ours.
	 *
	 * <p>It exists because nothing about which arm a top-face fragment is on can be seen from here:
	 * both arms' top faces are the same texels, the only thing that tells them apart is the
	 * handedness of the texture over the geometry, and GLSL does not run in the game tests. With the
	 * switch on, the base garment layer paints such a fragment red where the mirror test calls it
	 * mirrored and blue where it does not, and the preview layer paints it magenta where no cell of
	 * the design matched it — three readings that separate the three things that can be wrong. Left
	 * on, it would paint every shoulder of every ovve on the server, so this holds it off.
	 */
	@GameTest
	public void theTopFaceDebugPaintShipsOff(GameTestHelper helper) {
		String glsl = resource("/assets/ovvar/shaders/include/ovvar.glsl");
		for (String flag : List.of("OVVAR_DEBUG_TOP_FACES", "OVVAR_DEBUG_TOP_FACE_SIDES",
				"OVVAR_DEBUG_TOP_FACE_MISS", "OVVAR_DEBUG_TOP_FACE_HIT")) {
			if (!glsl.contains("const bool " + flag + " = false;")) {
				helper.fail(flag + " is not declared false in ovvar.glsl — every shoulder in the world would be painted");
			}
		}
		// The palette the switches paint from: four opaque texels at the right-hand end of the marker
		// row, in every kind of texture of ours (a base garment, a placement, a preview).
		int[] wanted = {0xFFFF0000, 0xFF0000FF, 0xFFFF00FF, 0xFF00FF00};
		List<Tex> textures = List.of(
				generated(Piece.TOP, Chapter.values()[0].id + "/" + Piece.TOP.id),
				generated(Piece.TOP, "patch/" + Spot.SHOULDER_R.id() + "/itk"),
				generated(Piece.TOP, "patch/preview_" + Piece.TOP.id));
		for (Tex tex : textures) {
			for (int i = 0; i < wanted.length; i++) {
				int got = tex.get(tex.width - MARKER_ROW_DATA + i, tex.height / 2 - 1);
				if (got != wanted[i]) {
					helper.fail("a garment texture's debug palette texel " + i + " is " + Integer.toHexString(got)
							+ ", wanted " + Integer.toHexString(wanted[i]));
				}
			}
		}
		helper.succeed();
	}

	/**
	 * <b>A patch texel is drawn at the colour it was painted, exactly as a texel of the garment's own
	 * cloth is.</b> Nothing on the way from the art to the screen scales it, and this pins every step
	 * of that path that a server-side test can see:
	 *
	 * <ul>
	 *   <li>datagen copies the art into the placement texture and into the preview library without
	 *	   touching a channel — every colour the art uses is in both, byte for byte;
	 *   <li>the only dyeable layer of an ovve is the preview layer, whose dye is patch <em>data</em>,
	 *	   not paint — so nothing else can be tinted by anything;
	 *   <li>the vertex shader lights a patch layer of ours with white instead of that data colour
	 *	   ({@code mix(Color, vec4(1.0), ovvar_patch_layer())}), which is what keeps the dye off the
	 *	   art; and
	 *   <li>the fragment shader modulates the sampled colour exactly once, with vanilla's own
	 *	   {@code faceVertexColor * ColorModulator} and nothing else — so a patch texel and a cloth
	 *	   texel on the same face of the same box come out of the same arithmetic.
	 * </ul>
	 *
	 * <p>What it cannot see is the screen. If a white patch pixel and a white pixel of the ovve's own
	 * cloth <em>on the same face, in the same frame</em> ever differ, none of the above is where it
	 * comes from and this test will not have caught it. (Comparing against another face is not that
	 * test: vanilla's entity lighting gives a face a diffuse factor from its normal, so a front face
	 * and a face turned any other way differ by a good tenth whatever is drawn on them.)
	 */
	@GameTest
	public void patchTexelsAreDrawnAtTheColourTheyWerePainted(GameTestHelper helper) {
		// 1. Datagen keeps every colour of the art, in the placement textures and in the library.
		int checked = 0;
		for (Patches.Patch patch : Patches.all()) {
			List<Integer> want = patchArt(patch).opaqueColours();
			if (want.isEmpty()) {
				helper.fail(patch.id() + "'s art has no opaque colour to check");
				continue;
			}
			for (Spot spot : Spot.values()) {
				if (!patch.fits(spot) || spot == Spot.SEAT) continue;   // the seat is cut in half per leg
				// Against the PNG this cell shows: a patch may ship its art at several sizes.
				List<Integer> cellWant = patchArt(Patches.artFor(patch, spot)).opaqueColours();
				List<Integer> got = artColours(generated(spot.piece, "patch/" + spot.id() + "/" + patch.id()));
				for (int colour : cellWant) {
					// A colour can be clipped off a cell (a shoulder keeps the art's middle), so only
					// the ones that survive are required — but any that does must be its own colour.
					if (!got.contains(colour)) continue;
					checked++;
				}
				for (int colour : got) {
					if (!cellWant.contains(colour)) {
						helper.fail(patch.id() + " on " + spot.id() + ": the texture has " + Integer.toHexString(colour)
								+ ", which the art has not — something on the way scaled a channel");
					}
				}
			}
			for (Piece piece : Piece.values()) {
				if (Patches.code(patch) > Looks.INSTANT_DESIGNS) continue;   // not in the library at all
				List<Integer> library = artColours(generated(piece, "patch/preview_" + piece.id));
				for (int colour : want) {
					if (!library.contains(colour)) {
						helper.fail(patch.id() + ": the " + piece + " preview library has not got its colour "
								+ Integer.toHexString(colour) + " — the art was altered on the way in");
					}
				}
			}
		}
		if (checked == 0) helper.fail("no patch colour was found in any placement texture at all");

		// 2. The preview layer is the only dyeable one: its colour is data, so nothing else may be tinted.
		String json = EquipmentJson.json(Chapter.values()[0], Piece.TOP, false,
				List.of(new Placement(Spot.FRONT_TOP_LEFT, Patches.get("maid"))));
		int dyeable = json.split("dyeable", -1).length - 1;
		if (dyeable != 1) helper.fail("an ovve half declares " + dyeable + " dyeable layer(s), wanted exactly one (the preview): " + json);
		if (!json.contains(EquipmentJson.previewTexture(Piece.TOP))) helper.fail("the preview layer is not in the asset: " + json);

		// 3. The shaders: a patch layer is lit white, and the sampled colour is modulated once.
		String vsh = resource("/assets/minecraft/shaders/core/entity.vsh");
		if (!vsh.contains("mix(Color, vec4(1.0), ovvar_patch_layer())")) {
			helper.fail("entity.vsh no longer lights a patch layer as white, so the dye colour would tint the art");
		}
		String fsh = resource("/assets/minecraft/shaders/core/entity.fsh");
		List<String> modulations = new ArrayList<>();
		for (String line : fsh.split("\n")) {
			String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
			if (code.contains("color *=") || code.contains("color.rgb *=")) modulations.add(code.trim());
		}
		// Vanilla's own two, which every layer of every entity gets alike: the lit vertex colour (white
		// on a patch layer of ours) and the lightmap. A third would be ours, and would be a patch texel
		// scaled away from its own colour.
		List<String> vanillas = List.of("color *= faceVertexColor * ColorModulator;", "color *= lightMapColor;");
		if (!modulations.stream().allMatch(vanillas::contains) || modulations.size() != vanillas.size()) {
			helper.fail("entity.fsh modulates the sampled colour with " + modulations + ", wanted vanilla's " + vanillas
					+ " — anything else would scale a patch texel away from its own colour");
		}
		helper.succeed();
	}

	/**
	 * Every opaque colour a garment texture <em>draws</em> with: its art, without the data texels
	 * datagen writes at the right-hand end of the marker row — the kind and layer texels and the
	 * debug palette. Taken by position rather than by colour, so a patch is free to be painted in
	 * any of those colours.
	 */
	private static List<Integer> artColours(Tex tex) {
		List<Integer> out = new ArrayList<>();
		for (int y = 0; y < tex.height; y++) {
			for (int x = 0; x < tex.width; x++) {
				if (y == tex.height / 2 - 1 && x >= tex.width - MARKER_ROW_DATA) continue;   // the marker row's data texels
				int p = tex.get(x, y);
				if (Tex.a(p) == 255 && !out.contains(p)) out.add(p);
			}
		}
		return out;
	}

	/**
	 * <b>Which arm a fragment is on is decided once, and with the sense of the kind of face it is
	 * on.</b> The armour model draws the wearer's left limbs as mirror images off the right limb's
	 * strips, so both arms' top faces are the very same texels and the only thing that can tell them
	 * apart is the handedness of the texture over the geometry (`ovvar_handed`). A box's top and
	 * bottom faces unwrap the other way round from its four sides — u runs the same way round the
	 * box but v runs across it, back edge to front, instead of down it — so that handedness comes
	 * out with the opposite sign there and the same answer means the opposite thing.
	 * `OVVAR_MIRROR_SENSE` is the side faces' calibration and `OVVAR_TOP_FACE_SAME_SENSE` says whether
	 * the top's is the same one (it is, which the playtest settled: inverting it put each shoulder's
	 * art on the other shoulder).
	 *
	 * <p>Getting it wrong is what made the wearer's right shoulder draw nothing: the unmirrored arm's
	 * top face read as mirrored, so the cell the shader only draws on unmirrored fragments was
	 * skipped on both arms. GLSL does not run in the game tests, so what is pinned here is the
	 * structure that bug had — the sense read in one branch and not another. `mirrored` is computed
	 * exactly once, from both senses, and `ovvar_handed` is read nowhere else outside the function
	 * that sets it, so the base garment's mirror strip, a placement's "hide it on the other limb"
	 * and the preview's cell sides cannot disagree about which arm they are on.
	 */
	@GameTest
	public void everyPathTellsTheArmsApartTheSameWay(GameTestHelper helper) {
		String glsl = resource("/assets/ovvar/shaders/include/ovvar.glsl");
		for (String constant : List.of("OVVAR_MIRROR_SENSE", "OVVAR_TOP_FACE_SAME_SENSE")) {
			if (!glsl.contains("const bool " + constant + " =")) {
				helper.fail("ovvar.glsl no longer declares " + constant + ", so nothing says which sign a face's mirroring has");
			}
		}
		List<String> assignments = new ArrayList<>();
		List<String> reads = new ArrayList<>();
		for (String line : glsl.split("\n")) {
			String code = line.contains("//") ? line.substring(0, line.indexOf("//")) : line;
			if (!code.contains("ovvar_handed")) continue;
			if (code.contains("bool ovvar_handed")) continue;   // the declaration, not an assignment
			(code.contains("ovvar_handed =") ? assignments : reads).add(code.trim());
		}
		if (assignments.size() != 1) helper.fail("ovvar_handed is assigned " + assignments.size() + " time(s), wanted once: " + assignments);
		// Every read of it: exactly one, the line that turns it into `mirrored` with both senses.
		if (reads.size() != 1) {
			helper.fail("ovvar_handed is read " + reads.size() + " times, wanted once — every path must tell the arms apart"
					+ " through the one `mirrored`, or a top face and a side face can disagree: " + reads);
		} else {
			String read = reads.getFirst();
			if (!read.contains("mirrored")) helper.fail("the one read of ovvar_handed does not compute `mirrored`: " + read);
			if (!read.contains("OVVAR_TOP_FACE_SAME_SENSE") || !read.contains("sides")) {
				helper.fail("`mirrored` does not read the handedness with the sense of the kind of face the fragment is on: " + read);
			}
		}
		helper.succeed();
	}

	/**
	 * The trim channel — the top's fourth instant patch, one trim pattern per (cell, patch), drawn by
	 * vanilla — is the body's own box and nothing else, and datagen writes a pattern and a texture
	 * for exactly those. The shoulders are <b>not</b> in it, which the playtest asked about: the
	 * reason is not the squeeze (a box's top face is not on the strip's perimeter, so there would be
	 * nothing to bake and a shoulder trim would be pixel-exact) but that a trim texture carries none
	 * of our marker texels — asserted here — so {@code ovvar.glsl} never sees it, there is no
	 * {@code side} to hide it on the other limb by, and a trim for a cell on one arm would be drawn
	 * on both of them, one of the two mirrored. The body is one box and has no other limb to leak
	 * onto. Nor is the channel the pack path: a shoulder goes through the pack as an ordinary
	 * equipment layer ({@code EquipmentJson.layerTextures}), which is checked in the clipping test.
	 */
	@GameTest
	public void theTrimChannelIsTheBodysOwnBoxAndVanillaDrawsItRaw(GameTestHelper helper) {
		int patterns = 0, limbs = 0;
		for (Spot spot : Spot.values()) {
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				Placement placement = new Placement(spot, patch);
				boolean wanted = spot.piece == Piece.TOP && spot.side == Spot.Side.BODY;
				if (Trims.fits(placement) != wanted) {
					helper.fail(placement.key() + ": Trims.fits says " + Trims.fits(placement) + ", wanted " + wanted
							+ " (the channel is the top half's body cells, vanilla drawing a limb cell's trim on both limbs)");
				}
				String name = Trims.patternName(placement);
				boolean hasPattern = has("/data/" + metacraft.ovvar.Ovvar.MOD_ID + "/trim_pattern/" + name + ".json");
				boolean hasTexture = has("/assets/" + metacraft.ovvar.Ovvar.MOD_ID + "/textures/trims/entity/" + spot.piece.layer + "/" + name + ".png");
				if (hasPattern != wanted || hasTexture != wanted) {
					helper.fail(placement.key() + ": trim pattern " + hasPattern + ", texture " + hasTexture + ", wanted " + wanted
							+ " — datagen and Trims.fits disagree about the channel");
				}
				if (spot.side != Spot.Side.BODY) limbs++;
				if (!wanted) continue;
				patterns++;
				// Why it can be the body only: vanilla draws this texture itself, so it carries none
				// of the marker texels the shader would need to hide it on one limb of a pair — while
				// the placement texture for the very same cell does carry them.
				Tex trim = trimTexture(spot, placement);
				if (Tex.a(trim.get(trim.width - 1, trim.height / 2 - 1)) != 0) {
					helper.fail(name + " carries a marker texel; a trim is drawn by vanilla and must not look like one of ours");
				}
				Tex layer = generated(spot.piece, "patch/" + spot.id() + "/" + patch.id());
				if (Tex.a(layer.get(layer.width - 1, layer.height / 2 - 1)) == 0) {
					helper.fail(placement.key() + "'s placement texture has no marker texel, so the shader would not draw it at all");
				}
			}
		}
		if (patterns == 0) helper.fail("no trim patterns at all");
		if (limbs == 0) helper.fail("no limb cell in the catalogue to check the channel keeps out");
		helper.succeed();
	}

	/** A generated trim texture, off the runtime classpath. */
	private static Tex trimTexture(Spot spot, Placement placement) {
		String path = "/assets/" + metacraft.ovvar.Ovvar.MOD_ID + "/textures/trims/entity/" + spot.piece.layer + "/" + Trims.patternName(placement) + ".png";
		try (var in = WardrobeTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path + " — run ./gradlew runDatagen");
			return Tex.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean has(String path) {
		try (var in = WardrobeTests.class.getResourceAsStream(path)) {
			return in != null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The shoulders are the arm boxes' whole top faces, and what datagen draws there is the art
	 * <b>clipped</b> to that face: a top face's four edges have no neighbouring face in the layout
	 * to continue onto, so the twelve-pixel patches keep their middle eight columns and rows and
	 * nothing of them is drawn anywhere else on the texture. The left arm's is pre-mirrored like
	 * every other left-limb cell, checked once whole and again at a distinctive pixel — one where
	 * the art and its mirror image disagree, which is the pixel a missing flip would move.
	 */
	@GameTest
	public void aShoulderPatchIsClippedToTheArmsTopFace(GameTestHelper helper) {
		int D = Spot.DETAIL, W = 64 * D, H = 32 * D;
		int clipped = 0, whole = 0;
		for (Spot spot : List.of(Spot.SHOULDER_R, Spot.SHOULDER_L)) {
			if (!spot.top() || Spot.face(spot) != Spot.TOP_FACE) {
				helper.fail(spot.id() + " is not on its box's top face (row " + spot.v + ", face " + Spot.face(spot) + ")");
			}
			if (spot.u != 44 || spot.v != Spot.TOP_ROW || spot.width != 4 || spot.height != 4) {
				helper.fail(spot.id() + " is " + spot.width + "x" + spot.height + " at (" + spot.u + ", " + spot.v + "), wanted 4x4 at (44, " + Spot.TOP_ROW + ")");
			}
			int x0 = spot.u * D, y0 = spot.v * D, x1 = x0 + spot.px(), y1 = y0 + spot.pxHeight();
			for (Patches.Patch patch : Patches.all()) {
				if (!patch.fits(spot)) continue;
				// The PNG a shoulder shows: a patch that ships a cell-sized variant lands there whole,
				// which is what the variants are for; one that does not is clipped, as before.
				Patches.Art chosen = Patches.artFor(patch, spot);
				Tex art = patchArt(chosen);
				// The model mirrors the left limb, so its texture holds the art flipped in x.
				Tex baked = spot.side == Spot.Side.LEFT ? art.flipX() : art;
				Tex drawn = generated(Piece.TOP, "patch/" + spot.id() + "/" + patch.id());
				int ox = x0 + chosen.offsetX(spot), oy = y0 + chosen.offsetY(spot);
				// Every art pixel that falls on the face is there, as drawn (for that arm).
				for (int ay = 0; ay < baked.height; ay++) {
					for (int ax = 0; ax < baked.width; ax++) {
						int pixel = baked.get(ax, ay), tx = ox + ax, ty = oy + ay;
						if (Tex.a(pixel) == 0 || tx < x0 || tx >= x1 || ty < y0 || ty >= y1) continue;
						if (drawn.get(tx, ty) != pixel) {
							helper.fail(patch.id() + " on " + spot.id() + ": texel (" + tx + ", " + ty + ") is "
									+ Integer.toHexString(drawn.get(tx, ty)) + ", the art has " + Integer.toHexString(pixel));
						}
					}
				}
				// And nothing outside the face: no bend over its edges, no wrap round the strip.
				for (int ty = 0; ty < H; ty++) {
					for (int tx = 0; tx < W; tx++) {
						if (tx >= x0 && tx < x1 && ty >= y0 && ty < y1) continue;
						if (ty == H / 2 - 1 && tx >= W - MARKER_ROW_DATA) continue;   // the marker row's data texels (marker, kind, layer, debug palette)
						if (Tex.a(drawn.get(tx, ty)) != 0) {
							helper.fail(patch.id() + " on " + spot.id() + " draws at (" + tx + ", " + ty + "), off the arm's top face");
						}
					}
				}
				if (chosen.oversize(spot)) clipped++;
				else whole++;
				// The distinctive pixel, on the arm the model mirrors: at the column the mirroring
				// puts it in, the art's own pixel is what shows.
				if (spot.side == Spot.Side.LEFT) {
					int[] tell = firstDifference(art, art.flipX());
					if (tell == null) {
						continue;   // a symmetrical patch cannot tell a missing flip from a correct one
					}
					int tx = x0 + chosen.offsetX(spot) + art.width - 1 - tell[0], ty = oy + tell[1];
					if (tx < x0 || tx >= x1 || ty < y0 || ty >= y1) continue;   // clipped away
					if (drawn.get(tx, ty) != art.get(tell[0], tell[1])) {
						helper.fail(patch.id() + " on " + spot.id() + ": art pixel (" + tell[0] + ", " + tell[1] + ") should be at texel ("
								+ tx + ", " + ty + ") on the mirrored arm, which has " + Integer.toHexString(drawn.get(tx, ty)));
					}
				}
			}
		}
		if (clipped == 0) helper.fail("no patch in the catalogue is bigger than a shoulder, so nothing here tested the clipping");
		// And the other half of it: a patch with a cell-sized variant is drawn there whole (every
		// pixel of that PNG is on the face, which the loop above has just checked pixel by pixel).
		if (whole == 0) helper.fail("no patch lands on a shoulder whole, so nothing here tested the per-size art");
		// And a design naming the new cells goes to the store and back.
		List<Placement> shoulders = List.of(new Placement(Spot.SHOULDER_R, Patches.get("itk")), new Placement(Spot.SHOULDER_L, Patches.get("nyckeln")));
		List<String> keys = shoulders.stream().map(Placement::key).toList();
		Optional<SpotPlacements> round = SpotPlacements.CODEC.parse(JavaOps.INSTANCE, keys).result();
		if (round.isEmpty() || !round.get().asPlacementList().equals(shoulders)) {
			helper.fail("a design of " + keys + " did not round-trip: " + round.map(SpotPlacements::asPlacementList));
		}
		if (!Spot.SHOULDER_R.label().equals("right shoulder") || !Spot.SHOULDER_L.label().equals("left shoulder")) {
			helper.fail("the shoulders are labelled " + Spot.SHOULDER_R.label() + " / " + Spot.SHOULDER_L.label());
		}
		helper.succeed();
	}

	/**
	 * How the paper doll shows a shoulder: a box's top face is turned away from all four sides, so
	 * the front and the back views each draw it <b>foreshortened</b> — the face's four texel rows
	 * averaged in pairs into two, a {@value WardrobePreview#CAP} px cap sitting on the top of the
	 * sleeve column and nothing else on the figure moved. The cap is over the sleeve's own columns,
	 * so the shoulder's tooltip shares the sleeve's slot; {@code angleOf} names the front view, and
	 * the back view has its own glyph — the same face from the opposite side, a different picture.
	 *
	 * <p>The squash is said against the art: two rows of it that differ must come out of
	 * {@link WardrobePreview#shownArt} the same, because the doll averaged them — which is also why
	 * the audit ({@code wardrobePreviewDrawsEveryCellsOwnPatchArt}) compares the glyph against that
	 * reference rather than against the raw art.
	 */
	@GameTest
	public void theShouldersAreDrawnAsAForeshortenedCapOnTheSleeve(GameTestHelper helper) {
		Object[][] cases = {{Spot.SHOULDER_R, Spot.SLEEVE_FRONT_TOP_R}, {Spot.SHOULDER_L, Spot.SLEEVE_FRONT_TOP_L}};
		for (Object[] pair : cases) {
			Spot shoulder = (Spot) pair[0], sleeve = (Spot) pair[1];
			if (WardrobePreview.angleOf(shoulder) != Angle.FRONT) helper.fail(shoulder + " is on the " + WardrobePreview.angleOf(shoulder) + " view, wanted the front");
			if (!WardrobePreview.anglesOf(shoulder).equals(List.of(Angle.FRONT, Angle.BACK))) {
				helper.fail(shoulder + " is drawn from " + WardrobePreview.anglesOf(shoulder) + ", wanted the front and the back");
			}
			int[] cap = WardrobePreview.cellRect(Angle.FRONT, shoulder);
			int[] arm = WardrobePreview.cellRect(Angle.FRONT, sleeve);
			if (cap == null || arm == null) {
				helper.fail(shoulder + " or " + sleeve + " has no rectangle on the front view");
				continue;
			}
			if (cap[1] != 0 || cap[3] != WardrobePreview.CAP) {
				helper.fail(shoulder + "'s cap is " + cap[3] + " px tall at y " + cap[1] + ", wanted " + WardrobePreview.CAP + " px at the top of the figure");
			}
			if (cap[0] != arm[0] || cap[2] != arm[2]) {
				helper.fail(shoulder + "'s cap spans x " + cap[0] + ".." + (cap[0] + cap[2]) + ", the sleeve under it " + arm[0] + ".." + (arm[0] + arm[2]));
			}
			// The cap sits on the sleeve, so the tooltip is on the sleeve's own slot.
			if (WardrobeGui.previewSlot(shoulder) != WardrobeGui.previewSlot(sleeve)) {
				helper.fail(shoulder + "'s front slot is " + WardrobeGui.previewSlot(shoulder) + ", the sleeve's " + WardrobeGui.previewSlot(sleeve));
			}
			if (WardrobeGui.previewSlot(Angle.BACK, shoulder) < 0) helper.fail(shoulder + " has no slot on the back view, which draws it");
			// A glyph per view, and the two are not the same picture (the face is turned round).
			Patches.Patch patch = Patches.get("itk");
			Placement placement = new Placement(shoulder, patch);
			WardrobeFont.Glyph front = WardrobePreview.patchGlyph(placement, Angle.FRONT), back = WardrobePreview.patchGlyph(placement, Angle.BACK);
			if (front == null || back == null) helper.fail(placement.key() + " has no glyph on the front or the back view");
			else if (front.codepoint() == back.codepoint()) helper.fail(placement.key() + " draws the same glyph from the front and the back");
			else if (front.height() > WardrobePreview.CAP) helper.fail(placement.key() + "'s front glyph is " + front.height() + " px tall, taller than the cap");
			// The squash, against the art: rows the doll averaged together read the same afterwards.
			Patches.Art chosen = Patches.artFor(patch, shoulder);
			Tex art = patchArt(chosen);
			Tex shown = WardrobePreview.shownArt(shoulder, chosen, art, Angle.FRONT);
			int squashed = 0;
			for (int y = 0; y + 1 < art.height; y++) {
				if (run(art, y).equals(run(art, y + 1))) continue;   // the art's own rows already agree
				if (run(shown, y).isEmpty() || run(shown, y + 1).isEmpty()) continue;   // clipped off the face
				if (run(shown, y).equals(run(shown, y + 1))) squashed++;
			}
			if (squashed == 0) {
				helper.fail(shoulder + ": no two rows of " + patch.id() + " came out of the cap averaged together, so nothing was foreshortened");
			}
		}
		helper.succeed();
	}

	/**
	 * The cells' own rectangles, the numbers the playtest asked for: the sleeves a texel lower than
	 * they were (v 21 and 25), the legs two lower (22 and 26) with the seat following the back of the
	 * legs, and the chest and back where they were (21 and 26 / 21). And, whatever the rows are, every
	 * cell stays on the boxes' side rows and off the row at either end of them — the collar and the
	 * waistband above, the belt, the hands and the cuff under a boot below.
	 */
	@GameTest
	public void cellRowsAreWhereThePlaytestPutThem(GameTestHelper helper) {
		int top = Spot.FACE_ROW, bottom = Spot.FACE_ROW + Spot.FACE_ROWS;
		for (Spot spot : Spot.values()) {
			String name = spot.name();
			// A cell on a box's TOP face has its own four rows, above the side rows, and fills them:
			// there is no collar or cuff up there to keep off, and the whole face is the cell.
			if (spot.top()) {
				if (spot.v != Spot.TOP_ROW || spot.height != Spot.TOP_ROWS) {
					helper.fail(spot.id() + " is on the box's top face at rows " + spot.v + ".." + (spot.v + spot.height)
							+ ", wanted " + Spot.TOP_ROW + ".." + (Spot.TOP_ROW + Spot.TOP_ROWS));
				}
				continue;
			}
			List<Integer> wanted;
			if (spot == Spot.SEAT) wanted = List.of(Spot.LEG_BACK_TOP_R.v);
			else if (spot == Spot.BACK_BIG) wanted = List.of(22);   // rows 22-29, clear of the collar and the belt
			else if (name.startsWith("SLEEVE_")) wanted = List.of(21, 25);
			else if (name.startsWith("LEG_")) wanted = List.of(22, 26);
			else wanted = List.of(21, 26);   // the chest and the back's top row, which the playtest left alone
			if (!wanted.contains(spot.v)) helper.fail(spot.id() + " is on row " + spot.v + ", wanted one of " + wanted);
			// The rows at either end of the side rows are not ours to draw on.
			if (spot.v <= top) helper.fail(spot.id() + " starts on row " + spot.v + ", which is the collar or the waistband");
			if (spot.v + spot.height >= bottom) {
				helper.fail(spot.id() + " reaches row " + (spot.v + spot.height - 1) + ", which is the belt, the hand or the cuff");
			}
		}
		helper.succeed();
	}

	/**
	 * The front view is where it always was: the geometry gives the chest and the front of the legs
	 * the very slots the old hand-written table did, so nothing anybody has sewn moves on the screen
	 * they already know. The figure turns in place, so the back's own cells land on the chest's slots.
	 */
	/**
	 * <b>The back wears all three of its cells at once.</b> Overlapping patches are the point of an
	 * ovve, so the big back cell and the two back-top cells that lie inside it are no longer
	 * exclusive: sew all three and they are drawn in {@link Spot#layer} order, the big one under the
	 * two small ones. Only the <b>seat</b> still shuts cells out, because it is not one patch on one
	 * cell — it is one patch cut in half across two cells of two different boxes, drawn as a single
	 * sprite on the seam between them, so "one of them on top" has no answer there.
	 *
	 * <p>The order is one number, {@code Spot.layer} (how many overlapping cells are bigger than
	 * this one), and every path that draws a patch takes it from there: the pack's layer list
	 * ({@code EquipmentJson.layerTextures}, which is what the paper doll composites as well), the
	 * sprites on a stand, and — since the ranked set in the dye colour has no order of its own — the
	 * cell table's own B, which the shader reads to pick the highest layer a fragment falls in.
	 */
	@GameTest
	public void theBackWearsTheBigCellAndTheTwoTopCellsAtOnce(GameTestHelper helper) {
		// What overlaps what, off the cells' own rectangles.
		Object[][] pairs = {
				{Spot.BACK_BIG, List.of(Spot.BACK_TOP_LEFT, Spot.BACK_TOP_RIGHT)},
				{Spot.BACK_TOP_LEFT, List.of(Spot.BACK_BIG)},
				{Spot.BACK_TOP_RIGHT, List.of(Spot.BACK_BIG)},
				{Spot.SEAT, List.of(Spot.LEG_BACK_TOP_R, Spot.LEG_BACK_TOP_L)},
				{Spot.LEG_BACK_TOP_R, List.of(Spot.SEAT)},
				{Spot.LEG_BACK_TOP_L, List.of(Spot.SEAT)},
		};
		List<Spot> expected = new ArrayList<>();
		for (Object[] pair : pairs) {
			Spot spot = (Spot) pair[0];
			@SuppressWarnings("unchecked") List<Spot> wanted = (List<Spot>) pair[1];
			expected.add(spot);
			List<Spot> got = new ArrayList<>(spot.overlaps());
			if (!got.containsAll(wanted) || got.size() != wanted.size()) helper.fail(spot.id() + " overlaps " + got + ", wanted " + wanted);
		}
		for (Spot spot : Spot.values()) {
			if (!expected.contains(spot) && !spot.overlaps().isEmpty()) {
				helper.fail(spot.id() + " overlaps " + spot.overlaps() + ", and should overlap nothing");
			}
			// And of those, only the seat's pairs shut each other out.
			List<Spot> exclusive = spot.overlapping();
			for (Spot other : exclusive) {
				if (other.side != Spot.Side.SEAT && spot.side != Spot.Side.SEAT) {
					helper.fail(spot.id() + " shuts " + other.id() + " out, and nothing but the seat does that any more");
				}
			}
			if (spot.side == Spot.Side.SEAT || Spot.SEAT_CELLS.contains(spot)) {
				if (exclusive.isEmpty()) helper.fail(spot.id() + " no longer shuts the seat's other cells out");
			} else if (!exclusive.isEmpty()) {
				helper.fail(spot.id() + " shuts " + exclusive + " out; only the seat's cells do");
			}
		}
		// The stack: the big one under the two that lie inside it.
		if (Spot.BACK_BIG.layer() != 0) helper.fail("the big back cell is on layer " + Spot.BACK_BIG.layer() + ", wanted the bottom");
		for (Spot small : List.of(Spot.BACK_TOP_LEFT, Spot.BACK_TOP_RIGHT)) {
			if (small.layer() <= Spot.BACK_BIG.layer()) helper.fail(small.id() + " is not drawn over the big back cell");
		}
		// Sewing all three, in the order that used to be refused.
		Patches.Patch itk = Patches.get("itk"), nyckeln = Patches.get("nyckeln");
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		List<Placement> three = List.of(new Placement(Spot.BACK_BIG, itk),
				new Placement(Spot.BACK_TOP_LEFT, nyckeln), new Placement(Spot.BACK_TOP_RIGHT, nyckeln));
		for (Placement placement : three) {
			if (!Looks.canSew(ovve, placement)) helper.fail("cannot sew " + placement.key() + " with " + Looks.sewn(ovve).map(SpotPlacements::asPlacementList));
			if (!Looks.sew(ovve, placement)) helper.fail("sewing " + placement.key() + " was refused");
		}
		for (Placement placement : three) {
			if (Looks.at(ovve, placement.spot()) == null) helper.fail(placement.spot().id() + " came off when the others went on");
		}
		// The other way round too: the big one sewn last does not take the two with it.
		ItemStack second = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		for (Placement placement : three.reversed()) {
			if (!Looks.sew(second, placement)) helper.fail("sewing " + placement.key() + " last-first was refused");
		}
		if (SpotPlacements.asPlacementList(Looks.sewn(second)).size() != 3) {
			helper.fail("the back holds " + SpotPlacements.asPlacementList(Looks.sewn(second)) + ", wanted all three");
		}
		// And the seat still does shut its cells out.
		ItemStack legs = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		if (!Looks.sew(legs, new Placement(Spot.LEG_BACK_TOP_R, itk))) helper.fail("could not sew the right leg's back cell");
		if (Looks.canSew(legs, new Placement(Spot.SEAT, Patches.get("rivals")))) helper.fail("a seat patch can go on over a leg-back cell");
		// The pack's layers, in the stack's order whatever order the placements arrive in.
		for (List<Placement> order : List.of(three, three.reversed())) {
			List<String> layers = EquipmentJson.layerTextures(Chapter.values()[0], Piece.TOP, false, order);
			int big = layers.indexOf("ovvar:patch/back_big/" + itk.id());
			int left = layers.indexOf("ovvar:patch/back_top_left/" + nyckeln.id());
			int right = layers.indexOf("ovvar:patch/back_top_right/" + nyckeln.id());
			if (big < 1 || left < 0 || right < 0) helper.fail("the three back placements are not all in " + layers);
			else if (big > left || big > right) helper.fail("the big back cell is stacked over the cells inside it: " + layers);
		}
		// And the instant path's copy of the same number, read the way the shader reads it.
		String glsl = resource("/assets/ovvar/shaders/include/ovvar.glsl");
		double tableX = shaderConst(helper, glsl, "OVVAR_TABLE_X"), columns = shaderConst(helper, glsl, "OVVAR_TABLE_COLUMNS");
		for (Piece piece : Piece.values()) {
			Tex preview = generated(piece, "patch/preview_" + piece.id);
			List<Spot> cells = Spot.cells(piece);
			for (int index = 0; index < cells.size(); index++) {
				Spot spot = cells.get(index);
				int texel = preview.get((int) tableX + (int) columns + index / 16, index % 16);
				if (Tex.b(texel) != spot.layer()) {
					helper.fail(spot.id() + " is on layer " + Tex.b(texel) + " in the " + piece + " cell table, and " + spot.layer() + " here");
				}
			}
		}
		helper.succeed();
	}

	/**
	 * The big back cell is {@link Patches#MAX_ART} square, so it is the one cell every patch in the
	 * catalogue fits inside: nothing of the art is wrapped round onto the face next door, which is
	 * what a patch bigger than its cell does everywhere else. Checked against what datagen really
	 * draws — the placement texture must be the art, centred, inside the back face's own columns.
	 */
	@GameTest
	public void theBigBackCellHoldsTheBiggestArtWholeAndCentred(GameTestHelper helper) {
		Spot spot = Spot.BACK_BIG;
		if (spot.px() != Patches.MAX_ART || spot.pxHeight() != Patches.MAX_ART) {
			helper.fail("the big back cell is " + spot.px() + "x" + spot.pxHeight() + " px, wanted " + Patches.MAX_ART + " square");
		}
		int D = Spot.DETAIL, faceStart = spot.u * D, faceEnd = (spot.u + spot.width) * D;
		for (Patches.Patch patch : Patches.all()) {
			if (!patch.fits(spot)) continue;
			// The PNG the big cell shows: the largest the patch ships that fits it, so a patch with a
			// MAX_ART variant fills the cell instead of floating in the middle of it.
			Patches.Art chosen = Patches.artFor(patch, spot);
			if (chosen.oversize(spot)) helper.fail(patch.id() + " hangs over the big back cell, which is " + Patches.MAX_ART + " square");
			int x = faceStart + chosen.offsetX(spot), y = spot.v * D + chosen.offsetY(spot);
			if (x < faceStart || x + chosen.width() > faceEnd) {
				helper.fail(patch.id() + " on the big back cell runs from texel " + x + " to " + (x + chosen.width()) + ", off the back face (" + faceStart + ".." + faceEnd + ")");
			}
			Tex art = patchArt(chosen);
			Tex drawn = generated(Piece.TOP, "patch/" + spot.id() + "/" + patch.id()).crop(x, y, chosen.width(), chosen.height());
			if (!same(drawn, art)) helper.fail(patch.id() + "'s big-back-cell texture is not its art, centred at (" + x + ", " + y + ")");
		}
		helper.succeed();
	}

	/**
	 * The instant channel's arithmetic still fits the shader. Every (cell, design) state of a half
	 * has to be under {@link Looks#INSTANT_STATES}, because past that the shader's float binomials
	 * stop being exact — adding cells (the big back cell) or designs (a patch) is exactly what walks
	 * into it, and {@code Looks.rank} would only throw once somebody wore the set that overflowed.
	 * A look at a real ovve with the channel full goes through the ranking and the packing too.
	 */
	@GameTest
	public void theInstantChannelStaysInsideWhatTheShaderCanUnrank(GameTestHelper helper) {
		for (Piece piece : Piece.values()) {
			int cells = Spot.cells(piece).size(), states = cells * Looks.INSTANT_DESIGNS;
			if (states > Looks.INSTANT_STATES) {
				helper.fail(piece + ": " + cells + " cells x " + Looks.INSTANT_DESIGNS + " designs = " + states
						+ " states, and the shader's binomials are exact only up to " + Looks.INSTANT_STATES);
			}
		}
		// The top is the half that grew: seven body cells (chest four, back three), twelve sleeve ones
		// and the two shoulders.
		if (Spot.cells(Piece.TOP).size() != 21) helper.fail("the top has " + Spot.cells(Piece.TOP).size() + " cells, wanted 21");
		// And the designs are as many as that leaves room for: the two shoulders are what took the
		// channel from 22 designs to 21, and one more of either would walk past the shader.
		int topCells = Spot.cells(Piece.TOP).size();
		if (topCells * (Looks.INSTANT_DESIGNS + 1) <= Looks.INSTANT_STATES) {
			helper.fail("the channel has room for " + (Looks.INSTANT_DESIGNS + 1) + " designs but only names "
					+ Looks.INSTANT_DESIGNS + ": " + topCells + " x " + (Looks.INSTANT_DESIGNS + 1) + " <= " + Looks.INSTANT_STATES);
		}
		// And the channel packs a full set of the highest-numbered cells without overflowing.
		ItemStack ovve = new ItemStack(ModContent.ovve(Chapter.values()[0]));
		List<Spot> top = Spot.cells(Piece.TOP);
		Patches.Patch itk = Patches.get("itk");
		for (int i = 0; i < Looks.INSTANT; i++) {
			Placement placement = new Placement(top.get(top.size() - 1 - i), itk);
			if (!Looks.sew(ovve, placement)) helper.fail("could not sew " + placement.key());
		}
		if (Looks.look(ovve, Piece.TOP, UUID.randomUUID(), false).dye() == 0) helper.fail("a full instant channel encoded to no dye colour");
		helper.succeed();
	}

	/**
	 * A design saved before a cell went away still opens. The back's two low cells went when the big
	 * back cell arrived, and players' items and stored wardrobe rows name them: the codec drops what
	 * this build has no cell (or no patch) for, logs it, and keeps the rest — rather than failing the
	 * whole design and costing somebody everything they had sewn. A design that is nothing but
	 * unknown cells is no design at all, and decodes as none.
	 */
	@GameTest
	public void aSavedDesignSurvivesACellThisBuildNoLongerHas(GameTestHelper helper) {
		Optional<SpotPlacements> kept = SpotPlacements.CODEC
				.parse(JavaOps.INSTANCE, List.of("back_low_left.itk", "front_top_left.itk", "leg_front_top_r.nyckeln", "front_low_right.no_such_patch"))
				.result();
		if (kept.isEmpty()) {
			helper.fail("a design naming one cell this build does not have decoded as nothing at all");
		} else {
			List<Placement> got = kept.get().asPlacementList();
			if (got.size() != 2) helper.fail("kept " + got.size() + " placement(s), wanted the 2 this build can read: " + got);
			if (kept.get().get(Spot.FRONT_TOP_LEFT).isEmpty()) helper.fail("the chest patch was dropped with the unknown ones");
			if (kept.get().get(Spot.LEG_FRONT_TOP_R).isEmpty()) helper.fail("the leg patch was dropped with the unknown ones");
		}
		if (SpotPlacements.CODEC.parse(JavaOps.INSTANCE, List.of("back_low_right.itk")).result().isPresent()) {
			helper.fail("a design of nothing but unknown cells decoded as a design");
		}
		helper.succeed();
	}

	/** The slot the big back cell's rectangle centres in: it covers the whole back face, and its centre falls here. */
	private static final int BACK_BIG_SLOT = 25;

	@GameTest
	public void wardrobePreviewFrontSlotsAreWhereTheyAlwaysWere(GameTestHelper helper) {
		Object[][] front = {
				{Spot.FRONT_TOP_LEFT, 15}, {Spot.FRONT_TOP_RIGHT, 16},
				{Spot.FRONT_LOW_LEFT, 24}, {Spot.FRONT_LOW_RIGHT, 25},
				{Spot.LEG_FRONT_TOP_R, 33}, {Spot.LEG_FRONT_MID_R, 42},
				{Spot.LEG_FRONT_TOP_L, 34}, {Spot.LEG_FRONT_MID_L, 43},
		};
		for (Object[] pair : front) {
			Spot spot = (Spot) pair[0];
			int wanted = (Integer) pair[1];
			int got = WardrobeGui.previewSlot(spot);
			if (got != wanted) helper.fail(spot + " on the front view: slot " + got + ", wanted " + wanted);
			if (WardrobeGui.previewSlot(Angle.FRONT, spot) != got) helper.fail(spot + ": the front overload disagrees with previewSlot(FRONT, spot)");
		}
		Object[][] back = {
				{Spot.BACK_TOP_LEFT, 15}, {Spot.BACK_TOP_RIGHT, 16},
				{Spot.BACK_BIG, BACK_BIG_SLOT},
		};
		for (Object[] pair : back) {
			Spot spot = (Spot) pair[0];
			int wanted = (Integer) pair[1];
			int got = WardrobeGui.previewSlot(Angle.BACK, spot);
			if (got != wanted) helper.fail(spot + " on the back view: slot " + got + ", wanted " + wanted);
			if (WardrobeGui.previewSlot(spot) >= 0) helper.fail(spot + " has a slot on the front view, which cannot show it");
		}
		helper.succeed();
	}

	/**
	 * The hover tooltips are on every angle, each where that angle draws the cell: a patch on the
	 * chest is hoverable from the front and nowhere else, one on the back from the back, one on the
	 * outer face of the right sleeve from the right — and a view shows tooltips for the cells it
	 * shows and for no others.
	 */
	@GameTest(maxTicks = 1200)
	public void wardrobePreviewTooltipsFollowTheAngle(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		AtomicReference<Wardrobes.Outcome> outcome = new AtomicReference<>();
		List<Placement> sewn = List.of(ITK, NYCKELN, SLEEVE);
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobes.use(server, new FileBackend(dir));
					Wardrobes.fetch(owner);
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(owner), "owner not loaded"))
				// The chest, the back and the right sleeve: three cells, three different sides.
				.thenExecute(() -> Wardrobes.update(owner, w -> w.add(ITK_PATCH, 2).add(NYCKELN_PATCH, 1)
						.sew(CHAPTER, ITK).orElseThrow()
						.sew(CHAPTER, NYCKELN).orElseThrow()
						.sew(CHAPTER, SLEEVE).orElse(null), outcome::set))
				.thenWaitUntil(() -> assertThat(outcome.get() == Wardrobes.Outcome.OK, "setup outcome " + outcome.get()))
				.thenExecute(() -> guarded(server, () -> {
					if (WardrobePreview.angleOf(ITK.spot()) != Angle.FRONT) helper.fail("the chest is not on the front view");
					if (WardrobePreview.angleOf(NYCKELN.spot()) != Angle.BACK) helper.fail("the back is not on the back view");
					if (WardrobePreview.angleOf(SLEEVE.spot()) != Angle.RIGHT) helper.fail("the right sleeve's outer face is not on the right view");
					for (Angle angle : Angle.values()) {
						WardrobeGui gui = WardrobeGui.forTest(player, CHAPTER, angle);
						int wanted = 0;
						for (Placement placement : sewn) {
							int slot = WardrobeGui.previewSlot(angle, placement.spot());
							if (WardrobePreview.angleOf(placement.spot()) != angle) {
								if (slot >= 0) helper.fail(placement.spot() + " has a slot on the " + angle + " view, which does not show it");
								continue;
							}
							wanted++;
							if (isEmpty(gui, slot)) helper.fail("no tooltip item for " + placement.spot() + " at slot " + slot + " on the " + angle + " view");
							else {
								if (gui.getGuiElement(slot).getGuiCallback() != GuiElement.EMPTY_CALLBACK) helper.fail("a preview item has a click callback");
								if (!lore(gui.getGuiElement(slot).getItemStack()).contains(placement.spot().label())) {
									helper.fail("the " + angle + " view's tooltip does not name " + placement.spot().label() + ": " + lore(gui.getGuiElement(slot).getItemStack()));
								}
							}
						}
						int filled = 0;
						for (int i = 9; i < 45; i++) if (i % 9 >= 5 && !isEmpty(gui, i)) filled++;
						if (filled != wanted) helper.fail("the " + angle + " view has " + filled + " tooltip item(s) over the preview, wanted " + wanted);
					}
					release(server);
				}))
				.thenSucceed();
	}

	/**
	 * {@code /ovvar look <player>}: somebody else's ovve, read-only. Their design drives the preview
	 * and its tooltips, their stash is not shown at all, and the action row has nothing on it but
	 * help and close — no take out, no put in, no sewing, no mannequin, and no click anywhere.
	 */
	@GameTest(maxTicks = 1200)
	public void wardrobeLookIsReadOnlyAndShowsTheirDesign(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("ovvar-wardrobes");
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		UUID them = UUID.randomUUID();
		Placement sleeve = new Placement(Spot.SLEEVE_OUT_MID_R, NYCKELN_PATCH);
		helper.startSequence()
				.thenWaitUntil(() -> assertThat(BUSY.compareAndSet(false, true), "another store test is running"))
				.thenExecute(() -> guarded(server, () -> {
					try {
						// Their wardrobe, written as if by another server, then loaded by UUID: a
						// wardrobe is a row in a store, so they need not be here at all.
						FileBackend backend = new FileBackend(dir);
						Wardrobe theirs = Wardrobe.NONE.add(ITK_PATCH, 1).sew(CHAPTER, ITK).orElseThrow()
								.add(sleeve.patch(), 1).sew(CHAPTER, sleeve).orElseThrow()
								.add(NYCKELN_PATCH, 4).withVersion(1);
						if (!backend.store(them, theirs, 0)) helper.fail("could not write the other player's wardrobe");
						Wardrobes.use(server, new FileBackend(dir));
						Wardrobes.fetch(them);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}))
				.thenWaitUntil(() -> assertThat(Wardrobes.loaded(them), "the other player's wardrobe never loaded"))
				.thenExecute(() -> guarded(server, () -> {
					Wardrobe theirs = Wardrobes.current(them);
					if (WardrobeGui.sewnCount(theirs, CHAPTER) != 2) helper.fail("their design did not load: " + theirs);
					WardrobeGui gui = WardrobeGui.forTestLook(viewer, them, "Nisse", CHAPTER, Angle.FRONT);
					if (gui.own()) helper.fail("a look at somebody else says it is the viewer's own");

					// The action row: help and close, and nothing else, none of it clickable but close.
					for (int slot = 45; slot < 54; slot++) {
						boolean allowed = slot == 52 || slot == 53;
						if (!allowed && !isEmpty(gui, slot)) {
							helper.fail("action slot " + slot + " is filled on a read-only look: " + gui.getGuiElement(slot).getItemStack());
						}
					}
					if (isEmpty(gui, 52)) helper.fail("no help item on a read-only look");
					if (isEmpty(gui, 53)) helper.fail("no close button on a read-only look");
					if (!gui.getGuiElement(52).getItemStack().getHoverName().getString().contains("Nisse")) {
						helper.fail("the help item does not say whose ovve this is: " + gui.getGuiElement(52).getItemStack().getHoverName().getString());
					}

					// Their stash is not shown, and the pocket says why.
					for (int slot = 9; slot < 45; slot++) {
						int col = slot % 9;
						if (col < 5 && !isEmpty(gui, slot)) helper.fail("their stash is on show at slot " + slot);
					}
					String title = gui.getTitle().getString();
					if (title.indexOf(WardrobeFont.LOOK_ONLY.codepoint()) < 0) helper.fail("the pocket does not say it is a look only");
					if (title.indexOf(WardrobeFont.NO_PATCHES.codepoint()) >= 0) helper.fail("a look draws the \"no patches yet\" notice over their stash");

					// Their design drives the preview: the bare ovve and both of their placements.
					if (title.indexOf(WardrobePreview.bareGlyph(CHAPTER, Angle.FRONT).codepoint()) < 0) helper.fail("no bare ovve on a look");
					if (title.indexOf(WardrobePreview.patchGlyph(ITK).codepoint()) < 0) helper.fail("their chest patch is not drawn on the front view");
					int slot = WardrobeGui.previewSlot(ITK.spot());
					if (isEmpty(gui, slot)) helper.fail("no tooltip for their patch");
					if (gui.getGuiElement(slot).getGuiCallback() != GuiElement.EMPTY_CALLBACK) helper.fail("a look's preview item is clickable");
					if (!lore(gui.getGuiElement(slot).getItemStack()).contains("Nisse")) {
						helper.fail("the tooltip does not say whose ovve the patch is on: " + lore(gui.getGuiElement(slot).getItemStack()));
					}

					// The tabs are the chapters they have sewn on, and they are clickable (turning and
					// switching chapters is all a look can do).
					if (isEmpty(gui, 0)) helper.fail("no tab for the chapter they have a design for");
					if (isEmpty(gui, WardrobeGui.ROTATE_LEFT) || isEmpty(gui, WardrobeGui.ROTATE_RIGHT)) helper.fail("a look cannot turn the figure");
					release(server);
				}))
				.thenSucceed();
	}

	private static boolean isEmpty(WardrobeGui gui, int slot) {
		GuiElement element = gui.getGuiElement(slot);
		return element == null || element.getItemStack().isEmpty();
	}

	/** {@code /ovvar look} with no name is a look at your own, and with a name resolves a player the server has seen. */
	@GameTest
	public void lookCommandOpensAReadOnlyWardrobe(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		var before = player.containerMenu;
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "ovvar look");
		if (player.containerMenu == before) helper.fail("/ovvar look did not open a menu");
		var own = player.containerMenu;
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "ovvar look " + player.getName().getString());
		if (player.containerMenu == own) helper.fail("/ovvar look <player> did not open a menu");
		// Whether a name resolves at all is the server's own business (its profile resolver): on an
		// online-mode server an unknown name is refused before this command runs, and on an
		// offline-mode one it resolves to an offline UUID, which simply has no wardrobe.
		helper.succeed();
	}

	/** {@code /ovvar stash} runs the command handler that opens the wardrobe screen (not the old StashGui). */
	@GameTest
	public void stashCommandOpensWardrobeGui(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		var before = player.containerMenu;
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "ovvar stash");
		if (player.containerMenu == before) helper.fail("/ovvar stash did not open a menu");
		helper.succeed();
	}

	// ---- the wardrobe mannequin

	/** One per player (a second click despawns the first); refused on a minigame server; the copy loses its bundle contents. */
	@GameTest
	public void wardrobeMannequinIsOneAtATimeAndDupeSafe(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		StashConfig stash = OvvarConfig.get().stash();
		try {
			ItemStack ovve = new ItemStack(ModContent.ovve(CHAPTER));
			OvveItem.setOwner(ovve, player.getUUID());
			// Through the ovve's own contents, not a bare `new BundleContents.Mutable()`: the bundle
			// mod's size factor rides along with the component, and a directly constructed Mutable
			// carries none at all (its weight maths then divides by a null factor).
			var bundle = ovve.getOrDefault(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,
					net.minecraft.world.item.component.BundleContents.EMPTY).asMutable();
			bundle.tryInsert(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
			ovve.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS, bundle.toImmutable());

			OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(),
					new StashConfig(true, stash.sewGameModes(), stash.ingameObjective(), stash.bankOnPickup(), stash.bankInCreative(),
							stash.unpickToStash(), stash.withdraw(), stash.sessions(), stash.stashClick(), stash.anyStand(),
							stash.sessionReach(), stash.sessionSeconds(), stash.explainInChat())));
			String refusal = WardrobeMannequin.show(player, ovve);
			if (refusal == null) helper.fail("no refusal on a minigame server");
			if (WardrobeMannequin.mannequinOf(player) != null) helper.fail("a mannequin was tracked despite the refusal");

			OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(), stash));
			String ok = WardrobeMannequin.show(player, ovve);
			if (ok != null) helper.fail("refused on a survival server: " + ok);
			var m = WardrobeMannequin.mannequinEntityOf(player);
			if (m == null) helper.fail("no mannequin tracked after show()");
			ItemStack worn = m.getItemBySlot(EquipmentSlot.LEGS);
			if (worn.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS)) helper.fail("the mannequin's ovve still carries BUNDLE_CONTENTS");
			if (!m.isPermanentlyInvulnerable()) helper.fail("the mannequin is not invulnerable");
			if (!m.entityTags().contains(WardrobeMannequin.TAG)) helper.fail("the mannequin is not tagged " + WardrobeMannequin.TAG);

			UUID beforeSecond = WardrobeMannequin.mannequinOf(player);
			WardrobeMannequin.show(player, ovve);
			var m2 = WardrobeMannequin.mannequinEntityOf(player);
			if (m2 == null) helper.fail("no mannequin tracked after the second show()");
			if (WardrobeMannequin.mannequinOf(player).equals(beforeSecond)) helper.fail("the second click reused the same entity instead of replacing it");
			if (!m.isRemoved()) helper.fail("the first mannequin was not discarded by the second click");
		} finally {
			OvvarConfig.modify(config -> new OvvarConfig(config.sewingMinigame(), config.stitches(), config.server(), config.designs(), stash));
		}
		helper.succeed();
	}

	// ---- the MOTD

	@GameTest
	public void motdNamesTheServerMode(GameTestHelper helper) {
		String survival = Motd.text("Testcraft", false);
		String minigame = Motd.text("Testcraft", true);
		if (!survival.startsWith("Testcraft ") || !minigame.startsWith("Testcraft ")) helper.fail("the MOTD does not name the server: " + survival + " / " + minigame);
		if (!survival.contains("Survival") || !survival.contains("sewing")) helper.fail("survival MOTD: " + survival);
		if (!minigame.contains("Minigame") || minigame.contains("sewing on stands")) helper.fail("minigame MOTD: " + minigame);
		if (!Motd.text("", false).startsWith(ServerConfig.DEFAULT.name())) helper.fail("a nameless server does not fall back on a name");
		OvvarConfig config = OvvarConfig.get();
		if (!Motd.text(config).equals(Motd.text(config.server().name(), config.stash().minigameServer()))) {
			helper.fail("this server's MOTD is not its config's: " + Motd.text(config));
		}
		helper.succeed();
	}

	/** A throwaway store back (never the run dir's configured one), and the next test may go. */
	private static void release(MinecraftServer server) {
		Wardrobes.use(server, idleBackend());
		BUSY.set(false);
	}


	// ---- per-size art

	/**
	 * A patch may ship its art at several sizes ({@code <id>_<w>x<h>.png} beside its own PNG), and
	 * <b>one</b> function says which of them a place shows: {@link Patches#artFor}. This is that
	 * decision, said against the real ITK — 12×12 in the catalogue, with an 8×8 and a 16×16 variant:
	 *
	 * <ul>
	 *   <li>on a shoulder the 8×8, because the art is <em>clipped</em> to a box's top face, so the
	 *	   variant is what lands there whole instead of losing its edges;
	 *   <li>on the big back cell the 16×16, filling a cell that is {@link Patches#MAX_ART} square
	 *	   rather than floating in the middle of it;
	 *   <li>on an ordinary chest cell the catalogue's own 12×12, hanging over its neighbours, which
	 *	   is the point of an oversize patch and exactly what it did before variants existed;
	 *   <li>in the inventory the 16×16, drawn at 1:1 instead of a 12×12 scaled up.
	 * </ul>
	 *
	 * <p>And a patch that has only the one art is not touched by any of it: every fit gives it that
	 * file, which is why the rest of the suite still pins the old behaviour.
	 */
	@GameTest
	public void patchArtComesInSizesAndOnePlacePicksBetweenThem(GameTestHelper helper) {
		Patches.Patch itk = Patches.get("itk"), it = Patches.get("it");
		List<String> files = itk.variants().stream().map(Patches.Art::file).toList();
		if (!files.equals(List.of("patches/itk_8x8", "patches/itk", "patches/itk_16x16"))) {
			helper.fail("itk's art files are " + files + ", wanted Kexana's 8x8, the catalogue's 12x12 and the 16x16 (smallest first)");
		}
		// IT is drawn at 12 and at 16 and nobody has drawn it at 8, so the 8×8 is scaled down here.
		List<String> itFiles = it.variants().stream().map(Patches.Art::file).toList();
		if (!itFiles.equals(List.of("patches/it_8x8", "patches/it", "patches/it_16x16"))) {
			helper.fail("it's art files are " + itFiles + ", wanted a generated 8x8, the catalogue's 12x12 and PolymITer's 16x16 (smallest first)");
		}
		for (Patches.Patch patch : Patches.all()) {
			int defaults = 0;
			for (Patches.Art art : patch.variants()) {
				if (art.byDefault()) defaults++;
				if (art.width() % 2 != 0 || art.height() % 2 != 0 || art.width() > Patches.MAX_ART || art.height() > Patches.MAX_ART) {
					helper.fail(art.file() + " is " + art.width() + "x" + art.height() + ", which is not an even size up to " + Patches.MAX_ART);
				}
				if (patch.seat() && art.width() != 2 * Spot.PX) helper.fail(art.file() + " is a seat patch's art but " + art.width() + " px wide");
				// A generated art ships no file at all — its pixels are its source's, scaled — so it is
				// the source that has to be on the classpath.
				String file = art.generated() ? art.source().resource() : art.resource();
				if (!has(file)) helper.fail(file + " is behind " + art + " in variants() but there is no such file");
				Tex tex = patchArt(art);
				if (tex.width != art.width() || tex.height != art.height()) {
					helper.fail(art + " is " + tex.width + "x" + tex.height + " of pixels, its name says " + art.width() + "x" + art.height());
				}
			}
			if (defaults != 1) helper.fail(patch.id() + " has " + defaults + " default art file(s) among " + patch.variants() + ", wanted exactly one");
		}
		// The choice, cell by cell, for the two patches that have more than one size — and, where a
		// patch has nothing that fits, the fall back to the catalogue's own art.
		Object[][] cases = {
				{itk, Spot.SHOULDER_R, 8, 8}, {itk, Spot.SHOULDER_L, 8, 8},
				{itk, Spot.BACK_BIG, Patches.MAX_ART, Patches.MAX_ART},
				{itk, Spot.FRONT_TOP_LEFT, itk.width(), itk.height()}, {itk, Spot.SLEEVE_OUT_TOP_R, itk.width(), itk.height()},
				{it, Spot.BACK_BIG, Patches.MAX_ART, Patches.MAX_ART},
				{it, Spot.SHOULDER_R, 8, 8}, {it, Spot.FRONT_TOP_LEFT, it.width(), it.height()},
		};
		for (Object[] c : cases) {
			Patches.Patch patch = (Patches.Patch) c[0];
			Spot spot = (Spot) c[1];
			Patches.Art chosen = Patches.artFor(patch, spot);
			if (chosen.width() != (Integer) c[2] || chosen.height() != (Integer) c[3]) {
				helper.fail(patch.id() + " on " + spot.id() + " is drawn as " + chosen + ", wanted " + c[2] + "x" + c[3]);
			}
		}
		// A cell that found art it can use must not cut it; one that did not still cuts, or hangs over.
		if (Patches.artFor(itk, Spot.SHOULDER_R).oversize(Spot.SHOULDER_R)) helper.fail("itk is still clipped on a shoulder");
		if (Patches.artFor(it, Spot.SHOULDER_R).oversize(Spot.SHOULDER_R)) helper.fail("it has a cell-sized art now, so a shoulder must not clip it");
		if (Patches.artFor(it, Spot.BACK_BIG).oversize(Spot.BACK_BIG)) helper.fail("it hangs over the big back cell, which its 16x16 fills exactly");
		if (Patches.artFor(itk, Spot.BACK_BIG).oversize(Spot.BACK_BIG)) helper.fail("itk hangs over the big back cell, which its 16x16 fills exactly");
		if (!Patches.artFor(itk, Spot.FRONT_TOP_LEFT).oversize(Spot.FRONT_TOP_LEFT)) helper.fail("itk no longer hangs over an ordinary chest cell");
		// The icon: the art at 16 px if the patch has one, else the fall-back it always was — the
		// catalogue's art scaled to fill 16 px and centred. Either way it is the generated texture
		// itself, so one piece of arithmetic says both.
		if (!Patches.iconArt(it).file().equals("patches/it_16x16")) helper.fail("it's icon draws " + Patches.iconArt(it) + ", not its 16 px art");
		if (!Patches.iconArt(itk).file().equals("patches/itk_16x16")) helper.fail("itk's icon draws " + Patches.iconArt(itk) + ", not its 16 px art");
		for (Patches.Patch patch : Patches.all()) {
			Patches.Art icon = Patches.iconArt(patch);
			Tex art = patchArt(icon);
			int scale = Math.max(1, Patches.ICON / Math.max(art.width, art.height));
			Tex drawn = itemTexture(metacraft.ovvar.content.ModContent.patchId(patch).getPath());
			if (drawn.width != Patches.ICON || drawn.height != Patches.ICON) helper.fail(patch.id() + "'s icon is not " + Patches.ICON + " square");
			Tex wanted = art.scale(scale);
			Tex got = drawn.crop((Patches.ICON - wanted.width) / 2, (Patches.ICON - wanted.height) / 2, wanted.width, wanted.height);
			if (!same(got, wanted)) helper.fail(patch.id() + "'s inventory icon is not its " + icon.file() + " art scaled x" + scale + " and centred");
		}
		// A patch with the one art: nothing about it changed.
		for (Patches.Patch patch : Patches.all()) {
			if (patch.variants().size() > 1) continue;
			for (Spot spot : Spot.values()) {
				if (!patch.fits(spot)) continue;
				Patches.Art chosen = Patches.artFor(patch, spot);
				if (!chosen.byDefault() || chosen.width() != patch.width() || chosen.height() != patch.height()) {
					helper.fail(patch.id() + " has one art but " + spot.id() + " draws " + chosen);
				}
			}
			if (!Patches.iconArt(patch).byDefault()) helper.fail(patch.id() + " has one art but its icon draws another");
		}
		helper.succeed();
	}

	/**
	 * The sizes nobody drew. A patch with a {@link Patches#MAX_ART} px art also has the smaller ones
	 * it ships no drawing of ({@link Patches#GENERATED_SIZES}), scaled down from it — so the artist's
	 * job is to draw the 16×16 and then draw again only the sizes the scaler gets wrong, and a
	 * drawing of a size always beats the scaling of it.
	 *
	 * <p>Nothing is written to the source tree: a generated art carries the art it comes from, has no
	 * classpath resource of its own, and is named like a variant all the same, because datagen names
	 * the textures and models it writes for an art by that name. Today this is IT alone — drawn at 12
	 * and at 16, so its 8×8 is scaled — while ITK, drawn at all three, generates nothing.
	 */
	@GameTest
	public void theSmallSizesNobodyDrewAreScaledDownFromTheSixteenPixelArt(GameTestHelper helper) {
		Patches.Patch it = Patches.get("it"), itk = Patches.get("itk");
		Patches.Art small = Patches.artFor(it, Patches.Fit.CLIPPED);
		if (!small.generated() || small.width() != Spot.PX || small.height() != Spot.PX) {
			helper.fail("a shoulder draws IT as " + small + ", wanted an 8x8 scaled down from its 16x16");
		}
		if (!small.file().equals("patches/it_8x8")) helper.fail("the generated art is called " + small.file() + ", not patches/it_8x8");
		if (small.byDefault()) helper.fail(small + " says it is the catalogue's own art");
		if (!small.source().file().equals("patches/it_16x16")) helper.fail(small + " is scaled from " + small.source() + ", not from IT's 16x16");
		if (!Patches.artFor(it, Spot.SHOULDER_R).equals(small)) helper.fail("the cell and the fit disagree about IT's shoulder art");
		// No file, and saying so out loud rather than naming a path nothing will ever be at.
		if (has("/art/" + metacraft.ovvar.Ovvar.MOD_ID + "/" + small.file() + ".png")) {
			helper.fail("somebody has drawn IT at 8x8, so that drawing should be what a shoulder shows");
		}
		try {
			small.resource();
			helper.fail(small + " answered a classpath resource, but nothing is written for a generated art");
		} catch (IllegalStateException expected) {
			// what it is for
		}
		// The pixels every path gets: the source scaled, and nothing about the source touched.
		if (!same(patchArt(small), patchArt(small.source()).downscaled(Spot.PX, Spot.PX))) {
			helper.fail("IT's generated 8x8 is not its 16x16 downscaled" + java.util.Arrays.toString(
					firstDifference(patchArt(small), patchArt(small.source()).downscaled(Spot.PX, Spot.PX))));
		}
		// A drawing wins: ITK is drawn at 8, 12 and 16, so it generates nothing at all.
		for (Patches.Art art : itk.variants()) {
			if (art.generated()) helper.fail("ITK is drawn at " + art.width() + "x" + art.height() + " but " + art + " was scaled anyway");
		}
		for (Patches.Patch patch : Patches.all()) {
			List<Patches.Art> made = patch.variants().stream().filter(Patches.Art::generated).toList();
			// A seat patch is the two cells' full width at every size it has, so a smaller one would
			// have nowhere to sit; a patch with nothing drawn at 16 px has nothing to scale down.
			boolean sixteen = patch.variants().stream().anyMatch(a -> a.width() == Patches.MAX_ART && a.height() == Patches.MAX_ART);
			if (patch.seat() && !made.isEmpty()) helper.fail(patch.id() + " is a seat patch and got " + made);
			if (!sixteen && !made.isEmpty()) helper.fail(patch.id() + " has no 16 px art but got " + made);
			for (Patches.Art art : made) {
				if (art.width() != art.height() || !Patches.GENERATED_SIZES.contains(art.width())) {
					helper.fail(art + " is not one of the sizes that are generated, " + Patches.GENERATED_SIZES);
				}
				if (art.source().width() != Patches.MAX_ART) helper.fail(art + " is scaled from " + art.source() + ", not from a 16 px art");
				long atSize = patch.variants().stream().filter(a -> a.width() == art.width() && a.height() == art.height()).count();
				if (atSize != 1) helper.fail(patch.id() + " has " + atSize + " arts at " + art.width() + "x" + art.height() + ", one of them scaled");
			}
			// Every size a Fit can ask for is there once a patch is drawn at 16 px, which is the whole
			// point: no cell has to fall back to art that does not fit it any more.
			if (sixteen && !patch.seat()) {
				for (int size : Patches.GENERATED_SIZES) {
					Patches.Art at = Patches.artFor(patch, size == Spot.PX ? Patches.Fit.CLIPPED : Patches.Fit.OVER);
					if (at.width() > size || at.height() > size) helper.fail(patch.id() + " is drawn at 16 px but a " + size + " px place still shows " + at);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * The scaling itself ({@link Tex#downscaled}): an area-weighted majority vote, so the result uses
	 * no colour the source did not — which the trim channel needs (its key palette is built from
	 * every opaque colour of every patch art, and a blended edge would be a colour with no slot) and
	 * pixel art wants anyway. The rules that are easy to get wrong, on images small enough to read:
	 *
	 * <ul>
	 *   <li>an even split goes to the colour that is <b>rarer</b> in the whole source, not to the one
	 *	   that comes first — the background always has the votes, so an outline, an eye or a letter
	 *	   stroke only survives if a tie falls its way;
	 *   <li>every invisible pixel votes as the same nothing, whatever colour it was written in, and a
	 *	   block that votes for nothing comes out fully clear;
	 *   <li>and the whole thing against the real ITK, whose 12 and 8 px results are pinned here texel
	 *	   for texel: this is the art the algorithm was chosen on, so a change to it that nobody meant
	 *	   shows up as this test rather than as a patch that looks slightly wrong on a shoulder.
	 * </ul>
	 */
	@GameTest
	public void theDownscaleIsAnAreaVoteThatKeepsTheRareColours(GameTestHelper helper) {
		int p = 0xFF9B59B6, q = 0xFF2ECC71, clearRed = 0x00FF0000;
		Map<Character, Integer> two = Map.of('p', p, 'q', q);
		// Each half is a 2x2 block of two p and two q. p is the rarer colour of the two (twice against
		// six) and q is the one that comes first, so a vote by scan order would answer "qq" instead.
		Tex tie = pixels(two, "qpqq", "pqqq").downscaled(2, 1);
		if (tie.get(0, 0) != p || tie.get(1, 0) != q) helper.fail("an even split went to the commoner colour, so thin art will dissolve");
		Map<Character, Integer> clear = Map.of('.', 0, 'x', clearRed, 'p', p);
		// Left block: three invisible pixels against one opaque, in two different clear colours, which
		// must vote as the one nothing. Right block: two against two, and the opaque one is rarer.
		Tex alpha = pixels(clear, "...p", "xpp.").downscaled(2, 1);
		if (alpha.get(0, 0) != 0) helper.fail("three clear pixels against one lost the vote, or came out as a colour: " + Integer.toHexString(alpha.get(0, 0)));
		if (alpha.get(1, 0) != p) helper.fail("an even split with transparency went to the transparency, which is the commoner of the two");
		// The real art: at both generated sizes, and never a colour that was not already there.
		Map<Character, Integer> itk = Map.of('.', 0, 'a', 0xFF00FF00, 'b', 0xFF000000, 'c', 0xFFADFF5C, 'd', 0xFF004100, 'e', 0xFF00CD00);
		Tex source = patchArt(Patches.get("itk").variants().stream().filter(a -> a.width() == Patches.MAX_ART).findFirst().orElseThrow());
		Tex twelve = pixels(itk,
				"....aaaa....",
				"..abbbbbba..",
				".abbbccbbba.",
				".bbccccccbb.",
				"abcaaaaaacba",
				"abcadaadacba",
				"accaaaaaacca",
				"abcaaddaacba",
				".bccaddaabb.",
				".abeaaaaeba.",
				"..abbbbbba..",
				"....aaaa....");
		Tex eight = pixels(itk,
				"..abba..",
				".bbccbb.",
				"abccccba",
				"bcaddacb",
				"bcaaaacb",
				"accddaba",
				".bbbbbb.",
				"..abba..");
		for (Tex want : List.of(twelve, eight)) {
			Tex got = source.downscaled(want.width, want.height);
			if (!same(got, want)) {
				helper.fail("ITK scaled to " + want.width + "x" + want.height + " is not what the algorithm was chosen for"
						+ java.util.Arrays.toString(firstDifference(got, want)));
			}
		}
		for (Patches.Patch patch : Patches.all()) {
			for (Patches.Art art : patch.variants()) {
				if (art.width() != Patches.MAX_ART || art.height() != Patches.MAX_ART) continue;
				List<Integer> had = new ArrayList<>(List.of(0));
				Tex full = patchArt(art);
				for (int i : full.pixels()) if (!had.contains(i)) had.add(i);
				for (int size : Patches.GENERATED_SIZES) {
					for (int shrunk : full.downscaled(size, size).pixels()) {
						if (!had.contains(shrunk)) helper.fail(art + " scaled to " + size + " px invented the colour " + Integer.toHexString(shrunk));
					}
				}
			}
		}
		helper.succeed();
	}

	/** A tiny image written out a character per pixel, the characters keyed by {@code palette}. */
	private static Tex pixels(Map<Character, Integer> palette, String... rows) {
		int w = rows[0].length();
		int[] argb = new int[w * rows.length];
		for (int y = 0; y < rows.length; y++) {
			for (int x = 0; x < w; x++) argb[y * w + x] = palette.get(rows[y].charAt(x));
		}
		return Tex.of(w, rows.length, argb);
	}

	/**
	 * The three paths agree about which art a cell shows, which is the whole point of there being
	 * one function. The pack path and the doll are checked against the art everywhere else in this
	 * suite; this is the <b>instant</b> path, which cannot ask a question at all — the dye colour
	 * carries a cell and a design, so the preview texture's design table holds a row per (design,
	 * {@link Patches.Fit}) and the shader reads the row for the fit of the cell it is drawing.
	 *
	 * <p>Read the way the shader reads it: the table positions come out of {@code ovvar.glsl}'s own
	 * constants, so a table that moved in datagen without moving in the shader fails here rather
	 * than on somebody's screen. Then every row must name the art {@link Patches#artFor} picks for
	 * that fit, and the library block it points at must be that art, pixel for pixel.
	 */
	@GameTest
	public void theInstantLibraryHoldsEveryArtADesignCanBeDrawnAs(GameTestHelper helper) {
		String glsl = resource("/assets/ovvar/shaders/include/ovvar.glsl");
		double tableX = shaderConst(helper, glsl, "OVVAR_TABLE_X"), columns = shaderConst(helper, glsl, "OVVAR_TABLE_COLUMNS");
		double slots = shaderConst(helper, glsl, "OVVAR_FIT_SLOTS");
		for (Patches.Fit fit : Patches.Fit.values()) {
			double declared = shaderConst(helper, glsl, "OVVAR_FIT_" + fit.name());
			if (declared != fit.ordinal()) helper.fail("ovvar.glsl calls " + fit + " fit " + declared + ", Patches.Fit says " + fit.ordinal());
		}
		if (Looks.INSTANT_DESIGNS > slots) helper.fail(Looks.INSTANT_DESIGNS + " designs do not fit " + slots + " slots per fit");
		int rows = 0;
		for (Piece piece : Piece.values()) {
			Tex preview = generated(piece, "patch/preview_" + piece.id);
			for (Patches.Patch patch : Patches.all()) {
				int design = Patches.code(patch) - 1;
				if (design >= Looks.INSTANT_DESIGNS) continue;   // never in the dye colour, so never in the library
				for (Patches.Fit fit : Patches.Fit.values()) {
					int slot = design + fit.ordinal() * (int) slots;
					int at = (int) tableX + 2 * (int) columns + slot / 16, size = (int) tableX + 3 * (int) columns + slot / 16;
					int entry = preview.get(at, slot % 16), sizes = preview.get(size, slot % 16);
					Patches.Art want = Patches.artFor(patch, fit);
					Tex art = patchArt(want);
					if (Tex.r(sizes) != want.width() || Tex.g(sizes) != want.height()) {
						helper.fail(patch.id() + " (" + fit + ") is " + Tex.r(sizes) + "x" + Tex.g(sizes) + " in the " + piece
								+ " design table, but " + fit + " draws " + want);
					}
					int x = Tex.r(entry), y = Tex.g(entry);
					Tex block = preview.crop(x, y, art.width, art.height);
					if (!same(block, art)) {
						helper.fail(patch.id() + " (" + fit + "): the " + piece + " library at (" + x + ", " + y + ") is not "
								+ want.file() + "'s art" + java.util.Arrays.toString(firstDifference(block, art)));
					}
					rows++;
				}
			}
		}
		if (rows == 0) helper.fail("no design row was checked at all");
		// And the rule the shader's three-line expression states, which is Patches.Fit.of's: the art
		// is clipped to a box's top face, a cell bigger than one cell is filled (never the seat,
		// whose two cells wear one patch drawn to their own size), everything else hangs over.
		for (Spot spot : Spot.values()) {
			boolean big = !spot.top() && spot.side != Spot.Side.SEAT && (spot.px() > Spot.PX || spot.pxHeight() > Spot.PX);
			Patches.Fit shader = spot.top() ? Patches.Fit.CLIPPED : big ? Patches.Fit.FILLED : Patches.Fit.OVER;
			if (Patches.Fit.of(spot) != shader) helper.fail(spot.id() + " is " + Patches.Fit.of(spot) + " here and " + shader + " in the shader");
			// The fits' own boxes: CLIPPED assumes a top cell is one cell square and FILLED that a
			// filled one is MAX_ART square, since the instant path keys the library by fit alone.
			if (spot.top() && (spot.px() != Spot.PX || spot.pxHeight() != Spot.PX)) {
				helper.fail(spot.id() + " is a clipped cell " + spot.px() + "x" + spot.pxHeight() + " px; the instant path's CLIPPED fit is one cell square");
			}
			if (big && (spot.px() != Patches.MAX_ART || spot.pxHeight() != Patches.MAX_ART)) {
				helper.fail(spot.id() + " is a filled cell " + spot.px() + "x" + spot.pxHeight() + " px; the instant path's FILLED fit is " + Patches.MAX_ART + " square");
			}
			for (Patches.Patch patch : Patches.all()) {
				if (patch.fits(spot) && !Patches.artFor(patch, spot).equals(Patches.artFor(patch, Patches.Fit.of(spot)))) {
					helper.fail(patch.id() + " on " + spot.id() + " is drawn as " + Patches.artFor(patch, spot)
							+ " by the cell and as " + Patches.artFor(patch, Patches.Fit.of(spot)) + " by its fit alone");
				}
			}
		}
		helper.succeed();
	}

	/** A {@code const float} of {@code ovvar.glsl}: a number, or a number times OVVAR_D. */
	private static double shaderConst(GameTestHelper helper, String glsl, String name) {
		var matcher = java.util.regex.Pattern.compile(name + "\\s*=\\s*([0-9.]+)(\\s*\\*\\s*OVVAR_D)?\\s*;").matcher(glsl);
		if (!matcher.find()) {
			helper.fail("ovvar.glsl no longer declares " + name + ", so nothing holds the shader to the tables datagen writes");
			return -1;
		}
		return Double.parseDouble(matcher.group(1)) * (matcher.group(2) == null ? 1 : Spot.DETAIL);
	}

	/** A generated item texture (a patch's inventory icon), off the runtime classpath. */
	private static Tex itemTexture(String name) {
		String path = "/assets/" + metacraft.ovvar.Ovvar.MOD_ID + "/textures/item/" + name + ".png";
		try (var in = WardrobeTests.class.getResourceAsStream(path)) {
			if (in == null) throw new IOException("missing " + path + " — run ./gradlew runDatagen");
			return Tex.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void assertThat(boolean condition, String message) {
		if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
	}
}
