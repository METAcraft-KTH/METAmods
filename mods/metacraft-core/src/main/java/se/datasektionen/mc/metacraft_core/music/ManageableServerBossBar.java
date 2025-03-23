package se.datasektionen.mc.metacraft_core.music;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.dynamic.Codecs;

import java.util.Optional;
import java.util.stream.Stream;

public class ManageableServerBossBar extends ServerBossBar {

	public static final String BOSS_BAR = "BossBar";

	private boolean trackingHealth = true;
	private boolean trackingName = true;

	private int value;
	private int max;

	private final BossBarMusicHandler handler = new BossBarMusicHandler(this);

	public ManageableServerBossBar(Text displayName, Color color, Style style) {
		super(displayName, color, style);
	}

	public void setMusic(MusicEntry music) {
		this.handler.setMusic(music);
	}

	public Optional<MusicEntry> getMusic() {
		return this.handler.getMusic();
	}

	public void addPlayer(ServerPlayerEntity player) {
		super.addPlayer(player);
		handler.onPlayerAdded(player);
	}

	public void removePlayer(ServerPlayerEntity player) {
		super.removePlayer(player);
		handler.onPlayerRemoved(player);
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		handler.onToggleVisibility(visible);
	}

	public boolean isTrackingHealth() {
		return trackingHealth;
	}

	public boolean isTrackingName() {
		return trackingName;
	}

	public ManageableServerBossBar copy() {
		var bossBar = new ManageableServerBossBar(getName(), getColor(), getStyle());
		getMusic().ifPresent(bossBar::setMusic);
		bossBar.setDarkenSky(darkenSky);
		bossBar.setDragonMusic(dragonMusic);
		bossBar.trackingHealth = trackingHealth;
		bossBar.trackingName = trackingName;
		bossBar.setPercent(percent);
		bossBar.value = value;
		bossBar.max = max;
		bossBar.setThickenFog(thickenFog);
		bossBar.setVisible(isVisible());
		return bossBar;
	}
	
	public static ManageableServerBossBar create() {
		return new ManageableServerBossBar(Text.empty(), Color.WHITE, Style.PROGRESS);
	}

	public void updateFromEntity(Entity entity) {
		if (trackingName) {
			setName(entity.getDisplayName());
		}
		if (trackingHealth && entity instanceof LivingEntity living) {
			setPercent(living.getHealth() / living.getMaxHealth());
		}
	}

	public BossBarData serialize() {
		return new BossBarData(
				color, style, darkenSky, thickenFog, isVisible(), getMusic(),
				trackingHealth ? Optional.empty() : Optional.of(new BossBarData.Health(value, max)),
				trackingName ? Optional.empty() : Optional.of(getName())
		);
	}

	public void deserialize(BossBarData data) {
		setColor(data.color);
		setStyle(data.style);
		setDarkenSky(data.darkenSky);
		setThickenFog(data.thickenFog);
		setVisible(data.visible);
		data.music.ifPresentOrElse(
				this::setMusic,
				() -> this.setMusic(null)
		);
		trackingHealth = data.health.isEmpty();
		data.health.ifPresent(
				health -> {
					value = health.value;
					max = health.max;
					if (max > 0) {
						this.setPercent((float) this.value / this.max);
					}
				}
		);
		trackingName = data.name.isEmpty();
		data.name.ifPresent(this::setName);
	}

	public void readNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		final var ops = lookup.getOps(NbtOps.INSTANCE);
		nbt.decode(BossBarData.MAP_CODEC, ops).ifPresent(this::deserialize);
	}

	public NbtCompound writeNBT(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		final var ops = lookup.getOps(NbtOps.INSTANCE);
		nbt.copyFromCodec(BossBarData.MAP_CODEC, ops, this.serialize());
		return nbt;
	}

	public record BossBarData(
			Color color,
			Style style,
			boolean darkenSky,
			boolean thickenFog,
			boolean visible,
			Optional<MusicEntry> music,
			Optional<Health> health,
			Optional<Text> name
	) {
		public static final BossBarData DEFAULT = new BossBarData(
				Color.WHITE, Style.PROGRESS, false, false, true, Optional.empty(),
				Optional.empty(), Optional.empty()
		);

		public static final MapCodec<BossBarData> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Color.CODEC.fieldOf("color").orElse(Color.WHITE).forGetter(BossBarData::color),
						Style.CODEC.fieldOf("style").orElse(Style.PROGRESS).forGetter(BossBarData::style),
						Codec.BOOL.optionalFieldOf("darken_sky", false).forGetter(BossBarData::darkenSky),
						Codec.BOOL.optionalFieldOf("thicken_fog", false).forGetter(BossBarData::thickenFog),
						Codec.BOOL.optionalFieldOf("visible", true).forGetter(BossBarData::visible),
						MusicEntry.CODEC.optionalFieldOf("music").forGetter(BossBarData::music),
						Health.OPT_CODEC.forGetter(BossBarData::health),
						TextCodecs.CODEC.optionalFieldOf("name").forGetter(BossBarData::name)
				).apply(instance, BossBarData::new)
		);

		public static final Codec<BossBarData> CODEC = MAP_CODEC.codec();

		public record Health(int value, int max) {
			public static final MapCodec<Health> CODEC = RecordCodecBuilder.mapCodec(
					instance -> instance.group(
							Codecs.NON_NEGATIVE_INT.fieldOf("value").forGetter(Health::value),
							Codecs.POSITIVE_INT.fieldOf("max").forGetter(Health::max)
					).apply(instance, Health::new)
			);

			public static final MapCodec<Optional<Health>> OPT_CODEC = new MapCodec<>() {
				@Override
				public <T> Stream<T> keys(DynamicOps<T> ops) {
					return CODEC.keys(ops);
				}

				@Override
				public <T> DataResult<Optional<Health>> decode(DynamicOps<T> ops, MapLike<T> input) {
					return DataResult.success(CODEC.decode(ops, input).resultOrPartial());
				}

				@Override
				public <T> RecordBuilder<T> encode(Optional<Health> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
					if (input.isPresent()) {
						return CODEC.encode(input.get(), ops, prefix);
					} else {
						return prefix;
					}
				}
			};
		}
	}

}
