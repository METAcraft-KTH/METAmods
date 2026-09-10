package metacraft.ovvar.sewing;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import metacraft.ovvar.pack.Combos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static metacraft.ovvar.sewing.SewingFont.CELL;
import static metacraft.ovvar.sewing.SewingFont.COLS;
import static metacraft.ovvar.sewing.SewingFont.MARK;
import static metacraft.ovvar.sewing.SewingFont.NEEDLE_LENGTH;
import static metacraft.ovvar.sewing.SewingFont.NEEDLE_WIDTH;
import static metacraft.ovvar.sewing.SewingFont.PICTURE_HEIGHT;
import static metacraft.ovvar.sewing.SewingFont.PICTURE_WIDTH;
import static metacraft.ovvar.sewing.SewingFont.ROWS;
import static metacraft.ovvar.sewing.SewingFont.overlayTop;
import static metacraft.ovvar.sewing.SewingFont.overlayX;

/**
 * The stitching minigame ({@code sewing_minigame} in the config): instead of sewing in one click,
 * right-clicking the stand opens a dialog that draws the patch lying on the ovve's cloth and the
 * seam going around its edge — the holes alternate just outside and just inside the edge, each a
 * little further along, like a whip stitch ({@link Seam}). The needle sits on the next hole,
 * coming in over the edge; you pull it through by clicking where it is. Each pull is a stitch
 * sound at the stand, and the last one sews the patch for real ({@link StandSewing#finish}).
 * Escape or the "Cut the thread" band abandons it; nothing is taken from the player until the
 * seam is done.
 *
 * <p>Every button in the dialog is a sprite: the picture is a grid of cloth cells whose labels are
 * glyphs of the {@link SewingFont}, hiding the vanilla buttons and meeting across the gaps; the
 * last cell's label also draws everything that lies on the cloth. The cell under the needle is
 * the one that carries the pull action.
 *
 * <p>The clicks come back as custom click actions ({@code ovvar:pull}, {@code ovvar:cut}) through
 * {@code CustomClickMixin}; a token per game keeps a stale dialog from stitching a new one.
 */
public final class SewingGame {
    private SewingGame() {}

    public static final Identifier PULL = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "pull");
    public static final Identifier CUT = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "cut");

    private record Game(UUID stand, Chapter chapter, Placement placement, Patches.Patch patch, int token, int stitches, int done) {
        Game advanced() {
            return new Game(stand, chapter, placement, patch, token, stitches, done + 1);
        }
    }

    private static final Map<UUID, Game> GAMES = new HashMap<>();

    /** Opens the seam for a placement the player just aimed at. Replaces any game they had going. */
    public static void start(ServerPlayer player, ArmorStand stand, Placement placement, Patches.Patch patch) {
        Chapter chapter = stand.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem ovve ? ovve.chapter : Chapter.values()[0];
        Game game = new Game(stand.getUUID(), chapter, placement, patch, ThreadLocalRandom.current().nextInt(), OvvarConfig.get().stitches(), 0);
        GAMES.put(player.getUUID(), game);
        show(player, game);
    }

    /** A custom click from a dialog: true if it was one of ours. */
    public static boolean click(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (id.equals(CUT)) {
            if (GAMES.remove(player.getUUID()) != null) {
                player.sendOverlayMessage(Component.literal("Thread cut"));
            }
            return true;
        }
        if (!id.equals(PULL)) return false;
        Game game = GAMES.get(player.getUUID());
        if (game == null) return true;
        if (!(payload.orElse(null) instanceof CompoundTag tag)
                || tag.getIntOr("token", 0) != game.token || tag.getIntOr("stitch", -1) != game.done) {
            Ovvar.LOGGER.debug("[ovvar] {} sent a stale pull {}", player.getName().getString(), payload);
            return true;   // a double click or an old dialog: the current one stays as it is
        }
        ArmorStand stand = standFor(player, game);
        if (stand == null) {
            abandon(player, "The thread snapped: the stand is out of reach");
            return true;
        }
        if (!(player.getMainHandItem().getItem() instanceof PatchItem held) || held.patch != game.patch) {
            abandon(player, "The thread snapped: you put the " + game.patch.name() + " away");
            return true;
        }
        Combos.Calm.sewing(player);
        Game next = game.advanced();
        Vec3 where = StandAim.cell(stand, game.placement.spot()).centre();
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, where.x, where.y, where.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6f,
                0.9f + 0.6f * next.done / next.stitches);
        level.sendParticles(ParticleTypes.CRIT, where.x, where.y, where.z, 2, 0.05, 0.05, 0.05, 0.0);
        if (next.done >= next.stitches) {
            GAMES.remove(player.getUUID());
            player.connection.send(ClientboundClearDialogPacket.INSTANCE);
            StandSewing.finish(player, stand, game.placement, held, where);
        } else {
            GAMES.put(player.getUUID(), next);
            show(player, next);
        }
        return true;
    }

    /** The payload the next needle button carries, or null with no seam open (what the game tests click with). */
    public static CompoundTag nextPull(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        return game == null ? null : pullPayload(game, game.done);
    }

    private static CompoundTag pullPayload(Game game, int stitch) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("token", game.token);
        payload.putInt("stitch", stitch);
        return payload;
    }

    private static ArmorStand standFor(ServerPlayer player, Game game) {
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(game.stand) instanceof ArmorStand stand)) return null;
        if (!(stand.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof OvveItem)) return null;
        if (player.getEyePosition().distanceTo(stand.position()) > StandSewing.REACH + 1) return null;
        return stand;
    }

    private static void abandon(ServerPlayer player, String why) {
        GAMES.remove(player.getUUID());
        player.connection.send(ClientboundClearDialogPacket.INSTANCE);
        player.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------ the dialog

    private static void show(ServerPlayer player, Game game) {
        player.openDialog(Holder.direct(dialog(game)));
    }

    /** The dialog as the player currently sees it, or null with no seam open (the game tests check it encodes). */
    public static Dialog dialog(ServerPlayer player) {
        Game game = GAMES.get(player.getUUID());
        return game == null ? null : dialog(game);
    }

    /** The dialog for any state of any seam, for the game tests; the pull payload carries token 0. */
    public static Dialog dialog(Chapter chapter, Patches.Patch patch, Spot spot, int stitches, int done) {
        return dialog(new Game(new UUID(0, 0), chapter, new Placement(spot, patch.id()), patch, 0, stitches, done));
    }

    private static Dialog dialog(Game game) {
        List<Seam.Hole> holes = new Seam(game.patch, game.stitches).holes();
        Seam.Hole next = game.done < holes.size() ? holes.get(game.done) : null;
        List<ActionButton> cells = new ArrayList<>(COLS * ROWS);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                SewingFont.Label label = new SewingFont.Label(CELL).cell(SewingFont.cloth(game.chapter));
                if (row == ROWS - 1 && col == COLS - 1) onTheCloth(label, game, holes, next);
                if (next != null && next.col() == col && next.row() == row) {
                    Component tooltip = Component.literal("Stitch " + (game.done + 1) + " of " + game.stitches);
                    cells.add(new ActionButton(new CommonButtonData(label.component(), Optional.of(tooltip), CELL),
                            Optional.of(new StaticAction(new ClickEvent.Custom(PULL, Optional.of(pullPayload(game, game.done)))))));
                } else {
                    cells.add(new ActionButton(new CommonButtonData(label.component(), CELL), Optional.empty()));
                }
            }
        }
        Component band = new SewingFont.Label(SewingFont.BAND).cell(SewingFont.BAND_GLYPH).component();
        ActionButton cut = new ActionButton(new CommonButtonData(band, Optional.of(Component.literal("Cut the thread")), SewingFont.BAND),
                Optional.of(new StaticAction(new ClickEvent.Custom(CUT, Optional.empty()))));
        Component title = Component.literal("Sewing on the " + game.patch.name());
        List<DialogBody> body = List.of(new PlainMessage(Component.literal(
                "Whip-stitch it onto the " + game.placement.spot().label() + ": click the needle to pull it through."), 250));
        return new MultiActionDialog(
                new CommonDialogData(title, Optional.of(title), true, false, DialogAction.NONE, body, List.of()),
                cells, Optional.of(cut), COLS);
    }

    /**
     * Everything lying on the cloth, drawn by the last cell over the whole picture: the patch, a
     * cross for every stitch pulled, a pinhole for every one to come, and the needle on the next.
     */
    private static void onTheCloth(SewingFont.Label label, Game game, List<Seam.Hole> holes, Seam.Hole next) {
        int cells = game.patch.cells();
        label.at(SewingFont.patch(game.patch), overlayX(Seam.patchX(cells)), overlayTop(Seam.patchY(cells)));
        for (int i = 0; i < holes.size(); i++) {
            Seam.Hole hole = holes.get(i);
            label.at(i < game.done ? SewingFont.CROSS : SewingFont.HOLE, overlayX(hole.x() - MARK / 2), overlayTop(hole.y() - MARK / 2));
        }
        if (next == null) return;
        int across = NEEDLE_WIDTH / 2, tail = NEEDLE_LENGTH - 1;
        switch (next.from()) {
            case LEFT -> needle(label, SewingFont.NEEDLE_R, next.x() - tail, next.y() - across);
            case RIGHT -> needle(label, SewingFont.NEEDLE_L, next.x(), next.y() - across);
            case ABOVE -> needle(label, SewingFont.NEEDLE_D, next.x() - across, next.y() - tail);
            case BELOW -> needle(label, SewingFont.NEEDLE_U, next.x() - across, next.y());
        }
    }

    /** The needle with its top-left at (x, y) on the picture, kept on the cloth. */
    private static void needle(SewingFont.Label label, SewingFont.Glyph glyph, int x, int y) {
        label.at(glyph, overlayX(Mth.clamp(x, 0, PICTURE_WIDTH - glyph.width())), overlayTop(Mth.clamp(y, 0, PICTURE_HEIGHT - glyph.height())));
    }
}
