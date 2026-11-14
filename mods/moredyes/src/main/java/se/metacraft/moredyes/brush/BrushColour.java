package se.metacraft.moredyes.brush;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import se.metacraft.moredyes.MoreDyes;

/** A vanilla brush carrying one colour copied off a canvas (see {@code CanvasEntityMixin}), stored as its {@code dyed_color}. */
public final class BrushColour {
	public static final Identifier MODEL = Identifier.fromNamespaceAndPath(MoreDyes.MOD_ID, "canvas_brush");

	private BrushColour() {}

	public static void init() {
		// Polymer leaves vanilla items alone unless asked
		PolymerItemUtils.CONTEXT_ITEM_CHECK.register((item, context) -> item instanceof ItemStack stack && isColoured(stack));
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((original, client, context) -> {
			if (isColoured(original)) client.set(DataComponents.ITEM_MODEL, MODEL);
			return client;
		});
		// canvases are clicked through their own entity, so these only see everything else
		UseItemCallback.EVENT.register((player, level, hand) ->
				player instanceof ServerPlayer server && clear(server, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				player instanceof ServerPlayer server && clear(server, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS);
	}

	public static boolean clear(ServerPlayer player, InteractionHand hand) {
		return player.isShiftKeyDown() && strip(player, hand);
	}

	public static boolean strip(ServerPlayer player, InteractionHand hand) {
		ItemStack brush = player.getItemInHand(hand);
		if (!isColoured(brush)) return false;
		brush.remove(DataComponents.DYED_COLOR);
		player.level().playSound(null, player.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	public static boolean isColoured(ItemStack stack) {
		return stack.is(Items.BRUSH) && stack.has(DataComponents.DYED_COLOR);
	}

	public static int rgb(ItemStack stack) {
		return stack.get(DataComponents.DYED_COLOR).rgb() & 0xFFFFFF;
	}

	/** An offhand item the canvas uses makes the main-hand brush that item's 3x3 tool instead. */
	public static boolean inUse(ServerPlayer player, boolean offhandIsCanvasTool) {
		return player.getMainHandItem().is(Items.BRUSH) && !offhandIsCanvasTool;
	}

	public static void sample(ServerPlayer player, InteractionHand hand, int rgb) {
		ItemStack brush = player.getItemInHand(hand);
		if (isColoured(brush) && rgb(brush) == rgb) return;
		brush.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF));
		player.level().playSound(null, player.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
