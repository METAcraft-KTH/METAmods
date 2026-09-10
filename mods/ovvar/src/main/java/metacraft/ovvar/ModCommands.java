package metacraft.ovvar;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Looks;
import metacraft.ovvar.content.ModComponents;
import metacraft.ovvar.content.ModContent;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.Patches;
import metacraft.ovvar.content.Placement;
import metacraft.ovvar.content.Spot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /ovvar} (gamemasters):
 * <ul>
 *   <li>{@code give [player] <chapter> [patches]} — an ovve, top up, with the given patches:
 *       {@code all} (every cell filled, cycling through the patches), {@code none}, or
 *       {@code spot.patch} / bare patch ids (first free cell) separated by commas/spaces; the word
 *       {@code down} anywhere gives it with the top rolled down;</li>
 *   <li>{@code patches <patches>} — re-sew the ovve in your main hand;</li>
 *   <li>{@code showcase <chapter>} — a row of armour stands in front of you: top down, top up, one
 *       per patch (on the chest), every cell filled;</li>
 *   <li>{@code stands <chapter>} — three posed stands wearing a plain ovve, for testing the sewing aim;</li>
 *   <li>{@code minigame [on|off] [stitches]} — the stitching minigame setting, saved to config/ovvar.json.</li>
 * </ul>
 */
public final class ModCommands {
    private ModCommands() {}

    private static final DynamicCommandExceptionType UNKNOWN_CHAPTER =
            new DynamicCommandExceptionType(name -> Component.literal("Unknown chapter '" + name + "'"));
    private static final DynamicCommandExceptionType UNKNOWN_PATCH =
            new DynamicCommandExceptionType(name -> Component.literal("Unknown patch '" + name + "'"));
    private static final DynamicCommandExceptionType NOT_AN_OVVE =
            new DynamicCommandExceptionType(what -> Component.literal("Hold an ovve in your main hand, not " + what));

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal(Ovvar.MOD_ID)
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .then(Commands.literal("give")
                                .then(chapterArg()
                                        .executes(ctx -> give(ctx, ctx.getSource().getPlayerOrException(), ""))
                                        .then(patchesArg().executes(ctx -> give(ctx, ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "patches")))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(chapterArg()
                                                .executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player"), ""))
                                                .then(patchesArg().executes(ctx -> give(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "patches")))))))
                        .then(Commands.literal("patches")
                                .then(patchesArg().executes(ModCommands::resew)))
                        .then(Commands.literal("showcase")
                                .then(chapterArg().executes(ModCommands::showcase)))
                        .then(Commands.literal("stands")
                                .then(chapterArg().executes(ModCommands::stands)))
                        .then(Commands.literal("minigame")
                                .executes(ctx -> minigame(ctx, null, 0))
                                .then(Commands.literal("on").executes(ctx -> minigame(ctx, true, 0))
                                        .then(Commands.argument("stitches", IntegerArgumentType.integer(1, OvvarConfig.MAX_STITCHES))
                                                .executes(ctx -> minigame(ctx, true, IntegerArgumentType.getInteger(ctx, "stitches")))))
                                .then(Commands.literal("off").executes(ctx -> minigame(ctx, false, 0))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> chapterArg() {
        return Commands.argument("chapter", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Chapter.values()).map(c -> c.id), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> patchesArg() {
        return Commands.argument("patches", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("all", "none"),
                        Stream.concat(Patches.all().stream().map(Patches.Patch::id),
                                Arrays.stream(Spot.values()).flatMap(spot -> Patches.all().stream().filter(p -> p.fits(spot))
                                        .map(p -> new Placement(spot, p.id()).key())))), builder));
    }

    private static Chapter chapter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "chapter");
        return Arrays.stream(Chapter.values()).filter(c -> c.id.equals(name)).findFirst().orElseThrow(() -> UNKNOWN_CHAPTER.create(name));
    }

    /** {@code all}, {@code none}, {@code spot.patch} entries, or bare patch ids (first free cell that takes it). */
    private static List<Placement> patches(String spec) throws CommandSyntaxException {
        String s = spec.trim().replaceAll("^(none)?[,\\s]+|[,\\s]+$", "");
        List<Placement> out = new ArrayList<>();
        if (s.isEmpty() || s.equals("none")) return out;
        if (s.equals("all")) {
            List<Patches.Patch> plain = Patches.all().stream().filter(p -> !p.seat()).toList();
            int i = 0;
            for (Spot spot : Spot.values()) {
                if (spot == Spot.SEAT || Spot.SEAT_CELLS.contains(spot)) continue;
                out.add(new Placement(spot, plain.get(i++ % plain.size()).id()));
            }
            Patches.all().stream().filter(Patches.Patch::seat).findFirst().ifPresent(p -> out.add(new Placement(Spot.SEAT, p.id())));
            return out;
        }
        for (String token : s.split("[,\\s]+")) {
            if (Placement.isKey(token)) {
                Placement p = Placement.parse(token);
                out.removeIf(o -> o.spot() == p.spot() || p.spot().overlapping().contains(o.spot()));
                out.add(p);
                continue;
            }
            if (!Patches.exists(token)) throw UNKNOWN_PATCH.create(token);
            Patches.Patch patch = Patches.get(token);
            Spot free = Arrays.stream(Spot.values()).filter(patch::fits)
                    .filter(spot -> out.stream().noneMatch(o -> o.spot() == spot || spot.overlapping().contains(o.spot())))
                    .findFirst().orElseThrow(() -> UNKNOWN_PATCH.create(token + " (no free cell takes it)"));
            out.add(new Placement(free, token));
        }
        return out;
    }

    private static ItemStack ovve(Chapter chapter, boolean topUp, List<Placement> patches) {
        ItemStack stack = new ItemStack(ModContent.ovve(chapter));
        OvveItem.setTopUp(stack, topUp && chapter.rollable || !chapter.rollable);
        Looks.setSewn(stack, patches);
        return stack;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String spec) throws CommandSyntaxException {
        Chapter chapter = chapter(ctx);
        boolean down = spec.matches("(?s).*\\bdown\\b.*");   // "down" anywhere in the spec: top rolled down
        List<Placement> patches = patches(spec.replaceAll("\\bdown\\b", " "));
        ItemStack stack = ovve(chapter, !down, patches);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + player.getName().getString() + " a " + chapter.name
                + " " + chapter.garmentWord() + " with " + patches.size() + " patch(es)"), true);
        return 1;
    }

    private static int resew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof OvveItem)) throw NOT_AN_OVVE.create(held.getItem().toString());
        List<Placement> patches = patches(StringArgumentType.getString(ctx, "patches"));
        Looks.setSewn(held, patches);
        ctx.getSource().sendSuccess(() -> Component.literal("Sewn: " + (patches.isEmpty() ? "nothing" : Placement.combo(patches))), false);
        return 1;
    }

    /**
     * Sewing test rig: three stands in front of you wearing a plain ovve, top up — one at rest,
     * one with the arms out and legs apart (every face reachable), one turned sideways. Hold a
     * patch and aim; sneak for the far face.
     */
    private static int stands(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Chapter chapter = chapter(ctx);
        ServerLevel level = player.level();
        float yaw = player.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 origin = player.position().add(forward.scale(2.5));
        Rotations[][] poses = {
                {new Rotations(-10, 0, 10), new Rotations(-10, 0, -10), new Rotations(0, 0, 0), new Rotations(0, 0, 0)},
                {new Rotations(-10, 0, 80), new Rotations(-10, 0, -80), new Rotations(0, 0, 25), new Rotations(0, 0, -25)},
                {new Rotations(-10, 0, 10), new Rotations(-10, 0, -10), new Rotations(0, 0, 0), new Rotations(0, 0, 0)}};
        String[] labels = {"at rest", "arms out, legs apart", "sideways"};
        for (int i = 0; i < 3; i++) {
            Vec3 pos = origin.add(right.scale(2.5 * (i - 1)));
            ArmorStand stand = new ArmorStand(level, pos.x, Math.floor(pos.y), pos.z);
            float facing = yaw + 180 + (i == 2 ? 90 : 0);
            stand.setYRot(facing);
            stand.setYBodyRot(facing);
            stand.setShowArms(true);
            stand.setRightArmPose(poses[i][0]);
            stand.setLeftArmPose(poses[i][1]);
            stand.setRightLegPose(poses[i][2]);
            stand.setLeftLegPose(poses[i][3]);
            stand.setItemSlot(EquipmentSlot.LEGS, ovve(chapter, true, List.of()));
            stand.setCustomName(Component.literal(labels[i]));
            stand.setCustomNameVisible(true);
            level.addFreshEntity(stand);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Placed 3 sewing stands; hold a patch and aim, sneak for the far face"), false);
        return 3;
    }

    /** Stands 2 blocks apart to the player's right, facing the player. */
    private static int showcase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Chapter chapter = chapter(ctx);
        ServerLevel level = player.level();

        List<ItemStack> looks = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (chapter.rollable) { looks.add(ovve(chapter, false, List.of())); labels.add("top down"); }
        looks.add(ovve(chapter, true, List.of())); labels.add("top up");
        for (Patches.Patch p : Patches.all()) {
            Spot spot = p.seat() ? Spot.SEAT : Spot.FRONT_TOP_RIGHT;
            looks.add(ovve(chapter, true, List.of(new Placement(spot, p.id()))));
            labels.add(p.name() + " (" + spot.label() + ")");
        }
        List<Placement> all = patches("all");
        looks.add(ovve(chapter, true, all)); labels.add("all " + all.size());

        float yaw = player.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 origin = player.position().add(forward.scale(3));
        for (int i = 0; i < looks.size(); i++) {
            Vec3 pos = origin.add(right.scale(2 * i - (looks.size() - 1)));
            ArmorStand stand = new ArmorStand(level, pos.x, Math.floor(pos.y), pos.z);
            stand.setYRot(yaw + 180);
            stand.setYBodyRot(yaw + 180);
            stand.setShowArms(true);
            stand.setLeftArmPose(new Rotations(-10, 0, -10));
            stand.setRightArmPose(new Rotations(-10, 0, 10));
            ItemStack ovve = looks.get(i);
            stand.setItemSlot(EquipmentSlot.LEGS, ovve);
            if (OvveItem.topUp(ovve)) {
                // The companion top is placed by hand so it shows before the first tick.
                ItemStack top = new ItemStack(ModContent.top(chapter));
                List<String> patches = ovve.get(ModComponents.PATCHES);
                if (patches != null) top.set(ModComponents.PATCHES, patches);
                stand.setItemSlot(EquipmentSlot.CHEST, top);
            }
            stand.setCustomName(Component.literal(labels.get(i)));
            stand.setCustomNameVisible(true);
            level.addFreshEntity(stand);
        }
        int count = looks.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Placed " + count + " " + chapter.name + " stands"), false);
        return count;
    }

    private static int minigame(CommandContext<CommandSourceStack> ctx, Boolean on, int stitches) {
        OvvarConfig config = OvvarConfig.get();
        if (on != null) {
            config = new OvvarConfig(on, stitches > 0 ? stitches : config.stitches());
            config.save();
        }
        OvvarConfig now = config;
        ctx.getSource().sendSuccess(() -> Component.literal("Stitching minigame " + (now.sewingMinigame() ? "on, " + now.stitches() + " stitches" : "off")), true);
        return 1;
    }
}
