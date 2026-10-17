package metacraft.kultur.compat.brewery.mixin;

import eu.pb4.brewery.BreweryInit;
import metacraft.kultur.compat.brewery.BreweryEffectsFiles;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Brewery 0.17 finds every {@code brewery_effects.json} — its drunkenness: the stagger, the nausea,
 * alcohol poisoning, bread and milk sobering you up — with {@code listResources("", …)}, and 26.3
 * refuses an empty path ("Invalid path ''"), so on a real server none of it loads. (In a dev
 * environment Brewery also builds its defaults in code, which hides this.) Here the file is looked
 * up in every namespace instead ({@link BreweryEffectsFiles}). Applied only when Brewery is loaded
 * ({@link metacraft.kultur.compat.brewery.KulturMixinPlugin}).
 */
@Mixin(BreweryInit.class)
public abstract class BreweryEffectsMixin {
	@Redirect(method = "loadDrinks", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Lnet/minecraft/server/packs/resources/ResourceManager$Selector;)Ljava/util/Map;"))
	private static Map<Identifier, Resource> kultur$listEffectsPerNamespace(ResourceManager manager, String path, ResourceManager.Selector filter) {
		return path.isEmpty() ? BreweryEffectsFiles.find(manager) : manager.listResources(path, filter);
	}
}
