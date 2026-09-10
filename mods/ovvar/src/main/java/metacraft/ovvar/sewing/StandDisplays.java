package metacraft.ovvar.sewing;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.OvveTopItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An ovve on an armour stand wears its patches as display entities, one flat item display per
 * placement, laid on the cell it is sewn to and following the stand's pose. The armour itself
 * draws none of them there ({@link ModComponents#ON_STAND}), so a sewing session needs neither
 * the dye channels nor a rebuilt pack, however many patches go on: the one reload comes when the
 * ovve is taken off the stand and its taker's pack cannot show them all ({@link OvveItem}).
 *
 * The displays are Polymer virtual entities attached to the stand: nothing is saved, nothing
 * exists server-side, and they are gone the moment the ovve is. Each patch item has a second,
 * flat model for this (its art 1:1 on the sprite), scaled so one art pixel is one of the
 * fabric's square pixels; later-sewn patches sit a hair further out, so they overlap the earlier.
 */
public final class StandDisplays {
    private StandDisplays() {}

    /** Stands wearing an ovve, by id: what they show, rebuilt when the patches change and moved when the pose does. */
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();
    /** Stands that got or lost an ovve since the last tick (from any thread's point of view, the tick sorts it out). */
    private static final Set<UUID> CHANGED = ConcurrentHashMap.newKeySet();

    private static final class Shown {
        final ArmorStand stand;
        final ElementHolder holder = new ElementHolder();
        final List<Placement> placements = new ArrayList<>();
        final List<ItemDisplayElement> elements = new ArrayList<>();
        boolean topShown;
        int poseHash;

        Shown(ArmorStand stand) {
            this.stand = stand;
            EntityAttachment.ofTicking(holder, stand);
        }
    }

    public static void init() {
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, next) -> {
            if (entity instanceof ArmorStand stand && (slot == EquipmentSlot.LEGS || slot == EquipmentSlot.CHEST)) CHANGED.add(stand.getUUID());
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ArmorStand stand && stand.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem) CHANGED.add(stand.getUUID());
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof ArmorStand) CHANGED.add(entity.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(StandDisplays::tick);
    }

    private static void tick(MinecraftServer server) {
        for (UUID id : List.copyOf(CHANGED)) {
            CHANGED.remove(id);
            ArmorStand stand = findStand(server, id);
            Shown shown = SHOWN.get(id);
            boolean wears = stand != null && stand.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem;
            if (wears && shown == null) {
                SHOWN.put(id, new Shown(stand));
            } else if (!wears && shown != null) {
                shown.holder.destroy();
                SHOWN.remove(id);
            }
        }
        for (Iterator<Shown> it = SHOWN.values().iterator(); it.hasNext(); ) {
            Shown shown = it.next();
            ItemStack ovve = shown.stand.getItemBySlot(EquipmentSlot.LEGS);
            if (shown.stand.isRemoved() || !(ovve.getItem() instanceof OvveItem)) {
                shown.holder.destroy();
                it.remove();
                continue;
            }
            if (!Boolean.TRUE.equals(ovve.get(ModComponents.ON_STAND))) ovve.set(ModComponents.ON_STAND, true);
            update(shown, ovve);
        }
    }

    private static ArmorStand findStand(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof ArmorStand stand) return stand;
        }
        return null;
    }

    /** The patches to show: all of the legs', and the top's while it is up and nothing else is worn over it. */
    private static List<Placement> shown(ArmorStand stand, ItemStack ovve, boolean topShown) {
        List<Placement> out = new ArrayList<>();
        for (Placement p : Looks.sewn(ovve)) {
            if (p.piece() == Piece.BOTTOM || topShown) out.add(p);
        }
        return out;
    }

    private static boolean topShown(ArmorStand stand, ItemStack ovve) {
        ItemStack chest = stand.getItemBySlot(EquipmentSlot.CHEST);
        return OvveItem.topUp(ovve) && (chest.isEmpty() || chest.getItem() instanceof OvveTopItem);
    }

    private static void update(Shown shown, ItemStack ovve) {
        boolean topShown = topShown(shown.stand, ovve);
        List<Placement> placements = shown(shown.stand, ovve, topShown);
        boolean rebuilt = false;
        if (!placements.equals(shown.placements) || topShown != shown.topShown) {
            for (ItemDisplayElement element : shown.elements) shown.holder.removeElement(element);
            shown.elements.clear();
            shown.placements.clear();
            shown.placements.addAll(placements);
            shown.topShown = topShown;
            for (Placement p : placements) {
                ItemStack item = new ItemStack(ModContent.patchItem(Patches.get(p.patch())));
                item.set(ModComponents.FLAT, true);
                ItemDisplayElement element = new ItemDisplayElement(item);
                element.setItemDisplayContext(ItemDisplayContext.NONE);
                element.setInterpolationDuration(0);
                element.setTeleportDuration(1);
                element.setViewRange(0.6f);
                shown.elements.add(element);
                shown.holder.addElement(element);
            }
            rebuilt = true;
        }
        int poseHash = Objects.hash(shown.stand.position(), shown.stand.yBodyRot, shown.stand.getBodyPose(), shown.stand.getRightArmPose(),
                shown.stand.getLeftArmPose(), shown.stand.getRightLegPose(), shown.stand.getLeftLegPose());
        if (!rebuilt && poseHash == shown.poseHash) return;
        shown.poseHash = poseHash;
        Vec3 origin = shown.holder.getPos();
        for (int i = 0; i < placements.size(); i++) place(shown.elements.get(i), placements.get(i), shown.stand, origin, i);
    }

    /** Lay a display on its cell: centred on the cell, facing out along its normal, the art's up along the part. */
    private static void place(ItemDisplayElement element, Placement placement, ArmorStand stand, Vec3 origin, int order) {
        Spot spot = placement.spot();
        StandAim.CellPoint at;
        if (spot == Spot.SEAT) {
            // Across both legs: between the two back cells, facing the way they do on average.
            StandAim.CellPoint r = StandAim.cell(stand, Spot.LEG_BACK_TOP_R), l = StandAim.cell(stand, Spot.LEG_BACK_TOP_L);
            at = new StandAim.CellPoint(r.centre().add(l.centre()).scale(0.5), r.normal().add(l.normal()).normalize(), r.up().add(l.up()).normalize());
        } else {
            at = StandAim.cell(stand, spot);
        }
        // One art pixel = the fabric's square pixel: Spot.pixel sixteenths per skin texel, two art
        // pixels per texel. The sprite is 16 pixels to a block at scale 1.
        float pixel = (float) Spot.pixel(spot.u, Spot.inflate(spot.piece));
        float scale = pixel / 2;
        Vec3 normal = at.normal(), up = at.up();
        Vec3 right = up.cross(normal);
        // The item display turns its item 180° about y: the readable side faces the display's -z.
        Matrix3f basis = new Matrix3f(
                new Vector3f((float) -right.x, (float) -right.y, (float) -right.z),
                new Vector3f((float) up.x, (float) up.y, (float) up.z),
                new Vector3f((float) -normal.x, (float) -normal.y, (float) -normal.z));
        Quaternionf rotation = new Quaternionf().setFromNormalized(basis);
        // Just off the fabric (the sprite slab is 1/16 thick), later patches a hair further out.
        double lift = scale / 32 + 0.003 + 0.002 * order;
        element.setOffset(at.centre().add(normal.scale(lift)).subtract(origin));
        element.setLeftRotation(rotation);
        element.setScale(new Vector3f(scale, scale, scale));
        element.startInterpolationIfDirty();
    }
}
