package se.datasektionen.mc.metacraft_season_4.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Block;
import net.minecraft.component.*;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.particle.TrailParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.TeleportTarget;
import org.pcollections.*;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.gamerules.METAcraftGameRules;
import se.datasektionen.mc.metacraft_core.music.ManageableServerBossBar;
import se.datasektionen.mc.metacraft_core.util.helper.BossBarHelper;
import se.datasektionen.mc.metacraft_core.util.helper.MusicHelper;
import se.datasektionen.mc.metacraft_core.util.helper.PlayerInventoryHelper;
import se.datasektionen.mc.metacraft_lib.config.container.ConfigContainer;
import se.datasektionen.mc.metacraft_lib.util.ExtraCodecs;
import se.datasektionen.mc.metacraft_lib.util.helper.PlayerDataHelper;
import se.datasektionen.mc.metacraft_lib.util.helper.WorldHelper;
import se.datasektionen.mc.metacraft_season_4.Season4;
import se.datasektionen.mc.metacraft_season_4.mixin.AccessorPlayerEntity;

import java.util.*;
import java.util.stream.IntStream;

public class EndBossPlayerState extends PersistentState {

	private static final String CAN_BE_BOSS = "metacraft.end_player_boss.can_be_boss";

	private static final Codec<PSet<UUID>> UUID_SET = ExtraCodecs.createPCollectionCodec(
			Uuids.STRICT_CODEC, HashTreePSet.empty()
	);

	private static final Codec<EndBossPlayerState> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Uuids.STRICT_CODEC.optionalFieldOf("current_boss").forGetter(d -> d.currentBossID),
					ManageableServerBossBar.BossBarData.CODEC.optionalFieldOf("boss_bar").forGetter(d -> d.bossbar),
					UUID_SET.optionalFieldOf("old_bosses_to_reset", HashTreePSet.empty()).forGetter(d -> d.oldBossesToReset),
					Codec.INT.optionalFieldOf("lives_remaining", 3).forGetter(d -> d.livesRemaining),
					Codec.STRING.optionalFieldOf("boss_prev_team").forGetter(d -> d.bossPrevTeam),
					Vec3d.CODEC.optionalFieldOf("player_spawn_pos").forGetter(d -> d.playerSpawnPos)
			).apply(instance, EndBossPlayerState::new)
	);

	private static final PersistentStateType<EndBossPlayerState> TYPE = new PersistentStateType<>(
			METAcraftCore.NAMESPACE + "-end-boss-player", EndBossPlayerState::new, CODEC, null
	);

	private static final Identifier BOSS_DATA_BACKUP = Season4.getID("player_data_backup_before_boss");

	private static final String BOSS_TEAM = "metacraft.end_player_boss.team";

	private static final Identifier ADVANCEMENT_STAT_STORAGE = Season4.getID("end-boss");

	private ConfigContainer<EndBossPlayerConfig> config;

	private Optional<UUID> currentBossID;
	private PSet<UUID> oldBossesToReset;
	private ServerPlayerEntity currentBoss;
	private Optional<String> bossPrevTeam;
	private Optional<ManageableServerBossBar.BossBarData> bossbar;
	private ManageableServerBossBar cachedBossBar = null;
	private int livesRemaining = 3;
	private Optional<Vec3d> playerSpawnPos;

	private Vec3d particlePos;
	private int particleTime = -1;

	public EndBossPlayerState() {
		this(Optional.empty(), Optional.empty(), HashTreePSet.empty(), 3, Optional.empty(), Optional.empty());
	}

	public EndBossPlayerState(
			Optional<UUID> currentBoss, Optional<ManageableServerBossBar.BossBarData> bossbar,
			PSet<UUID> oldBossesToReset, int livesRemaining,
			Optional<String> bossPrevTeam, Optional<Vec3d> playerSpawnPos
	) {
		this.currentBossID = currentBoss;
		this.bossbar = bossbar;
		this.oldBossesToReset = oldBossesToReset;
		this.livesRemaining = livesRemaining;
		this.bossPrevTeam = bossPrevTeam;
		this.playerSpawnPos = playerSpawnPos;
	}

	public static Optional<EndBossPlayerState> getInstance(ServerWorld world) {
		return Optional.ofNullable(world.getPersistentStateManager().get(TYPE));
	}

	public static void initBossState(ServerWorld world, ServerPlayerEntity boss, Vec3d spawnPos) {
		var instance = world.getPersistentStateManager().getOrCreate(TYPE);
		instance.setPlayerSpawnPos(spawnPos);
		instance.reset(world);
		instance.setCurrentBoss(boss);
	}

	private void reset(ServerWorld world) {
		initConfig(world);
		livesRemaining = config.get().bossLives();
		markDirty();
	}

	public void setPlayerSpawnPos(Vec3d pos) {
		this.playerSpawnPos = Optional.of(pos);
		markDirty();
	}

	private void beforeChangingBoss() {
		if (currentBossID.isPresent()) {
			oldBossesToReset = oldBossesToReset.plus(currentBossID.get());
			markDirty();
		}
	}

	private void triggerEnd(ServerWorld world) {
		beforeChangingBoss();
		currentBoss = null;
		currentBossID = Optional.empty();
		markDirty();
		config.get().bossDefeatedCommand().ifPresent(command -> {
			var source = world.getServer().getCommandFunctionManager()
					.getScheduledCommandSource().withWorld(world).withPosition(
							particlePos != null ? particlePos : playerSpawnPos.orElse(Vec3d.ZERO)
					);
			world.getServer().getCommandManager().executeWithPrefix(source, command);
		});
	}

	private ManageableServerBossBar createBossBar() {
		var bossbar = new ManageableServerBossBar(Text.literal(""), BossBar.Color.WHITE, BossBar.Style.PROGRESS);
		this.bossbar.ifPresent(bossbar::deserialize);
		return bossbar;
	}

	public void setCurrentBoss(ServerPlayerEntity player) {
		CutsceneHelper.forceOutOfCutscene(player);
		if (currentBoss != null) {
			BossBarHelper.transferBossBar(currentBoss, player);
		} else {
			if (bossbar.isPresent()) {
				BossBarHelper.setBossBar(player, createBossBar());
			} else {
				bossbar = BossBarHelper.getBossBar(player).map(ManageableServerBossBar::serialize);
				markDirty();
			}
		}
		beforeChangingBoss();

		currentBoss = player;
		currentBossID = Optional.of(player.getUuid());
		prepareBoss(player);
		markDirty();
	}

	public Vec3d getPlayerSpawnPos() {
		return playerSpawnPos.orElse(Vec3d.ZERO);
	}

	public EndBossPlayerConfig getConfig() {
		return config.get();
	}

	private void unPrepareBoss(ServerPlayerEntity player) {
		var xpLevel = player.experienceLevel;
		var xpProgress = player.experienceProgress;
		var totalXP = player.totalExperience;

		((AccessorPlayerEntity) player).callVanishCursedItems();
		player.getInventory().dropAll();

		PlayerDataHelper.loadPlayerData(
				player, BOSS_DATA_BACKUP, false,
				true, true
		);
		PlayerDataHelper.removePlayerData(player, BOSS_DATA_BACKUP);

		var scoreboard = player.getWorld().getScoreboard();
		scoreboard.clearTeam(player.getNameForScoreboard());
		if (bossPrevTeam.isPresent()) {
			var team = scoreboard.getTeam(bossPrevTeam.get());
			if (team != null) {
				scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);
			}
		}

		player.experienceProgress = xpProgress;
		player.totalExperience = totalXP;
		player.setExperienceLevel(xpLevel);
	}

	private static RegistryEntry<Enchantment> getEnchantment(
			RegistryWrapper.WrapperLookup lookup, RegistryKey<Enchantment> key
	) {
		return lookup.getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemEnchantmentsComponent prepareEnchantments(
			ItemEnchantmentsComponent existing,
			RegistryWrapper.WrapperLookup lookup,
			Map<RegistryKey<Enchantment>, Integer> enchantments
	) {
		ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(existing);
		for (var enchantment : enchantments.entrySet()) {
			builder.add(getEnchantment(lookup, enchantment.getKey()), enchantment.getValue());
		}
		return builder.build();
	}

	public static ItemEnchantmentsComponent prepareEnchantments(
			RegistryWrapper.WrapperLookup lookup,
			Map<RegistryKey<Enchantment>, Integer> enchantments
	) {
		return prepareEnchantments(
				ItemEnchantmentsComponent.DEFAULT, lookup, enchantments
		);
	}

	public static final PMap<RegistryKey<Enchantment>, Integer> CUSTOM_BOSS_ITEM_ENCHANTS = HashTreePMap.singleton(
			Enchantments.UNBREAKING, 3
	).plus(
			Enchantments.MENDING, 1
	);

	public static final PMap<RegistryKey<Enchantment>, Integer> BOSS_ARMOUR_ENCHANTS = CUSTOM_BOSS_ITEM_ENCHANTS.plus(
			Enchantments.PROTECTION, 4
	).plus(
			Enchantments.BINDING_CURSE, 1
	);

	public static final PMap<RegistryKey<Enchantment>, Integer> ENCHANTS_APPLIED_TO_ALL_ITEMS = HashTreePMap.singleton(
			Enchantments.VANISHING_CURSE, 1
	);

	private void mutateItemForBoss(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
		for (var c : config.get().componentsToApply()) {
			EndBossPlayerConfig.ItemEntry.mergeComponentIntoStack(stack, c, lookup);
		}
	}

	public static EquippableComponent addOverlay(EquippableComponent equippable, Identifier overlay) {
		return new EquippableComponent(
				equippable.slot(), equippable.equipSound(), equippable.assetId(), Optional.ofNullable(overlay),
				equippable.allowedEntities(), equippable.dispensable(), equippable.swappable(), equippable.damageOnHurt(),
				equippable.equipOnInteract(), equippable.canBeSheared(), equippable.shearingSound()
		);
	}

	private boolean isDisposableJunk(ItemStack stack, int pass) {
		if (pass > 2) return true;
		if (stack.contains(DataComponentTypes.ENCHANTMENTS)) return false;
		if (stack.contains(DataComponentTypes.BUNDLE_CONTENTS)) return false;
		if (stack.contains(DataComponentTypes.DEATH_PROTECTION) && pass < 2) return false;
		if (stack.contains(DataComponentTypes.POTION_CONTENTS) && pass < 2) return false;
		if (stack.contains(DataComponentTypes.FOOD) && pass < 1) return false;
		if (stack.contains(DataComponentTypes.CUSTOM_NAME) && pass < 1) return false;
		if (stack.contains(DataComponentTypes.TOOL) && pass < 1) return false;
		if (stack.contains(DataComponentTypes.BUCKET_ENTITY_DATA) && pass < 1) return false;
		if (stack.contains(DataComponentTypes.WEAPON) && pass < 1) return false;
		if (stack.getItem() instanceof TridentItem && pass < 1) return false;
		if (stack.getItem() instanceof BowItem && pass < 1) return false;
		if (stack.getItem() instanceof CrossbowItem && pass < 1) return false;
		if (stack.getItem() instanceof MaceItem && pass < 1) return false;
		return true;
	}

	private IntList getScrambledItemStackOrder(PlayerInventory inv) {
		return Util.shuffle(IntStream.range(0, inv.size()), inv.player.getRandom());
	}

	private void clearOneSlot(ServerPlayerEntity player) {
		var inv = player.getInventory();
		for (int p = 0; p < 3; p++) {
			var stacks = getScrambledItemStackOrder(player.getInventory());
			for (int slot : stacks) {
				var stack = inv.getStack(slot);
				if (!stack.isEmpty() && isDisposableJunk(stack, p)) {
					inv.setStack(slot, ItemStack.EMPTY);
					return;
				}
			}
		}
	}

	private static final ItemStack BLOCKER = new ItemStack(
			Items.BARRIER.getRegistryEntry(), 1, ComponentChanges.builder().add(
					DataComponentTypes.MAX_STACK_SIZE, 1
			).build()
	);

	private void insertStack(int slot, PlayerInventory inv, EndBossPlayerConfig.ItemEntry toInsert) {
		var itemToInsert = toInsert.getItem(inv, slot);
		if (!inv.getStack(slot).isEmpty()) {
			var stack = inv.getStack(slot);
			inv.setStack(slot, BLOCKER);
			inv.insertStack(stack);
		}
		inv.setStack(slot, itemToInsert);
	}

	private void insertStack(EquipmentSlot slot, PlayerInventory inv, EndBossPlayerConfig.ItemEntry toInsert) {
		var intSlot = PlayerInventoryHelper.getSlot(inv.player, slot);
		if (intSlot != -1) {
			insertStack(intSlot, inv, toInsert);
		}
	}

	private ItemStack createBossFood(int count) {
		return new ItemStack(
				Items.COOKED_BEEF.getRegistryEntry(), count,
				ComponentChanges.builder().add(
						DataComponentTypes.ITEM_NAME, Text.literal("Mutated Food")
				).build()
		);
	}

	private void prepareInventory(ServerPlayerEntity player) {

		int firstEmptySlot = player.getInventory().getEmptySlot();

		Map<EquipmentSlot, EndBossPlayerConfig.ItemEntry> equipment = config.get().equipment();

		PVector<EndBossPlayerConfig.ItemEntry> itemsToInsert = config.get().items();

		int expectedFood = 64 * 4;

		int foodAmount = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			var stack = player.getInventory().getStack(i);
			if (stack.contains(DataComponentTypes.FOOD)) {
				foodAmount += stack.get(DataComponentTypes.FOOD).nutrition();
			}
		}

		if (foodAmount < expectedFood) {
			int foodItemsToAdd = (expectedFood - foodAmount)/4;
			itemsToInsert = itemsToInsert.plus(
					new EndBossPlayerConfig.ItemEntry(
							createBossFood(foodItemsToAdd)
					)
			);
		}

		int itemsCount = equipment.size() + itemsToInsert.size();

		int numFreeSlots;
		if (firstEmptySlot == -1) {
			numFreeSlots = 0;
		} else {
			numFreeSlots = player.getInventory().size() - firstEmptySlot;
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

	private void prepareBoss(ServerPlayerEntity player) {
		player.getCommandTags().remove(CAN_BE_BOSS);
		PlayerDataHelper.detachPassengersBeforeSaving(player);
		PlayerDataHelper.saveCurrentPlayerData(player, BOSS_DATA_BACKUP);
		PlayerDataHelper.unloadAllPlayerConnectedEntities(player);
		PlayerDataHelper.getPlayerData(player, BOSS_DATA_BACKUP).ifPresent(data -> {
			data.remove(ManageableServerBossBar.BOSS_BAR);
		});

		PlayerDataHelper.setAdvancementTracker(player, ADVANCEMENT_STAT_STORAGE, true);
		PlayerDataHelper.setStatHandler(player, ADVANCEMENT_STAT_STORAGE, true);
		PlayerDataHelper.setAnnounceAdvancements(player, false);


		player.getCommandTags().removeAll(config.get().tagsToRemove());

		config.get().attributeModifiers().forEach((attribute, modifiers) -> {
			var instance = player.getAttributeInstance(attribute);
			if (instance == null) {
				Season4.LOGGER.error("Attempted to add modifiers for " + attribute + " but players do not support this attribute!");
			} else {
				instance.addPersistentModifiers(modifiers);
			}
		});
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(20);
		player.setGlowing(true);

		var inv = player.getInventory();
		prepareInventory(player);
		for (int i = 0; i < inv.size(); i++) {
			var stack = inv.getStack(i);
			if (!stack.isEmpty()) {
				mutateItemForBoss(stack, player.getRegistryManager());
			}
		}

		config.get().bossInitCommand().ifPresent(command -> {
			player.getServer().getCommandManager().executeWithPrefix(
					player.getCommandSource().withSilent().withLevel(2), command
			);
		});
	}

	public void switchToBoss(ServerPlayerEntity player) {
		//TODO Cutscene?
		if (particlePos == null) {
			particlePos = playerSpawnPos.orElse(Vec3d.ZERO);
		}
		particleTime = 80;
		setCurrentBoss(player);
	}

	public boolean hasBoss() {
		return currentBossID.isPresent();
	}

	public boolean isBoss(ServerPlayerEntity player) {
		return player == currentBoss || player.getUuid().equals(currentBossID.orElse(null));
	}

	public Text getBossName() {
		if (hasBoss()) {
			if (currentBoss != null) {
				return currentBoss.getName();
			} else {
				return Text.literal("Disconnected Player");
			}
		}
		return Text.literal("missingno");
	}

	public void findNewBoss(ServerWorld world) {
		var options = world.getPlayers().stream().filter(
				player -> player.getCommandTags().contains(CAN_BE_BOSS)
		).toList();
		if (!options.isEmpty()) {
			var player = options.get(world.getRandom().nextInt(options.size()));
			switchToBoss(player);
		} else {
			livesRemaining = 1;
			markDirty();
		}
	}

	private void triggerLowHP(ServerWorld world) {
		if (livesRemaining <= 1) {
			triggerEnd(world);
			return;
		} else {
			livesRemaining--;
			markDirty();
		}
		findNewBoss(world);
	}

	private boolean isSafeSpawn(ServerWorld world, Entity entity, Box box, BlockPos pos) {
		return world.isSpaceEmpty(entity, box, true) && Block.isFaceFullSquare(world.getBlockState(pos).getCollisionShape(world, pos), Direction.UP);
	}

	private Vec3d getPosAroundPos(ServerWorld world, Entity entity, Vec3d pos) {
		double maxRange = config.get().maxSpawnDist();
		double minRange = config.get().minSpawnDist();

		var box = entity.getDimensions(EntityPose.STANDING).getBoxAt(Vec3d.ZERO);

		BlockPos.Mutable blockPos = new BlockPos.Mutable();

		for (int t = 0; t < 50; t++) {
			double range = entity.getRandom().nextDouble() * (maxRange - minRange) + minRange;
			double angle = entity.getRandom().nextDouble() * Math.PI * 2;
			var xOffset = Math.cos(angle) * range;
			var zOffset = Math.sin(angle) * range;

			double x = pos.getX() + xOffset;
			int y = MathHelper.floor(pos.getY());
			double z = pos.getZ() + zOffset;

			if (isSafeSpawn(world, entity, box.offset(x, y, z), blockPos.set(x, y, z))) {
				return new Vec3d(x, y, z);
			}

			if (y >= world.getTopYInclusive() || y < world.getBottomY()) {
				y = world.getBottomY() + world.getHeight()/2;
			}

			for (int i = 1; i < world.getHeight()/2; i++) {
				int upY = y + i;
				if (isSafeSpawn(world, entity, box.offset(x, upY, z), blockPos.setY(upY-1)) && upY <= world.getTopYInclusive()) {
					return new Vec3d(x, upY, z);
				}
				int downY = y - i;
				if (isSafeSpawn(world, entity, box.offset(x, downY, z), blockPos.setY(downY-1)) && downY > world.getBottomY()) {
					return new Vec3d(x, downY, z);
				}
			}
		}

		return pos;
	}

	private TeleportTarget getTargetAroundPos(ServerWorld world, Entity entity, Vec3d pos) {
		return new TeleportTarget(
				world, getPosAroundPos(world, entity, pos),
				Vec3d.ZERO, entity.getRandom().nextFloat() * 360 - 180,
				0, TeleportTarget.NO_OP
		);
	}

	private TeleportTarget getNearBoss(ServerWorld world, Entity entity) {
		if (currentBoss != null) {
			return getTargetAroundPos(currentBoss.getWorld(), entity, currentBoss.getPos());
		} else {
			return getPlayerSpawnPoint(world, entity);
		}
	}

	public TeleportTarget getPlayerSpawnPoint(ServerWorld world, Entity entity) {
		return getTargetAroundPos(world, entity, playerSpawnPos.orElse(Vec3d.ZERO));
	}

	private void tickBossAlive(ServerWorld world) {
		var gameRules = world.getGameRules();
		if (!gameRules.getBoolean(GameRules.KEEP_INVENTORY)) {
			gameRules.get(GameRules.KEEP_INVENTORY).set(true, world.getServer());
		}
		if (gameRules.getBoolean(METAcraftGameRules.DO_ARMOR_DAMAGE)) {
			gameRules.get(METAcraftGameRules.DO_ARMOR_DAMAGE).set(false, world.getServer());
		}
		if (world.getScoreboard().getTeam(BOSS_TEAM) == null) {
			var team = world.getScoreboard().addTeam(BOSS_TEAM);
			team.setColor(Formatting.DARK_PURPLE);
		}

		if (particleTime > 0) {
			particleTime--;
			currentBoss.getWorld().spawnParticles(
					new TrailParticleEffect(
							currentBoss.getBoundingBox().getCenter(),
							-12648385, 20
					), true, true,
					particlePos.getX(), particlePos.getY(), particlePos.getZ(),
					100, 0.5, 0.5, 0.5, 1
			);
		} else {
			particlePos = currentBoss.getBoundingBox().getCenter();
		}
		if (this.playerSpawnPos.isEmpty()) {
			this.playerSpawnPos = Optional.of(currentBoss.getPos());
		}
		BossBarHelper.getBossBar(currentBoss).ifPresent(bar -> {
			if (bar != this.cachedBossBar) {
				this.cachedBossBar = bar;
				this.bossbar = Optional.of(bar.serialize());
				markDirty();
			}
		});

		var scoreboard = world.getScoreboard();
		var bossName = currentBoss.getNameForScoreboard();
		var bossTeam = scoreboard.getTeam(BOSS_TEAM);
		var currentTeam = scoreboard.getScoreHolderTeam(bossName);
		if (currentTeam != bossTeam) {
			scoreboard.addScoreHolderToTeam(bossName, bossTeam);
			if (currentTeam != null) {
				bossPrevTeam = Optional.of(currentTeam.getName());
				markDirty();
			}
		}

		for (var player : world.getPlayers()) {
			if (player != currentBoss && player.distanceTo(currentBoss) > config.get().maxDistanceFromBoss()) {
				player.teleportTo(getNearBoss(currentBoss.getWorld(), player));
			}
		}
		if (currentBoss.getPos().distanceTo(playerSpawnPos.orElse(null)) > config.get().maxDistanceFromSpawn() || currentBoss.getWorld() != world) {
			currentBoss.teleportTo(getPlayerSpawnPoint(world, currentBoss));
		}
		if (currentBoss.getY() < world.getBottomY()) {
			currentBoss.teleportTo(getPlayerSpawnPoint(world, currentBoss));
		}

		config.get().musicForBoss().ifPresent(music -> {
			if (!MusicHelper.isMusicPlaying(currentBoss, music)) {
				MusicHelper.playMusic(currentBoss, music, player -> player == currentBoss);
			}
		});
	}

	private void initConfig(ServerWorld world) {
		if (config == null) {
			config = EndBossPlayerConfig.CONFIG_BUILDER.build(
					WorldHelper.getSession(world.getServer()).getWorldDirectory(world.getRegistryKey()).resolve(
							"end-boss-player.json"
					),
					world.getRegistryManager()
			);
		}
	}

	public void tick(ServerWorld world) {
		initConfig(world);
		trackBoss(world);
		if (currentBoss != null) {
			tickBossAlive(world);
		}
		for (var oldBoss : oldBossesToReset) {
			var player = world.getServer().getPlayerManager().getPlayer(oldBoss);
			if (player != null && player.isAlive() && !CutsceneHelper.isInCutscene(player)) {
				unPrepareBoss(player);
				oldBossesToReset = oldBossesToReset.minus(oldBoss);
				markDirty();
			}
		}
	}

	private void trackBoss(ServerWorld world) {
		if (currentBoss == null && currentBossID.isPresent()) {
			currentBoss = world.getServer().getPlayerManager().getPlayer(currentBossID.get());
		}
		if (currentBoss != null) {
			if (currentBoss.isDisconnected()) {
				currentBoss = null;
				return;
			}
			if (currentBoss.isDead() || (currentBoss.getHealth() / currentBoss.getMaxHealth()) <= config.get().getHealthPercent(livesRemaining)) {
				triggerLowHP(world);
			}
		}
	}
}
