package se.datasektionen.mc.metacraft_core.entity.entities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.stream.DoubleStream;

public class MovingMarker extends MarkerEntity implements PolymerEntity {

	public static final String PATH = "path";
	public static final String PATH_TIME = "path_time";
	public static final String PATH_PROGRESS = "path_progress";

	private static final Codec<InterpolationSet<MovingMarker, Target>> PATH_CODEC = InterpolationSet.createCodec(
			Target.CODEC.fieldOf("target"), Target::fromStream
	);

	private int pathTime = 100;
	private int pathProgress = 0;

	private InterpolationSet<MovingMarker, Target> path;

	public MovingMarker(EntityType<?> entityType, World world) {
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
			refreshPositionAndAngles(pos.pos.getX(), pos.pos.getY(), pos.pos.getZ(), pos.yaw, pos.pitch);
			pathProgress++;
			if (pathProgress > pathTime) {
				pathProgress = 0;
			}
		}
	}

	@Override
	protected void readCustomData(ReadView nbt) {
		super.readCustomData(nbt);

		nbt.read(PATH, PATH_CODEC).ifPresentOrElse(p -> {
			path = p;
			path = path.setStartIfNotPresent(new Target(this.getPos(), getYaw(), getPitch()));
			path = path.setEndIfNotPresent(new Target(this.getPos(), getYaw(), getPitch()));
		}, () -> path = null);

		pathTime = nbt.getInt(PATH_TIME, 100);
		pathProgress = nbt.getInt(PATH_PROGRESS, 0);
	}

	@Override
	protected void writeCustomData(WriteView nbt) {
		super.writeCustomData(nbt);
		if (path != null) {
			nbt.put(PATH, PATH_CODEC, path);
		}
		nbt.putInt(PATH_TIME, pathTime);
		nbt.putInt(PATH_PROGRESS, pathProgress);
	}

	public record Target(Vec3d pos, float yaw, float pitch) implements Interpolatable<MovingMarker> {

		public static final Codec<Target> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						Vec3d.CODEC.fieldOf("pos").forGetter(Target::pos),
						Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(Target::yaw),
						Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(Target::pitch)
				).apply(instance, Target::new)
		);

		@Override
		public DoubleList getValues(MovingMarker context) {
			return DoubleList.of(pos.getX(), pos.getY(), pos.getZ(), yaw, pitch);
		}

		public static Target fromStream(DoubleStream stream) {
			var arr = stream.limit(5).toArray();
			return new Target(new Vec3d(arr[0], arr[1], arr[2]), (float) arr[3], (float) arr[4]);
		}
	}
}
