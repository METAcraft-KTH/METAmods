package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.InstantTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;

import java.util.Optional;

public abstract class DropItem extends InstantTransition {

	protected final GeneralSettings settings;

	public DropItem(GeneralSettings settings) {
		this.settings = settings;
	}

	public static void setThrowVelocity(Entity thrower, float yawOffset, float pitchOffset, ItemEntity itemEntity, Random random) {
		float yaw = thrower.getYaw() + yawOffset;
		float pitch = thrower.getPitch() + pitchOffset;
		float g = MathHelper.sin(pitch * ((float)Math.PI / 180F));
		float h = MathHelper.cos(pitch * ((float)Math.PI / 180F));
		float i = MathHelper.sin(yaw * ((float)Math.PI / 180F));
		float j = MathHelper.cos(yaw * ((float)Math.PI / 180F));
		float k = random.nextFloat() * ((float)Math.PI * 2F);
		float l = 0.02F * random.nextFloat();
		itemEntity.setVelocity(
				(double)(-i * h * 0.3F) + Math.cos(k) * (double)l,
				-g * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F,
				(double)(j * h * 0.3F) + Math.sin(k) * (double)l
		);
	}

	protected abstract ItemStack getItemToDrop(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, Entity entity);

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		settings.entity.get(cutscene.getRefContext()).forEach(entity -> {
			Vec3d pos = settings.offset.map(offset -> new Vec3d(
					entity.getX() + offset.x, entity.getY() + offset.y, entity.getZ() + offset.z
			)).orElse(new Vec3d(entity.getX(), entity.getEyeY() - 0.3, entity.getZ()));
			var stack = getItemToDrop(cutscene, interval, entity);
			if (!stack.isEmpty()) {
				var item = new ItemEntity(entity.getWorld(), pos.x, pos.y, pos.z, stack);
				item.setThrower(entity);
				item.setPickupDelay(40);
				setThrowVelocity(entity, settings.yawOffset, settings.pitchOffset, item, settings.randomSeed.map(Random::create).orElse(entity.getRandom()));
				cutscene.getCutsceneWorld().addEntity(settings.newID, item);
			}
		});
	}

	public record GeneralSettings(
			EntityRef entity,
			String newID,
			Optional<Vec3d> offset,
			float yawOffset,
			float pitchOffset,
			Optional<Long> randomSeed
	) {
		public static final MapCodec<GeneralSettings> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
						Codec.STRING.fieldOf("new_id").forGetter(t -> t.newID),
						Vec3d.CODEC.optionalFieldOf("offset").forGetter(t -> t.offset),
						Codec.FLOAT.optionalFieldOf("yaw_offset", 0.0f).forGetter(t -> t.yawOffset),
						Codec.FLOAT.optionalFieldOf("pitch_offset", 0.0f).forGetter(t -> t.pitchOffset),
						Codec.LONG.optionalFieldOf("random_seed").forGetter(t -> t.randomSeed)
				).apply(instance, GeneralSettings::new)
		);
	}

	public static class DropSpecificStack extends DropItem {

		public static final MapCodec<DropSpecificStack> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						GeneralSettings.CODEC.forGetter(t -> t.settings),
						ItemStack.CODEC.fieldOf("item").forGetter(t -> t.item)
				).apply(instance, DropSpecificStack::new)
		);

		private final ItemStack item;

		public DropSpecificStack(
				GeneralSettings settings,
				ItemStack item
		) {
			super(settings);
			this.item = item;
		}

		@Override
		protected ItemStack getItemToDrop(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, Entity entity) {
			return item.copy();
		}

		@Override
		public TransitionType<?> getType() {
			return TransitionRegistry.ENTITY_DROP_STACK;
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.ENTITY_DROP_STACK;
		}
	}

	public static class DropFromSlot extends DropItem {

		public static final MapCodec<DropFromSlot> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						GeneralSettings.CODEC.forGetter(t -> t.settings),
						EquipmentSlot.CODEC.fieldOf("slot").forGetter(t -> t.slot),
						Codec.INT.optionalFieldOf("amount", 1).forGetter(t -> t.amountToDrop),
						Codec.BOOL.optionalFieldOf("remove_from_slot", true).forGetter(t -> t.removeFromSlot)
				).apply(instance, DropFromSlot::new)
		);

		private final EquipmentSlot slot;
		private final int amountToDrop;
		private final boolean removeFromSlot;

		public DropFromSlot(
				GeneralSettings settings,
				EquipmentSlot slot, int amountToDrop, boolean removeFromSlot
		) {
			super(settings);
			this.slot = slot;
			this.amountToDrop = amountToDrop;
			this.removeFromSlot = removeFromSlot;
		}

		@Override
		protected ItemStack getItemToDrop(
				CutsceneInstance cutscene,
				IntervalMap.Interval<Transition> interval,
				Entity entity
		) {
			if (entity instanceof MobEntity mob) {
				var stack = mob.getEquippedStack(slot);
				if (removeFromSlot) {
					return stack.split(amountToDrop);
				} else {
					return stack.copyWithCount(amountToDrop);
				}
			} else {
				return ItemStack.EMPTY;
			}
		}

		@Override
		public TransitionType<?> getType() {
			return TransitionRegistry.ENTITY_DROP_SLOT;
		}

		@Override
		public TransitionConfigType<?> getConfigType() {
			return TransitionConfigRegistry.ENTITY_DROP_SLOT;
		}
	}
}
