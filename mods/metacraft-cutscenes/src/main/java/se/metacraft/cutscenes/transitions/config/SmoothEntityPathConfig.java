package se.metacraft.cutscenes.transitions.config;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;
import se.metacraft.cutscenes.transitions.PlayerSpecificCameraPathTransition;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import se.metacraft.cutscenes.Cutscenes;
import se.metacraft.core.entity_ref.EntityRef;
import se.metacraft.core.registry.EntityRefRegistry;
import se.metacraft.cutscenes.registry.TransitionConfigRegistry;
import se.metacraft.cutscenes.transitions.SmoothEntityPathTranstion;
import se.metacraft.cutscenes.transitions.Transition;
import se.metacraft.cutscenes.util.CutsceneContext;
import se.metacraft.core.util.Interpolatable;
import se.metacraft.cutscenes.util.InterpolationSetContainer;
import se.metacraft.cutscenes.util.Target;
import se.metacraft.core.util.InterpolationSet;
import se.metacraft.lib.util.error_reporters.LoggingErrorReporter;
import org.joml.Vector3fc;

import java.util.stream.DoubleStream;

public record SmoothEntityPathConfig(
		InterpolationSetContainer<DynamicDisplayTarget> targets,
		int interpolationDuration, int teleportInterval,
		EntityRef entity
) implements TransitionConfig {

	public static final MapCodec<InterpolationSetContainer<DynamicDisplayTarget>> SMOOTH_DYNAMIC_PATH = InterpolationSetContainer.createCodec(
			DynamicDisplayTarget.MAP_CODEC, DynamicDisplayTarget::fromList, DynamicDisplayTarget.ADJUSTER
	);

	public static final MapCodec<SmoothEntityPathConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					SMOOTH_DYNAMIC_PATH.forGetter(c -> c.targets),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("linear_interpolation_duration", 20).forGetter(SmoothEntityPathConfig::interpolationDuration),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("teleport_interval", 1).forGetter(SmoothEntityPathConfig::teleportInterval),
					EntityRefRegistry.CODEC.fieldOf("entity").forGetter(SmoothEntityPathConfig::entity)
			).apply(instance, SmoothEntityPathConfig::new)
	);

	public record DynamicDisplayTarget(
		PlayerSpecificCameraPathTransition.DynamicTarget target, DisplayEntityProperties displayEntityProperties
	) implements Interpolatable<CutsceneContext> {
		public static final Int2ObjectMap<InterpolationSet.Adjuster> ADJUSTER = new Int2ObjectOpenHashMap<>();

		static {
			ADJUSTER.putAll(Target.ADJUSTER);
			ADJUSTER.putAll(DisplayEntityProperties.adjuster(PlayerSpecificCameraPathTransition.DynamicTarget.SIZE));
		}

		public static final MapCodec<DynamicDisplayTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				PlayerSpecificCameraPathTransition.DynamicTarget.OR_FIXED_CODEC.forGetter(DynamicDisplayTarget::target),
				DisplayEntityProperties.CODEC.forGetter(DynamicDisplayTarget::displayEntityProperties)
			).apply(instance, DynamicDisplayTarget::new)
		);
		public static final DynamicDisplayTarget DEFAULT = new DynamicDisplayTarget(
			PlayerSpecificCameraPathTransition.DynamicTarget.DEFAULT, DisplayEntityProperties.DEFAULT
		);

		public static DynamicDisplayTarget fromList(DoubleStream stream) {
			var list = DoubleArrayList.toList(stream.limit(PlayerSpecificCameraPathTransition.DynamicTarget.SIZE + DisplayEntityProperties.SIZE));
			return new DynamicDisplayTarget(
				PlayerSpecificCameraPathTransition.DynamicTarget.fromList(list.doubleStream()),
				DisplayEntityProperties.fromList(
					PlayerSpecificCameraPathTransition.DynamicTarget.SIZE,
					list.doubleStream()
				)
			);
		}

		@Override
		public DoubleList getValues(CutsceneContext context) {
			var combined = new DoubleArrayList();
			combined.addAll(target.getValues(context));
			combined.addAll(displayEntityProperties.getValues());
			return combined;
		}

		public static DynamicDisplayTarget fromEntity(Entity entity) {
			return new DynamicDisplayTarget(
				PlayerSpecificCameraPathTransition.DynamicTarget.fromEntity(entity),
				DisplayEntityProperties.fromEntity(entity)
			);
		}
	}

	public record DisplayEntityProperties(
		Transformation transformation,
		float shadowRadius, float shadowStrength,
		int background, byte textOpacity //Only used on text displays.
	) {

		public static Int2ObjectMap<InterpolationSet.Adjuster> adjuster(int start) {
			return Int2ObjectMaps.singleton(
				start + TRANSFORMATION_SIZE + 2,
				elements -> DoubleArrayList.toList(elements.doubleStream().map(e -> e < 0 ? 256 + e : e))
			);
		}

		public static final MapCodec<DisplayEntityProperties> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
				Transformation.CODEC.optionalFieldOf("transformation", Transformation.IDENTITY).forGetter(DisplayEntityProperties::transformation),
				Codec.FLOAT.optionalFieldOf("shadow_radius", 0.0f).forGetter(DisplayEntityProperties::shadowRadius),
				Codec.FLOAT.optionalFieldOf("shadow_strength", 1.0f).forGetter(DisplayEntityProperties::shadowStrength),
				ExtraCodecs.STRING_ARGB_COLOR.optionalFieldOf("background", Display.TextDisplay.INITIAL_BACKGROUND).forGetter(DisplayEntityProperties::background),
				Codec.BYTE.optionalFieldOf("text_opacity", (byte) -1).forGetter(DisplayEntityProperties::textOpacity)
			).apply(instance, DisplayEntityProperties::new)
		);

		public static final DisplayEntityProperties DEFAULT = new SmoothEntityPathConfig.DisplayEntityProperties(
			Transformation.IDENTITY,
			0, 0, Display.TextDisplay.INITIAL_BACKGROUND,  (byte) -1
		);

		public static DisplayEntityProperties fromEntity(Entity entity) {
			CompoundTag data;
			try (var logging = LoggingErrorReporter.create(() -> "metacraft:SmoothEntityPathConfig#fromEntity", Cutscenes.LOGGER)) {
				var writeView = TagValueOutput.createWithContext(logging, entity.registryAccess());
				entity.saveWithoutId(writeView);
				data = writeView.buildResult();
			}
			var transformation = Transformation.IDENTITY;
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
			return new DisplayEntityProperties(
				transformation, shadowRadius, shadowStrength, background, textOpacity
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
			writeVec(transformation.translation(), list);
			writeAxisAngle(transformation.leftRotation().get(new AxisAngle4f()), list);
			writeVec(transformation.scale(), list);
			writeAxisAngle(transformation.rightRotation().get(new AxisAngle4f()), list);
		}

		public DoubleList getValues() {
			var list = new DoubleArrayList(SIZE);
			writeAffine(transformation, list);

			list.add(shadowRadius);
			list.add(shadowStrength);
			list.add(textOpacity);
			list.add(ARGB.alpha(background));
			list.add(ARGB.red(background));
			list.add(ARGB.green(background));
			list.add(ARGB.blue(background));

			return list;
		}

		private static final int TRANSFORMATION_SIZE = 14;
		private static final int SIZE = TRANSFORMATION_SIZE + 7;
		private static final int VARS_POS = TRANSFORMATION_SIZE;

		public static DisplayEntityProperties fromList(int start, DoubleStream stream) {
			var array = stream.limit(start + SIZE).toArray();
			var transformation = readAffine(array, start);

			var shadowRadius = array[start + VARS_POS];
			var shadowStrength = array[start + VARS_POS + 1];
			var textOpacity = array[start + VARS_POS + 2];
			var background = ARGB.color(
				(int) Math.round(array[start + VARS_POS + 3]),
				(int) Math.round(array[start + VARS_POS + 4]),
				(int) Math.round(array[start + VARS_POS + 5]),
				(int) Math.round(array[start + VARS_POS + 6])
			);

			return new DisplayEntityProperties(
				transformation, (float) shadowRadius, (float) shadowStrength,
				background, (byte) Math.clamp(Math.round(textOpacity), 0, 255)
			);
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
