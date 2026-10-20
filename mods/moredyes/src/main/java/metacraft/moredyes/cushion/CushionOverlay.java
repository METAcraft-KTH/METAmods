package metacraft.moredyes.cushion;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Cushion;

import java.util.List;

/**
 * How a client sees a cushion. Vanilla's are left alone. One in our colour is sent as an interaction
 * entity of the cushion's size: invisible, clickable, and something a player can sit on; its look is
 * the display model {@link Cushions} puts on it.
 */
public final class CushionOverlay implements PolymerEntity {
	/** Interaction's tracked data after the 8 every entity has: width, height, response. */
	private static final int WIDTH = 8, HEIGHT = 9, RESPONSE = 10;
	private final Cushion cushion;

	public CushionOverlay(Cushion cushion) {
		this.cushion = cushion;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return Cushions.colour(cushion) == null ? EntityTypes.CUSHION : EntityTypes.INTERACTION;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		if (Cushions.colour(cushion) == null) return;
		// the cushion's own data (its DyeColor) means something else to an interaction entity
		data.removeIf(value -> value.id() >= WIDTH);
		if (initial) {
			data.add(new SynchedEntityData.DataValue<>(WIDTH, EntityDataSerializers.FLOAT, cushion.getBbWidth()));
			data.add(new SynchedEntityData.DataValue<>(HEIGHT, EntityDataSerializers.FLOAT, cushion.getBbHeight()));
			data.add(new SynchedEntityData.DataValue<>(RESPONSE, EntityDataSerializers.BOOLEAN, true));
		}
	}
}
