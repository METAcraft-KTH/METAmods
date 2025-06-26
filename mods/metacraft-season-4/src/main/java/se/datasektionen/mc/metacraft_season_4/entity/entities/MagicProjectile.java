package se.datasektionen.mc.metacraft_season_4.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.util.math.floatprovider.FloatProvider;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_season_4.status_effects.Season4StatusEffects;
import se.metacraft.bosses.util.StatusEffectEntry;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.*;

public class MagicProjectile extends AbstractFireballEntity implements PolymerEntity {

	private final ItemDisplayElement texture;
	private final InteractionElement hitbox;

	public static final String CLOUD_EFFECTS = "cloud_effects";
	public static final String HIT_EFFECTS = "hit_effects";
	public static final String HIT_EFFECT_COUNT = "hit_effect_count";
	public static final String CLOUD_EFFECT_COUNT = "cloud_effect_count";

	public static final Codec<Pool<StatusEffectEntry>> HIT_EFFECT_POOL_CODEC = Pool.createCodec(StatusEffectEntry.CODEC.codec());
	public static final Codec<Pool<EffectEntry>> CLOUD_EFFECT_POOL_CODEC = Pool.createCodec(EffectEntry.CODEC);

	public static final IntProvider ONE = ConstantIntProvider.create(1);
	public static final IntProvider ZERO = ConstantIntProvider.create(0);
	public static final IntProvider VARIES = UniformIntProvider.create(0, 2);

	public static final IntProvider LONG = UniformIntProvider.create(350, 550);
	public static final IntProvider MID = UniformIntProvider.create(150, 250);
	public static final IntProvider SHORT = UniformIntProvider.create(50, 100);

	protected Pool<EffectEntry> cloudEffects = createCloudDefaults().build();

	protected Pool<StatusEffectEntry> hitEffects = createHitDefaults().build();

	protected IntProvider hitEffectCount = ONE;
	protected IntProvider cloudEffectCount = ONE;

	public static Object createEffectPool(RegistryWrapper.WrapperLookup lookup, Pool<StatusEffectEntry> effects) {
		return HIT_EFFECT_POOL_CODEC.encodeStart(
				lookup.getOps(JavaOps.INSTANCE),
				effects
		).getOrThrow();
	}

	public static Object createCloudEffectPool(RegistryWrapper.WrapperLookup lookup, Pool<EffectEntry> effects) {
		return CLOUD_EFFECT_POOL_CODEC.encodeStart(
				lookup.getOps(JavaOps.INSTANCE),
				effects
		).getOrThrow();
	}

	public static Object createIntProvider(RegistryWrapper.WrapperLookup lookup, IntProvider provider) {
		return IntProvider.VALUE_CODEC.encodeStart(
				lookup.getOps(JavaOps.INSTANCE),
				provider
		).getOrThrow();
	}

	public static Pool.Builder<EffectEntry> createCloudDefaults() {
		return Pool.<EffectEntry>builder().add(
				EffectEntry.create(StatusEffectEntry.create(StatusEffects.BLINDNESS, SHORT, ZERO))
		).add(
				EffectEntry.create(StatusEffectEntry.create(StatusEffects.SLOWNESS, MID, ZERO))
		).add(
				EffectEntry.create(StatusEffectEntry.create(StatusEffects.MINING_FATIGUE, MID, ZERO))
		).add(
				EffectEntry.create(StatusEffectEntry.create(StatusEffects.POISON, SHORT, ZERO))
		).add(
				EffectEntry.create(StatusEffectEntry.create(Season4StatusEffects.FREEZE, MID, ZERO))
		).add(
				EffectEntry.create(StatusEffectEntry.create(Season4StatusEffects.FIRE, ONE, UniformIntProvider.create(2, 4)))
		);
	}

	public static Pool.Builder<StatusEffectEntry> createHitDefaults() {
		return Pool.<StatusEffectEntry>builder().add(
				StatusEffectEntry.create(StatusEffects.WEAKNESS, MID, ZERO)
		).add(
				StatusEffectEntry.create(StatusEffects.BLINDNESS, MID, ZERO)
		).add(
				StatusEffectEntry.create(StatusEffects.SLOWNESS, LONG, VARIES)
		).add(
				StatusEffectEntry.create(StatusEffects.MINING_FATIGUE, LONG, VARIES)
		).add(
				StatusEffectEntry.create(StatusEffects.INSTANT_DAMAGE, ONE, VARIES)
		).add(
				StatusEffectEntry.create(StatusEffects.WITHER, SHORT, VARIES)
		).add(
				StatusEffectEntry.create(StatusEffects.POISON, SHORT, VARIES)
		).add(
				StatusEffectEntry.create(Season4StatusEffects.FREEZE, MID, VARIES)
		).add(
				StatusEffectEntry.create(Season4StatusEffects.FIRE, ONE, UniformIntProvider.create(8, 15))
		);
	}

	public MagicProjectile(EntityType<? extends AbstractFireballEntity> entityType, World world) {
		super(entityType, world);
		texture = new ItemDisplayElement();
		hitbox = new InteractionElement();
		setItem(getDefaultItem());
		setup();
	}

	protected ItemStack getDefaultItem() {
		return new ItemStack(Items.FIREWORK_STAR);
	}

	@Override
	public void readCustomData(ReadView nbt) {
		super.readCustomData(nbt);
		if (nbt.read("Item", ItemStack.CODEC).isEmpty()) {
			setItem(getDefaultItem());
		}

		this.cloudEffects = nbt.read(CLOUD_EFFECTS, CLOUD_EFFECT_POOL_CODEC).orElse(Pool.empty());
		this.hitEffects = nbt.read(HIT_EFFECTS, HIT_EFFECT_POOL_CODEC).orElse(Pool.empty());
		this.hitEffectCount = nbt.read(HIT_EFFECT_COUNT, IntProvider.NON_NEGATIVE_CODEC).orElse(ONE);
		this.hitEffectCount = nbt.read(CLOUD_EFFECT_COUNT, IntProvider.NON_NEGATIVE_CODEC).orElse(ONE);
	}

	@Override
	public void writeCustomData(WriteView nbt) {
		super.writeCustomData(nbt);
		nbt.put(
				CLOUD_EFFECTS, CLOUD_EFFECT_POOL_CODEC,
				cloudEffects
		);
		nbt.put(
				HIT_EFFECTS, HIT_EFFECT_POOL_CODEC,
				hitEffects
		);
		nbt.put(
				HIT_EFFECT_COUNT, IntProvider.NON_NEGATIVE_CODEC,
				hitEffectCount
		);
		nbt.put(
				CLOUD_EFFECT_COUNT, IntProvider.NON_NEGATIVE_CODEC,
				cloudEffectCount
		);
	}

	@Override
	public void setItem(ItemStack stack) {
		texture.setItem(stack);
	}

	@Override
	public ItemStack getStack() {
		return texture.getItem();
	}

	public void setCloudEffects(Pool<EffectEntry> cloudEffects) {
		this.cloudEffects = cloudEffects;
	}

	public void setHitEffects(Pool<StatusEffectEntry> hitEffects) {
		this.hitEffects = hitEffects;
	}

	public Pool<EffectEntry> getCloudEffects() {
		return cloudEffects;
	}

	public Pool<StatusEffectEntry> getHitEffects() {
		return hitEffects;
	}

	public void setCloudEffectCount(IntProvider cloudEffectCount) {
		this.cloudEffectCount = cloudEffectCount;
	}

	public void setHitEffectCount(IntProvider hitEffectCount) {
		this.hitEffectCount = hitEffectCount;
	}

	private void setup() {
		texture.setOffset(new Vec3d(0, 0.5, 0));
		texture.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
		texture.setTeleportDuration(1);
		hitbox.setHandler(VirtualElement.InteractionHandler.redirect(this));
		ElementHolder holder = new ElementHolder();
		holder.addElement(texture);
		holder.addElement(hitbox);
		EntityAttachment.ofTicking(holder, this);
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity entity = entityHitResult.getEntity();
		entity.serverDamage(this.getDamageSources().indirectMagic(this, this.getOwner()), 5);
		if (entity instanceof LivingEntity living) {
			int c = hitEffectCount.get(living.getRandom());
			for (int i = 0; i < c; i++) {
				hitEffects.getOrEmpty(living.getRandom()).ifPresent(
						effect -> living.addStatusEffect(effect.createEffect(random))
				);
			}
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		getWorld().playSound(
				null, hitResult.getPos().getX(), hitResult.getPos().getY(), hitResult.getPos().getZ(),
				SoundEvents.BLOCK_END_PORTAL_FRAME_FILL,
				SoundCategory.HOSTILE, 1, 0.5f
		);
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(
					ParticleTypes.ENCHANTED_HIT, hitResult.getPos().getX(), hitResult.getPos().getY(), hitResult.getPos().getZ(),
					50, 0, 0, 0, 0.5
			);
			int c = cloudEffectCount.get(getRandom());
			Set<StatusEffect> effects = new HashSet<>();
			for (int i = 0; i < c; i++) {
				cloudEffects.getOrEmpty(this.getRandom()).ifPresent(effect -> {
					if (effects.contains(effect.effect.effect().value())) {
						return;
					}
					var cloud = EntityType.AREA_EFFECT_CLOUD.create(sw, SpawnReason.TRIGGERED);
					cloud.setRadius(effect.radius.get(getRandom()));
					cloud.setDuration(effect.cloudDuration.get(getRandom()));
					getWorld().spawnEntity(cloud);
					effects.add(effect.effect.effect().value());
				});
			}
		}
		if (!getWorld().isClient()) {
			discard();
		}
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityType.MARKER;
	}

	public record EffectEntry(
			StatusEffectEntry effect,
			FloatProvider radius, IntProvider cloudDuration
	) {
		public static EffectEntry create(StatusEffectEntry effect) {
			return new EffectEntry(effect, ConstantFloatProvider.create(3), ConstantIntProvider.create(600));
		}

		public static final Codec<EffectEntry> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						StatusEffectEntry.CODEC.forGetter(EffectEntry::effect),
						FloatProvider.createValidatedCodec(0, Float.MAX_VALUE).optionalFieldOf(
								"radius", ConstantFloatProvider.create(3)
						).forGetter(EffectEntry::radius),
						IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf(
								"cloud_duration", ConstantIntProvider.create(600)
						).forGetter(EffectEntry::cloudDuration)
				).apply(instance, EffectEntry::new)
		);
	}
}
