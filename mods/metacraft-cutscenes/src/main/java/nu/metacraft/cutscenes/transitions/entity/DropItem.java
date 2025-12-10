package nu.metacraft.cutscenes.transitions.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import nu.metacraft.cutscenes.cutscene.CutsceneInstance;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.registry.TransitionRegistry;
import nu.metacraft.cutscenes.transitions.InstantTransition;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.transitions.TransitionType;
import nu.metacraft.cutscenes.transitions.config.TransitionConfigType;
import nu.metacraft.cutscenes.util.IntervalMap;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public abstract class DropItem extends InstantTransition {

	protected final GeneralSettings settings;

	public DropItem(GeneralSettings settings) {
		this.settings = settings;
	}

	public static void setThrowVelocity(Entity thrower, float yawOffset, float pitchOffset, ItemEntity itemEntity, RandomSource random) {
		float yaw = thrower.getYRot() + yawOffset;
		float pitch = thrower.getXRot() + pitchOffset;
		float g = Mth.sin(pitch * ((float)Math.PI / 180F));
		float h = Mth.cos(pitch * ((float)Math.PI / 180F));
		float i = Mth.sin(yaw * ((float)Math.PI / 180F));
		float j = Mth.cos(yaw * ((float)Math.PI / 180F));
		float k = random.nextFloat() * ((float)Math.PI * 2F);
		float l = 0.02F * random.nextFloat();
		itemEntity.setDeltaMovement(
				(double)(-i * h * 0.3F) + Math.cos(k) * (double)l,
				-g * 0.3F + 0.1F + (random.nextFloat() - random.nextFloat()) * 0.1F,
				(double)(j * h * 0.3F) + Math.sin(k) * (double)l
		);
	}

	protected abstract ItemStack getItemToDrop(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval, Entity entity);

	@Override
	public void activate(CutsceneInstance cutscene, IntervalMap.Interval<Transition> interval) {
		settings.entity.get(cutscene.getRefContext()).forEach(entity -> {
			Vec3 pos = settings.offset.map(offset -> new Vec3(
					entity.getX() + offset.x, entity.getY() + offset.y, entity.getZ() + offset.z
			)).orElse(new Vec3(entity.getX(), entity.getEyeY() - 0.3, entity.getZ()));
			var stack = getItemToDrop(cutscene, interval, entity);
			if (!stack.isEmpty()) {
				var item = new ItemEntity(entity.level(), pos.x, pos.y, pos.z, stack);
				item.setThrower(entity);
				item.setPickUpDelay(40);
				setThrowVelocity(entity, settings.yawOffset, settings.pitchOffset, item, settings.randomSeed.map(RandomSource::create).orElse(entity.getRandom()));
				cutscene.getCutsceneWorld().addEntity(settings.newID, item);
			}
		});
	}

	public record GeneralSettings(
			EntityRef entity,
			String newID,
			Optional<Vec3> offset,
			float yawOffset,
			float pitchOffset,
			Optional<Long> randomSeed
	) {
		public static final MapCodec<GeneralSettings> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						EntityRefRegistry.CODEC.fieldOf("entity").forGetter(t -> t.entity),
						Codec.STRING.fieldOf("new_id").forGetter(t -> t.newID),
						Vec3.CODEC.optionalFieldOf("offset").forGetter(t -> t.offset),
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
			if (entity instanceof Mob mob) {
				var stack = mob.getItemBySlot(slot);
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
