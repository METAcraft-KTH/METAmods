package nu.metacraft.core.music;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import nu.metacraft.core.util.helper.BossBarHelper;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.*;
import java.util.stream.Stream;

public class ManageableServerBossBar extends ServerBossEvent {

	public static final String BOSS_BAR = "BossBar";

	private boolean trackingHealth = true;
	private boolean trackingName = true;
	private boolean includePassengers = false;

	private Entity mainEntity;
	private UUID mainUUID;
	private final Map<UUID, Entity> includedEntities = new HashMap<>();
	private final Set<UUID> includedEntityIDs = new LinkedHashSet<>();
	private final Map<ServerPlayer, MutableInt> playerAddedCounts = new HashMap<>();
	private boolean loaded = false;

	private long lastTick = -1;

	private float extraMaxHealth;

	private int value;
	private int max;

	private final BossBarMusicHandler handler = new BossBarMusicHandler(this);

	public ManageableServerBossBar(Component displayName, BossBarColor color, BossBarOverlay style) {
		super(displayName, color, style);
	}

	public void setMusic(PlayerMusic music) {
		this.handler.setMusic(music);
	}

	public Optional<PlayerMusic> getMusic() {
		return this.handler.getMusic();
	}

	protected void onAddedForReal(ServerPlayer player) {
		super.addPlayer(player);
		handler.onPlayerAdded(player);
	}

	protected void onRemovedForReal(ServerPlayer player) {
		super.removePlayer(player);
		handler.onPlayerRemoved(player);
	}

	public void addPlayer(ServerPlayer player) {
		MutableInt count = playerAddedCounts.computeIfAbsent(player, k -> new MutableInt(0));
		if (count.getValue() == 0) onAddedForReal(player);
		count.increment();
	}

	public void removePlayer(ServerPlayer player) {
		MutableInt count = playerAddedCounts.get(player);
		if (count != null) {
			count.decrement();
		}
		if (count == null || count.getValue() <= 0) {
			playerAddedCounts.remove(player);
			onRemovedForReal(player);
		}
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
		includedEntityIDs.add(entity.getUUID());
		includedEntities.put(entity.getUUID(), entity);
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
		removeEntity(entity.getUUID());
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
		var bossBar = new ManageableServerBossBar(getName(), getColor(), getOverlay());
		getMusic().ifPresent(bossBar::setMusic);
		bossBar.setDarkenScreen(darkenScreen);
		bossBar.setPlayBossMusic(playBossMusic);
		bossBar.trackingHealth = trackingHealth;
		bossBar.trackingName = trackingName;
		bossBar.setProgress(progress);
		bossBar.value = value;
		bossBar.max = max;
		bossBar.setCreateWorldFog(createWorldFog);
		bossBar.setVisible(isVisible());
		return bossBar;
	}
	
	public static ManageableServerBossBar create() {
		return new ManageableServerBossBar(Component.empty(), BossBarColor.WHITE, BossBarOverlay.PROGRESS);
	}

	private boolean shouldExtendExtraHealth(Entity entity) {
		if (entity instanceof Slime slime) {
			return slime.getSize() == Slime.MIN_SIZE;
		}
		return true;
	}

	public void onEntityRemoved(Entity entity, Entity.RemovalReason reason) {
		includedEntities.remove(entity.getUUID());
		if (reason == Entity.RemovalReason.KILLED && entity instanceof LivingEntity living && shouldExtendExtraHealth(entity)) {
			extraMaxHealth += living.getMaxHealth();
		}
		if (reason.shouldDestroy()) {
			includedEntityIDs.remove(entity.getUUID());
		}
		updateMainEntity();
	}

	public void setMainEntity(Entity entity) {
		this.mainEntity = entity;
		this.mainUUID = entity.getUUID();
	}

	private UUID findMainEntity() {
		return includedEntityIDs.stream().filter(includedEntities::containsKey).filter(e -> includedEntities.get(e).isAlive()).findFirst().orElse(null);
	}

	public boolean isMainEntity(Entity entity) {
		if (includedEntityIDs.isEmpty()) return true;
		if (mainEntity == entity) return true;
		var first = findMainEntity();
		return first == null || Objects.equals(entity.getUUID(), first);
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
		if (!includedEntityIDs.contains(entity.getUUID())) {
			addEntity(entity);
		}
		if (includePassengers && entity.tickCount % 20 == 0) {
			entity.getRootVehicle().getSelfAndPassengers().forEach(passenger -> {
				if (!includedEntityIDs.contains(passenger.getUUID())) {
					addEntity(passenger);
				}
			});
		}
		updateMainEntity();
		if (isMainEntity(entity)) {
			if (trackingName) {
				setName(entity.getDisplayName());
			}
		}
		if (entity.level().getGameTime() != lastTick) {
			if (trackingHealth) {
				float health = 0;
				float maxHealth = extraMaxHealth;
				for (var e : includedEntities.values()) {
					if (e instanceof LivingEntity living) {
						health += living.getHealth();
						maxHealth += living.getMaxHealth();
					}
				}
				setProgress(health / maxHealth);
			}
			lastTick = entity.level().getGameTime();
		}
		if (mainEntity == null && mainUUID != null) {
			mainEntity = entity.level().getEntity(mainUUID);
		}
		if (!loaded) {
			loaded = true;
			for (var id : includedEntityIDs) {
				var otherE = entity.level().getEntity(id);
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
				color, overlay, darkenScreen, createWorldFog, isVisible(), includePassengers, extraMaxHealth, getMusic(),
				trackingHealth ? Optional.empty() : Optional.of(new BossBarData.Health(value, max)),
				trackingName ? Optional.empty() : Optional.of(getName()), entityIds, main
		);
	}

	public void deserialize(BossBarData data) {
		setColor(data.color);
		setOverlay(data.style);
		setDarkenScreen(data.darkenSky);
		setCreateWorldFog(data.thickenFog);
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
						this.setProgress((float) this.value / this.max);
					}
				}
		);
		trackingName = data.name.isEmpty();
		data.name.ifPresent(this::setName);
	}

	public void readNBT(CompoundTag nbt, HolderLookup.Provider lookup) {
		final var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
		nbt.read(BossBarData.MAP_CODEC, ops).ifPresent(this::deserialize);
	}

	public CompoundTag writeNBT(CompoundTag nbt, HolderLookup.Provider lookup) {
		final var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
		nbt.store(BossBarData.MAP_CODEC, ops, this.serialize());
		return nbt;
	}

	public record BossBarData(
			BossBarColor color,
			BossBarOverlay style,
			boolean darkenSky,
			boolean thickenFog,
			boolean visible,
			boolean includePassengers,
			float extraMaxHealth,
			Optional<PlayerMusic> music,
			Optional<Health> health,
			Optional<Component> name,
			Set<UUID> entities,
			Optional<UUID> mainEntity
	) {
		public static final BossBarData DEFAULT = new BossBarData(
				BossBarColor.WHITE, BossBarOverlay.PROGRESS, false, false, true, false, 0,
				Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), Optional.empty()
		);

		public static final MapCodec<BossBarData> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						BossBarColor.CODEC.fieldOf("color").orElse(BossBarColor.WHITE).forGetter(BossBarData::color),
						BossBarOverlay.CODEC.fieldOf("style").orElse(BossBarOverlay.PROGRESS).forGetter(BossBarData::style),
						Codec.BOOL.optionalFieldOf("darken_sky", false).forGetter(BossBarData::darkenSky),
						Codec.BOOL.optionalFieldOf("thicken_fog", false).forGetter(BossBarData::thickenFog),
						Codec.BOOL.optionalFieldOf("visible", true).forGetter(BossBarData::visible),
						Codec.BOOL.optionalFieldOf("include_passengers", false).forGetter(BossBarData::includePassengers),
						Codec.FLOAT.optionalFieldOf("extra_max_health", 0.0f).forGetter(BossBarData::extraMaxHealth),
						PlayerMusic.EASY_CODEC.optionalFieldOf("music").forGetter(BossBarData::music),
						Health.OPT_CODEC.forGetter(BossBarData::health),
						ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(BossBarData::name),
						UUIDUtil.CODEC_LINKED_SET.optionalFieldOf("entities", Set.of()).forGetter(BossBarData::entities),
						UUIDUtil.CODEC.optionalFieldOf("main_entity").forGetter(BossBarData::mainEntity)
				).apply(instance, BossBarData::new)
		);

		public static final Codec<BossBarData> CODEC = MAP_CODEC.codec();

		public record Health(int value, int max) {
			public static final MapCodec<Health> CODEC = RecordCodecBuilder.mapCodec(
					instance -> instance.group(
							ExtraCodecs.NON_NEGATIVE_INT.fieldOf("value").forGetter(Health::value),
							ExtraCodecs.POSITIVE_INT.fieldOf("max").forGetter(Health::max)
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
