package nu.metacraft.saved_items;

import com.mojang.brigadier.arguments.*;
import com.mojang.serialization.JavaOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.util.valueproviders.ConstantInt;
import nu.metacraft.saved_items.item_saving.SavedItemsData;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Commands {


	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("extract").requires(p -> p.hasPermission(4)).then(
							argument("type", StringArgumentType.string()).executes(ctx -> {
								var player = ctx.getSource().getPlayerOrException();
								var arg = StringArgumentType.getString(ctx, "type");
								return SavedItemsConfig.SavingType.CODEC.parse(
										JavaOps.INSTANCE, arg
								).resultOrPartial(SavedItems.LOGGER::error).map(value -> {
									SavedItemsData.getInstance(ctx.getSource().getServer()).extractItems(
											value, ConstantInt.of(2), ConstantInt.of(64), ItemPredicate.Builder.item().build()
									).forEach(item -> {
										if (!player.getInventory().add(item)) {
											player.drop(item, false, false);
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
