package metacraft.ovvar.sewing;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import metacraft.ovvar.content.*;
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
 * exists server-side, and they are gone the moment the ovve is. Each patch item has flat models
 * for this (pieces of its art 1:1 on the sprite, {@link PatchPieces}), scaled so one art pixel
 * is one of the fabric's square pixels; a big patch is cut at the corners of its face and each
 * piece laid on the face it hangs over — round the sides, and over the top of a sleeve or leg —
 * so it bends round the box as the sewn one will. Later-sewn patches sit a hair further out, so
 * they overlap the earlier.
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
        final List<Element> elements = new ArrayList<>();
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
        for (Placement p : SpotPlacements.asPlacementList(Looks.sewn(ovve))) {
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
            for (Element element : shown.elements) shown.holder.removeElement(element.display);
            shown.elements.clear();
            shown.placements.clear();
            shown.placements.addAll(placements);
            shown.topShown = topShown;
            for (int i = 0; i < placements.size(); i++) {
                Placement p = placements.get(i);
                Patches.Patch patch = p.patch();
                for (PatchPieces.Piece piece : PatchPieces.of(p.spot(), patch)) {
                    ItemStack item = new ItemStack(ModContent.patchItem(patch));
                    item.set(ModComponents.FLAT, piece.key());
                    ItemDisplayElement display = new ItemDisplayElement(item);
                    display.setItemDisplayContext(ItemDisplayContext.NONE);
                    display.setInterpolationDuration(0);
                    display.setTeleportDuration(1);
                    display.setViewRange(0.6f);
                    shown.elements.add(new Element(display, p, patch, piece, i));
                    shown.holder.addElement(display);
                }
            }
            rebuilt = true;
        }
        int poseHash = Objects.hash(shown.stand.position(), shown.stand.yBodyRot, shown.stand.getBodyPose(), shown.stand.getRightArmPose(),
                shown.stand.getLeftArmPose(), shown.stand.getRightLegPose(), shown.stand.getLeftLegPose());
        if (!rebuilt && poseHash == shown.poseHash) return;
        shown.poseHash = poseHash;
        Vec3 origin = shown.holder.getPos();
        for (Element element : shown.elements) place(element, shown.stand, origin);
    }

    /** One sprite: a piece of a placement's art, and where in the sewing order it is (later ones lie on top). */
    private record Element(ItemDisplayElement display, Placement placement, Patches.Patch patch, PatchPieces.Piece piece, int order) {}

    /**
     * Lay a piece on its plane. The whole art is centred on the cell, so every piece's sprite is
     * the whole 16×16 with only its own pixels drawn, and placing it means putting the art's
     * centre where it belongs on that plane: on the cell's face, at the cell; round a corner, on
     * the neighbouring face's plane, continuing from the corner; over the top, on the part's top
     * face. A box is convex, so each neighbouring plane follows from the face's own frame.
     */
    private static void place(Element element, ArmorStand stand, Vec3 origin) {
        Spot spot = element.placement.spot();
        PatchPieces.Piece piece = element.piece;
        // The seat: one half on each leg's back face, centred on that cell like any patch.
        if (spot == Spot.SEAT) spot = piece.x0() == 0 ? Spot.LEG_BACK_TOP_R : Spot.LEG_BACK_TOP_L;
        StandAim.CellPoint at = StandAim.cell(stand, spot);
        double inflate = Spot.inflate(spot.piece), a = Spot.pixel(spot.u, inflate) / 2;   // sixteenths per art pixel
        float scale = (float) a;   // the sprite is 16 pixels to a block: one sprite pixel = a sixteenths at scale a
        int w = element.patch.width(), h = element.patch.height(), n = PatchPieces.faceTexels(spot);
        Vec3 normal = at.normal(), up = at.up(), right = up.cross(normal);
        // The cell's centre relative to the face's centre, and the face's half extents (sixteenths).
        double cellX = (PatchPieces.columnInFace(spot) + Spot.PX / 2.0 - n) * a, cellY = ((spot.v - 20) * 2 + Spot.PX / 2.0 - 12) * a;
        double halfFace = (n + 2 * inflate) / 2, halfTop = (12 + 2 * inflate) / 2;
        Vec3 centre, n2, u2, r2;
        switch (piece.where()) {
            case RIGHT -> {
                Vec3 corner = at.centre().add(right.scale((halfFace - cellX) / 16));
                r2 = normal.scale(-1); n2 = right; u2 = up;   // round the corner, "right" turns away from the face
                centre = corner.add(r2.scale((piece.start() + (w / 2.0 - piece.x0()) * a) / 16));
            }
            case LEFT -> {
                Vec3 corner = at.centre().subtract(right.scale((halfFace + cellX) / 16));
                r2 = normal; n2 = right.scale(-1); u2 = up;
                centre = corner.add(r2.scale(((w / 2.0 - piece.x1()) * a - piece.start()) / 16));
            }
            case TOP -> {
                Vec3 corner = at.centre().add(up.scale((halfTop + cellY) / 16));
                n2 = up; u2 = normal.scale(-1); r2 = right;   // over the shoulder, "up" turns inward across the top
                centre = corner.add(normal.scale(((h / 2.0 - piece.y1()) * a - piece.start()) / 16));
            }
            default -> {
                n2 = normal; u2 = up; r2 = right;
                centre = at.centre();
            }
        }
        // The item display turns its item 180° about y: the readable side faces the display's -z.
        Matrix3f basis = new Matrix3f(
                new Vector3f((float) -r2.x, (float) -r2.y, (float) -r2.z),
                new Vector3f((float) u2.x, (float) u2.y, (float) u2.z),
                new Vector3f((float) -n2.x, (float) -n2.y, (float) -n2.z));
        Quaternionf rotation = new Quaternionf().setFromNormalized(basis);
        // Just off the fabric, later patches a hair further out. A piece round a corner is also
        // pushed that far back towards the corner, so its lifted edge meets the face piece's
        // lifted edge and no fabric shows in the seam.
        double lift = 0.005 + 0.002 * element.order;
        Vec3 pos = centre.add(n2.scale(lift));
        switch (piece.where()) {
            case RIGHT -> pos = pos.subtract(r2.scale(lift));
            case LEFT -> pos = pos.add(r2.scale(lift));
            case TOP -> pos = pos.subtract(u2.scale(lift));
            default -> {}
        }
        element.display.setOffset(pos.subtract(origin));
        element.display.setLeftRotation(rotation);
        element.display.setScale(new Vector3f(scale, scale, scale));
        element.display.startInterpolationIfDirty();
    }
}
