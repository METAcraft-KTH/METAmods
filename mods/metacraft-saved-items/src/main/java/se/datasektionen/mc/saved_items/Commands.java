package se.datasektionen.mc.saved_items;

import com.mojang.brigadier.arguments.*;
import com.mojang.serialization.JavaOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import se.datasektionen.mc.saved_items.item_saving.SavedItemsData;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {


	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("extract").requires(p -> p.hasPermissionLevel(4)).then(
							argument("type", StringArgumentType.string()).executes(ctx -> {
								var player = ctx.getSource().getPlayerOrThrow();
								var arg = StringArgumentType.getString(ctx, "type");
								return SavedItemsConfig.SavingType.CODEC.parse(
										JavaOps.INSTANCE, arg
								).resultOrPartial(SavedItems.LOGGER::error).map(value -> {
									SavedItemsData.getInstance(ctx.getSource().getServer()).extractItems(
											value, ConstantIntProvider.create(2), ConstantIntProvider.create(64), ItemPredicate.Builder.create().build()
									).forEach(item -> {
										if (!player.getInventory().insertStack(item)) {
											player.dropItem(item, false, false);
										}
									});
									return 1;
								}).orElse(0);
							})
					)
			);
		});
	}
}
