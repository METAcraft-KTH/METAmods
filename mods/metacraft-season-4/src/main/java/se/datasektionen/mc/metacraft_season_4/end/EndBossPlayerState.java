package se.datasektionen.mc.metacraft_season_4.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Block;
import net.minecraft.component.*;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
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
import net.minecraft.world.TeleportTarget;
import org.pcollections.*;
import se.datasektionen.mc.cutscenes.util.helper.CutsceneHelper;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.gamerules.METAcraftGameRules;
import se.datasektionen.mc.metacraft_core.music.ServerBossBarWithMusic;
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

	private static final String KEY = METAcraftCore.NAMESPACE + "-end-boss-player";

	private static final Type<EndBossPlayerState> TYPE = new Type<>(
			EndBossPlayerState::new, EndBossPlayerState::fromNBT, null
	);

	private static final String CAN_BE_BOSS = "metacraft.end_player_boss.can_be_boss";

	private static final MapCodec<Optional<UUID>> CURRENT_BOSS_ID = Uuids.STRICT_CODEC.optionalFieldOf("current_boss");
	private static final MapCodec<NbtCompound> BOSSBAR = NbtCompound.CODEC.optionalFieldOf("boss_bar", new NbtCompound());
	private static final MapCodec<PSet<UUID>> OLD_BOSSES_TO_RESET = ExtraCodecs.<UUID, PSet<UUID>>createPCollectionCodec(Uuids.STRICT_CODEC, HashTreePSet.empty()).optionalFieldOf("old_bosses_to_reset", HashTreePSet.empty());
	private static final MapCodec<Integer> LIVES_REMAINING = Codec.INT.optionalFieldOf("lives_remaining", 3);
	private static final MapCodec<Optional<String>> BOSS_PREV_TEAM = Codec.STRING.optionalFieldOf("boss_prev_team");
	private static final MapCodec<Optional<Vec3d>> PLAYER_SPAWN_POS = Vec3d.CODEC.optionalFieldOf("player_spawn_pos");

	private static final Identifier BOSS_DATA_BACKUP = Season4.getID("player_data_backup_before_boss");
	private static final Identifier BOSS_HEALTH = Season4.getID("boss_health");

	private static final String BOSS_TEAM = "metacraft.end_player_boss.team";

	private ConfigContainer<EndBossPlayerConfig> config;

	private UUID currentBossID;
	private PSet<UUID> oldBossesToReset = HashTreePSet.empty();
	private ServerPlayerEntity currentBoss;
	private String bossPrevTeam;
	private ServerBossBarWithMusic bossbar;
	private int livesRemaining = 3;
	private Vec3d playerSpawnPos;

	private Vec3d particlePos;
	private int particleTime = -1;

	private static EndBossPlayerState fromNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		var data = new EndBossPlayerState();
		data.readNBT(nbt, lookup);
		return data;
	}

	public static Optional<EndBossPlayerState> getInstance(ServerWorld world) {
		return Optional.ofNullable(world.getPersistentStateManager().get(TYPE, KEY));
	}

	public static void initBossState(ServerWorld world, ServerPlayerEntity boss, Vec3d spawnPos) {
		var instance = world.getPersistentStateManager().getOrCreate(TYPE, KEY);
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
		this.playerSpawnPos = pos;
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		var ops = registries.getOps(NbtOps.INSTANCE);
		var builder = ops.mapBuilder();

		if (currentBossID != null) {
			builder = CURRENT_BOSS_ID.encode(Optional.of(currentBossID), ops, builder);
		}
		if (bossbar != null) {
			builder = BOSSBAR.encode(bossbar.writeNBT(new NbtCompound(), registries), ops, builder);
		}
		if (!oldBossesToReset.isEmpty()) {
			builder = OLD_BOSSES_TO_RESET.encode(oldBossesToReset, ops, builder);
		}
		if (bossPrevTeam != null) {
			builder = BOSS_PREV_TEAM.encode(Optional.of(bossPrevTeam), ops, builder);
		}
		if (playerSpawnPos != null) {
			builder = PLAYER_SPAWN_POS.encode(Optional.of(playerSpawnPos), ops, builder);
		}

		builder = LIVES_REMAINING.encode(livesRemaining, ops, builder);

		return (NbtCompound) builder.build(nbt).resultOrPartial(Season4.LOGGER::error).orElse(nbt);
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		var ops = registries.getOps(NbtOps.INSTANCE);
		ops.getMap(nbt).resultOrPartial(Season4.LOGGER::error).ifPresent(data -> {
			CURRENT_BOSS_ID.decode(ops, data).resultOrPartial(Season4.LOGGER::error).ifPresent(currentBoss -> this.currentBossID = currentBoss.orElse(null));
			BOSSBAR.decode(ops, data).resultOrPartial(Season4.LOGGER::error).ifPresent(bossbar -> {
				this.bossbar = new ServerBossBarWithMusic(Text.literal(""), BossBar.Color.WHITE, BossBar.Style.PROGRESS);
				this.bossbar.readNBT(bossbar, registries);
			});
			OLD_BOSSES_TO_RESET.decode(ops, data).resultOrPartial(Season4.LOGGER::error).ifPresent(oldBosses -> oldBossesToReset = oldBosses);
			BOSS_PREV_TEAM.decode(ops, data).resultOrPartial(Season4.LOGGER::error).ifPresent(prevTeam -> bossPrevTeam = prevTeam.orElse(null));
			LIVES_REMAINING.decode(ops, data).resultOrPartial(Season4.LOGGER::error).ifPresent(s -> livesRemaining = s);
			PLAYER_SPAWN_POS.decode(ops, data).resultOrPartial(Season4.LOGGER::error).ifPresent(p -> playerSpawnPos = p.orElse(null));
		});
	}

	private void beforeChangingBoss() {
		if (currentBossID != null) {
			oldBossesToReset = oldBossesToReset.plus(currentBossID);
			markDirty();
		}
	}

	private void triggerEnd(ServerWorld world) {
		beforeChangingBoss();
		currentBoss = null;
		currentBossID = null;
		markDirty();
		config.get().bossDefeatedCommand().ifPresent(command -> {
			var source = world.getServer().getCommandFunctionManager()
					.getScheduledCommandSource().withWorld(world).withPosition(
							particlePos != null ? particlePos : playerSpawnPos
					);
			world.getServer().getCommandManager().executeWithPrefix(source, command);
		});
	}

	public void setCurrentBoss(ServerPlayerEntity player) {
		CutsceneHelper.forceOutOfCutscene(player);
		if (currentBoss != null) {
			BossBarHelper.transferBossBar(currentBoss, player);
		} else {
			if (bossbar != null) {
				BossBarHelper.setBossBar(player, bossbar);
			} else {
				bossbar = BossBarHelper.getBossBar(player).orElse(null);
				markDirty();
			}
		}
		beforeChangingBoss();

		currentBoss = player;
		currentBossID = player.getUuid();
		prepareBoss(player);
		markDirty();
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
		if (bossPrevTeam != null) {
			var team = scoreboard.getTeam(bossPrevTeam);
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
				equippable.allowedEntities(), equippable.dispensable(), equippable.swappable(), equippable.damageOnHurt()
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
		if (stack.getItem() instanceof SwordItem && pass < 1) return false;
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
			data.remove(ServerBossBarWithMusic.BOSS_BAR);
		});


		player.getCommandTags().removeAll(config.get().tagsToRemove());

		player.getAttributeInstance(EntityAttributes.MAX_HEALTH).addPersistentModifier(
				new EntityAttributeModifier(
						BOSS_HEALTH, 480,
						EntityAttributeModifier.Operation.ADD_VALUE
				)
		);
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
			particlePos = playerSpawnPos;
		}
		particleTime = 80;
		setCurrentBoss(player);
	}

	public boolean hasBoss() {
		return currentBossID != null;
	}

	public boolean isBoss(ServerPlayerEntity player) {
		return player == currentBoss || player.getUuid().equals(currentBossID);
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
			return getTargetAroundPos(currentBoss.getServerWorld(), entity, currentBoss.getPos());
		} else {
			return getPlayerSpawnPoint(world, entity);
		}
	}

	public TeleportTarget getPlayerSpawnPoint(ServerWorld world, Entity entity) {
		return getTargetAroundPos(world, entity, playerSpawnPos);
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
			currentBoss.getServerWorld().spawnParticles(
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
		if (this.playerSpawnPos == null) {
			this.playerSpawnPos = currentBoss.getPos();
		}
		BossBarHelper.getBossBar(currentBoss).ifPresent(bar -> {
			if (bar != this.bossbar) {
				this.bossbar = bar;
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
				bossPrevTeam = currentTeam.getName();
				markDirty();
			}
		}

		for (var player : world.getPlayers()) {
			if (player != currentBoss && player.distanceTo(currentBoss) > config.get().maxDistanceFromBoss()) {
				player.teleportTo(getNearBoss(currentBoss.getServerWorld(), player));
			}
		}
		if (currentBoss.getPos().distanceTo(playerSpawnPos) > config.get().maxDistanceFromSpawn() || currentBoss.getWorld() != world) {
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
		if (currentBoss == null && currentBossID != null) {
			currentBoss = world.getServer().getPlayerManager().getPlayer(currentBossID);
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
