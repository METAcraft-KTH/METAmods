package nu.metacraft.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import nu.metacraft.core.countdown.Countdown;
import nu.metacraft.core.util.METAcraftCoreData;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CountdownCommand {
	private static final SimpleCommandExceptionType COUNTDOWN_ALREADY_RUNNING
		= new SimpleCommandExceptionType(new LiteralMessage("There is already a timer running."));
	private static final DynamicCommandExceptionType INVALID_DATE
		= new DynamicCommandExceptionType(dateString -> new LiteralMessage("The specified date '"+ dateString +"' is invalid, please use the format " + Countdown.DATE_FORMAT.toPattern()));
	private static final SimpleCommandExceptionType NO_ITEM_DISPLAY
		= new SimpleCommandExceptionType(new LiteralMessage("Please stand next to at least one text display with the tag \"countdown\"."));
	private static final SimpleCommandExceptionType NO_COUNTDOWN
		= new SimpleCommandExceptionType(new LiteralMessage("There is no countdown right now"));

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
		dispatcher.register(
			CommandManager.literal("countdown")
				.requires(Permissions.require("metacraft.countdown", 2))
				.then(
					CommandManager.literal("init")
						.then(
							CommandManager.argument("date", StringArgumentType.greedyString())
								.executes(context -> {
									ServerCommandSource source = context.getSource();
									METAcraftCoreData data = METAcraftCoreData.getInstance(source.getServer());
									if (data.getCountdown().isPresent())
										throw COUNTDOWN_ALREADY_RUNNING.create();

									String dateString = StringArgumentType.getString(context, "date");
									Date date;
									try {
										date = Countdown.DATE_FORMAT.parse(dateString);
									} catch (ParseException e) {
										throw INVALID_DATE.create(dateString);
									}

									BlockPos center = BlockPos.ofFloored(source.getPosition());
									List<UUID> entityUuids = source.getWorld().getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, Box.enclosing(
											center.south(20).east(20).down(20),
											center.north(20).west(20).up(20)
										), entity -> entity.getCommandTags().contains("countdown"))
										.stream()
										.map(Entity::getUuid)
										.toList();

									if (entityUuids.isEmpty()) throw NO_ITEM_DISPLAY.create();

									Countdown countdown = new Countdown(date, entityUuids);
									data.setCountdown(Optional.of(countdown));
									return 1;
								})
						)
				)
				.then(
					CommandManager.literal("date")
						.then(
							CommandManager.argument("date", StringArgumentType.greedyString())
								.executes(context -> {
									Countdown countdown = getCountdownRequired(context);
									String dateString = StringArgumentType.getString(context, "date");
									Date date;
									try {
										date = Countdown.DATE_FORMAT.parse(dateString);
									} catch (ParseException e) {
										throw INVALID_DATE.create(dateString);
									}

									countdown.setDate(date);
									context.getSource().sendMessage(Text.literal("Set the date to " + date.toString() + " (" + countdown.getTimeLeftString() + " left)"));

									return 1;
								})
						)
						.executes(context -> {
							Countdown countdown = getCountdownRequired(context);

							context.getSource().sendMessage(Text.literal(countdown.getDate().toString()));

							return 1;
						})
				)
				.then(
					CommandManager.literal("stop")
						.executes(context -> {
							METAcraftCoreData data = METAcraftCoreData.getInstance(context.getSource().getServer());
							if (data.getCountdown().isEmpty())
								throw NO_COUNTDOWN.create();

							data.setCountdown(Optional.empty());

							context.getSource().sendMessage(Text.literal("The countdown was stopped"));

							return 1;
						})
				)
				.then(
					CommandManager.literal("interval")
						.then(
							CommandManager.argument("interval", TimeArgumentType.time(1))
								.executes(context -> {
									int interval = IntegerArgumentType.getInteger(context, "interval");
									Countdown countdown = getCountdownRequired(context);
									countdown.setInterval(interval);
									context.getSource().sendMessage(Text.literal("Set the interval to "+ interval + " ticks"));
									return 1;
								})
						)
						.executes(context -> {
							Countdown countdown = getCountdownRequired(context);
							int interval = countdown.getInterval();
							context.getSource().sendMessage(Text.literal("The interval is currently set to "+ interval));
							return 1;
						})
				)
				.then(
					CommandManager.literal("action")
						.then(
							CommandManager.literal("add")
								.then(
									CommandManager.argument("name", StringArgumentType.word())
										.then(
											CommandManager.argument("activationMillis", IntegerArgumentType.integer(0))
												.then(
													CommandManager.argument("command", StringArgumentType.greedyString())
														.executes(context -> {
															Countdown countdown = getCountdownRequired(context);
															String name = StringArgumentType.getString(context, "name");
															int activationMillis = IntegerArgumentType.getInteger(context, "activationMillis");
															String command = StringArgumentType.getString(context, "command");

															countdown.addAction(new Countdown.Action(name, activationMillis, command));
															context.getSource().sendMessage(Text.literal("Added action \""+ name +"\" to be executed at "+ activationMillis +" ms: /"+ command));
															return 1;
														})
												)
										)
								)
						)
						.then(
							CommandManager.literal("remove")
								.then(
									CommandManager.argument("name", StringArgumentType.word())
										.suggests((context, builder) -> {
											Countdown countdown = getCountdownRequired(context);
											for (Countdown.Action action : countdown.getActions()) {
                                                if (action.name().startsWith(builder.getRemaining())) {
                                                    builder.suggest(action.name());
                                                }
                                            }
											return builder.buildFuture();
										})
										.executes(context -> {
											Countdown countdown = getCountdownRequired(context);
											String name = StringArgumentType.getString(context, "name");
											boolean removed = countdown.removeAction(name);
											if (removed) {
												context.getSource().sendMessage(Text.literal("Removed action \""+ name +"\"."));
											} else {
												context.getSource().sendMessage(Text.literal("No action with the name \""+ name +"\" was found."));
											}
											return 1;
										})
								)
						)
						.then(
							CommandManager.literal("list")
								.executes(context -> {
									Countdown countdown = getCountdownRequired(context);
									List<Countdown.Action> actions = countdown.getActions();
									if (actions.isEmpty()) {
										context.getSource().sendMessage(Text.literal("There are no actions defined."));
									} else {
										context.getSource().sendMessage(Text.literal("Defined actions:"));
										for (Countdown.Action action : actions) {
											context.getSource().sendMessage(Text.literal(
												"- \""+ action.name() +"\" at "+ action.activationMillis() +" ms: /"+ action.command()
											));
										}
									}
									return 1;
								})
						)
				)
				.then(
					CommandManager.literal("update-entities")
						.executes(context -> {
							Countdown countdown = getCountdownRequired(context);
							BlockPos center = BlockPos.ofFloored(context.getSource().getPosition());
							List<UUID> entityUuids = context.getSource().getWorld().getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, Box.enclosing(
									center.south(20).east(20).down(20),
									center.north(20).west(20).up(20)
								), entity -> entity.getCommandTags().contains("countdown"))
								.stream()
								.map(Entity::getUuid)
								.toList();

							if (entityUuids.isEmpty()) throw NO_ITEM_DISPLAY.create();

							countdown.updateEntityUuids(entityUuids);
							context.getSource().sendMessage(Text.literal("Updated countdown display entities, now using " + entityUuids.size() + " entities."));
							return 1;
						})
				)
		);
	}

	/**
	 * Get the current countdown or throw a fitting exception if it doesn't exist.
	 *
	 * @return The {@link Countdown}
	 * @throws CommandSyntaxException If there is no active {@link Countdown}
	 */
	public static Countdown getCountdownRequired(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		METAcraftCoreData data = METAcraftCoreData.getInstance(ctx.getSource().getServer());
		return data.getCountdown().orElseThrow(NO_COUNTDOWN::create);
	}

}
