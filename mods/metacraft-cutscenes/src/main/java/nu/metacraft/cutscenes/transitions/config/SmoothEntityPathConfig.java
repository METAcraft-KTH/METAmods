package nu.metacraft.cutscenes.transitions.config;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nu.metacraft.cutscenes.Cutscenes;
import nu.metacraft.core.entity_ref.EntityRef;
import nu.metacraft.core.registry.EntityRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.SmoothEntityPathTranstion;
import nu.metacraft.cutscenes.transitions.Transition;
import nu.metacraft.cutscenes.util.CutsceneContext;
import nu.metacraft.core.util.Interpolatable;
import nu.metacraft.cutscenes.util.InterpolationSetContainer;
import nu.metacraft.cutscenes.util.Target;
import nu.metacraft.core.util.InterpolationSet;
import nu.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import org.joml.Vector3fc;

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
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(SmoothEntityPathConfig::interpolationDuration),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(SmoothEntityPathConfig::teleportInterval),
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(SmoothEntityPathConfig::entity)
			).apply(instance, SmoothEntityPathConfig::new)
	);

	public record DisplayEntityTarget(
			Target target, Transformation transformation,
			float shadowRadius, float shadowStrength,
			int background, byte textOpacity //Only used on text displays.
	) implements Interpolatable<CutsceneContext> {

		public static final Int2ObjectMap<InterpolationSet.Adjuster> ADJUSTER = Target.ADJUSTER;

		public static final MapCodec<DisplayEntityTarget> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Target.MAP_CODEC.forGetter(DisplayEntityTarget::target),
						Transformation.CODEC.optionalFieldOf("transformation", Transformation.identity()).forGetter(DisplayEntityTarget::transformation),
						Codec.FLOAT.optionalFieldOf("shadow_radius", 0.0f).forGetter(DisplayEntityTarget::shadowRadius),
						Codec.FLOAT.optionalFieldOf("shadow_strength", 1.0f).forGetter(DisplayEntityTarget::shadowStrength),
						Codec.INT.optionalFieldOf("background", Display.TextDisplay.INITIAL_BACKGROUND).forGetter(DisplayEntityTarget::background),
						Codec.BYTE.optionalFieldOf("text_opacity", (byte) -1).forGetter(DisplayEntityTarget::textOpacity)
				).apply(instance, DisplayEntityTarget::new)
		);

		public static final DisplayEntityTarget DEFAULT = new SmoothEntityPathConfig.DisplayEntityTarget(
				Target.DEFAULT,
				Transformation.identity(),
				0, 0, Display.TextDisplay.INITIAL_BACKGROUND,  (byte) -1
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
			CompoundTag data;
			try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothEntityPathConfig#fromEntity", Cutscenes.LOGGER)) {
				var writeView = TagValueOutput.createWithContext(logging, entity.registryAccess());
				entity.saveWithoutId(writeView);
				data = writeView.buildResult();
			}
			var transformation = Transformation.identity();
			if (data.contains(Display.TAG_TRANSFORMATION)) {
				transformation = Transformation.EXTENDED_CODEC.parse(NbtOps.INSTANCE, data.get(Display.TAG_TRANSFORMATION)).resultOrPartial(
						Cutscenes.LOGGER::error
				).orElse(transformation);
			}
			float shadowRadius = 0;
			if (data.contains(Display.TAG_SHADOW_RADIUS)) {
				shadowRadius = data.getFloatOr(Display.TAG_SHADOW_RADIUS, 0);
			}
			float shadowStrength = 1;
			if (data.contains(Display.TAG_SHADOW_STRENGTH)) {
				shadowStrength = data.getFloatOr(Display.TAG_SHADOW_STRENGTH, 1);
			}

			int background = Display.TextDisplay.INITIAL_BACKGROUND;
			if (data.contains("background")) {
				background = data.getIntOr("background", 1073741824);
			}

			byte textOpacity = -1;
			if (data.contains("text_opacity")) {
				textOpacity = data.getByteOr("text_opacity", (byte) -1);
			}
			return new DisplayEntityTarget(
					target, transformation, shadowRadius, shadowStrength, background, textOpacity
			);
		}

		public static Transformation readAffine(double[] array, int startPoint) {
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
			return new Transformation(
					translation, leftRot.get(new Quaternionf()),
					scale, rightRot.get(new Quaternionf())
			);
		}

		public static void writeVec(Vector3fc vec, DoubleList list) {
			list.add(vec.x());
			list.add(vec.y());
			list.add(vec.z());
		}

		public static void writeAxisAngle(AxisAngle4f axisAngle, DoubleList list) {
			list.add(axisAngle.angle);
			list.add(axisAngle.x);
			list.add(axisAngle.y);
			list.add(axisAngle.z);
		}

		public static void writeAffine(Transformation transformation, DoubleList list) {
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
