package nu.metacraft.core.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.GuiInterface;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Util;
import nu.metacraft.lib.util.helper.GameProfileHelper;
import org.apache.commons.lang3.mutable.MutableObject;

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
			var apiServices = gui.getPlayer().getEntityWorld().getServer().getApiServices();
			if (apiServices.sessionService().getTextures(profile) == MinecraftProfileTextures.EMPTY) {
				Util.getDownloadWorkerExecutor().execute(() -> {
					var textures = new MutableObject<>(apiServices.profileResolver().getProfileById(profile.id()));
					if (textures.getValue().isEmpty()) {
						textures.setValue(apiServices.profileResolver().getProfileByName(profile.name()));
					}
					if (textures.getValue().isPresent()) {
						if (gui.isOpen()) {
							gui.getPlayer().getEntityWorld().getServer().execute(() -> {
								head.set(
										DataComponentTypes.PROFILE,
										GameProfileHelper.staticComponentBuilder()
												.withID(textures.getValue().get().id())
												.withProperties(textures.getValue().get().properties()).build()
								);
							});
						}
					}
				});
			} else {
				head.set(
						DataComponentTypes.PROFILE,
						GameProfileHelper.staticComponentBuilder()
								.withID(profile.id()).withProperties(profile.properties()).build()
				);
			}
		}
		return GuiElementInterface.super.getItemStackForDisplay(gui);
	}
}
