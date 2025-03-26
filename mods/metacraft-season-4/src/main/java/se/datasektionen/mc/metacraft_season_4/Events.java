package se.datasektionen.mc.metacraft_season_4;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import se.datasektionen.mc.metacraft_season_4.end.EndBossPlayerState;

public class Events {

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			EndBossPlayerState.getInstance(world).ifPresent(inst -> inst.tick(world));
		});

		PolymerItemUtils.ITEM_CHECK.register(stack -> stack.isOf(Items.TOTEM_OF_UNDYING));
		PolymerItemUtils.syncDefaultComponent(Items.TOTEM_OF_UNDYING, DataComponentTypes.ENCHANTABLE);
	}

}
