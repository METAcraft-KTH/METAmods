package nu.metacraft.pause;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.function.Consumer;

public class PauseData extends SavedData {

	private static final Identifier PAUSED = METAcraftPause.getID("paused");
	
	private boolean paused;

	private final Timer timer = new Timer();
	private UnpauseTask unpauseTask = null;

	public static PauseData getInstance(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	private static final Codec<PauseData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.BOOL.optionalFieldOf("paused", false).forGetter(d -> d.paused)
			).apply(instance, PauseData::new)
	);

	private static final SavedDataType<PauseData> TYPE = new SavedDataType<>(
			METAcraftPause.getID("pause"), PauseData::createNew, CODEC, null
	);

	private static PauseData createNew() {
		return new PauseData(false);
	}

	private void updateAttribute(LivingEntity entity, Holder<Attribute> attribute, Consumer<AttributeInstance> updateAction) {
		var instance = entity.getAttribute(attribute);
		if (instance != null) {
			updateAction.accept(instance);
		}
	}

	private void addTransientAttributeModifier(LivingEntity entity, Holder<Attribute> attribute, AttributeModifier modifier) {
		updateAttribute(entity, attribute, instance -> {
			instance.addOrUpdateTransientModifier(modifier);
		});
	}

	private void removeAttributeModifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		updateAttribute(entity, attribute, instance -> instance.removeModifier(id));
	}

	public void updatePaused(ServerPlayer player) {
		if (shouldFreezeWhenPaused(player)) {
			player.connection.send(new ClientboundBundlePacket(List.of(
					new ClientboundSetTitlesAnimationPacket(0, Integer.MAX_VALUE, 0),
					new ClientboundSetTitleTextPacket(Component.literal("Game Paused")),
					new ClientboundSetSubtitleTextPacket(Component.literal(""))
			)));
			player.closeContainer();
			player.stopSleeping();

			var data = (PausePlayerData) player;
			var beforePauseVelocity = data.metacraft_pause$getBeforePauseVelocity();
			if (beforePauseVelocity == null) {
				data.metacraft_pause$setBeforePauseVelocity(player.getRootVehicle().getDeltaMovement());
			}
			var modifier = new AttributeModifier(
					PAUSED, -1,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
			);

			addTransientAttributeModifier(player, Attributes.GRAVITY, modifier);
			addTransientAttributeModifier(player, Attributes.JUMP_STRENGTH, modifier);

			if (player.getRootVehicle() instanceof LivingEntity living && living != player) {
				addTransientAttributeModifier(living, Attributes.GRAVITY, modifier);
				addTransientAttributeModifier(living, Attributes.JUMP_STRENGTH, modifier);
			}
		}
	}

	public void updateResumed(ServerPlayer player) {
		player.connection.send(new ClientboundBundlePacket(List.of(
				new ClientboundSetTitleTextPacket(Component.literal("Game Resumed!")),
				new ClientboundSetSubtitleTextPacket(Component.literal("")),
				new ClientboundSoundPacket(
						SoundEvents.NOTE_BLOCK_PLING, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(),
						1, 2, player.getRandom().nextLong()
				),
				new ClientboundContainerSetContentPacket(
						0, player.inventoryMenu.getStateId(),
						player.inventoryMenu.getItems(), player.inventoryMenu.getCarried()
				)
		)));


		if (shouldFreezeWhenPaused(player)) {
			player.connection.teleport(
					player.getX(), player.getY(), player.getZ(),
					player.getYRot(), player.getXRot()
			);
			removeAttributeModifier(player, Attributes.GRAVITY, PAUSED);
			removeAttributeModifier(player, Attributes.JUMP_STRENGTH, PAUSED);

			if (player.getRootVehicle() instanceof LivingEntity living && living != player) {
				removeAttributeModifier(living, Attributes.GRAVITY, PAUSED);
				removeAttributeModifier(living, Attributes.JUMP_STRENGTH, PAUSED);
			}

			var data = (PausePlayerData) player;
			var beforePauseVelocity = data.metacraft_pause$getBeforePauseVelocity();
			if (beforePauseVelocity != null) {
				data.metacraft_pause$setBeforePauseVelocity(null);
				player.getRootVehicle().setDeltaMovement(beforePauseVelocity);
				player.syncVelocity = true;
			}
		}
	}

	public void onPlayerJoin(ServerPlayer player) {
		if (paused) {
			updatePaused(player);
		}
	}

	public void updatePauseState(MinecraftServer server) {
		if (unpauseTask != null) {
			unpauseTask.cancel();
			unpauseTask = null;
		}

		var tickManager = server.tickRateManager();

		if (paused) {
			if (tickManager.isSprinting()) {
				tickManager.stopSprinting();
			}

			if (tickManager.isSteppingForward()) {
				tickManager.stopStepping();
			}
			for (var player : server.getPlayerList().getPlayers()) {
				updatePaused(player);
			}
		}

		tickManager.setFrozen(paused);

		if (!paused) {
			for (var player : server.getPlayerList().getPlayers()) {
				updateResumed(player);
			}
		}
	}
	
	protected void setPaused(MinecraftServer server, boolean paused) {
		this.paused = paused;
		updatePauseState(server);
		setDirty();
	}

	public boolean isResuming() {
		return unpauseTask != null;
	}

	public void pause(MinecraftServer server) {
		setPaused(server, true);
	}

	public void unpause(MinecraftServer server, int secondsDelay) {
		if (secondsDelay <= 0) {
			setPaused(server, false);
		} else {
			timer.schedule(
					unpauseTask = new UnpauseTask(server, secondsDelay),
					0,
					1000
			);
		}
	}
	
	public boolean isPaused() {
		return paused;
	}

	protected PauseData(boolean paused) {
		this.paused = paused;
	}

	public void killTimer() {
		timer.cancel();
	}

	public static class UnpauseTask extends TimerTask {

		private final MinecraftServer server;
		private int secondsRemaining;

		public UnpauseTask(MinecraftServer server, int secondsRemaining) {
			this.server = server;
			this.secondsRemaining = secondsRemaining;
		}

		private Holder<SoundEvent> getSound(int timeRemaining) {
			if (timeRemaining > 3) {
				return SoundEvents.NOTE_BLOCK_BELL;
			} else {
				return SoundEvents.NOTE_BLOCK_PLING;
			}
		}

		private float getPitch(int timeRemaining) {
			return 1;
		}

		@Override
		public void run() {
			if (secondsRemaining <= 0) {
				server.execute(() -> {
					var data = PauseData.getInstance(server);
					data.setPaused(server, false);
				});
				cancel();
			} else {
				int timeCapture = secondsRemaining;
				server.execute(() -> {
					for (var player : server.getPlayerList().getPlayers()) {
						player.connection.send(new ClientboundBundlePacket(List.of(
								new ClientboundClearTitlesPacket(true),
								new ClientboundSetTitleTextPacket(Component.literal("Game Resuming!")),
								new ClientboundSetSubtitleTextPacket(Component.literal("In " + timeCapture + " seconds!")),
								new ClientboundSoundPacket(
										getSound(timeCapture), SoundSource.MASTER, player.getX(), player.getY(), player.getZ(),
										1, getPitch(timeCapture), player.getRandom().nextLong()
								)
						)));
					}
				});
				secondsRemaining--;
			}
		}
	}

	public static boolean shouldFreezeWhenPaused(ServerPlayer player) {
		return !Permissions.check(player.createCommandSourceStack(), "metacraft.pause.can_move", PermissionLevel.ADMINS);
	}

}
