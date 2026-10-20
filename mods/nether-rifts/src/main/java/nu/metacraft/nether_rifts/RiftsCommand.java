package nu.metacraft.nether_rifts;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.nether_rifts.mob.Showcase;
import nu.metacraft.nether_rifts.raid.RiftRaid;
import nu.metacraft.nether_rifts.rift.*;

import java.util.Optional;

/** {@code /rifts}: staff controls. Later tasks add subcommands to {@link #root()}. */
public final class RiftsCommand {

	public static final Identifier PERMISSION = NetherRifts.getID("rifts");

	private RiftsCommand() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(root()));
	}

	static LiteralArgumentBuilder<CommandSourceStack> root() {
		return Commands.literal("rifts").requires(PermissionPredicates.require(PERMISSION, PermissionLevel.GAMEMASTERS))
				.then(Commands.literal("stabilise").executes(ctx -> {
					Stabilisation.stabilise(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("The realm steadies. Nether portals work again."), true);
					return 1;
				}))
				.then(Commands.literal("destabilise").executes(ctx -> {
					Stabilisation.destabilise(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("The realm is unstable again. Portals open rifts."), true);
					return 1;
				}))
				.then(Commands.literal("showcase").executes(ctx -> {
					var source = ctx.getSource();
					var spawned = Showcase.spawn(source.getLevel(), source.getPosition(), source.getRotation().y);
					source.sendSuccess(() -> Component.literal("Showcase: " + spawned.size() + " mobs. /rifts showcase clear takes them away."), true);
					return spawned.size();
				}).then(Commands.literal("clear").executes(ctx -> {
					int n = Showcase.clear(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.literal("Showcase cleared: " + n + " mobs"), true);
					return n;
				})))
				.then(Commands.literal("reload").executes(ctx -> {
					NetherRiftsConfig.reload();
					ctx.getSource().sendSuccess(() -> Component.literal("Reloaded nether-rifts.json5"), true);
					return 1;
				}))
				.then(Commands.literal("stage").then(Commands.argument("pos", BlockPosArgument.blockPos())
						.then(Commands.literal("lit").executes(ctx -> stage(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Stage.LIT)))
						.then(Commands.literal("unstable").executes(ctx -> stage(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Stage.UNSTABLE)))
						.then(Commands.literal("torn").executes(ctx -> stage(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Stage.TORN)))))
				.then(Commands.literal("form").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
					var level = ctx.getSource().getLevel();
					var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
					var shape = RiftShape.fill(level, pos, RiftShape.FrameTest.of(s -> !s.isAir() && !s.is(net.minecraft.tags.BlockTags.FIRE)));
					if (shape.isEmpty()) {
						ctx.getSource().sendFailure(Component.literal("No closed frame around " + pos.toShortString()));
						return 0;
					}
					RiftData.get(level).open(level, shape.get(), false);
					ctx.getSource().sendSuccess(() -> Component.literal("Opened a rift of " + shape.get().size() + " blocks"), true);
					return 1;
				})))
				.then(Commands.literal("list").executes(ctx -> {
					int n = 0;
					for (var level : ctx.getSource().getServer().getAllLevels()) {
						for (var rift : RiftData.get(level).all()) {
							n++;
							var line = level.dimension().identifier() + " " + rift.shape().centreBlock().toShortString() + " " + rift.stage().getSerializedName()
									+ " " + rift.tier().getSerializedName() + (rift.stage() == Stage.RAID ? " wave " + rift.wave() + "/" + rift.tier().waves() : "");
							ctx.getSource().sendSuccess(() -> Component.literal(line), false);
						}
					}
					int total = n;
					ctx.getSource().sendSuccess(() -> Component.literal(total + " rift(s)"), false);
					return total;
				}))
				.then(raidAction("next-wave", (level, rift, data) -> {
					RiftRaid.aliveMobs(level, rift).forEach(net.minecraft.world.entity.Entity::discard);
					RiftRaid.advanceIfCleared(level, rift, data, java.util.List.of(), () -> true);
				}))
				.then(raidAction("close", RiftRaid::victory))
				.then(raidAction("collapse", (level, rift, data) -> {
					if (rift.stage().raiding()) {
						RiftRaid.collapse(level, rift, data);
					} else {
						rift.fade(level, data);
					}
				}));
	}

	interface RiftAction {
		void run(net.minecraft.server.level.ServerLevel level, Rift rift, RiftData data);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> raidAction(String name, RiftAction action) {
		return Commands.literal(name).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> {
			var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
			var rift = riftNear(ctx.getSource(), pos);
			if (rift.isEmpty()) {
				ctx.getSource().sendFailure(Component.literal("No rift within 16 blocks of " + pos.toShortString()));
				return 0;
			}
			action.run(ctx.getSource().getLevel(), rift.get(), RiftData.get(ctx.getSource().getLevel()));
			ctx.getSource().sendSuccess(() -> Component.literal(name + ": done"), true);
			return 1;
		}));
	}

	static Optional<Rift> riftNear(CommandSourceStack source, BlockPos pos) {
		var data = RiftData.get(source.getLevel());
		return data.at(pos).or(() -> data.all().stream()
				.filter(r -> r.centre().distanceToSqr(Vec3.atCenterOf(pos)) <= 16 * 16)
				.min(java.util.Comparator.comparingDouble(r -> r.centre().distanceToSqr(Vec3.atCenterOf(pos)))));
	}

	private static int stage(CommandSourceStack source, BlockPos pos, Stage stage) {
		var rift = riftNear(source, pos);
		if (rift.isEmpty()) {
			source.sendFailure(Component.literal("No rift within 16 blocks of " + pos.toShortString()));
			return 0;
		}
		if (stage != Stage.TORN && rift.get().stage().raiding()) {
			source.sendFailure(Component.literal("That rift is raiding; use /rifts collapse or close"));
			return 0;
		}
		if (stage == Stage.TORN) {
			rift.get().tear(source.getLevel());
		} else {
			rift.get().setStage(source.getLevel(), stage);
		}
		source.sendSuccess(() -> Component.literal("Rift is now " + stage.getSerializedName()), true);
		return 1;
	}
}
