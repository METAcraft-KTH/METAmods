package se.metacraft.player_bosses.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.TeamColor;
import org.pcollections.*;
import se.metacraft.config.container.ConfigContainer;
import se.metacraft.core.gamerules.METAcraftGameRules;
import se.metacraft.core.music.ManageableServerBossBar;
import se.metacraft.core.util.helper.BossBarHelper;
import se.metacraft.core.util.helper.MusicHelper;
import se.metacraft.core.util.helper.PlayerInventoryHelper;
import se.metacraft.cutscenes.util.helper.CutsceneHelper;
import se.metacraft.lib.util.METACodecs;
import se.metacraft.lib.util.helper.PlayerDataHelper;
import se.metacraft.lib.util.helper.WorldHelper;
import se.metacraft.player_bosses.PlayerBosses;
import se.metacraft.player_bosses.mixin.PlayerAccessor;

import java.util.*;
import java.util.stream.IntStream;

public class PlayerBossState extends SavedData {

	private static final String CAN_BE_BOSS = "metacraft.end_player_boss.can_be_boss";

	private static final Codec<PSet<UUID>> UUID_SET = METACodecs.createPCollectionCodec(
		UUIDUtil.LENIENT_CODEC, HashTreePSet.empty()
	);

	private static final Codec<PlayerBossState> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			UUIDUtil.LENIENT_CODEC.optionalFieldOf("current_boss").forGetter(d -> d.currentBossID),
			ManageableServerBossBar.BossBarData.CODEC.optionalFieldOf("boss_bar").forGetter(d -> d.bossbar),
			UUID_SET.optionalFieldOf("old_bosses_to_reset", HashTreePSet.empty()).forGetter(d -> d.oldBossesToReset),
			Codec.INT.optionalFieldOf("lives_remaining", 3).forGetter(d -> d.livesRemaining),
			Codec.STRING.optionalFieldOf("boss_prev_team").forGetter(d -> d.bossPrevTeam),
			Vec3.CODEC.optionalFieldOf("player_spawn_pos").forGetter(d -> d.playerSpawnPos)
		).apply(instance, PlayerBossState::new)
	);

	private static final SavedDataType<PlayerBossState> TYPE = new SavedDataType<>(
		PlayerBosses.getID("boss-player"), PlayerBossState::new, CODEC, null
	);

	private static final Identifier BOSS_DATA_BACKUP = PlayerBosses.getID("player_data_backup_before_boss");

	private static final String BOSS_TEAM = "metacraft.end_player_boss.team";

	private static final Identifier ADVANCEMENT_STAT_STORAGE = PlayerBosses.getID("end-boss");

	private ConfigContainer<PlayerBossConfig> config;

	private Optional<UUID> currentBossID;
	private PSet<UUID> oldBossesToReset;
	private ServerPlayer currentBoss;
	private Optional<String> bossPrevTeam;
	private Optional<ManageableServerBossBar.BossBarData> bossbar;
	private ManageableServerBossBar cachedBossBar = null;
	private int livesRemaining = 3;
	private Optional<Vec3> playerSpawnPos;

	private Vec3 particlePos;
	private int particleTime = -1;

	public PlayerBossState() {
		this(Optional.empty(), Optional.empty(), HashTreePSet.empty(), 3, Optional.empty(), Optional.empty());
	}

	public PlayerBossState(
		Optional<UUID> currentBoss, Optional<ManageableServerBossBar.BossBarData> bossbar,
		PSet<UUID> oldBossesToReset, int livesRemaining,
		Optional<String> bossPrevTeam, Optional<Vec3> playerSpawnPos
	) {
		this.currentBossID = currentBoss;
		this.bossbar = bossbar;
		this.oldBossesToReset = oldBossesToReset;
		this.livesRemaining = livesRemaining;
		this.bossPrevTeam = bossPrevTeam;
		this.playerSpawnPos = playerSpawnPos;
	}

	public static Optional<PlayerBossState> getInstance(ServerLevel world) {
		return Optional.ofNullable(world.getDataStorage().get(TYPE));
	}

	public static void initBossState(ServerLevel world, ServerPlayer boss, Vec3 spawnPos) {
		var instance = world.getDataStorage().computeIfAbsent(TYPE);
		instance.setPlayerSpawnPos(spawnPos);
		instance.reset(world);
		instance.setCurrentBoss(boss);
	}

	private void reset(ServerLevel world) {
		initConfig(world);
		livesRemaining = config.get().bossLives();
		setDirty();
	}

	public void setPlayerSpawnPos(Vec3 pos) {
		this.playerSpawnPos = Optional.of(pos);
		setDirty();
	}

	private void beforeChangingBoss() {
		if (currentBossID.isPresent()) {
			oldBossesToReset = oldBossesToReset.plus(currentBossID.get());
			setDirty();
		}
	}

	private void triggerEnd(ServerLevel world) {
		beforeChangingBoss();
		currentBoss = null;
		currentBossID = Optional.empty();
		setDirty();
		config.get().bossDefeatedCommand().ifPresent(command -> {
			var source = world.getServer().getFunctions()
				.getGameLoopSender().withLevel(world).withPosition(
					particlePos != null ? particlePos : playerSpawnPos.orElse(Vec3.ZERO)
				);
			world.getServer().getCommands().performPrefixedCommand(source, command);
		});
	}

	private ManageableServerBossBar createBossBar(RandomSource random) {
		var bossbar = ManageableServerBossBar.create(random);
		this.bossbar.ifPresent(bossbar::deserialize);
		return bossbar;
	}

	public void setCurrentBoss(ServerPlayer player) {
		CutsceneHelper.forceOutOfCutscene(player);
		if (currentBoss != null) {
			BossBarHelper.transferBossBar(currentBoss, player);
		} else {
			if (bossbar.isPresent()) {
				BossBarHelper.setBossBar(player, createBossBar(player.getRandom()));
			} else {
				bossbar = BossBarHelper.getBossBar(player).map(ManageableServerBossBar::serialize);
				setDirty();
			}
		}
		beforeChangingBoss();

		currentBoss = player;
		currentBossID = Optional.of(player.getUUID());
		prepareBoss(player);
		setDirty();
	}

	public Vec3 getPlayerSpawnPos() {
		return playerSpawnPos.orElse(Vec3.ZERO);
	}

	public PlayerBossConfig getConfig() {
		return config.get();
	}

	private void unPrepareBoss(ServerPlayer player) {
		var xpLevel = player.experienceLevel;
		var xpProgress = player.experienceProgress;
		var totalXP = player.totalExperience;

		((PlayerAccessor) player).callDestroyVanishingCursedItems();
		player.getInventory().dropAll();

		PlayerDataHelper.loadPlayerData(
			player, BOSS_DATA_BACKUP, false,
			true, true
		);
		PlayerDataHelper.removePlayerData(player, BOSS_DATA_BACKUP);

		var scoreboard = player.level().getScoreboard();
		scoreboard.removePlayerFromTeam(player.getScoreboardName());
		if (bossPrevTeam.isPresent()) {
			var team = scoreboard.getPlayerTeam(bossPrevTeam.get());
			if (team != null) {
				scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
			}
		}

		player.experienceProgress = xpProgress;
		player.totalExperience = totalXP;
		player.setExperienceLevels(xpLevel);
	}

	private static Holder<Enchantment> getEnchantment(
		HolderLookup.Provider lookup, ResourceKey<Enchantment> key
	) {
		return lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemEnchantments prepareEnchantments(
		ItemEnchantments existing,
		HolderLookup.Provider lookup,
		Map<ResourceKey<Enchantment>, Integer> enchantments
	) {
		ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(existing);
		for (var enchantment : enchantments.entrySet()) {
			builder.upgrade(getEnchantment(lookup, enchantment.getKey()), enchantment.getValue());
		}
		return builder.toImmutable();
	}

	public static ItemEnchantments prepareEnchantments(
		HolderLookup.Provider lookup,
		Map<ResourceKey<Enchantment>, Integer> enchantments
	) {
		return prepareEnchantments(
			ItemEnchantments.EMPTY, lookup, enchantments
		);
	}

	public static final PMap<ResourceKey<Enchantment>, Integer> CUSTOM_BOSS_ITEM_ENCHANTS = HashTreePMap.singleton(
		Enchantments.UNBREAKING, 3
	).plus(
		Enchantments.MENDING, 1
	);

	public static final PMap<ResourceKey<Enchantment>, Integer> BOSS_ARMOUR_ENCHANTS = CUSTOM_BOSS_ITEM_ENCHANTS.plus(
		Enchantments.PROTECTION, 4
	).plus(
		Enchantments.BINDING_CURSE, 1
	);

	public static final PMap<ResourceKey<Enchantment>, Integer> ENCHANTS_APPLIED_TO_ALL_ITEMS = HashTreePMap.singleton(
		Enchantments.VANISHING_CURSE, 1
	);

	private void mutateItemForBoss(ItemStack stack, HolderLookup.Provider lookup) {
		for (var c : config.get().componentsToApply()) {
			PlayerBossConfig.ItemEntry.mergeComponentIntoStack(stack, c, lookup);
		}
	}

	public static Equippable addOverlay(Equippable equippable, Identifier overlay) {
		return new Equippable(
			equippable.slot(), equippable.equipSound(), equippable.assetId(), Optional.ofNullable(overlay),
			equippable.allowedEntities(), equippable.dispensable(), equippable.swappable(), equippable.damageOnHurt(),
			equippable.equipOnInteract(), equippable.canBeSheared(), equippable.shearingSound()
		);
	}

	private boolean isDisposableJunk(ItemStack stack, int pass) {
		if (pass > 2) return true;
		if (stack.has(DataComponents.ENCHANTMENTS)) return false;
		if (stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
		if (stack.has(DataComponents.DEATH_PROTECTION) && pass < 2) return false;
		if (stack.has(DataComponents.POTION_CONTENTS) && pass < 2) return false;
		if (stack.has(DataComponents.FOOD) && pass < 1) return false;
		if (stack.has(DataComponents.CUSTOM_NAME) && pass < 1) return false;
		if (stack.has(DataComponents.TOOL) && pass < 1) return false;
		if (stack.has(DataComponents.BUCKET_ENTITY_DATA) && pass < 1) return false;
		if (stack.has(DataComponents.WEAPON) && pass < 1) return false;
		if (stack.getItem() instanceof TridentItem && pass < 1) return false;
		if (stack.getItem() instanceof BowItem && pass < 1) return false;
		if (stack.getItem() instanceof CrossbowItem && pass < 1) return false;
		if (stack.getItem() instanceof MaceItem && pass < 1) return false;
		return true;
	}

	private IntList getScrambledItemStackOrder(Inventory inv) {
		return Util.toShuffledList(IntStream.range(0, inv.getContainerSize()), inv.player.getRandom());
	}

	private void clearOneSlot(ServerPlayer player) {
		var inv = player.getInventory();
		for (int p = 0; p < 3; p++) {
			var stacks = getScrambledItemStackOrder(player.getInventory());
			for (int slot : stacks) {
				var stack = inv.getItem(slot);
				if (!stack.isEmpty() && isDisposableJunk(stack, p)) {
					inv.setItem(slot, ItemStack.EMPTY);
					return;
				}
			}
		}
	}

	private static final ItemStack BLOCKER = new ItemStack(
		Items.BARRIER.builtInRegistryHolder(), 1, DataComponentPatch.builder().set(
		DataComponents.MAX_STACK_SIZE, 1
	).build()
	);

	private void insertStack(int slot, Inventory inv, PlayerBossConfig.ItemEntry toInsert) {
		var itemToInsert = toInsert.getItem(inv, slot);
		if (!inv.getItem(slot).isEmpty()) {
			var stack = inv.getItem(slot);
			inv.setItem(slot, BLOCKER);
			inv.add(stack);
		}
		inv.setItem(slot, itemToInsert);
	}

	private void insertStack(EquipmentSlot slot, Inventory inv, PlayerBossConfig.ItemEntry toInsert) {
		var intSlot = PlayerInventoryHelper.getSlot(inv.player, slot);
		if (intSlot != -1) {
			insertStack(intSlot, inv, toInsert);
		}
	}

	private ItemStack createBossFood(int count) {
		return new ItemStack(
			Items.COOKED_BEEF.builtInRegistryHolder(), count,
			DataComponentPatch.builder().set(
				DataComponents.ITEM_NAME, Component.literal("Mutated Food")
			).build()
		);
	}

	private void prepareInventory(ServerPlayer player) {

		int firstEmptySlot = player.getInventory().getFreeSlot();

		Map<EquipmentSlot, PlayerBossConfig.ItemEntry> equipment = config.get().equipment();

		PVector<PlayerBossConfig.ItemEntry> itemsToInsert = config.get().items();

		int expectedFood = 64 * 4;

		int foodAmount = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			var stack = player.getInventory().getItem(i);
			if (stack.has(DataComponents.FOOD)) {
				foodAmount += stack.get(DataComponents.FOOD).nutrition();
			}
		}

		if (foodAmount < expectedFood) {
			int foodItemsToAdd = (expectedFood - foodAmount)/4;
			itemsToInsert = itemsToInsert.plus(
				new PlayerBossConfig.ItemEntry(
					createBossFood(foodItemsToAdd)
				)
			);
		}

		int itemsCount = equipment.size() + itemsToInsert.size();

		int numFreeSlots;
		if (firstEmptySlot == -1) {
			numFreeSlots = 0;
		} else {
			numFreeSlots = player.getInventory().getContainerSize() - firstEmptySlot;
		}

		int slotsToFree = Math.max(0, itemsCount - numFreeSlots);

		for (int k = 0; k < slotsToFree; k++) {
			clearOneSlot(player);
		}

		for (var e : equipment.entrySet()) {
			insertStack(e.getKey(), player.getInventory(), e.getValue());
		}

		for (int i = 0; i < itemsToInsert.size(); i++) {
			insertStack(i, player.getInventory(), itemsToInsert.get(i));
		}




	}

	private void prepareBoss(ServerPlayer player) {
		player.entityTags().remove(CAN_BE_BOSS);
		PlayerDataHelper.detachPassengersBeforeSaving(player);
		PlayerDataHelper.saveCurrentPlayerData(player, BOSS_DATA_BACKUP);
		PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
		PlayerDataHelper.getPlayerData(player, BOSS_DATA_BACKUP).ifPresent(data -> {
			data.remove(ManageableServerBossBar.BOSS_BAR);
		});

		PlayerDataHelper.setAdvancementTracker(player, ADVANCEMENT_STAT_STORAGE, true);
		PlayerDataHelper.setStatHandler(player, ADVANCEMENT_STAT_STORAGE, true);
		PlayerDataHelper.setAnnounceAdvancements(player, false);


		player.entityTags().removeAll(config.get().tagsToRemove());

		config.get().attributeModifiers().forEach((attribute, modifiers) -> {
			var instance = player.getAttribute(attribute);
			if (instance == null) {
				PlayerBosses.LOGGER.error("Attempted to add modifiers for " + attribute + " but players do not support this attribute!");
			} else {
				instance.addPermanentModifiers(modifiers);
			}
		});
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20);
		player.setGlowingTag(true);

		var inv = player.getInventory();
		prepareInventory(player);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				mutateItemForBoss(stack, player.registryAccess());
			}
		}

		config.get().bossInitCommand().ifPresent(command -> {
			player.level().getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack().withSuppressedOutput().withPermission(LevelBasedPermissionSet.GAMEMASTER), command
			);
		});
	}

	public void switchToBoss(ServerPlayer player) {
		//TODO Cutscene?
		if (particlePos == null) {
			particlePos = playerSpawnPos.orElse(Vec3.ZERO);
		}
		particleTime = 80;
		setCurrentBoss(player);
	}

	public boolean hasBoss() {
		return currentBossID.isPresent();
	}

	public boolean isBoss(ServerPlayer player) {
		return player == currentBoss || player.getUUID().equals(currentBossID.orElse(null));
	}

	public Component getBossName() {
		if (hasBoss()) {
			if (currentBoss != null) {
				return currentBoss.getName();
			} else {
				return Component.literal("Disconnected Player");
			}
		}
		return Component.literal("missingno");
	}

	public void findNewBoss(ServerLevel world) {
		var options = world.players().stream().filter(
			player -> player.entityTags().contains(CAN_BE_BOSS)
		).toList();
		if (!options.isEmpty()) {
			var player = options.get(world.getRandom().nextInt(options.size()));
			switchToBoss(player);
		} else {
			livesRemaining = 1;
			setDirty();
		}
	}

	private void triggerLowHP(ServerLevel world) {
		if (livesRemaining <= 1) {
			triggerEnd(world);
			return;
		} else {
			livesRemaining--;
			setDirty();
		}
		findNewBoss(world);
	}

	private boolean isSafeSpawn(ServerLevel world, Entity entity, AABB box, BlockPos pos) {
		return world.noCollision(entity, box, true) && Block.isFaceFull(world.getBlockState(pos).getCollisionShape(world, pos), Direction.UP);
	}

	private Vec3 getPosAroundPos(ServerLevel world, Entity entity, Vec3 pos) {
		double maxRange = config.get().maxSpawnDist();
		double minRange = config.get().minSpawnDist();

		var box = entity.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);

		BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

		for (int t = 0; t < 50; t++) {
			double range = entity.getRandom().nextDouble() * (maxRange - minRange) + minRange;
			double angle = entity.getRandom().nextDouble() * Math.PI * 2;
			var xOffset = Math.cos(angle) * range;
			var zOffset = Math.sin(angle) * range;

			double x = pos.x() + xOffset;
			int y = Mth.floor(pos.y());
			double z = pos.z() + zOffset;

			if (isSafeSpawn(world, entity, box.move(x, y, z), blockPos.set(x, y, z))) {
				return new Vec3(x, y, z);
			}

			if (y >= world.getMaxY() || y < world.getMinY()) {
				y = world.getMinY() + world.getHeight()/2;
			}

			for (int i = 1; i < world.getHeight()/2; i++) {
				int upY = y + i;
				if (isSafeSpawn(world, entity, box.move(x, upY, z), blockPos.setY(upY-1)) && upY <= world.getMaxY()) {
					return new Vec3(x, upY, z);
				}
				int downY = y - i;
				if (isSafeSpawn(world, entity, box.move(x, downY, z), blockPos.setY(downY-1)) && downY > world.getMinY()) {
					return new Vec3(x, downY, z);
				}
			}
		}

		return pos;
	}

	private TeleportTransition getTargetAroundPos(ServerLevel world, Entity entity, Vec3 pos) {
		return new TeleportTransition(
			world, getPosAroundPos(world, entity, pos),
			Vec3.ZERO, entity.getRandom().nextFloat() * 360 - 180,
			0, TeleportTransition.DO_NOTHING
		);
	}

	private TeleportTransition getNearBoss(ServerLevel world, Entity entity) {
		if (currentBoss != null) {
			return getTargetAroundPos(currentBoss.level(), entity, currentBoss.position());
		} else {
			return getPlayerSpawnPoint(world, entity);
		}
	}

	public TeleportTransition getPlayerSpawnPoint(ServerLevel world, Entity entity) {
		return getTargetAroundPos(world, entity, playerSpawnPos.orElse(Vec3.ZERO));
	}

	private void tickBossAlive(ServerLevel world) {
		var gameRules = world.getGameRules();
		if (!gameRules.get(GameRules.KEEP_INVENTORY)) {
			gameRules.set(GameRules.KEEP_INVENTORY, true, world.getServer());
		}
		if (gameRules.get(METAcraftGameRules.ARMOR_DAMAGE)) {
			gameRules.set(METAcraftGameRules.ARMOR_DAMAGE, false, world.getServer());
		}
		if (world.getScoreboard().getPlayerTeam(BOSS_TEAM) == null) {
			var team = world.getScoreboard().addPlayerTeam(BOSS_TEAM);
			team.setColor(Optional.of(TeamColor.DARK_PURPLE));
		}

		if (particleTime > 0) {
			particleTime--;
			currentBoss.level().sendParticles(
				new TrailParticleOption(
					currentBoss.getBoundingBox().getCenter(),
					-12648385, 20
				), true, true,
				particlePos.x(), particlePos.y(), particlePos.z(),
				100, 0.5, 0.5, 0.5, 1
			);
		} else {
			particlePos = currentBoss.getBoundingBox().getCenter();
		}
		if (this.playerSpawnPos.isEmpty()) {
			this.playerSpawnPos = Optional.of(currentBoss.position());
		}
		BossBarHelper.getBossBar(currentBoss).ifPresent(bar -> {
			if (bar != this.cachedBossBar) {
				this.cachedBossBar = bar;
				this.bossbar = Optional.of(bar.serialize());
				setDirty();
			}
		});

		var scoreboard = world.getScoreboard();
		var bossName = currentBoss.getScoreboardName();
		var bossTeam = scoreboard.getPlayerTeam(BOSS_TEAM);
		var currentTeam = scoreboard.getPlayersTeam(bossName);
		if (currentTeam != bossTeam) {
			scoreboard.addPlayerToTeam(bossName, bossTeam);
			if (currentTeam != null) {
				bossPrevTeam = Optional.of(currentTeam.getName());
				setDirty();
			}
		}

		for (var player : world.players()) {
			if (player != currentBoss && player.distanceTo(currentBoss) > config.get().maxDistanceFromBoss()) {
				player.teleport(getNearBoss(currentBoss.level(), player));
			}
		}
		if (currentBoss.position().distanceTo(playerSpawnPos.orElse(null)) > config.get().maxDistanceFromSpawn() || currentBoss.level() != world) {
			currentBoss.teleport(getPlayerSpawnPoint(world, currentBoss));
		}
		if (currentBoss.getY() < world.getMinY()) {
			currentBoss.teleport(getPlayerSpawnPoint(world, currentBoss));
		}

		config.get().musicForBoss().ifPresent(music -> {
			if (!MusicHelper.isMusicPlaying(currentBoss, music)) {
				MusicHelper.playMusic(currentBoss, music, player -> player == currentBoss);
			}
		});
	}

	private void initConfig(ServerLevel world) {
		if (config == null) {
			config = PlayerBossConfig.CONFIG_BUILDER.withPath(
				WorldHelper.getSession(world.getServer()).getDimensionPath(world.dimension()).resolve(
					"boss-player.json"
				)
			).disableConfigScreen().build(
				"boss-player",
				world.registryAccess()
			);
		}
	}

	public void tick(ServerLevel world) {
		initConfig(world);
		trackBoss(world);
		if (currentBoss != null) {
			tickBossAlive(world);
		}
		for (var oldBoss : oldBossesToReset) {
			var player = world.getServer().getPlayerList().getPlayer(oldBoss);
			if (player != null && player.isAlive() && !CutsceneHelper.isInCutscene(player)) {
				unPrepareBoss(player);
				oldBossesToReset = oldBossesToReset.minus(oldBoss);
				setDirty();
			}
		}
	}

	private void trackBoss(ServerLevel world) {
		if (currentBoss == null && currentBossID.isPresent()) {
			currentBoss = world.getServer().getPlayerList().getPlayer(currentBossID.get());
		}
		if (currentBoss != null) {
			if (currentBoss.hasDisconnected()) {
				currentBoss = null;
				return;
			}
			if (currentBoss.isDeadOrDying() || (currentBoss.getHealth() / currentBoss.getMaxHealth()) <= config.get().getHealthPercent(livesRemaining)) {
				triggerLowHP(world);
			}
		}
	}
}

