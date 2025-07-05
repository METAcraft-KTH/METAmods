package nu.metacraft.core.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.GuiInterface;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Creates a player head icon without causing lag spikes when loading the game profile.
 */
public class DeferredPlayerHead implements GuiElementInterface {

	private final GameProfile profile;
	private final ItemStack head;
	private boolean initialized = false;
	private final ClickCallback callback;

	/**
	 * Creates a player head icon without causing lag spikes when loading the game profile.
	 * @param profile The game profile to attach when it is ready. Will overwrite whatever is in components when ready.
	 * @param components The components of the item stack. May contain a profile component to use until profile is ready.
	 * @param callback The click event.
	 */
	public DeferredPlayerHead(GameProfile profile, ComponentChanges components, ClickCallback callback) {
		this.profile = profile;
		this.head = new ItemStack(Items.PLAYER_HEAD.getRegistryEntry(), 1, components);
		this.callback = callback;
	}

	@Override
	public ItemStack getItemStack() {
		return head;
	}

	@Override
	public ClickCallback getGuiCallback() {
		return callback;
	}

	@Override
	public ItemStack getItemStackForDisplay(GuiInterface gui) {
		if (gui.isOpen() && !initialized) {
			initialized = true;
			if (gui.getPlayer().getServer().getSessionService().getTextures(profile) == MinecraftProfileTextures.EMPTY) {
				SkullBlockEntity.fetchProfileByUuid(profile.getId()).thenAccept(profile -> {
					profile.ifPresent(p -> {
						if (gui.isOpen()) {
							gui.getPlayer().getServer().execute(() -> {
								head.set(DataComponentTypes.PROFILE, new ProfileComponent(p));
							});
						}
					});
				});
			} else {
				head.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
			}
		}
		return GuiElementInterface.super.getItemStackForDisplay(gui);
	}
}
