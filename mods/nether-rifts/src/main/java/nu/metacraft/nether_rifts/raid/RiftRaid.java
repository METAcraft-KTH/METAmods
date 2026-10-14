package nu.metacraft.nether_rifts.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRifts;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.RiftMessages;
import nu.metacraft.nether_rifts.mob.BurningPatches;
import nu.metacraft.nether_rifts.mob.RiftEntities;
import nu.metacraft.nether_rifts.mob.WarGhast;
import nu.metacraft.nether_rifts.rift.Rift;
import nu.metacraft.nether_rifts.rift.RiftData;
import nu.metacraft.nether_rifts.rift.Stage;
import nu.metacraft.nether_rifts.rift.Tier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A torn rift's raid: its waves, who takes part, the boss bar, strays, and how it ends. */
public final class RiftRaid {

	/** How long a wave may look cleared with its surroundings not entity-loaded before it counts as cleared anyway. */
	public static final int UNLOADED_GRACE = 200;

	/** How far from a rift's centre its mobs may come through when there's no room right in front of it. */
	private static final int NEARBY_RANGE = 8;

	/** Offsets within {@link #NEARBY_RANGE} blocks, nearest first. */
	private static final List<BlockPos> NEARBY = nearby();

	private RiftRaid() {}

	public static void tick(ServerLevel level, Rift rift, RiftData data) {
		var config = NetherRiftsConfig.getInstance();
		var players = participants(level, rift);
		var bar = bossBar(rift);
		if (level.getGameTime() % 20 == 0) {
			for (var shown : List.copyOf(bar.getPlayers())) {
				if (!players.contains(shown)) {
					bar.removePlayer(shown);
				}
			}
			players.forEach(bar::addPlayer);
		}
		if (players.isEmpty()) {
			if (++rift.abandonTicks >= config.timers().abandonAfter()) {
				RiftMessages.say(level, rift, "Nobody held the rift. It collapses.");
				collapse(level, rift, data);
				return;
			}
		} else {
			rift.abandonTicks = 0;
		}
		if (rift.stage() != Stage.RAID) {
			return;
		}
		var alive = aliveMobs(level, rift);
		rift.waveTicks++;
		if (rift.waveTicks == config.timers().glowAfter()) {
			alive.forEach(mob -> mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false)));
		}
		if (level.getGameTime() % 20 == 0) {
			tether(level, rift, alive, config.ranges().tether());
		}
		updateCaptainBar(level, rift, alive, players);
		bar.setProgress(rift.waveSize == 0 ? 0f : Mth.clamp(alive.size() / (float) rift.waveSize, 0f, 1f));
		advanceIfCleared(level, rift, data, alive, () -> surroundingsLoaded(level, rift, config.ranges().tether()));
	}

	/** The Captain's own red bar, shown to the participants while the tagged Captain lives. */
	private static void updateCaptainBar(ServerLevel level, Rift rift, List<LivingEntity> alive, List<ServerPlayer> players) {
		var captain = alive.stream().filter(e -> e.entityTags().contains(RiftMobs.CAPTAIN_TAG)).findFirst();
		var bar = rift.captainBar();
		if (captain.isEmpty()) {
			if (bar != null) {
				bar.removeAllPlayers();
				rift.setCaptainBar(null);
			}
			return;
		}
		if (bar == null) {
			bar = new ServerBossEvent(Mth.createInsecureUUID(RandomSource.create()), captain.get().getDisplayName(),
					BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
			rift.setCaptainBar(bar);
		}
		bar.setProgress(Mth.clamp(captain.get().getHealth() / captain.get().getMaxHealth(), 0f, 1f));
		for (var shown : List.copyOf(bar.getPlayers())) {
			if (!players.contains(shown)) {
				bar.removePlayer(shown);
			}
		}
		players.forEach(bar::addPlayer);
	}

	/**
	 * When no mob of the wave is left, the next wave (or victory). An unloaded mob isn't a dead one, so with
	 * part of the tether range not entity-loaded the wave holds, but only for {@link #UNLOADED_GRACE} ticks:
	 * a cleared raid must never stall.
	 */
	public static void advanceIfCleared(ServerLevel level, Rift rift, RiftData data, List<LivingEntity> alive, BooleanSupplier surroundingsLoaded) {
		if (rift.stage() != Stage.RAID) {
			return;
		}
		if (!alive.isEmpty()) {
			rift.clearedTicks = 0;
			return;
		}
		if (!surroundingsLoaded.getAsBoolean() && ++rift.clearedTicks < UNLOADED_GRACE) {
			return;
		}
		rift.clearedTicks = 0;
		if (rift.wave() >= rift.tier().waves()) {
			victory(level, rift, data);
		} else {
			startWave(level, rift, rift.wave() + 1);
		}
	}

	public static void startWave(ServerLevel level, Rift rift, int wave) {
		var config = NetherRiftsConfig.getInstance();
		int players = Math.max(1, participants(level, rift).size());
		var counts = WaveTable.counts(rift.tier(), wave, players, level.getRandom()::nextInt, config.trooperScalingPerPlayer(), config.trooperScalingCap());
		int spawned = 0;
		for (var entry : counts.entrySet()) {
			var spawnSlot = entry.getKey() == Slot.HEAVY && rift.tier() == Tier.LARGE && wave >= 5 ? Slot.BOMBARDIER : entry.getKey();
			for (int i = 0; i < entry.getValue(); i++) {
				spawned += spawnOne(level, rift, spawnSlot).size();
			}
		}
		rift.setWave(wave, spawned);
		bossBar(rift).setName(waveName(rift));
		bossBar(rift).setProgress(1f);
	}

	private static Component waveName(Rift rift) {
		return Component.literal("Rift Raid: Wave " + rift.wave() + "/" + rift.tier().waves());
	}

	/**
	 * One pick for the slot, come through the rift where it fits (with whatever rides it), pushed away from the
	 * rift; returns every entity spawned (a mount and its riders).
	 */
	public static List<Entity> spawnOne(ServerLevel level, Rift rift, Slot slot) {
		var spawned = RiftMobs.spawn(level, rift.id(), slot, rift.shape().centreBlock(), Vec3.ZERO);
		if (spawned.isEmpty()) {
			return spawned;
		}
		var root = spawned.getFirst().getRootVehicle();
		// seats the riders, so the stack's whole height is known before it's placed
		root.teleportTo(root.getX(), root.getY(), root.getZ());
		var spot = clearSpot(level, rift, root, randomSide(level, rift));
		root.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		var normal = normal(rift);
		var c = rift.centre();
		double away = Math.signum((spot.getX() + 0.5 - c.x) * normal.getStepX() + (spot.getZ() + 0.5 - c.z) * normal.getStepZ());
		root.setDeltaMovement(normal.getStepX() * away * 0.35, 0.2, normal.getStepZ() * away * 0.35);
		return spawned;
	}

	private static Direction normal(Rift rift) {
		return rift.shape().axis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
	}

	private static Direction randomSide(ServerLevel level, Rift rift) {
		var normal = normal(rift);
		return level.getRandom().nextBoolean() ? normal : normal.getOpposite();
	}

	private static void tether(ServerLevel level, Rift rift, List<LivingEntity> alive, int range) {
		var centre = rift.centre();
		for (var mob : alive) {
			if (mob.getVehicle() == null && mob.position().distanceToSqr(centre) > (double) range * range) {
				var back = clearSpot(level, rift, mob, randomSide(level, rift));
				level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.4, 0.8, 0.4, 0.5);
				mob.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
			}
		}
	}

	/**
	 * Where a mob and whatever rides it (or, for a war ghast, hangs under it) fit, clear of blocks and of the rift
	 * itself: in front of the rift (a few blocks out on either side, ghasts higher and further), else the nearest spot
	 * within {@link #NEARBY_RANGE} blocks of its centre, else the first spot straight above it, else the surface over it.
	 */
	private static BlockPos clearSpot(ServerLevel level, Rift rift, Entity root, Direction side) {
		var stack = root instanceof WarGhast ghast ? ghast.stackBox() : root.getBoundingBox();
		for (var rider : root.getIndirectPassengers()) {
			stack = stack.minmax(rider.getBoundingBox());
		}
		// the stack's box with the root's feet at the origin
		var shape = stack.move(-root.getX(), -root.getY(), -root.getZ());
		var random = level.getRandom();
		var centre = rift.shape().centreBlock();
		for (var dir : new Direction[] {side, side.getOpposite()}) {
			if (root instanceof Ghast) {
				// as high again as anything hanging under it (a war ghast's deck)
				int below = (int) Math.ceil(-Math.min(0, shape.minY));
				for (int out = 4; out <= 8; out += 2) {
					var pos = centre.relative(dir, out).above(2 + below);
					if (fits(level, rift, shape, pos)) {
						return pos;
					}
				}
			} else {
				for (int out = 1; out <= 4; out++) {
					var pos = rift.randomBlock(random).relative(dir, out);
					if (fits(level, rift, shape, pos)) {
						return pos;
					}
				}
			}
		}
		for (var offset : NEARBY) {
			var pos = centre.offset(offset);
			if (fits(level, rift, shape, pos)) {
				return pos;
			}
		}
		int top = rift.shape().blocks().stream().mapToInt(BlockPos::getY).max().orElse(centre.getY());
		for (int y = top + 1; y + shape.maxY < level.getMaxY(); y++) {
			var pos = new BlockPos(centre.getX(), y, centre.getZ());
			if (fits(level, rift, shape, pos)) {
				return pos;
			}
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, centre);
	}

	/** Whether a stack's box, its root's feet at the middle of {@code pos}, touches no block and no rift block. */
	private static boolean fits(ServerLevel level, Rift rift, AABB shape, BlockPos pos) {
		var box = shape.move(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (!level.noCollision(box)) {
			return false;
		}
		// rift blocks don't collide, but nothing should come through standing in the rift's plane
		return BlockPos.betweenClosedStream(box.deflate(1.0E-7)).noneMatch(rift.shape().blocks()::contains);
	}

	private static List<BlockPos> nearby() {
		var out = new ArrayList<BlockPos>();
		int r = NEARBY_RANGE;
		for (var p : BlockPos.betweenClosed(-r, -r, -r, r, r, r)) {
			if (p.distSqr(BlockPos.ZERO) <= r * r) {
				out.add(p.immutable());
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.distSqr(BlockPos.ZERO)));
		return List.copyOf(out);
	}

	/** Whether entities are loaded across the tether range (they follow view distance, not simulation distance). */
	private static boolean surroundingsLoaded(ServerLevel level, Rift rift, int range) {
		var c = rift.shape().centreBlock();
		var min = ChunkPos.containing(c.offset(-range, 0, -range));
		var max = ChunkPos.containing(c.offset(range, 0, range));
		for (int x = min.x(); x <= max.x(); x++) {
			for (int z = min.z(); z <= max.z(); z++) {
				if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) {
					return false;
				}
			}
		}
		return true;
	}

	public static List<LivingEntity> aliveMobs(ServerLevel level, Rift rift) {
		return level.getEntitiesOfClass(LivingEntity.class, reach(rift), e -> e.isAlive() && rift.id().equals(e.getAttached(RiftMobs.RIFT)));
	}

	/** Where a rift's mobs (and what they throw) may be: past the tether, with room for a mob on its way back. */
	private static AABB reach(Rift rift) {
		double range = NetherRiftsConfig.getInstance().ranges().tether() + 48;
		return new AABB(rift.centre(), rift.centre()).inflate(range);
	}

	public static List<ServerPlayer> participants(ServerLevel level, Rift rift) {
		double range = NetherRiftsConfig.getInstance().ranges().participants();
		var centre = rift.centre();
		return level.players().stream()
				.filter(p -> p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(centre) <= range * range)
				.toList();
	}

	public static void victory(ServerLevel level, Rift rift, RiftData data) {
		var c = rift.centre();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 2.0f, 0.7f);
		RiftMessages.say(level, rift, "The rift closes. The realm takes back what it lost.");
		var key = ResourceKey.create(Registries.LOOT_TABLE, NetherRifts.getID("victory/" + rift.tier().getSerializedName()));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, c).create(LootContextParamSets.CHEST);
		for (var stack : table.getRandomItems(params)) {
			var item = new ItemEntity(level, c.x, c.y, c.z, stack);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		// A natural victory has no mobs left; /rifts close may.
		end(level, rift, data, true);
	}

	public static void collapse(ServerLevel level, Rift rift, RiftData data) {
		var c = rift.centre();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		end(level, rift, data, true);
	}

	/**
	 * Takes the rift down: its mobs (if asked), blocks, frame (as config says), boss bar and visual. Only frame
	 * positions still holding a frame material change: whatever a player put in a broken frame stays. A frame
	 * block another live rift shares stays too (its frame must stay whole); the last rift using it converts it.
	 */
	public static void end(ServerLevel level, Rift rift, RiftData data, boolean discardMobs) {
		if (discardMobs) {
			for (var mob : aliveMobs(level, rift)) {
				level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.4, 0.8, 0.4, 0.5);
				mob.discard();
			}
			// bombs still falling go with the war ghasts that dropped them, and so do their ground warnings and patches
			for (var bomb : level.getEntities(RiftEntities.MAGMA_BOMB, reach(rift), b -> rift.id().equals(b.getAttached(RiftMobs.RIFT)))) {
				bomb.discard();
			}
			BurningPatches.clear(level, rift.id());
		}
		rift.clearBlocks(level);
		var collapse = NetherRiftsConfig.getInstance().frameCollapse();
		for (var pos : rift.shape().frame()) {
			var block = level.getBlockState(pos).getBlock();
			if (!rift.shape().frameMaterials().contains(block) || data.claimedByOther(pos, rift)) {
				continue;
			}
			var to = rift.crying() ? Blocks.AIR : collapse.getOrDefault(block, Blocks.AIR);
			level.setBlockAndUpdate(pos, to.defaultBlockState());
		}
		if (rift.bossBar() != null) {
			rift.bossBar().removeAllPlayers();
		}
		if (rift.captainBar() != null) {
			rift.captainBar().removeAllPlayers();
			rift.setCaptainBar(null);
		}
		rift.destroyVisual();
		data.remove(rift);
	}

	/** The rift's boss bar; after a restart (bars aren't saved) it comes back already naming the wave. */
	private static ServerBossEvent bossBar(Rift rift) {
		if (rift.bossBar() == null) {
			var name = rift.stage() == Stage.RAID ? waveName(rift) : Component.literal("Rift Raid");
			rift.setBossBar(new ServerBossEvent(Mth.createInsecureUUID(RandomSource.create()), name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
		}
		return rift.bossBar();
	}
}
