package nu.metacraft.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import nu.metacraft.lib.util.helper.TestHelper;

import java.net.Proxy;
import java.util.Optional;

@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin extends MinecraftServer implements TestHelper.TestServerExtension {

	@Shadow @Nullable private MultipleTestTracker testTracker;

	public GameTestServerMixin(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes, NotificationManager notificationManager) {
		super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes, notificationManager);
	}

	@WrapOperation(
		method = {
				"onServerExit",
				"onServerCrash"
		},
		at = @At(
				value = "INVOKE",
				target = "Ljava/lang/System;exit(I)V"
		)
	)
	private void onExit(int status, Operation<Void> original) {
		this.getRunningThread().interrupt();
	}

	@Override
	public boolean metacraft$testPassed() {
		return testTracker != null && !testTracker.hasFailedRequired();
	}

}
