package se.datasektionen.mc.metacraft_lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.test.TestServer;
import net.minecraft.test.TestSet;
import net.minecraft.util.ApiServices;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import se.datasektionen.mc.metacraft_lib.util.helper.TestHelper;

import java.net.Proxy;

@Mixin(TestServer.class)
public abstract class MixinTestServer extends MinecraftServer implements TestHelper.TestServerExtension {

	@Shadow @Nullable private TestSet testSet;

	public MixinTestServer(Thread serverThread, LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, Proxy proxy, DataFixer dataFixer, ApiServices apiServices, WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory) {
		super(serverThread, session, dataPackManager, saveLoader, proxy, dataFixer, apiServices, worldGenerationProgressListenerFactory);
	}

	@WrapOperation(
		method = {
				"exit",
				"setCrashReport"
		},
		at = @At(
				value = "INVOKE",
				target = "Ljava/lang/System;exit(I)V"
		)
	)
	private void onExit(int status, Operation<Void> original) {
		this.getThread().interrupt();
	}

	@Override
	public boolean metacraft$testPassed() {
		return testSet != null && !testSet.failed();
	}

}
