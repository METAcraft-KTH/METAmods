package metacraft.ovvar;

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
 *   <li>{@code give [player] <chapter> [patches]} — an ovve, top up, with the given patches
 *       ({@code all}, {@code none}, or ids separated by commas/spaces);</li>
 *   <li>{@code patches <patches>} — re-sew the ovve in your main hand;</li>
 *   <li>{@code showcase <chapter>} — a row of armour stands in front of you: top down, top up, each
 *       patch alone, every patch at once.</li>
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
                                .then(chapterArg().executes(ModCommands::showcase)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> chapterArg() {
        return Commands.argument("chapter", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Chapter.values()).map(c -> c.id), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> patchesArg() {
        return Commands.argument("patches", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Stream.concat(Stream.of("all", "none"), Patches.all().stream().map(Patches.Patch::id)), builder));
    }

    private static Chapter chapter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "chapter");
        return Arrays.stream(Chapter.values()).filter(c -> c.id.equals(name)).findFirst().orElseThrow(() -> UNKNOWN_CHAPTER.create(name));
    }

    private static List<String> patches(String spec) throws CommandSyntaxException {
        String s = spec.trim();
        if (s.isEmpty() || s.equals("none")) return List.of();
        if (s.equals("all")) return Patches.all().stream().map(Patches.Patch::id).toList();
        List<String> ids = new ArrayList<>();
        for (String id : s.split("[,\\s]+")) {
            if (!Patches.exists(id)) throw UNKNOWN_PATCH.create(id);
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private static ItemStack ovve(Chapter chapter, boolean topUp, List<String> patches) {
        ItemStack stack = new ItemStack(ModContent.ovve(chapter));
        OvveItem.setTopUp(stack, topUp && chapter.rollable || !chapter.rollable);
        Looks.setPatches(stack, patches);
        return stack;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String spec) throws CommandSyntaxException {
        Chapter chapter = chapter(ctx);
        List<String> patches = patches(spec);
        ItemStack stack = ovve(chapter, true, patches);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + player.getName().getString() + " a " + chapter.name
                + " " + chapter.garmentWord() + " with " + patches.size() + " patch(es)"), true);
        return 1;
    }

    private static int resew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof OvveItem)) throw NOT_AN_OVVE.create(held.getItem().toString());
        List<String> patches = patches(StringArgumentType.getString(ctx, "patches"));
        Looks.setPatches(held, patches);
        ctx.getSource().sendSuccess(() -> Component.literal("Sewn: " + (patches.isEmpty() ? "nothing" : String.join(", ", patches))), false);
        return 1;
    }

    /** Stands 2 blocks apart to the player's right, facing the player. */
    private static int showcase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Chapter chapter = chapter(ctx);
        ServerLevel level = player.level();

        List<ItemStack> looks = new ArrayList<>();
        if (chapter.rollable) looks.add(ovve(chapter, false, List.of()));
        looks.add(ovve(chapter, true, List.of()));
        for (Patches.Patch patch : Patches.all()) looks.add(ovve(chapter, true, List.of(patch.id())));
        looks.add(ovve(chapter, true, Patches.all().stream().map(Patches.Patch::id).toList()));

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
                // Stands don't tick inventories, so the companion top is placed by hand.
                ItemStack top = new ItemStack(ModContent.top(chapter));
                List<String> patches = Looks.patches(ovve);
                if (!patches.isEmpty()) top.set(ModComponents.PATCHES, patches);
                stand.setItemSlot(EquipmentSlot.CHEST, top);
            }
            String label = i == 0 && chapter.rollable ? "top down"
                    : Looks.patches(ovve).isEmpty() ? "top up"
                    : Looks.patches(ovve).size() == 1 ? Looks.patches(ovve).getFirst()
                    : "all " + Looks.patches(ovve).size();
            stand.setCustomName(Component.literal(label));
            stand.setCustomNameVisible(true);
            level.addFreshEntity(stand);
        }
        int count = looks.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Placed " + count + " " + chapter.name + " stands"), false);
        return count;
    }
}
