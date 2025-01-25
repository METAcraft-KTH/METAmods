package se.datasektionen.mc.cutscenes.transitions.entity;

import com.mojang.datafixers.Products;
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
import se.datasektionen.mc.cutscenes.cutscene.CutsceneInstance;
import se.datasektionen.mc.cutscenes.entity_ref.EntityRef;
import se.datasektionen.mc.cutscenes.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionRegistry;
import se.datasektionen.mc.cutscenes.transitions.InstantTransition;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.transitions.TransitionType;
import se.datasektionen.mc.cutscenes.transitions.config.TransitionConfigType;
import se.datasektionen.mc.cutscenes.util.IntervalMap;

import java.util.Optional;

public abstract class DropItem extends InstantTransition {

	protected static <P extends DropItem> Products.P5<RecordCodecBuilder.Mu<P>, EntityRef, String, Optional<Vec3d>, Float, Float> fillDropItemFields(
			RecordCodecBuilder.Instance<P> instance
	) {
		return instance.group(
				EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
				Codec.STRING.fieldOf("new_id").forGetter(t -> t.newID),
				Vec3d.CODEC.optionalFieldOf("offset").forGetter(t -> t.offset),
				Codec.FLOAT.optionalFieldOf("yaw_offset", 0.0f).forGetter(t -> t.yawOffset),
				Codec.FLOAT.optionalFieldOf("pitch_offset", 0.0f).forGetter(t -> t.pitchOffset)
		);
	}

	protected final EntityRef entity;
	protected final String newID;
	protected final Optional<Vec3d> offset;
	protected final float yawOffset;
	protected final float pitchOffset;

	public DropItem(
			EntityRef entity,
			String newID,
			Optional<Vec3d> offset,
			float yawOffset,
			float pitchOffset
	) {
		this.entity = entity;
		this.newID = newID;
		this.offset = offset;
		this.yawOffset = yawOffset;
		this.pitchOffset = pitchOffset;
	}

	public static void setThrowVelocity(Entity thrower, float yawOffset, float pitchOffset, ItemEntity itemEntity) {
		float yaw = thrower.getYaw() + yawOffset;
		float pitch = thrower.getPitch() + pitchOffset;
		float g = MathHelper.sin(pitch * ((float)Math.PI / 180F));
		float h = MathHelper.cos(pitch * ((float)Math.PI / 180F));
		float i = MathHelper.sin(yaw * ((float)Math.PI / 180F));
		float j = MathHelper.cos(yaw * ((float)Math.PI / 180F));
		float k = thrower.getRandom().nextFloat() * ((float)Math.PI * 2F);
		float l = 0.02F * thrower.getRandom().nextFloat();
		itemEntity.setVelocity(
				(double)(-i * h * 0.3F) + Math.cos(k) * (double)l,
				-g * 0.3F + 0.1F + (thrower.getRandom().nextFloat() - thrower.getRandom().nextFloat()) * 0.1F,
				(double)(j * h * 0.3F) + Math.sin(k) * (double)l
		);
	}

	protected abstract ItemStack getItemToDrop(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, Entity entity);

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		entity.get(null, cutscene).forEach(entity -> {
			Vec3d pos = this.offset.map(offset -> new Vec3d(
					entity.getX() + offset.x, entity.getY() + offset.y, entity.getZ() + offset.z
			)).orElse(new Vec3d(entity.getX(), entity.getEyeY() - 0.3, entity.getZ()));
			var stack = getItemToDrop(cutscene, interval, entity);
			if (!stack.isEmpty()) {
				var item = new ItemEntity(entity.getWorld(), pos.x, pos.y, pos.z, stack);
				item.setThrower(entity);
				item.setPickupDelay(40);
				setThrowVelocity(entity, yawOffset, pitchOffset, item);
				cutscene.getCutsceneWorld().addEntity(newID, item);
			}
		});
	}

	public static class DropSpecificStack extends DropItem {

		public static final MapCodec<DropSpecificStack> CODEC = RecordCodecBuilder.mapCodec(
				instance -> fillDropItemFields(instance).and(
						ItemStack.CODEC.fieldOf("item").forGetter(t -> t.item)
				).apply(instance, DropSpecificStack::new)
		);

		private final ItemStack item;

		public DropSpecificStack(
				EntityRef entity, String newID,
				Optional<Vec3d> offset,
				float yawOffset,
				float pitchOffset,
				ItemStack item
		) {
			super(entity, newID, offset, yawOffset, pitchOffset);
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
				instance -> fillDropItemFields(instance).and(
						EquipmentSlot.CODEC.fieldOf("slot").forGetter(t -> t.slot)
				).and(
						Codec.INT.optionalFieldOf("amount", 1).forGetter(t -> t.amountToDrop)
				).and(
						Codec.BOOL.optionalFieldOf("remove_from_slot", true).forGetter(t -> t.removeFromSlot)
				).apply(instance, DropFromSlot::new)
		);

		private final EquipmentSlot slot;
		private final int amountToDrop;
		private final boolean removeFromSlot;

		public DropFromSlot(
				EntityRef entity, String newID,
				Optional<Vec3d> offset,
				float yawOffset,
				float pitchOffset,
				EquipmentSlot slot, int amountToDrop, boolean removeFromSlot
		) {
			super(entity, newID, offset, yawOffset, pitchOffset);
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
