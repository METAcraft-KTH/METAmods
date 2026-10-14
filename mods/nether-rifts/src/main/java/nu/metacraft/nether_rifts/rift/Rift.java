package nu.metacraft.nether_rifts.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.NetherRiftsConfig;
import nu.metacraft.nether_rifts.RiftMessages;
import nu.metacraft.nether_rifts.raid.RiftRaid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One rift: where it is, how far along it is, and (for a raid) which wave and how long things have taken. */
public final class Rift {

	public static final Codec<Rift> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(r -> r.id),
			RiftShape.CODEC.fieldOf("shape").forGetter(r -> r.shape),
			Codec.BOOL.fieldOf("crying").forGetter(r -> r.crying),
			Tier.CODEC.fieldOf("tier").forGetter(r -> r.tier),
			Stage.CODEC.fieldOf("stage").forGetter(r -> r.stage),
			Codec.INT.fieldOf("stage_ticks").forGetter(r -> r.stageTicks),
			Codec.INT.fieldOf("countdown").forGetter(r -> r.countdown),
			Codec.INT.fieldOf("wave").forGetter(r -> r.wave),
			Codec.INT.fieldOf("wave_size").forGetter(r -> r.waveSize),
			Codec.INT.fieldOf("wave_ticks").forGetter(r -> r.waveTicks),
			Codec.INT.fieldOf("abandon_ticks").forGetter(r -> r.abandonTicks)
	).apply(i, Rift::new));

	private final UUID id;
	private final RiftShape shape;
	private final boolean crying;
	private final Tier tier;
	private final List<BlockPos> blockList;
	private Stage stage;
	int stageTicks;
	int countdown;
	int wave;
	/** How many mobs the current wave started with (the boss bar's 100%). Written by RiftRaid. */
	public int waveSize;
	public int waveTicks;
	/** Ticks with no participant in range; RiftRaid collapses the raid when it reaches abandon_after. */
	public int abandonTicks;
	/** Ticks a wave has looked cleared while its surroundings weren't loaded; not saved. Written by RiftRaid. */
	public int clearedTicks;
	@Nullable private RiftVisual visual;
	@Nullable ServerBossEvent bossBar;
	/** The Rift Captain's own bar; not saved, RiftRaid recreates it from the tagged Captain. */
	@Nullable ServerBossEvent captainBar;

	private Rift(UUID id, RiftShape shape, boolean crying, Tier tier, Stage stage, int stageTicks,
			int countdown, int wave, int waveSize, int waveTicks, int abandonTicks) {
		this.id = id;
		this.shape = shape;
		this.crying = crying;
		this.tier = tier;
		this.blockList = new ArrayList<>(shape.blocks());
		this.stage = stage;
		this.stageTicks = stageTicks;
		this.countdown = countdown;
		this.wave = wave;
		this.waveSize = waveSize;
		this.waveTicks = waveTicks;
		this.abandonTicks = abandonTicks;
	}

	Rift(RiftShape shape, boolean crying) {
		this(UUID.randomUUID(), shape, crying, Tier.of(shape.size(), NetherRiftsConfig.getInstance().tiers()), Stage.LIT, 0, 0, 0, 0, 0, 0);
	}

	public UUID id() {
		return id;
	}

	public RiftShape shape() {
		return shape;
	}

	public boolean crying() {
		return crying;
	}

	public Tier tier() {
		return tier;
	}

	public Stage stage() {
		return stage;
	}

	public int wave() {
		return wave;
	}

	@Nullable
	public RiftVisual visual() {
		return visual;
	}

	@Nullable
	public ServerBossEvent bossBar() {
		return bossBar;
	}

	@Nullable
	public ServerBossEvent captainBar() {
		return captainBar;
	}

	public void setWave(int wave, int size) {
		this.wave = wave;
		this.waveSize = size;
		this.waveTicks = 0;
	}

	public void setBossBar(ServerBossEvent bar) {
		this.bossBar = bar;
	}

	public void setCaptainBar(@Nullable ServerBossEvent bar) {
		this.captainBar = bar;
	}

	public Vec3 centre() {
		return shape.centre();
	}

	public BlockPos randomBlock(RandomSource random) {
		return blockList.get(random.nextInt(blockList.size()));
	}

	public void setStage(ServerLevel level, Stage stage) {
		this.stage = stage;
		this.stageTicks = 0;
		if (visual != null) {
			visual.setStage(stage);
		}
	}

	public void placeBlocks(ServerLevel level) {
		var state = RiftBlocks.RIFT.defaultBlockState().setValue(RiftBlock.AXIS, shape.axis());
		for (var pos : shape.blocks()) {
			level.setBlock(pos, state, Block.UPDATE_ALL);
		}
	}

	/** Rift blocks saved before they had an axis load facing X; clients see them as portals, so turn them. */
	private void alignBlocks(ServerLevel level) {
		for (var pos : shape.blocks()) {
			var state = level.getBlockState(pos);
			if (state.is(RiftBlocks.RIFT) && state.getValue(RiftBlock.AXIS) != shape.axis()) {
				level.setBlock(pos, state.setValue(RiftBlock.AXIS, shape.axis()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
	}

	public void clearBlocks(ServerLevel level) {
		for (var pos : shape.blocks()) {
			if (level.getBlockState(pos).is(RiftBlocks.RIFT)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	public void destroyVisual() {
		if (visual != null) {
			visual.destroy();
			visual = null;
		}
	}

	/** The realm steadied while this rift waited: it becomes the working portal it would have been. */
	public void becomePortal(ServerLevel level, RiftData data) {
		var portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, shape.axis());
		destroyVisual();
		data.remove(this);
		for (var pos : shape.blocks()) {
			level.setBlock(pos, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}

	/** A waiting rift goes away quietly: its blocks and visual, nothing else. */
	public void fade(ServerLevel level, RiftData data) {
		clearBlocks(level);
		destroyVisual();
		data.remove(this);
	}

	public void tick(ServerLevel level, RiftData data) {
		if (!level.isPositionEntityTicking(shape.centreBlock())) {
			// Unloaded: nothing moves, but nobody is holding a raid either.
			if (stage.raiding() && ++abandonTicks >= NetherRiftsConfig.getInstance().timers().abandonAfter()) {
				RiftRaid.collapse(level, this, data);
			}
			return;
		}
		// Polymer detaches a chunk's holders when the chunk unloads; the rift draws itself again once it ticks.
		if (visual != null && visual.getAttachment() == null) {
			visual.destroy();
			visual = null;
		}
		if (visual == null) {
			alignBlocks(level);
			visual = RiftVisual.attach(level, this);
		}
		stageTicks++;
		if (stageTicks % 20 == 0 && !shape.frameIntact(level)) {
			if (stage.raiding()) {
				RiftRaid.collapse(level, this, data);
			} else {
				fade(level, data);
			}
			return;
		}
		var timers = NetherRiftsConfig.getInstance().timers();
		switch (stage) {
			case UNSTABLE -> {
				if (stageTicks >= timers.unstableFallback()) {
					setStage(level, Stage.LIT);
					playAt(level, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.8f);
				}
			}
			case TORN -> {
				RiftRaid.tick(level, this, data);
				// An abandoned rift collapses inside RiftRaid.tick; it mustn't then spawn a wave for nobody.
				if (data.byId(id).isPresent() && --countdown <= 0) {
					setStage(level, Stage.RAID);
					RiftRaid.startWave(level, this, 1);
				}
			}
			case RAID -> RiftRaid.tick(level, this, data);
			default -> {
			}
		}
	}

	/** One more light: lit becomes unstable, unstable tears open (unless the server is at its raid cap). */
	public boolean light(ServerLevel level) {
		switch (stage) {
			case LIT -> {
				setStage(level, Stage.UNSTABLE);
				playAt(level, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5f);
				RiftMessages.say(level, this, "The rift strains. One more and it tears open.");
				return true;
			}
			case UNSTABLE -> {
				if (RiftData.activeRaids(level.getServer()) >= NetherRiftsConfig.getInstance().activeRaidCap()) {
					RiftMessages.say(level, this, "The realm resists. Another tear is draining it.");
					return false;
				}
				tear(level);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** Torn open: the countdown to the first wave starts. */
	public void tear(ServerLevel level) {
		setStage(level, Stage.TORN);
		countdown = NetherRiftsConfig.getInstance().timers().countdown();
		var c = centre();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RAID_HORN, SoundSource.HOSTILE, 8.0f, 0.6f);
		RiftMessages.say(level, this, "The rift tears open!");
	}

	private void playAt(ServerLevel level, net.minecraft.sounds.SoundEvent sound, float pitch) {
		var c = centre();
		level.playSound(null, c.x, c.y, c.z, sound, SoundSource.BLOCKS, 1.0f, pitch);
	}
}
