package nu.metacraft.core.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import nu.metacraft.core.util.Interpolatable;
import nu.metacraft.core.util.InterpolationSet;

import java.util.stream.DoubleStream;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class MovingMarker extends Marker implements PolymerEntity {

	public static final String PATH = "path";
	public static final String PATH_TIME = "path_time";
	public static final String PATH_PROGRESS = "path_progress";

	private static final Codec<InterpolationSet<MovingMarker, Target>> PATH_CODEC = InterpolationSet.createCodec(
			Target.CODEC.fieldOf("target"), Target::fromStream
	);

	private int pathTime = 100;
	private int pathProgress = 0;

	private InterpolationSet<MovingMarker, Target> path;

	public MovingMarker(EntityType<?> entityType, Level world) {
		super(entityType, world);
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityType.MARKER;
	}

	@Override
	public void tick() {
		super.tick();
		if (path != null) {
			if (pathTime == 0) {
				pathTime = 100;
			}
			var pos = path.interpolate((double) pathProgress / pathTime);
			snapTo(pos.pos.x(), pos.pos.y(), pos.pos.z(), pos.yaw, pos.pitch);
			pathProgress++;
			if (pathProgress > pathTime) {
				pathProgress = 0;
			}
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput nbt) {
		super.readAdditionalSaveData(nbt);

		nbt.read(PATH, PATH_CODEC).ifPresentOrElse(p -> {
			path = p;
			path = path.setStartIfNotPresent(new Target(this.position(), getYRot(), getXRot()));
			path = path.setEndIfNotPresent(new Target(this.position(), getYRot(), getXRot()));
		}, () -> path = null);

		pathTime = nbt.getIntOr(PATH_TIME, 100);
		pathProgress = nbt.getIntOr(PATH_PROGRESS, 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput nbt) {
		super.addAdditionalSaveData(nbt);
		if (path != null) {
			nbt.store(PATH, PATH_CODEC, path);
		}
		nbt.putInt(PATH_TIME, pathTime);
		nbt.putInt(PATH_PROGRESS, pathProgress);
	}

	public record Target(Vec3 pos, float yaw, float pitch) implements Interpolatable<MovingMarker> {

		public static final Codec<Target> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Vec3.CODEC.fieldOf("pos").forGetter(Target::pos),
						Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Target::yaw),
						Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(Target::pitch)
				).apply(instance, Target::new)
		);

		@Override
		public DoubleList getValues(MovingMarker context) {
			return DoubleList.of(pos.x(), pos.y(), pos.z(), yaw, pitch);
		}

		public static Target fromStream(DoubleStream stream) {
			var arr = stream.limit(5).toArray();
			return new Target(new Vec3(arr[0], arr[1], arr[2]), (float) arr[3], (float) arr[4]);
		}
	}
}
