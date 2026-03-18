package nu.metacraft.zones.zone.data;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import nu.metacraft.lib.util.StoredCondition;

public class PreventEntryData extends ZoneDataEntityTracking {

	public static final MapCodec<PreventEntryData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					StoredCondition.CODEC.fieldOf("condition").forGetter(data -> data.condition)
			).apply(instance, PreventEntryData::new)
	);

	private StoredCondition condition;

	public PreventEntryData(StoredCondition condition) {
		this.condition = condition;
	}

	public void setStoredCondition(StoredCondition condition) {
		this.condition = condition;
		markDirty();
	}

	@Override
	public ZoneDataType<? extends ZoneData> getType() {
		return ZoneDataRegistry.PREVENT_ENTRY;
	}

	@Override
	public void tick(Entity entity) {
		super.tick(entity);
		var src = new CommandSourceStack(
				CommandSource.NULL,
				entity.position(),
				entity.getRotationVector(),
				(ServerLevel) entity.level(),
				LevelBasedPermissionSet.GAMEMASTER,
				entity.getName().getString(),
				entity.getDisplayName(),
				entity.level().getServer(),
				entity
		);
		if (condition.matches(src)) {
			var vector = getZone().getZone().getInwardVector(entity.position()).vector().reverse();
			if (vector.length() == 0) return;
			entity.setDeltaMovement(vector);
			entity.hurtMarked = true;
		}
	}
}
