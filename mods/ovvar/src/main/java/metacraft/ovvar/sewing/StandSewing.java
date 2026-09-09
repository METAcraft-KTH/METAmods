package metacraft.ovvar.sewing;

import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Spot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sewing on an armour stand. Put the ovve on a stand, hold a patch, look at the stand: the patch
 * previews on the cell you're looking at, right-click sews it.
 * Empty hand on a sewn patch takes it back. The preview is a transient component on the stand's
 * ovve, so everyone sees it, and nothing is persisted until the click.
 *
 * Aiming is a ray against the humanoid model's boxes in the stand's own frame; faces map to the
 * texture strips the way the model unwraps them (front strip runs from the wearer's right to
 * left, back from left to right, sides from back to front around the box).
 */
public final class StandSewing {
    private StandSewing() {}

    private static final double REACH = 6.0;
    /** Armour boxes in sixteenths, in the stand's frame: x to the wearer's right, y up, z forward. Dilated 1. */
    private record Box(String part, Piece piece, Spot.Side side, double x1, double x2, double y1, double y2, double z1, double z2) {}
    private static final Box[] BOXES = {
            new Box("body", Piece.TOP, Spot.Side.BODY, -5, 5, 11, 25, -3, 3),
            new Box("right arm", Piece.TOP, Spot.Side.RIGHT, 3, 9, 11, 25, -3, 3),
            new Box("left arm", Piece.TOP, Spot.Side.LEFT, -9, -3, 11, 25, -3, 3),
            new Box("right leg", Piece.BOTTOM, Spot.Side.RIGHT, -1, 5, -1, 13, -3, 3),
            new Box("left leg", Piece.BOTTOM, Spot.Side.LEFT, -5, 1, -1, 13, -3, 3),
    };

    /** What a player is currently previewing: stand and placement. */
    private record Aim(UUID stand, Placement placement) {}
    private static final Map<UUID, Aim> AIMS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StandSewing::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof ArmorStand stand)) {
                return InteractionResult.PASS;
            }
            ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
            if (!(ovve.getItem() instanceof OvveItem)) return InteractionResult.PASS;
            ItemStack held = player.getMainHandItem();
            Hit aimed = aim(serverPlayer, stand);
            if (held.getItem() instanceof PatchItem patchItem) {
                Spot spot = aimed == null ? null : spotFor(aimed.spot, patchItem.patch);
                if (spot == null) return InteractionResult.FAIL;
                Placement placement = new Placement(spot, patchItem.patch.id());
                Looks.sew(ovve, placement);
                Looks.setPreview(ovve, null);
                AIMS.remove(player.getUUID());
                if (!player.isCreative()) held.shrink(1);
                celebrate((ServerLevel) level, aimed.where, true);
                serverPlayer.sendOverlayMessage(Component.literal(patchItem.patch.name() + " sewn on the " + spot.label()));
                return InteractionResult.SUCCESS;
            }
            if (held.isEmpty() && aimed != null && aimed.spot != null) {
                Spot spot = aimed.spot;
                Placement there = Looks.at(ovve, spot);
                if (there == null && Spot.SEAT_CELLS.contains(spot)) { spot = Spot.SEAT; there = Looks.at(ovve, spot); }
                if (there == null) return InteractionResult.PASS;
                Looks.unpick(ovve, spot);
                ItemStack back = new ItemStack(ModContent.patchItem(Patches.get(there.patch())));
                if (!player.getInventory().add(back)) player.drop(back, false);
                celebrate((ServerLevel) level, aimed.where, false);
                serverPlayer.sendOverlayMessage(Component.literal(Patches.get(there.patch()).name() + " unpicked"));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    /** Where a patch lands when aimed at a cell: a seat patch aimed at either seat cell goes on the seat; else the cell, if it takes the patch. */
    private static Spot spotFor(Spot aimed, Patches.Patch patch) {
        if (aimed == null) return null;
        if (patch.seat()) return Spot.SEAT_CELLS.contains(aimed) ? Spot.SEAT : null;
        return patch.fits(aimed) ? aimed : null;
    }

    private static void celebrate(ServerLevel level, Vec3 where, boolean sewn) {
        level.sendParticles(sewn ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.POOF, where.x, where.y, where.z, 12, 0.15, 0.15, 0.15, 0.02);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, sewn ? 1.2f : 0.8f);
    }

    /** Every tick: a player holding a patch previews it on the stand cell they look at. */
    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Aim previous = AIMS.get(player.getUUID());
            Aim current = null;
            ItemStack aimedOvve = null;
            if (player.getMainHandItem().getItem() instanceof PatchItem patchItem) {
                for (ArmorStand stand : player.level().getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(REACH))) {
                    ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
                    if (!(ovve.getItem() instanceof OvveItem)) continue;
                    Hit hit = aim(player, stand);
                    if (hit == null) continue;
                    Spot spot = spotFor(hit.spot, patchItem.patch);
                    if (spot != null) {
                        current = new Aim(stand.getUUID(), new Placement(spot, patchItem.patch.id()));
                        aimedOvve = ovve;
                        if (server.getTickCount() % 10 == 0) player.sendOverlayMessage(Component.literal("→ " + spot.label()));
                    } else if (server.getTickCount() % 20 == 0) {
                        player.sendOverlayMessage(Component.literal(hit.spot == null
                                ? "Nothing goes on the " + hit.part
                                : patchItem.patch.name() + " doesn't go on the " + hit.spot.label()));
                    }
                    break;
                }
            }
            if (previous != null && !previous.equals(current)) {
                // Clear before setting: the new spot may be on the same stand.
                clearPreview(player.level(), previous.stand);
                AIMS.remove(player.getUUID());
            }
            if (current != null && !current.equals(previous)) {
                Looks.setPreview(aimedOvve, current.placement);
                AIMS.put(player.getUUID(), current);
            }
        }
    }

    private static void clearPreview(net.minecraft.world.level.Level level, UUID standId) {
        if (level instanceof ServerLevel server && server.getEntity(standId) instanceof ArmorStand stand) {
            ItemStack ovve = stand.getItemBySlot(EquipmentSlot.LEGS);
            if (ovve.getItem() instanceof OvveItem) Looks.setPreview(ovve, null);
        }
    }

    // ------------------------------------------------------------ aiming

    /** @param spot null when no patch goes on the aimed part of the face */
    private record Hit(String part, Spot spot, Vec3 where) {}

    /** Where the player's view ray meets the stand's model: the cell there, or null if the ray misses. */
    private static @Nullable Hit aim(ServerPlayer player, ArmorStand stand) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0f);
        // The stand's frame.
        float yaw = stand.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 o = eye.subtract(stand.position());
        double[] origin = {o.dot(right) * 16, o.y * 16, o.dot(forward) * 16};
        double[] dir = {view.dot(right), view.y, view.dot(forward)};

        double bestT = REACH * 16;
        Box bestBox = null;
        int bestAxis = -1;
        double bestSign = 0;
        for (Box box : BOXES) {
            double[] hit = slab(origin, dir, box);
            if (hit != null && hit[0] < bestT) {
                bestT = hit[0];
                bestBox = box;
                bestAxis = (int) hit[1];
                bestSign = hit[2];
            }
        }
        if (bestBox == null || bestAxis == 1) return null; // top/bottom faces carry no patches
        double px = origin[0] + dir[0] * bestT, py = origin[1] + dir[1] * bestT, pz = origin[2] + dir[2] * bestT;
        Vec3 where = eye.add(view.scale(bestT / 16));

        // Which strip of the box, and the cell within it. Strips are 4 wide except the body's front and back (8).
        int strip;      // texture u of the strip's left edge, in the standard (right-limb) layout
        double along;   // 0..width along the strip's u direction, in texels
        boolean bodyBox = bestBox.part.equals("body");
        int base = bodyBox ? 16 : bestBox.piece == Piece.TOP ? 40 : 0;
        int depth = bodyBox ? 8 : 4;
        // For a mirrored limb the outer face is on the other side, so flip x into right-limb terms.
        double lx = bestBox.side == Spot.Side.LEFT ? -px : px;
        double lz = pz;
        double xMin = bestBox.side == Spot.Side.LEFT ? -bestBox.x2 : bestBox.x1;
        double xMax = bestBox.side == Spot.Side.LEFT ? -bestBox.x1 : bestBox.x2;
        double sign = bestBox.side == Spot.Side.LEFT ? -bestSign : bestSign;
        if (bestAxis == 0 && sign > 0) {          // wearer's right face / outer: u runs back → front
            strip = base;
            along = lz - bestBox.z1;
        } else if (bestAxis == 2 && sign > 0) {   // front: u runs right → left
            strip = base + 4;
            along = xMax - lx;
        } else if (bestAxis == 0) {               // left face / inner: u runs front → back
            strip = base + 4 + depth;
            along = bestBox.z2 - lz;
        } else {                                  // back: u runs left → right
            strip = base + 8 + depth;
            along = lx - xMin;
        }
        int col = Math.max(0, Math.min(depth / 4 - 1, (int) Math.floor(along / 4)));
        double v = 20 + Math.max(0, Math.min(11.999, bestBox.y2 - 1 - py));   // texel row down the 12-tall face
        return new Hit(bestBox.part, Spot.nearest(bestBox.piece, strip + col * 4, v, bestBox.side), where);
    }

    /** Ray/box slab test: [t, axis, sign of the entered face's normal] or null. */
    private static double @Nullable [] slab(double[] o, double[] d, Box b) {
        double[] min = {b.x1, b.y1, b.z1}, max = {b.x2, b.y2, b.z2};
        double tNear = 0, tFar = Double.MAX_VALUE;
        int axis = -1;
        double sign = 0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (o[i] < min[i] || o[i] > max[i]) return null;
                continue;
            }
            double t1 = (min[i] - o[i]) / d[i], t2 = (max[i] - o[i]) / d[i];
            double enterSign = -1;
            if (t1 > t2) { double t = t1; t1 = t2; t2 = t; enterSign = 1; }
            if (t1 > tNear) { tNear = t1; axis = i; sign = enterSign; }
            tFar = Math.min(tFar, t2);
            if (tNear > tFar) return null;
        }
        return axis < 0 ? null : new double[]{tNear, axis, sign};
    }
}
