package se.datasektionen.mc.cutscenes.transitions.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.AffineTransformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import se.datasektionen.mc.cutscenes.Cutscenes;
import se.datasektionen.mc.metacraft_core.entity_ref.EntityRef;
import se.datasektionen.mc.metacraft_core.registry.EntityRefRegistry;
import se.datasektionen.mc.cutscenes.registry.TransitionConfigRegistry;
import se.datasektionen.mc.cutscenes.transitions.SmoothEntityPathTranstion;
import se.datasektionen.mc.cutscenes.transitions.Transition;
import se.datasektionen.mc.cutscenes.util.CutsceneContext;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.cutscenes.util.InterpolationSetContainer;
import se.datasektionen.mc.cutscenes.util.Target;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;

import java.util.Arrays;
import java.util.stream.DoubleStream;

public record SmoothEntityPathConfig(
		InterpolationSetContainer<DisplayEntityTarget> targets, int interpolationDuration, int teleportInterval,
		EntityRef entity
) implements TransitionConfig {

	public static final MapCodec<InterpolationSetContainer<DisplayEntityTarget>> SMOOTH_PATH = InterpolationSetContainer.createCodec(
			DisplayEntityTarget.CODEC, DisplayEntityTarget::fromList, DisplayEntityTarget.ADJUSTER
	);

	public static final MapCodec<SmoothEntityPathConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SMOOTH_PATH.forGetter(c -> c.targets),
					Codecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(SmoothEntityPathConfig::interpolationDuration),
					Codecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(SmoothEntityPathConfig::teleportInterval),
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(SmoothEntityPathConfig::entity)
			).apply(instance, SmoothEntityPathConfig::new)
	);

	public record DisplayEntityTarget(
			Target target, AffineTransformation transformation,
			float shadowRadius, float shadowStrength,
			int background, byte textOpacity //Only used on text displays.
	) implements Interpolatable<CutsceneContext> {

		public static final Int2ObjectMap<InterpolationSet.Adjuster> ADJUSTER = Target.ADJUSTER;

		public static final MapCodec<DisplayEntityTarget> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Target.MAP_CODEC.forGetter(DisplayEntityTarget::target),
						AffineTransformation.CODEC.optionalFieldOf("transformation", AffineTransformation.identity()).forGetter(DisplayEntityTarget::transformation),
						Codec.FLOAT.optionalFieldOf("shadow_radius", 0.0f).forGetter(DisplayEntityTarget::shadowRadius),
						Codec.FLOAT.optionalFieldOf("shadow_strength", 1.0f).forGetter(DisplayEntityTarget::shadowStrength),
						Codec.INT.optionalFieldOf("background", DisplayEntity.TextDisplayEntity.INITIAL_BACKGROUND).forGetter(DisplayEntityTarget::background),
						Codec.BYTE.optionalFieldOf("text_opacity", (byte) -1).forGetter(DisplayEntityTarget::textOpacity)
				).apply(instance, DisplayEntityTarget::new)
		);

		public static final DisplayEntityTarget DEFAULT = new SmoothEntityPathConfig.DisplayEntityTarget(
				Target.DEFAULT,
				AffineTransformation.identity(),
				0, 0, DisplayEntity.TextDisplayEntity.INITIAL_BACKGROUND,  (byte) -1
		);

		public DisplayEntityTarget {
			transformation.getScale(); //Initialize internal variables in transformation to prevent crash when serializing.
		}

		private static final int TARGET_SIZE = 5;
		private static final int TRANSFORMATION_START = TARGET_SIZE;
		private static final int TRANSFORMATION_SIZE = 14;
		private static final int VARS_POS = TARGET_SIZE + TRANSFORMATION_SIZE;
		private static final int SIZE = TARGET_SIZE+ TRANSFORMATION_SIZE +4;

		public static DisplayEntityTarget fromEntity(Entity entity) {
			var target = Target.fromEntity(entity);
			var data = entity.writeNbt(new NbtCompound());
			var transformation = AffineTransformation.identity();
			if (data.contains(DisplayEntity.TRANSFORMATION_NBT_KEY)) {
				transformation = AffineTransformation.ANY_CODEC.parse(NbtOps.INSTANCE, data.get(DisplayEntity.TRANSFORMATION_NBT_KEY)).resultOrPartial(
						Cutscenes.LOGGER::error
				).orElse(transformation);
			}
			float shadowRadius = 0;
			if (data.contains(DisplayEntity.SHADOW_RADIUS_NBT_KEY)) {
				shadowRadius = data.getFloat(DisplayEntity.SHADOW_RADIUS_NBT_KEY);
			}
			float shadowStrength = 1;
			if (data.contains(DisplayEntity.SHADOW_STRENGTH_NBT_KEY)) {
				shadowStrength = data.getFloat(DisplayEntity.SHADOW_STRENGTH_NBT_KEY);
			}

			int background = DisplayEntity.TextDisplayEntity.INITIAL_BACKGROUND;
			if (data.contains("background")) {
				background = data.getInt("background");
			}

			byte textOpacity = -1;
			if (data.contains("text_opacity")) {
				textOpacity = data.getByte("text_opacity");
			}
			return new DisplayEntityTarget(
					target, transformation, shadowRadius, shadowStrength, background, textOpacity
			);
		}

		public static AffineTransformation readAffine(double[] array, int startPoint) {
			Vector3f translation = new Vector3f(
					(float) array[startPoint],
					(float) array[startPoint+1],
					(float) array[startPoint+2]
			);
			AxisAngle4f leftRot = new AxisAngle4f(
					(float) array[startPoint+3],
					(float) array[startPoint+4],
					(float) array[startPoint+5],
					(float) array[startPoint+6]
			);
			Vector3f scale = new Vector3f(
					(float) array[startPoint+7],
					(float) array[startPoint+8],
					(float) array[startPoint+9]
			);
			AxisAngle4f rightRot = new AxisAngle4f(
					(float) array[startPoint+10],
					(float) array[startPoint+11],
					(float) array[startPoint+12],
					(float) array[startPoint+13]
			);
			return new AffineTransformation(
					translation, leftRot.get(new Quaternionf()),
					scale, rightRot.get(new Quaternionf())
			);
		}

		public static void writeVec(Vector3f vec, DoubleList list) {
			list.add(vec.x);
			list.add(vec.y);
			list.add(vec.z);
		}

		public static void writeAxisAngle(AxisAngle4f axisAngle, DoubleList list) {
			list.add(axisAngle.angle);
			list.add(axisAngle.x);
			list.add(axisAngle.y);
			list.add(axisAngle.z);
		}

		public static void writeAffine(AffineTransformation transformation, DoubleList list) {
			writeVec(transformation.getTranslation(), list);
			writeAxisAngle(transformation.getLeftRotation().get(new AxisAngle4f()), list);
			writeVec(transformation.getScale(), list);
			writeAxisAngle(transformation.getRightRotation().get(new AxisAngle4f()), list);
		}

		public static DisplayEntityTarget fromList(DoubleStream stream) {
			var array = stream.limit(SIZE).toArray();
			var target = Target.fromList(Arrays.stream(array));
			var transformation = readAffine(array, TRANSFORMATION_START);

			var shadowRadius = array[VARS_POS];
			var shadowStrength = array[VARS_POS + 1];
			var background = array[VARS_POS + 2];
			var textOpacity = array[VARS_POS + 3];

			return new DisplayEntityTarget(
					target, transformation, (float) shadowRadius, (float) shadowStrength,
					(int) Math.round(background), (byte) Math.round(textOpacity)
			);
		}

		@Override
		public DoubleList getValues(@Nullable CutsceneContext ctx) {
			var list = new DoubleArrayList(SIZE);
			list.addAll(target.getValues(ctx));

			writeAffine(transformation, list);

			list.add(shadowRadius);
			list.add(shadowStrength);
			list.add(background);
			list.add(textOpacity);

			return list;
		}
	}

	@Override
	public Transition create() {
		return new SmoothEntityPathTranstion(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.SMOOTH_ENTITY_PATH;
	}

}
