package se.metacraft.ovvar.gametest;

import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import de.tomalbrc.bil.core.model.Animation;
import de.tomalbrc.bil.core.model.Frame;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.core.model.Node;
import de.tomalbrc.bil.core.model.Pose;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WanderingTraderData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.serialization.JsonOps;
import se.metacraft.ovvar.PunkphozConfig;
import se.metacraft.ovvar.PunkphozConfig.PoolEntry;
import se.metacraft.ovvar.content.Chapter;
import se.metacraft.ovvar.content.ModContent;
import se.metacraft.ovvar.content.PatchLoot;
import se.metacraft.ovvar.content.Patches;
import se.metacraft.ovvar.content.entity.Punkphoz;
import se.metacraft.ovvar.content.entity.PunkphozAnimations;
import se.metacraft.ovvar.content.entity.PunkphozEntities;
import se.metacraft.ovvar.content.entity.PunkphozHistory;
import se.metacraft.ovvar.content.entity.PunkphozSpawning;
import se.metacraft.ovvar.content.entity.PunkphozStock;
import se.metacraft.ovvar.content.enchantment.EnchantmentsConfig;
import se.metacraft.ovvar.content.enchantment.OvveEnchantments;
import se.metacraft.ovvar.content.enchantment.SpilledDrink;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Punkphöz: a wandering trader in the villager rig with Dante's look, who walks and shakes his head like a villager and drinks no invisibility potion. */
public final class PunkphozTests {

	@GameTest
	public void punkphozIsAWanderingTrader(GameTestHelper helper) {
		var punkphoz = PunkphozEntities.PUNKPHOZ.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (!(punkphoz instanceof WanderingTrader)) helper.fail("Punkphöz is not a wandering trader: " + punkphoz);
		helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(PunkphozEntities.PUNKPHOZ),
				Identifier.fromNamespaceAndPath("ovvar", "punkphoz"), "Punkphöz's entity id");
		helper.succeed();
	}

	/**
	 * The model is the vanilla villager rig (head > hat, nose, beanie, antenna; body > jacket; arms; legs) with the walk and the
	 * shake, bundled once: it is ovvar's own file, no longer a copy of the traders' model made at build time.
	 */
	@GameTest
	public void modelIsTheVillagerRig(GameTestHelper helper) {
		try {
			List<URL> copies = Collections.list(Punkphoz.class.getClassLoader().getResources("model/ovvar/punkphoz.bbmodel"));
			helper.assertValueEqual(copies.size(), 1, "copies of model/ovvar/punkphoz.bbmodel on the classpath " + copies);
		} catch (IOException e) {
			helper.fail("listing the model: " + e);
		}
		Model model = PunkphozEntities.model();
		if (model == null) {
			helper.fail("PunkphozEntities.model() is null");
			return;
		}
		Map<String, Node> nodes = new HashMap<>();
		for (Node node : model.nodeMap().values()) nodes.put(node.name(), node);
		for (String name : List.of("head", "hat", "nose", "beanie", "antenna", "body", "jacket", "arms", "right_leg", "left_leg")) {
			if (!nodes.containsKey(name)) helper.fail("the model has no bone " + name + ", only " + nodes.keySet());
		}
		helper.assertValueEqual(nodes.get("head").tag(), Node.NodeTag.HEAD, "the head's tag (BIL turns it with his look)");
		for (String name : List.of("hat", "nose", "beanie", "antenna")) {
			helper.assertValueEqual(nodes.get(name).parent(), nodes.get("head"), name + "'s parent");
			helper.assertValueEqual(nodes.get(name).tag(), Node.NodeTag.HEAD_CHILD, name + "'s tag (turns with the head)");
		}
		helper.assertValueEqual(nodes.get("jacket").parent(), nodes.get("body"), "the jacket's parent");
		Animation idle = model.animations().get(PunkphozAnimations.IDLE);
		Animation walk = model.animations().get(PunkphozAnimations.WALK);
		Animation shake = model.animations().get(PunkphozAnimations.SHAKE);
		if (idle == null || walk == null || shake == null) {
			helper.fail("the model's animations are " + model.animations().keySet() + ", not idle, walk and shake");
			return;
		}
		helper.assertValueEqual(walk.duration(), PunkphozAnimations.SPEED_BANDS * PunkphozAnimations.STRIDE_FRAMES, "walk frames");
		// four periods of the roll, 0.45 rad a tick: 4 * 2 pi / 0.45 = 55.85 ticks
		helper.assertValueEqual(shake.duration(), 56, "shake frames");
		helper.succeed();
	}

	/**
	 * His hands are behind his back, as in Dante's traders model: the arms bone pivots at (0, 21.05, -0.05) and is tipped -47°
	 * about x, and no animation moves it, so every frame of idle, walk and shake holds the arms at their rest pose.
	 */
	@GameTest
	public void handsStayBehindHisBack(GameTestHelper helper) {
		Model model = PunkphozEntities.model();
		if (model == null) {
			helper.fail("PunkphozEntities.model() is null");
			return;
		}
		Node arms = null;
		for (Node node : model.nodeMap().values()) if (node.name().equals("arms")) arms = node;
		if (arms == null) {
			helper.fail("the model has no arms bone");
			return;
		}
		// BIL keeps the file's rotation in degrees and the pivot in blocks (pixels / 16)
		Vector3fc rotation = arms.transform().rotation();
		if (!rotation.equals(new Vector3f(-47, 0, 0), 1e-3f)) {
			helper.fail("the arms bone's rotation is " + rotation + ", not -47° about x (hands behind his back)");
		}
		Vector3fc origin = arms.transform().origin();
		if (!origin.equals(new Vector3f(0, 21.05f, -0.05f).div(16), 1e-5f)) {
			helper.fail("the arms bone's pivot is " + origin + ", not (0, 21.05, -0.05) / 16");
		}
		Pose rest = model.defaultPose().get(arms.uuid());
		for (String name : List.of(PunkphozAnimations.IDLE, PunkphozAnimations.WALK, PunkphozAnimations.SHAKE)) {
			Animation animation = model.animations().get(name);
			if (animation == null) {
				helper.fail("the model has no animation " + name);
				return;
			}
			for (Frame frame : animation.frames()) {
				Pose pose = frame.poses().get(arms.uuid());
				if (pose == null) continue;
				if (rest == null || !pose.readOnlyTranslation().equals(rest.readOnlyTranslation(), 1e-4f)
						|| !pose.readOnlyLeftRotation().equals(rest.readOnlyLeftRotation(), 1e-4f)
						|| !pose.readOnlyRightRotation().equals(rest.readOnlyRightRotation(), 1e-4f)
						|| !pose.readOnlyScale().equals(rest.readOnlyScale(), 1e-4f)) {
					helper.fail(name + " moves the arms at " + frame.time() + ": " + pose + ", rest " + rest);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Polymer's pack builds with BIL's parts of his model: his texture, and for each bone with cubes (ten: head, hat, nose, beanie,
	 * antenna, body, jacket, arms and the legs) an item asset and its model, and nothing more under his name.
	 */
	@GameTest(maxTicks = 400)
	public void packCarriesHisModelOnce(GameTestHelper helper) {
		Set<String> entries = new HashSet<>();
		try {
			Path pack = Files.createTempFile("ovvar-punkphoz-pack", ".zip");
			try {
				// hadIssues covers every mod's assets, so it is not judged here; his entries are
				PolymerResourcePackUtils.getInstance().build(pack);
				try (ZipFile zip = new ZipFile(pack.toFile())) {
					zip.stream().map(ZipEntry::getName).filter(n -> n.contains("punkphoz") && !n.endsWith("/")).forEach(entries::add);
				}
			} finally {
				Files.deleteIfExists(pack);
			}
		} catch (IOException | ExecutionException | InterruptedException e) {
			helper.fail("building the pack: " + e);
			return;
		}
		if (!entries.contains("assets/bil/textures/item/punkphoz/punkphoz.png")) helper.fail("no texture in the pack's punkphoz entries " + entries);
		for (Node node : PunkphozEntities.model().nodeMap().values()) {
			if (node.modelData() == null) continue;
			// BIL's ids: item bil:punkphoz_punkphoz_<bone uuid>, its model bil:item/punkphoz/punkphoz_<bone uuid>
			String item = "assets/bil/items/" + node.modelData().getPath() + ".json";
			String part = "assets/bil/models/item/punkphoz/" + node.modelData().getPath().substring("punkphoz_".length()) + ".json";
			if (!entries.contains(item) || !entries.contains(part)) {
				helper.fail("the " + node.name() + " bone lacks " + item + " or " + part + " in " + entries);
			}
		}
		long models = entries.stream().filter(n -> n.startsWith("assets/bil/models/item/punkphoz/") && n.endsWith(".json")).count();
		helper.assertValueEqual(models, 10L, "item models under bil:punkphoz, one per bone");
		helper.succeed();
	}

	/** walkFrame: the speed band, then the stride phase of walkAnimationPos * 0.6662 within it. */
	@GameTest
	public void walkFrameFollowsSpeedAndStride(GameTestHelper helper) {
		float halfStride = (float) (Math.PI / 0.6662);
		helper.assertValueEqual(PunkphozAnimations.walkFrame(1f, 0f), 180, "full speed, stride start");
		helper.assertValueEqual(PunkphozAnimations.walkFrame(1f, halfStride), 190, "full speed, half a stride");
		helper.assertValueEqual(PunkphozAnimations.walkFrame(0.5f, halfStride / 2), 85, "half speed, a quarter stride");
		helper.assertValueEqual(PunkphozAnimations.walkFrame(0.06f, 2 * halfStride), 0, "slowest band, a whole stride");
		helper.assertValueEqual(PunkphozAnimations.walkFrame(3f, 0f), 180, "faster than 1 is the top band");
		helper.succeed();
	}

	/** Moving, he plays the walk; once his walk speed has run down he stands in the rest pose again (his goals off: no stroll). */
	@GameTest(maxTicks = 60)
	public void walksWhileMoving(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		goals(punkphoz).removeAllGoals(g -> true);
		punkphoz.walkAnimation.setSpeed(1f);
		helper.runAfterDelay(1, () -> {
			if (!punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.WALK)) {
				helper.fail("no walk at walk speed " + punkphoz.walkAnimation.speed());
			}
		});
		helper.runAfterDelay(30, () -> {
			if (punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.WALK)) {
				helper.fail("still walking at walk speed " + punkphoz.walkAnimation.speed());
			}
			if (!punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.IDLE)) helper.fail("no idle under the walk: BIL would stop posing him");
			helper.succeed();
		});
	}

	/** Asked to trade while sold out, he shakes his head for the villager's 40 ticks, looking level, then stops. */
	@GameTest(maxTicks = 100)
	public void soldOutShakesHisHead(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		punkphoz.getOffers().forEach(MerchantOffer::setToOutOfStock);
		punkphoz.mobInteract(helper.makeMockServerPlayerInLevel(), InteractionHand.MAIN_HAND);
		helper.assertValueEqual(punkphoz.getUnhappyCounter(), Punkphoz.UNHAPPY_TICKS, "the unhappy counter after a sold-out ask");
		helper.runAfterDelay(2, () -> {
			if (!punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.SHAKE)) helper.fail("no shake while unhappy");
			helper.assertValueEqual(punkphoz.getXRot(), 0f, "his look's pitch while shaking");
		});
		helper.runAfterDelay(Punkphoz.UNHAPPY_TICKS + 5, () -> {
			helper.assertValueEqual(punkphoz.getUnhappyCounter(), 0, "the unhappy counter after the shake");
			if (punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.SHAKE)) helper.fail("still shaking after the counter ran out");
			if (!punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.IDLE)) helper.fail("no idle after the shake: his head would stay tilted");
			helper.succeed();
		});
	}

	/** With something left to sell he trades without a shake. */
	@GameTest
	public void inStockNoShake(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		punkphoz.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE), 1, 0, 0.05f));
		punkphoz.mobInteract(helper.makeMockServerPlayerInLevel(), InteractionHand.MAIN_HAND);
		helper.assertValueEqual(punkphoz.getUnhappyCounter(), 0, "the unhappy counter after asking a stocked Punkphöz");
		helper.runAfterDelay(2, () -> {
			if (punkphoz.getHolder().getAnimator().isPlaying(PunkphozAnimations.SHAKE)) helper.fail("shaking with stock to sell");
			helper.succeed();
		});
	}

	@GameTest
	public void noInvisibilityPotion(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		List<Goal> goals = goals(punkphoz).getAvailableGoals().stream().map(WrappedGoal::getGoal).toList();
		if (goals.stream().anyMatch(g -> g instanceof UseItemGoal)) helper.fail("Punkphöz still has a UseItemGoal (a potion or milk)");
		if (goals.stream().noneMatch(g -> g instanceof TradeWithPlayerGoal)) helper.fail("Punkphöz has no TradeWithPlayerGoal");
		if (goals.stream().noneMatch(g -> g.getClass().getSimpleName().equals("WanderToPositionGoal"))) {
			helper.fail("Punkphöz has no WanderToPositionGoal");
		}
		helper.succeed();
	}

	@GameTest
	public void leavesLikeAWanderer(GameTestHelper helper) {
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		punkphoz.setDespawnDelay(5);
		helper.runAfterDelay(20, () -> {
			if (punkphoz.isAlive()) helper.fail("Punkphöz is still here after his despawn delay ran out");
			helper.succeed();
		});
	}

	/** His offers, a trade made on one of them, survive a save and load; loading him is not a visit. */
	@GameTest
	public void stockSurvivesSaveAndLoad(GameTestHelper helper) {
		var level = helper.getLevel();
		Punkphoz punkphoz = helper.spawn(PunkphozEntities.PUNKPHOZ, new BlockPos(1, 1, 1));
		MerchantOffers before = punkphoz.getOffers();
		if (before.isEmpty()) {
			helper.fail("Punkphöz has no stock to save");
			return;
		}
		before.getFirst().increaseUses();
		int visits = PunkphozHistory.get(level.getServer()).visits();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		punkphoz.saveWithoutId(output);
		CompoundTag saved = output.buildResult();

		Punkphoz loaded = PunkphozEntities.PUNKPHOZ.create(level, EntitySpawnReason.LOAD);
		if (loaded == null) {
			helper.fail("no fresh Punkphöz to load into");
			return;
		}
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		helper.assertValueEqual(describe(loaded.getOffers()), describe(before), "the offers after a save and load");
		helper.assertValueEqual(loaded.getOffers().getFirst().getUses(), 1, "the traded offer's uses after a save and load");
		helper.assertValueEqual(PunkphozHistory.get(level.getServer()).visits(), visits, "visits after loading him");
		helper.succeed();
	}

	private static final List<PoolEntry> SIX = List.of(entry("minecraft:apple", 1), entry("minecraft:bread", 2),
			entry("minecraft:diamond", 3), entry("minecraft:saddle", 4), entry("minecraft:name_tag", 5), entry("minecraft:cake", 6));

	private static PunkphozConfig withCounts(int chapterOvves, int poolPicks) {
		var d = PunkphozConfig.DEFAULT;
		return new PunkphozConfig(d.enabled(), d.share(), chapterOvves, d.ovvePrice(), d.patches(), d.patchPrice(),
				poolPicks, d.playersPerRoll(), d.maxLoaded(), List.of());
	}

	private static Set<String> lootIds() {
		Set<String> ids = new HashSet<>();
		for (Patches.Patch patch : PatchLoot.looted()) ids.add(BuiltInRegistries.ITEM.getKey(ModContent.patchItem(patch)).toString());
		return ids;
	}

	private static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static Chapter chapterOf(ItemStack stack) {
		for (Chapter chapter : Chapter.values()) if (stack.is(ModContent.ovve(chapter))) return chapter;
		return null;
	}

	@GameTest
	public void rollHasExactCounts(GameTestHelper helper) {
		var stock = PunkphozStock.roll(PunkphozConfig.DEFAULT, SIX, RandomSource.create(42), new PunkphozHistory());
		helper.assertValueEqual(stock.size(), 9, "offers");
		Set<Chapter> chapters = EnumSet.noneOf(Chapter.class);
		for (var s : stock.subList(0, 3)) {
			Chapter chapter = chapterOf(s.result());
			if (chapter == null || !PunkphozStock.EXCLUSIVE.contains(chapter)) helper.fail("not an exclusive ovve: " + id(s.result()));
			chapters.add(chapter);
			helper.assertValueEqual(s.emeralds(), 24, "an ovve's price");
			helper.assertValueEqual(s.maxUses(), 1, "an ovve's max uses");
		}
		helper.assertValueEqual(chapters.size(), 3, "distinct chapters");
		Set<String> loot = lootIds(), patches = new HashSet<>();
		for (var s : stock.subList(3, 5)) {
			if (!loot.contains(id(s.result()))) helper.fail("not a loot patch: " + id(s.result()));
			patches.add(id(s.result()));
			helper.assertValueEqual(s.emeralds(), 6, "a patch's price");
		}
		helper.assertValueEqual(patches.size(), 2, "distinct patches");
		Map<String, Integer> prices = new HashMap<>();
		for (PoolEntry e : SIX) prices.put(e.item().toString(), e.price());
		Set<String> picks = new HashSet<>();
		for (var s : stock.subList(5, 9)) {
			Integer price = prices.get(id(s.result()));
			if (price == null) helper.fail("not from the pool: " + id(s.result()));
			helper.assertValueEqual(s.emeralds(), price, "the price of " + id(s.result()));
			picks.add(id(s.result()));
		}
		helper.assertValueEqual(picks.size(), 4, "distinct pool picks");
		helper.succeed();
	}

	/** Does any of these offers sell the Curse of the Spilled Drink's book (stored level 1) for 16 emeralds? */
	private static boolean sellsCurseBook(GameTestHelper helper, List<PunkphozStock.Stocked> stock) {
		var curse = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(OvveEnchantments.SPILLED_DRINK_CURSE);
		for (PunkphozStock.Stocked s : stock) {
			var stored = s.result().get(DataComponents.STORED_ENCHANTMENTS);
			if (!s.result().is(Items.ENCHANTED_BOOK) || stored == null || stored.getLevel(curse) == 0) continue;
			helper.assertValueEqual(stored.getLevel(curse), 1, "the curse book's level");
			helper.assertValueEqual(s.emeralds(), SpilledDrink.WANDERER_PRICE, "the curse book's price");
			helper.assertValueEqual(s.maxUses(), 1, "the curse book's max uses");
			return true;
		}
		return false;
	}

	/** How many of 200 seeded visits sell the curse book, with the pool SIX and these extras. */
	private static int visitsWithCurseBook(GameTestHelper helper, List<PunkphozStock.Stocked> extras) {
		PunkphozHistory history = new PunkphozHistory();
		int visits = 0;
		for (int seed = 0; seed < 200; seed++) {
			if (sellsCurseBook(helper, PunkphozStock.roll(PunkphozConfig.DEFAULT, SIX, extras, RandomSource.create(seed), history))) visits++;
		}
		return visits;
	}

	/**
	 * The curse book is one of his pool picks only where Brewery is installed (the very extras his updateTrades uses,
	 * {@code OvveEnchantments.BREWERY}); without Brewery, or with the curse switched off, never.
	 */
	@GameTest
	public void wandererSellsTheCurseBook(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		var real = PunkphozStock.extras(registries, EnchantmentsConfig.DEFAULT, OvveEnchantments.BREWERY);
		helper.assertValueEqual(real.size(), OvveEnchantments.BREWERY ? 1 : 0, "extras with Brewery " + (OvveEnchantments.BREWERY ? "installed" : "absent"));
		int withBook = visitsWithCurseBook(helper, real);
		if (OvveEnchantments.BREWERY) {
			// 4 picks from 7 candidates: about 4/7 of visits.
			helper.assertTrue(withBook > 60 && withBook < 170, "visits selling the curse book with Brewery: " + withBook + " of 200");
		} else {
			helper.assertValueEqual(withBook, 0, "visits selling the curse book without Brewery");
		}
		var noBrewery = PunkphozStock.extras(registries, EnchantmentsConfig.DEFAULT, false);
		helper.assertValueEqual(noBrewery.size(), 0, "extras forced without Brewery");
		helper.assertValueEqual(visitsWithCurseBook(helper, noBrewery), 0, "visits selling the curse book forced without Brewery");
		EnchantmentsConfig off = new EnchantmentsConfig(EnchantmentsConfig.Toggle.ON, EnchantmentsConfig.Patchwork.DEFAULT, EnchantmentsConfig.Magpie.DEFAULT,
				EnchantmentsConfig.Toggle.ON, EnchantmentsConfig.Keyring.DEFAULT, new EnchantmentsConfig.SpilledDrink(false, 100, 0.5f, 0.5f, 200));
		helper.assertValueEqual(PunkphozStock.extras(registries, off, true).size(), 0, "extras with the curse switched off");
		helper.succeed();
	}

	@GameTest
	public void patchesOnlyFromLoot(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7);
		Set<String> nonLoot = new HashSet<>();
		for (String patch : PatchLoot.NON_LOOT) nonLoot.add("ovvar:patch_" + patch);
		Set<String> loot = lootIds();
		for (int i = 0; i < 2000; i++) {
			for (var s : PunkphozStock.roll(withCounts(0, 0), List.of(), random, new PunkphozHistory())) {
				String id = id(s.result());
				if (!loot.contains(id) || nonLoot.contains(id)) {
					helper.fail("a patch not from PatchLoot.LOOT: " + id);
					return;
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void chapterPicksFavourTheLongestWaiting(GameTestHelper helper) {
		Chapter waiting = PunkphozStock.EXCLUSIVE.iterator().next();
		Map<String, Integer> last = new HashMap<>();
		for (Chapter chapter : PunkphozStock.EXCLUSIVE) if (chapter != waiting) last.put(chapter.id, 9);
		RandomSource random = RandomSource.create(42);
		int picked = 0;
		for (int i = 0; i < 1000; i++) {
			var stock = PunkphozStock.roll(withCounts(1, 0), List.of(), random, new PunkphozHistory(10, last));
			if (stock.getFirst().result().is(ModContent.ovve(waiting))) picked++;
		}
		if (picked < 400) helper.fail(waiting.id + " (never offered) picked only " + picked + " of 1000 times");
		helper.succeed();
	}

	@GameTest
	public void historyRecordsTheVisit(GameTestHelper helper) {
		PunkphozHistory history = new PunkphozHistory();
		var stock = PunkphozStock.roll(PunkphozConfig.DEFAULT, List.of(), RandomSource.create(42), history);
		helper.assertValueEqual(history.visits(), 1, "visits");
		int offered = 0;
		for (Chapter chapter : PunkphozStock.EXCLUSIVE) {
			boolean inStock = stock.stream().anyMatch(s -> s.result().is(ModContent.ovve(chapter)));
			helper.assertValueEqual(history.lastOffered(chapter), inStock ? 0 : -1, "lastOffered(" + chapter.id + ")");
			if (inStock) offered++;
		}
		helper.assertValueEqual(offered, 3, "chapters offered");
		helper.succeed();
	}

	@GameTest
	public void fewerExclusivesThanAsked(GameTestHelper helper) {
		var stock = PunkphozStock.roll(withCounts(99, 0), List.of(), RandomSource.create(42), new PunkphozHistory());
		Set<Chapter> chapters = EnumSet.noneOf(Chapter.class);
		int ovves = 0;
		for (var s : stock) {
			Chapter chapter = chapterOf(s.result());
			if (chapter == null) continue;
			ovves++;
			chapters.add(chapter);
		}
		helper.assertValueEqual(ovves, PunkphozStock.EXCLUSIVE.size(), "ovve offers");
		helper.assertValueEqual(chapters, EnumSet.copyOf(PunkphozStock.EXCLUSIVE), "chapters offered");
		helper.succeed();
	}

	@GameTest
	public void poolSmallerThanPicks(GameTestHelper helper) {
		var stock = PunkphozStock.roll(withCounts(0, 4), SIX.subList(0, 2), RandomSource.create(42), new PunkphozHistory());
		var pool = stock.subList(PunkphozConfig.DEFAULT.patches(), stock.size()).stream().map(s -> id(s.result())).toList();
		helper.assertValueEqual(pool.size(), 2, "pool offers");
		helper.assertValueEqual(new HashSet<>(pool), Set.of("minecraft:apple", "minecraft:bread"), "pool offers");
		helper.succeed();
	}

	@GameTest
	public void nothingButPatches(GameTestHelper helper) {
		var stock = PunkphozStock.roll(withCounts(0, 4), List.of(), RandomSource.create(42), new PunkphozHistory());
		helper.assertValueEqual(stock.size(), 2, "offers");
		Set<String> loot = lootIds();
		for (var s : stock) if (!loot.contains(id(s.result()))) helper.fail("not a patch: " + id(s.result()));
		helper.succeed();
	}

	private static PoolEntry entry(String item, int price) {
		return new PoolEntry(Identifier.parse(item), price, 1, 1);
	}

	private static PunkphozConfig withPool(PoolEntry... pool) {
		var d = PunkphozConfig.DEFAULT;
		return new PunkphozConfig(d.enabled(), d.share(), d.chapterOvves(), d.ovvePrice(), d.patches(), d.patchPrice(),
				d.poolPicks(), d.playersPerRoll(), d.maxLoaded(), List.of(pool));
	}

	@GameTest
	public void defaultsAreWritten(GameTestHelper helper) {
		JsonObject json = PunkphozConfig.CODEC.codec().encodeStart(JsonOps.INSTANCE, PunkphozConfig.DEFAULT).getOrThrow().getAsJsonObject();
		for (String key : List.of("enabled", "share", "chapter_ovves", "ovve_price", "patches", "patch_price", "pool_picks",
				"players_per_roll", "max_loaded", "pool")) {
			if (!json.has(key)) helper.fail("the encoded default has no '" + key + "': " + json);
		}
		helper.assertValueEqual(json.size(), 10, "keys written");
		helper.succeed();
	}

	@GameTest
	public void poolSkipsMissingItems(GameTestHelper helper) {
		var resolved = PunkphozConfig.resolve(withPool(entry("nosuchmod:thing", 3), entry("minecraft:emerald_block", 5)));
		helper.assertValueEqual(resolved.entries().stream().map(PoolEntry::item).toList(),
				List.of(Identifier.parse("minecraft:emerald_block")), "entries");
		helper.assertValueEqual(resolved.warnings(),
				List.of("Punkphöz: leaving out nosuchmod:thing from the pool: it isn't registered"), "warnings");
		helper.succeed();
	}

	@GameTest
	public void poolRejectsGivenOnlyPatches(GameTestHelper helper) {
		var resolved = PunkphozConfig.resolve(withPool(entry("ovvar:patch_rivals", 4), entry("ovvar:patch_tmeit-marshal", 4),
				entry("ovvar:patch_metacrew", 4), entry("ovvar:patch_spiken", 4)));
		helper.assertValueEqual(resolved.entries().stream().map(e -> e.item().getPath()).toList(), List.of("patch_spiken"), "entries");
		helper.assertValueEqual(resolved.warnings(), List.of(
				"Punkphöz: leaving out ovvar:patch_rivals from the pool: patch 'rivals' is given-only (PatchLoot.NON_LOOT)",
				"Punkphöz: leaving out ovvar:patch_tmeit-marshal from the pool: patch 'tmeit-marshal' is given-only (PatchLoot.NON_LOOT)",
				"Punkphöz: leaving out ovvar:patch_metacrew from the pool: patch 'metacrew' is given-only (PatchLoot.NON_LOOT)"), "warnings");
		helper.succeed();
	}

	/**
	 * The default pool is every kultur pattern that has an item (ITK, QMISK, TMEIT and Pirkko; the chapter logos
	 * are free in the loom), loot-flagged or not: all there with kultur loaded, all warned about without.
	 */
	@GameTest
	public void defaultPoolMatchesKultur(GameTestHelper helper) {
		var resolved = PunkphozConfig.resolve(PunkphozConfig.DEFAULT);
		if (FabricLoader.getInstance().isModLoaded("metacraft-kultur")) {
			helper.assertValueEqual(resolved.entries().size(), 4, "entries with kultur");
			helper.assertValueEqual(resolved.warnings(), List.<String>of(), "warnings with kultur");
			var patternItems = BuiltInRegistries.ITEM.keySet().stream()
					.filter(id -> id.getNamespace().equals("kultur") && id.getPath().endsWith("_banner_pattern"))
					.map(Identifier::toString).sorted().toList();
			var pool = PunkphozConfig.DEFAULT.pool().stream().map(e -> e.item().toString()).sorted().toList();
			helper.assertValueEqual(pool, patternItems, "the default pool against kultur's pattern items");
		} else {
			helper.assertValueEqual(resolved.entries().size(), 0, "entries without kultur");
			helper.assertValueEqual(resolved.warnings().size(), 4, "warnings without kultur");
			for (String warning : resolved.warnings()) {
				if (!warning.contains("kultur:")) helper.fail("a warning that names no kultur id: " + warning);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void poolWarningsOncePerLoad(GameTestHelper helper) {
		var config = withPool(entry("nosuchmod:thing", 3), entry("ovvar:patch_rivals", 4), entry("minecraft:emerald_block", 5));
		int before = PunkphozConfig.warningsLogged();
		for (int i = 0; i < 50; i++) PunkphozConfig.poolOnOffer(config);
		helper.assertValueEqual(PunkphozConfig.warningsLogged() - before, 2, "warnings logged over 50 calls");
		helper.succeed();
	}

	/** Vanilla's visits per attempt: the 25/50/75 ramp, then spawn's one-in-ten gate. */
	private static final double VANILLA_RATE = 1520.0 / 21487;

	private static PunkphozConfig spawning(boolean enabled, double share, int playersPerRoll, int maxLoaded) {
		var d = PunkphozConfig.DEFAULT;
		return new PunkphozConfig(enabled, share, d.chapterOvves(), d.ovvePrice(), d.patches(), d.patchPrice(),
				d.poolPicks(), playersPerRoll, maxLoaded, List.of());
	}

	private static PunkphozConfig share(double share) {
		return spawning(true, share, PunkphozConfig.DEFAULT.playersPerRoll(), PunkphozConfig.DEFAULT.maxLoaded());
	}

	private static void near(GameTestHelper helper, double actual, double expected, double tolerance, String what) {
		if (Math.abs(actual - expected) > tolerance) helper.fail(what + ": " + actual + ", expected " + expected + " ± " + tolerance);
	}

	@GameTest
	public void vanillaRateIsTheRamp(GameTestHelper helper) {
		near(helper, PunkphozSpawning.vanillaRate(), VANILLA_RATE, 1e-9, "vanillaRate()");
		helper.succeed();
	}

	@GameTest
	public void quarterShareChance(GameTestHelper helper) {
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(share(0.25)), 0.0805896, 1e-6, "chance at share 0.25");
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(share(0.5)), 0.2417687, 1e-6, "chance at share 0.5");
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(share(0)), 0, 0, "chance at share 0");
		near(helper, PunkphozSpawning.chanceOnAFailedRoll(spawning(false, 0.5, 5, 3)), 0, 0, "chance when disabled");
		helper.succeed();
	}

	@GameTest
	public void shareIsCappedInConfig(GameTestHelper helper) {
		JsonObject json = new JsonObject();
		json.addProperty("share", 0.6);
		var result = PunkphozConfig.CODEC.codec().parse(JsonOps.INSTANCE, json);
		if (result.error().isEmpty()) {
			helper.fail("share 0.6 decoded without an error: " + result);
			return;
		}
		String message = result.error().get().message();
		// DFU's doubleRange: "Value 0.6 outside of range [0.0:0.5]".
		if (!message.contains("0.6") || !message.contains("[0.0:0.5]")) helper.fail("the error names neither the value nor the range: " + message);
		helper.succeed();
	}

	@GameTest
	public void vanillaRollIsUntouched(GameTestHelper helper) {
		RandomSource random = RandomSource.create(5);
		for (int chance : new int[] {25, 50, 75}) {
			for (int roll = 0; roll < 100; roll++) {
				var outcome = PunkphozSpawning.decide(chance, roll, share(0.5), random);
				if ((outcome == PunkphozSpawning.Outcome.VANILLA_ROLL) != (roll <= chance)) {
					helper.fail("chance " + chance + ", roll " + roll + ": " + outcome);
					return;
				}
				if (roll <= chance && outcome == PunkphozSpawning.Outcome.PUNKPHOZ) {
					helper.fail("Punkphöz on vanilla's passing roll: chance " + chance + ", roll " + roll);
					return;
				}
			}
		}
		helper.succeed();
	}

	/**
	 * {vanilla visits, Punkphöz visits} over {@code attempts} of vanilla's ramp; with players, his visits from targets().
	 * A passing vanilla roll is a visit only through spawn's one-in-ten gate, and only a visit resets the ramp; his
	 * spawns skip the gate.
	 */
	private static long[] simulate(PunkphozConfig config, long seed, int attempts, int players) {
		RandomSource random = RandomSource.create(seed);
		List<String> online = new ArrayList<>();
		for (int i = 0; i < players; i++) online.add("p" + i);
		long vanilla = 0, punkphoz = 0;
		int state = 25;
		for (int i = 0; i < attempts; i++) {
			int chance = state;
			state = Math.min(state + 25, 75);
			int roll = random.nextInt(100);
			if (roll <= chance) {
				if (random.nextInt(10) == 0) {
					vanilla++;
					state = 25;
				}
			} else if (players == 0) {
				if (PunkphozSpawning.decide(chance, roll, config, random) == PunkphozSpawning.Outcome.PUNKPHOZ) punkphoz++;
			} else {
				punkphoz += PunkphozSpawning.targets(online, 0, config, random).size();
			}
		}
		return new long[] {vanilla, punkphoz};
	}

	@GameTest
	public void vanillaRateIsUnchanged(GameTestHelper helper) {
		int attempts = 1000000;
		long[] quarter = simulate(share(0.25), 11, attempts, 0);
		near(helper, (double) quarter[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt at share 0.25");
		near(helper, (double) quarter[1] / (quarter[0] + quarter[1]), 0.25, 0.01, "Punkphöz's share at 0.25");
		long[] off = simulate(spawning(false, 0.25, 5, 3), 11, attempts, 0);
		near(helper, (double) off[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt when disabled");
		helper.assertValueEqual(off[1], 0L, "Punkphöz visits when disabled");
		long[] half = simulate(share(0.5), 11, attempts, 0);
		near(helper, (double) half[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt at share 0.5");
		near(helper, (double) half[1] / (half[0] + half[1]), 0.5, 0.01, "Punkphöz's share at 0.5");
		helper.succeed();
	}

	@GameTest
	public void rollsPerPlayers(GameTestHelper helper) {
		var five = spawning(true, 0.25, 5, 3);
		int[] players = {0, 1, 5, 6, 20};
		int[] expected = {0, 1, 1, 2, 4};
		for (int i = 0; i < players.length; i++) {
			helper.assertValueEqual(PunkphozSpawning.rolls(players[i], five), expected[i], "rolls for " + players[i] + " players");
		}
		helper.assertValueEqual(PunkphozSpawning.rolls(3, spawning(true, 0.25, 1, 3)), 3, "rolls for 3 players at 1 per roll");
		helper.assertValueEqual(PunkphozSpawning.rolls(20, spawning(false, 0.25, 5, 3)), 0, "rolls when disabled");
		helper.succeed();
	}

	@GameTest
	public void distinctTargets(GameTestHelper helper) {
		List<String> players = new ArrayList<>();
		for (int i = 0; i < 20; i++) players.add("p" + i);
		var config = spawning(true, 0.5, 5, 20);
		RandomSource random = RandomSource.create(3);
		Set<String> picked = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			List<String> targets = PunkphozSpawning.targets(players, 0, config, random);
			if (new HashSet<>(targets).size() != targets.size()) {
				helper.fail("a player picked twice: " + targets);
				return;
			}
			if (targets.size() > 4) {
				helper.fail("more than 4 targets: " + targets);
				return;
			}
			picked.addAll(targets);
		}
		helper.assertValueEqual(picked.size(), 20, "players ever picked");
		helper.succeed();
	}

	@GameTest
	public void capRespected(GameTestHelper helper) {
		List<String> players = new ArrayList<>();
		for (int i = 0; i < 20; i++) players.add("p" + i);
		var config = spawning(true, 0.25, 1, 3);
		int[] loaded = {0, 2, 3, 5};
		int[] expected = {3, 1, 0, 0};
		for (int i = 0; i < loaded.length; i++) {
			int size = PunkphozSpawning.targets(players, loaded[i], config, 1.0, RandomSource.create(i)).size();
			helper.assertValueEqual(size, expected[i], "targets with " + loaded[i] + " loaded");
		}
		helper.succeed();
	}

	@GameTest
	public void vanillaRateIsUnchangedWithPlayers(GameTestHelper helper) {
		int attempts = 1000000;
		var config = spawning(true, 0.25, 5, 20);
		long[] visits = simulate(config, 11, attempts, 20);
		near(helper, (double) visits[0] / attempts, VANILLA_RATE, 0.002, "vanilla visits/attempt with 20 players");
		double x = PunkphozSpawning.chanceOnAFailedRoll(config);
		near(helper, (double) visits[1] / attempts, 4 * x * (1 - 10 * VANILLA_RATE), 0.01, "Punkphöz visits/attempt with 20 players");
		helper.succeed();
	}

	@GameTest
	public void spawnerMixinIsIn(GameTestHelper helper) {
		Set<String> names = new HashSet<>();
		for (var method : WanderingTraderSpawner.class.getDeclaredMethods()) names.add(method.getName());
		for (String handler : List.of("punkphozRoll", "punkphozType", "punkphozNear", "punkphozGate")) {
			if (names.stream().noneMatch(n -> n.contains(handler))) helper.fail("WanderingTraderSpawner has no " + handler + ": " + names);
		}
		helper.succeed();
	}

	/**
	 * The mixin's spawn path end to end: with {@code TARGET} set, the overworld's own {@code WanderingTraderSpawner}
	 * runs vanilla's {@code spawn} for him. Five calls make five Punkphözes although vanilla's one-in-ten gate would
	 * pass only one in ten of them, each near the player, with vanilla's despawn delay and its llamas, and the
	 * spawner's chance and delay ({@code WanderingTraderData}) untouched. It spawns into the world, on a {@link Ground}
	 * of its own, so it takes {@link WardrobeTests#BUSY} and discards all it made.
	 */
	@GameTest(maxTicks = 400)
	public void spawnerBringsPunkphoz(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Ground ground = Ground.north(helper, 2048);
		helper.startSequence()
				.thenExecute(ground::force)
				.thenWaitUntil(() -> WardrobeTests.assertThat(ground.ready(), "the spawn ground's chunks are not ticking entities yet"))
				.thenWaitUntil(() -> WardrobeTests.assertThat(WardrobeTests.BUSY.compareAndSet(false, true), "another global-state test is running"))
				.thenExecute(() -> {
					ServerPlayer player = helper.makeMockServerPlayerInLevel();
					List<Entity> made = new ArrayList<>();
					try {
						ground.assertFlat();
						BlockPos at = ground.surface();
						player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
						WanderingTraderSpawner spawner = overworldSpawner(level);
						WanderingTraderData data = traderData(spawner);
						int chance = data.spawnChance(), delay = data.spawnDelay();
						for (int i = 0; i < 5; i++) {
							Set<WanderingTrader> before = new HashSet<>(traders(level));
							PunkphozSpawning.TARGET.set(player);
							boolean spawned;
							try {
								spawned = spawn(spawner, level);
							} finally {
								PunkphozSpawning.TARGET.remove();
							}
							List<WanderingTrader> fresh = traders(level).stream().filter(t -> !before.contains(t)).toList();
							made.addAll(fresh);
							for (WanderingTrader trader : fresh) made.addAll(llamasOf(level, trader));
							WardrobeTests.assertThat(spawned, "spawn " + i + " returned false (gate or no room)");
							WardrobeTests.assertThat(fresh.size() == 1, "spawn " + i + " made " + fresh.size() + " traders");
							WanderingTrader trader = fresh.getFirst();
							WardrobeTests.assertThat(trader instanceof Punkphoz, "spawn " + i + " made a " + trader.getType() + ", not Punkphöz");
							WardrobeTests.assertThat(trader.getType() == PunkphozEntities.PUNKPHOZ, "spawn " + i + "'s type " + trader.getType());
							// Vanilla picks x and z each within 48 blocks of the player (no meeting point here).
							double dx = Math.abs(trader.getX() - player.getX()), dz = Math.abs(trader.getZ() - player.getZ());
							WardrobeTests.assertThat(dx <= 49 && dz <= 49, "spawn " + i + " landed " + dx + ", " + dz + " blocks from the player");
							WardrobeTests.assertThat(trader.getDespawnDelay() == 48000, "spawn " + i + "'s despawn delay " + trader.getDespawnDelay());
							WardrobeTests.assertThat(!llamasOf(level, trader).isEmpty(), "spawn " + i + " brought no llama");
						}
						WardrobeTests.assertThat(data.spawnChance() == chance, "spawn chance moved: " + chance + " -> " + data.spawnChance());
						WardrobeTests.assertThat(data.spawnDelay() == delay, "spawn delay moved: " + delay + " -> " + data.spawnDelay());
						WardrobeTests.assertThat(traderData(spawner) == data, "the spawner's trader data was replaced");
					} finally {
						made.forEach(Entity::discard);
						level.getServer().getPlayerList().remove(player);
						ground.release();
						WardrobeTests.BUSY.set(false);
					}
				})
				.thenSucceed();
	}

	/**
	 * With {@code TARGET} unset the same {@code spawn} is vanilla's: it keeps calling until vanilla's gate lets one
	 * through, and every trader that comes is a plain wandering trader. {@code /summon} makes a plain one too.
	 */
	@GameTest(maxTicks = 400)
	public void typeOnlySwapsWhileSpawning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Ground ground = Ground.north(helper, 2560);
		helper.startSequence()
				.thenExecute(ground::force)
				.thenWaitUntil(() -> WardrobeTests.assertThat(ground.ready(), "the spawn ground's chunks are not ticking entities yet"))
				.thenWaitUntil(() -> WardrobeTests.assertThat(WardrobeTests.BUSY.compareAndSet(false, true), "another global-state test is running"))
				.thenExecute(() -> {
					ServerPlayer player = helper.makeMockServerPlayerInLevel();
					List<Entity> made = new ArrayList<>();
					try {
						WardrobeTests.assertThat(PunkphozSpawning.TARGET.get() == null, "TARGET is set outside a spawn");
						ground.assertFlat();
						BlockPos at = ground.surface();
						player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
						WanderingTraderSpawner spawner = overworldSpawner(level);
						int calls = 0;
						// Vanilla's gate passes one in ten; 300 calls all failing is about 2e-14.
						while (calls < 300 && made.isEmpty()) {
							Set<WanderingTrader> before = new HashSet<>(traders(level));
							spawn(spawner, level);
							calls++;
							for (WanderingTrader trader : traders(level)) {
								if (before.contains(trader)) continue;
								made.add(trader);
								made.addAll(llamasOf(level, trader));
							}
						}
						WardrobeTests.assertThat(!made.isEmpty(), "no trader came in " + calls + " vanilla spawns");
						for (Entity entity : made) {
							if (!(entity instanceof WanderingTrader trader)) continue;
							WardrobeTests.assertThat(!(trader instanceof Punkphoz), "vanilla's spawn made a Punkphöz");
							WardrobeTests.assertThat(trader.getType() == EntityTypes.WANDERING_TRADER, "vanilla's spawn made a " + trader.getType());
						}

						var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
						level.getServer().getCommands().performPrefixedCommand(source,
								"summon minecraft:wandering_trader " + at.getX() + " " + at.getY() + " " + at.getZ());
						var summoned = level.getEntities(EntityTypeTest.forClass(WanderingTrader.class), new AABB(at).inflate(2), e -> !made.contains(e));
						made.addAll(summoned);
						WardrobeTests.assertThat(!summoned.isEmpty(), "/summon made no wandering trader");
						for (WanderingTrader trader : summoned) {
							WardrobeTests.assertThat(!(trader instanceof Punkphoz), "/summon wandering_trader made a Punkphöz");
							WardrobeTests.assertThat(trader.getType() == EntityTypes.WANDERING_TRADER, "the summoned trader's type " + trader.getType());
						}
					} finally {
						made.forEach(Entity::discard);
						level.getServer().getPlayerList().remove(player);
						ground.release();
						WardrobeTests.BUSY.set(false);
					}
				})
				.thenSucceed();
	}

	/**
	 * A flat patch of the test world, {@code north} blocks north of a test, for vanilla's spawn search: that picks x and z
	 * each within 48 blocks of the player (and the llamas within 4 of the trader). Around the test's own spot the search
	 * runs into other tests' barrier walls, where {@code hasEnoughSpace} fails and {@code spawn} returns false, and into
	 * chunks that are loaded but do not tick entities, where the trader is added yet {@code getEntities} never sees it
	 * (and nothing discards it). Its chunks are forced, so every one of them ticks entities, until {@link #release}.
	 */
	private record Ground(ServerLevel level, BlockPos centre, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
		/** 48 blocks of search, 4 more for a llama, and a little over. */
		private static final int REACH = 56;

		static Ground north(GameTestHelper helper, int north) {
			BlockPos centre = helper.absolutePos(BlockPos.ZERO).north(north);
			return new Ground(helper.getLevel(), centre, (centre.getX() - REACH) >> 4, (centre.getX() + REACH) >> 4,
					(centre.getZ() - REACH) >> 4, (centre.getZ() + REACH) >> 4);
		}

		void force() {
			forced(true);
		}

		void release() {
			forced(false);
		}

		private void forced(boolean on) {
			for (int x = minChunkX; x <= maxChunkX; x++) {
				for (int z = minChunkZ; z <= maxChunkZ; z++) level.setChunkForced(x, z, on);
			}
		}

		boolean ready() {
			for (int x = minChunkX; x <= maxChunkX; x++) {
				for (int z = minChunkZ; z <= maxChunkZ; z++) {
					if (!level.isPositionEntityTicking(new BlockPos(x << 4, centre.getY(), z << 4))) return false;
				}
			}
			return true;
		}

		/** Where the player stands: on top of the ground at its centre. */
		BlockPos surface() {
			return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, centre);
		}

		/** Every column the search can land on has the centre's height: nobody else's test is standing here. */
		void assertFlat() {
			int y = surface().getY();
			for (int x = minChunkX << 4; x < (maxChunkX + 1) << 4; x++) {
				for (int z = minChunkZ << 4; z < (maxChunkZ + 1) << 4; z++) {
					int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					WardrobeTests.assertThat(h == y, "the spawn ground is not flat: " + h + " at " + x + ", " + z + ", not " + y);
				}
			}
		}
	}

	private static List<WanderingTrader> traders(ServerLevel level) {
		return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(WanderingTrader.class), e -> true));
	}

	private static List<TraderLlama> llamasOf(ServerLevel level, WanderingTrader trader) {
		return level.getEntities(EntityTypeTest.forClass(TraderLlama.class), trader.getBoundingBox().inflate(16),
				llama -> llama.getLeashHolder() == trader);
	}

	/** The overworld's {@code WanderingTraderSpawner}, from {@code ServerLevel.customSpawners}. */
	private static WanderingTraderSpawner overworldSpawner(ServerLevel level) {
		try {
			Field field = ServerLevel.class.getDeclaredField("customSpawners");
			field.setAccessible(true);
			for (Object spawner : (List<?>) field.get(level.getServer().overworld())) {
				if (spawner instanceof WanderingTraderSpawner wandering) return wandering;
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("ServerLevel.customSpawners", e);
		}
		throw new IllegalStateException("the overworld has no WanderingTraderSpawner");
	}

	private static WanderingTraderData traderData(WanderingTraderSpawner spawner) {
		try {
			Method method = WanderingTraderSpawner.class.getDeclaredMethod("getTraderData");
			method.setAccessible(true);
			return (WanderingTraderData) method.invoke(spawner);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("WanderingTraderSpawner.getTraderData", e);
		}
	}

	private static boolean spawn(WanderingTraderSpawner spawner, ServerLevel level) {
		try {
			Method method = WanderingTraderSpawner.class.getDeclaredMethod("spawn", ServerLevel.class);
			method.setAccessible(true);
			return (boolean) method.invoke(spawner, level);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("WanderingTraderSpawner.spawn", e);
		}
	}

	private static List<String> describe(MerchantOffers offers) {
		return offers.stream().map(PunkphozTests::describe).toList();
	}

	private static String describe(MerchantOffer offer) {
		return describe(offer.getResult()) + " for " + describe(offer.getBaseCostA()) + " + " + describe(offer.getCostB())
				+ ", used " + offer.getUses() + "/" + offer.getMaxUses();
	}

	private static String describe(ItemStack stack) {
		return stack.isEmpty() ? "nothing" : stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	private static GoalSelector goals(Mob mob) {
		try {
			Field field = Mob.class.getDeclaredField("goalSelector");
			field.setAccessible(true);
			return (GoalSelector) field.get(mob);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Mob.goalSelector", e);
		}
	}
}
