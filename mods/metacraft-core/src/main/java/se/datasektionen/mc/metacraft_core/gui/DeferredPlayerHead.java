package se.datasektionen.mc.metacraft_core.gui;

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

public class DeferredPlayerHead implements GuiElementInterface {

	private final GameProfile profile;
	private final ItemStack head;
	private boolean initialized = false;
	private final ClickCallback callback;

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
