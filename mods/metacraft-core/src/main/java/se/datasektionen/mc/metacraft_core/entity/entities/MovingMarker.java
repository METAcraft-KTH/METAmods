package se.datasektionen.mc.metacraft_core.entity.entities;

import com.mojang.serialization.Codec;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.util.Interpolatable;
import se.datasektionen.mc.metacraft_core.util.InterpolationSet;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.stream.DoubleStream;

public class MovingMarker extends MarkerEntity implements PolymerEntity {

	public static final String PATH = "path";
	public static final String PATH_TIME = "path_time";
	public static final String PATH_PROGRESS = "path_progress";

	private static final Codec<InterpolationSet<MovingMarker, Target>> PATH_CODEC = InterpolationSet.createCodec(
			Target.CODEC.fieldOf("pos"), Target::fromStream
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
			var pos = path.interpolate((double) pathProgress / pathTime).pos;
			refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), this.getYaw(), this.getPitch());
			pathProgress++;
			if (pathProgress > pathTime) {
				pathProgress = 0;
			}
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);

		if (nbt.contains(PATH)) {
			PATH_CODEC.parse(this.getRegistryManager().getOps(NbtOps.INSTANCE), nbt.get(PATH)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(p -> path = p);

			if (path != null) {
				path = path.setStartIfNotPresent(new Target(this.getPos()));
				path = path.setEndIfNotPresent(new Target(this.getPos()));
			}
		} else {
			path = null;
		}

		pathTime = nbt.getInt(PATH_TIME);
		pathProgress = nbt.getInt(PATH_PROGRESS);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (path != null) {
			nbt.put(PATH, PATH_CODEC.encodeStart(this.getRegistryManager().getOps(NbtOps.INSTANCE), path).getOrThrow());
		}
		nbt.putInt(PATH_TIME, pathTime);
		nbt.putInt(PATH_PROGRESS, pathProgress);
	}

	public record Target(Vec3d pos) implements Interpolatable<MovingMarker> {

		public static final Codec<Target> CODEC = Vec3d.CODEC.xmap(Target::new, Target::pos);

		@Override
		public DoubleList getValues(MovingMarker context) {
			return DoubleList.of(pos.getX(), pos.getY(), pos.getZ());
		}

		public static Target fromStream(DoubleStream stream) {
			var arr = stream.limit(3).toArray();
			return new Target(new Vec3d(arr[0], arr[1], arr[2]));
		}
	}
}
