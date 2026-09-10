package metacraft.ovvar.sewing;

import metacraft.ovvar.Ovvar;
import metacraft.ovvar.OvvarConfig;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.PatchItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
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

/**
 * The stitching minigame ({@code sewing_minigame} in the config): instead of sewing in one click,
 * right-clicking the stand opens a dialog with the seam running down the middle of a two-column
 * grid, one row per stitch. The needle sits on alternate sides; you pull it through by clicking it,
 * so a patch is sewn by clicking back and forth across the seam. Each pull is a stitch sound at
 * the stand, and the last one sews the patch for real ({@link StandSewing#finish}). Escape or
 * "Cut the thread" abandons it; nothing is taken from the player until the seam is done.
 *
 * The clicks come back as custom click actions ({@code ovvar:pull}, {@code ovvar:cut}) through
 * {@code CustomClickMixin}; a token per game keeps a stale dialog from stitching a new one.
 */
public final class SewingGame {
    private SewingGame() {}

    public static final Identifier PULL = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "pull");
    public static final Identifier CUT = Identifier.fromNamespaceAndPath(Ovvar.MOD_ID, "cut");
    private static final int BUTTON_WIDTH = 110;

    private record Game(UUID stand, Placement placement, Patches.Patch patch, int token, int stitches, int done) {
        Game advanced() {
            return new Game(stand, placement, patch, token, stitches, done + 1);
        }

        boolean needleOnLeft(int row) {
            return row % 2 == 0;
        }
    }

    private static final Map<UUID, Game> GAMES = new HashMap<>();

    /** Opens the seam for a placement the player just aimed at. Replaces any game they had going. */
    public static void start(ServerPlayer player, ArmorStand stand, Placement placement, Patches.Patch patch) {
        Game game = new Game(stand.getUUID(), placement, patch, ThreadLocalRandom.current().nextInt(), OvvarConfig.get().stitches(), 0);
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

    private static Dialog dialog(Game game) {
        List<ActionButton> cells = new ArrayList<>(game.stitches * 2);
        for (int row = 0; row < game.stitches; row++) {
            boolean left = game.needleOnLeft(row);
            ActionButton needleSide, otherSide;
            if (row < game.done) {
                needleSide = label(Component.literal("✕").withStyle(ChatFormatting.GOLD));
                otherSide = label(Component.literal("┄").withStyle(ChatFormatting.DARK_GRAY));
            } else if (row == game.done) {
                Component text = Component.literal(left ? "» pull the thread" : "pull the thread «").withStyle(ChatFormatting.GREEN);
                CompoundTag payload = pullPayload(game, row);
                needleSide = new ActionButton(new CommonButtonData(text, Optional.of(Component.literal("Stitch " + (row + 1) + " of " + game.stitches)), BUTTON_WIDTH),
                        Optional.of(new StaticAction(new ClickEvent.Custom(PULL, Optional.of(payload)))));
                otherSide = label(Component.literal("·").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                needleSide = label(Component.literal("·").withStyle(ChatFormatting.DARK_GRAY));
                otherSide = label(Component.literal("·").withStyle(ChatFormatting.DARK_GRAY));
            }
            cells.add(left ? needleSide : otherSide);
            cells.add(left ? otherSide : needleSide);
        }
        ActionButton cut = new ActionButton(new CommonButtonData(Component.literal("Cut the thread"), BUTTON_WIDTH * 2 + 10),
                Optional.of(new StaticAction(new ClickEvent.Custom(CUT, Optional.empty()))));
        Component title = Component.literal("Sewing on the " + game.patch.name());
        List<DialogBody> body = List.of(new PlainMessage(Component.literal(
                "Pull the needle through from the side it is on, back and forth down the seam, on the " + game.placement.spot().label() + "."), 250));
        return new MultiActionDialog(
                new CommonDialogData(title, Optional.of(title), true, false, DialogAction.NONE, body, List.of()),
                cells, Optional.of(cut), 2);
    }

    private static ActionButton label(Component text) {
        return new ActionButton(new CommonButtonData(text, BUTTON_WIDTH), Optional.empty());
    }
}
