package se.metacraft.kultur.clienttest.mixin;

import net.minecraft.client.multiplayer.RegistryDataCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The test client and its server share a JVM, and with it the static registries — every item and
 * its default components. A client joining over the network binds those components to its own
 * registries ({@code provides_banner_patterns} of every pattern item to its own banner patterns),
 * which in a shared JVM rebinds the server's items: the server's loom then puts a client holder in
 * the result banner and cannot send it ("Can't find id for kultur:arkitektur"). Vanilla skips that
 * binding for a client in the same JVM only when the connection is in memory; this one is over
 * localhost, so say so here. The synchronised registries still come from the server as for any
 * vanilla client. Test source set only; never ships. See {@code KulturLoomPacketTests}.
 */
@Mixin(RegistryDataCollector.class)
public abstract class SameJvmRegistriesMixin {
	@ModifyVariable(method = "collectGameRegistries", at = @At("HEAD"), argsOnly = true)
	private boolean kultur$sameJvm(boolean tagsAndComponentsForSynchronizedRegistriesOnly) {
		return true;
	}
}
