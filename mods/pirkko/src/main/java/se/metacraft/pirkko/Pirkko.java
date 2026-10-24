package se.metacraft.pirkko;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public final class Pirkko implements ModInitializer {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("pirkko", "pirkko");

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets("pirkko");
		PolymerResourcePackUtils.markAsRequired();
		PirkkoHeads.register();
		PirkkoCommands.register();
		PirkkoEntity.register();
	}
}
