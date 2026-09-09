package metacraft.moredyes.sheep;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import metacraft.moredyes.mixin.SheepAccessor;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;

import java.util.List;

/**
 * Polymer overlay on every vanilla sheep. For a sheep in one of our colours, the client is told the
 * sheep is invisible (and sheared, belt and braces): vanilla clients can only tint wool with the 16
 * built-in colours, and a client-interpolated body never lines up with server-driven wool, so the
 * whole sheep is drawn by {@link SheepWoolRig} instead. Hitbox, name tag and interactions stay.
 */
public final class SheepOverlay implements PolymerEntity {
    private static final int SHEARED_BIT = 16;
    private static final int FLAGS_ID = 0;      // Entity.DATA_SHARED_FLAGS_ID
    private static final byte INVISIBLE = 1 << 5; // Entity.FLAG_INVISIBLE
    private final Sheep sheep;

    public SheepOverlay(Sheep sheep) {
        this.sheep = sheep;
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return EntityTypes.SHEEP;
    }

    @Override
    public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
        if (SheepColors.get(sheep) == null) return;
        EntityDataAccessor<Byte> woolId = SheepAccessor.moredyes$woolId();
        boolean sawFlags = false;
        for (int i = 0; i < data.size(); i++) {
            SynchedEntityData.DataValue<?> value = data.get(i);
            if (value.id() == woolId.id() && value.value() instanceof Byte b) {
                data.set(i, new SynchedEntityData.DataValue<>(value.id(), woolId.serializer(), (byte) (b | SHEARED_BIT)));
            } else if (value.id() == FLAGS_ID && value.value() instanceof Byte b) {
                data.set(i, new SynchedEntityData.DataValue<>(FLAGS_ID, EntityDataSerializers.BYTE, (byte) (b | INVISIBLE)));
                sawFlags = true;
            }
        }
        // Default flags (0) are omitted from the spawn data; add them so the sheep starts invisible.
        if (initial && !sawFlags) {
            data.add(new SynchedEntityData.DataValue<>(FLAGS_ID, EntityDataSerializers.BYTE, INVISIBLE));
        }
    }
}
