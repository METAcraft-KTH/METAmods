package metacraft.ovvar;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.Layout;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /ovvar} (gamemasters):
 * <ul>
 *   <li>{@code give [player] <chapter> [patches]} — an ovve, top up, with the given patches:
 *       {@code all} (every field filled with its first patch), {@code none}, or {@code field=patch}
 *       entries separated by commas/spaces;</li>
 *   <li>{@code patches <patches>} — re-sew the ovve in your main hand;</li>
 *   <li>{@code showcase <chapter>} — a row of armour stands in front of you: top down, top up, each
 *       field alone, every field at once.</li>
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
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("all", "none"),
                        Layout.all().stream().flatMap(f -> f.patches().stream().map(p -> Looks.entry(f.id(), p)))), builder));
    }

    private static Chapter chapter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "chapter");
        return Arrays.stream(Chapter.values()).filter(c -> c.id.equals(name)).findFirst().orElseThrow(() -> UNKNOWN_CHAPTER.create(name));
    }

    /** {@code all}, {@code none}, or {@code field=patch} entries; a bare patch id goes into the first field that takes it. */
    private static Map<String, String> patches(String spec) throws CommandSyntaxException {
        String s = spec.trim();
        Map<String, String> out = new LinkedHashMap<>();
        if (s.isEmpty() || s.equals("none")) return out;
        if (s.equals("all")) {
            for (Layout.Field f : Layout.all()) out.put(f.id(), f.patches().getFirst());
            return out;
        }
        for (String token : s.split("[,\\s]+")) {
            int eq = token.indexOf('=');
            String field = eq < 0 ? null : token.substring(0, eq), patch = eq < 0 ? token : token.substring(eq + 1);
            if (!Patches.exists(patch)) throw UNKNOWN_PATCH.create(patch);
            if (field == null) {
                field = Layout.all().stream().filter(f -> f.accepts(patch) && !out.containsKey(f.id())).map(Layout.Field::id)
                        .findFirst().orElseThrow(() -> UNKNOWN_PATCH.create(patch + " (no free field takes it)"));
            } else if (!Layout.exists(field) || !Layout.get(field).accepts(patch)) {
                throw UNKNOWN_PATCH.create(token);
            }
            out.put(field, patch);
        }
        return out;
    }

    private static ItemStack ovve(Chapter chapter, boolean topUp, Map<String, String> patches) {
        ItemStack stack = new ItemStack(ModContent.ovve(chapter));
        OvveItem.setTopUp(stack, topUp && chapter.rollable || !chapter.rollable);
        Looks.setSewn(stack, patches);
        return stack;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String spec) throws CommandSyntaxException {
        Chapter chapter = chapter(ctx);
        Map<String, String> patches = patches(spec);
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
        Map<String, String> patches = patches(StringArgumentType.getString(ctx, "patches"));
        Looks.setSewn(held, patches);
        ctx.getSource().sendSuccess(() -> Component.literal("Sewn: " + (patches.isEmpty() ? "nothing" : patches.toString())), false);
        return 1;
    }

    /** Stands 2 blocks apart to the player's right, facing the player. */
    private static int showcase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Chapter chapter = chapter(ctx);
        ServerLevel level = player.level();

        List<ItemStack> looks = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (chapter.rollable) { looks.add(ovve(chapter, false, Map.of())); labels.add("top down"); }
        looks.add(ovve(chapter, true, Map.of())); labels.add("top up");
        for (Layout.Field f : Layout.all()) {
            looks.add(ovve(chapter, true, Map.of(f.id(), f.patches().getFirst())));
            labels.add(f.name() + ": " + Patches.get(f.patches().getFirst()).name());
        }
        Map<String, String> all = new LinkedHashMap<>();
        for (Layout.Field f : Layout.all()) all.put(f.id(), f.patches().get(Math.min(f.patches().size() - 1, Layout.all().indexOf(f))));
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
}
