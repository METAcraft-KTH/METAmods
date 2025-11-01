package nu.metacraft.core.music;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Uuids;
import net.minecraft.util.dynamic.Codecs;
import nu.metacraft.core.util.helper.BossBarHelper;

import java.util.*;
import java.util.stream.Stream;

public class ManageableServerBossBar extends ServerBossBar {

	public static final String BOSS_BAR = "BossBar";

	private boolean trackingHealth = true;
	private boolean trackingName = true;
	private boolean includePassengers = false;

	private Entity mainEntity;
	private UUID mainUUID;
	private final Map<UUID, Entity> includedEntities = new HashMap<>();
	private final Set<UUID> includedEntityIDs = new LinkedHashSet<>();
	private boolean loaded = false;

	private float extraMaxHealth;

	private int value;
	private int max;

	private final BossBarMusicHandler handler = new BossBarMusicHandler(this);

	public ManageableServerBossBar(Text displayName, Color color, Style style) {
		super(displayName, color, style);
	}

	public void setMusic(PlayerMusic music) {
		this.handler.setMusic(music);
	}

	public Optional<PlayerMusic> getMusic() {
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

	public void setIncludePassengers(boolean includePassengers) {
		this.includePassengers = includePassengers;
	}

	public void addEntity(Entity entity) {
		includedEntityIDs.add(entity.getUuid());
		includedEntities.put(entity.getUuid(), entity);
		var b = BossBarHelper.getBossBar(entity);
		if (b.isEmpty() || b.get() != this) {
			BossBarHelper.setBossBar(entity, this);
		}
	}

	public void removeEntity(UUID entity) {
		includedEntityIDs.remove(entity);
		includedEntities.remove(entity);
		updateMainEntity();
	}

	public void removeEntity(Entity entity) {
		removeEntity(entity.getUuid());
	}

	public boolean isTrackingHealth() {
		return trackingHealth;
	}

	public boolean isTrackingName() {
		return trackingName;
	}

	public boolean isIncludePassengers() {
		return includePassengers;
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

	private boolean shouldExtendExtraHealth(Entity entity) {
		if (entity instanceof SlimeEntity slime) {
			return slime.getSize() == SlimeEntity.MIN_SIZE;
		}
		return true;
	}

	public void onEntityRemoved(Entity entity, Entity.RemovalReason reason) {
		includedEntities.remove(entity.getUuid());
		if (reason == Entity.RemovalReason.KILLED && entity instanceof LivingEntity living && shouldExtendExtraHealth(entity)) {
			extraMaxHealth += living.getMaxHealth();
		}
		if (reason.shouldDestroy()) {
			includedEntityIDs.remove(entity.getUuid());
		}
		updateMainEntity();
	}

	public void setMainEntity(Entity entity) {
		this.mainEntity = entity;
		this.mainUUID = entity.getUuid();
	}

	private UUID findMainEntity() {
		return includedEntityIDs.stream().filter(includedEntities::containsKey).filter(e -> includedEntities.get(e).isAlive()).findFirst().orElse(null);
	}

	public boolean isMainEntity(Entity entity) {
		if (includedEntityIDs.isEmpty()) return true;
		if (mainEntity == entity) return true;
		var first = findMainEntity();
		return first == null || Objects.equals(entity.getUuid(), first);
	}

	private void updateMainEntity() {
		if (mainEntity == null || !mainEntity.isAlive()) {
			var main = findMainEntity();
			if (main != null) {
				setMainEntity(includedEntities.get(main));
			}
		}
	}

	public void updateFromEntity(Entity entity) {
		if (!includedEntityIDs.contains(entity.getUuid())) {
			addEntity(entity);
		}
		if (includePassengers && entity.age % 20 == 0) {
			entity.getRootVehicle().streamSelfAndPassengers().forEach(passenger -> {
				if (!includedEntityIDs.contains(passenger.getUuid())) {
					addEntity(passenger);
				}
			});
		}
		updateMainEntity();
		if (isMainEntity(entity)) {
			if (trackingName) {
				setName(entity.getDisplayName());
			}
			if (trackingHealth) {
				float health = 0;
				float maxHealth = extraMaxHealth;
				for (var e : includedEntities.values()) {
					if (e instanceof LivingEntity living) {
						health += living.getHealth();
						maxHealth += living.getMaxHealth();
					}
				}
				setPercent(health / maxHealth);
			}
		}
		if (mainEntity == null && mainUUID != null) {
			mainEntity = entity.getEntityWorld().getEntity(mainUUID);
		}
		if (!loaded) {
			loaded = true;
			for (var id : includedEntityIDs) {
				if (includedEntities.containsKey(id)) return;
				var otherE = entity.getEntityWorld().getEntity(id);
				if (otherE != null) {
					includedEntities.put(id, otherE);
					BossBarHelper.setBossBar(otherE, this);
				} else {
					loaded = false;
				}
			}
		}

	}

	public BossBarData serialize() {
		var entityIds = includedEntityIDs.size() == 1 ? Set.<UUID>of() : includedEntityIDs;
		var main = includedEntityIDs.size() == 1 ? Optional.<UUID>empty() : Optional.ofNullable(mainUUID);
		return new BossBarData(
				color, style, darkenSky, thickenFog, isVisible(), includePassengers, extraMaxHealth, getMusic(),
				trackingHealth ? Optional.empty() : Optional.of(new BossBarData.Health(value, max)),
				trackingName ? Optional.empty() : Optional.of(getName()), entityIds, main
		);
	}

	public void deserialize(BossBarData data) {
		setColor(data.color);
		setStyle(data.style);
		setDarkenSky(data.darkenSky);
		setThickenFog(data.thickenFog);
		setVisible(data.visible);
		setIncludePassengers(data.includePassengers);
		extraMaxHealth = data.extraMaxHealth;
		this.mainUUID = data.mainEntity.orElse(null);
		includedEntities.clear();
		includedEntityIDs.clear();
		includedEntityIDs.addAll(data.entities);
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
			boolean includePassengers,
			float extraMaxHealth,
			Optional<PlayerMusic> music,
			Optional<Health> health,
			Optional<Text> name,
			Set<UUID> entities,
			Optional<UUID> mainEntity
	) {
		public static final BossBarData DEFAULT = new BossBarData(
				Color.WHITE, Style.PROGRESS, false, false, true, false, 0,
				Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), Optional.empty()
		);

		public static final MapCodec<BossBarData> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Color.CODEC.fieldOf("color").orElse(Color.WHITE).forGetter(BossBarData::color),
						Style.CODEC.fieldOf("style").orElse(Style.PROGRESS).forGetter(BossBarData::style),
						Codec.BOOL.optionalFieldOf("darken_sky", false).forGetter(BossBarData::darkenSky),
						Codec.BOOL.optionalFieldOf("thicken_fog", false).forGetter(BossBarData::thickenFog),
						Codec.BOOL.optionalFieldOf("visible", true).forGetter(BossBarData::visible),
						Codec.BOOL.optionalFieldOf("include_passengers", false).forGetter(BossBarData::includePassengers),
						Codec.FLOAT.optionalFieldOf("extra_max_health", 0.0f).forGetter(BossBarData::extraMaxHealth),
						PlayerMusic.EASY_CODEC.optionalFieldOf("music").forGetter(BossBarData::music),
						Health.OPT_CODEC.forGetter(BossBarData::health),
						TextCodecs.CODEC.optionalFieldOf("name").forGetter(BossBarData::name),
						Uuids.LINKED_SET_CODEC.optionalFieldOf("entities", Set.of()).forGetter(BossBarData::entities),
						Uuids.INT_STREAM_CODEC.optionalFieldOf("main_entity").forGetter(BossBarData::mainEntity)
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
