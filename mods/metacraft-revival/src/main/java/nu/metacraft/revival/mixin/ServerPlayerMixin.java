package nu.metacraft.revival.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.Team;
import nu.metacraft.revival.METAcraftRevival;
import nu.metacraft.revival.RevivalConfig;
import nu.metacraft.revival.RevivalDamageTypes;
import nu.metacraft.revival.RevivalTags;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import nu.metacraft.revival.util.helper.RevivalHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements ServerPlayerExtension {

	@Unique
	private static final AttributeModifier FREEZE = new AttributeModifier(
			METAcraftRevival.getID("revival_stop_moving"),
			-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
	);

	public ServerPlayerMixin(Level level, GameProfile gameProfile) {
		super(level, gameProfile);
	}

	@Shadow
	public abstract @NotNull ServerLevel level();

	@Shadow
	@Final
	private MinecraftServer server;
	@Shadow
	public ServerGamePacketListenerImpl connection;
	@Unique
	private boolean metacraft$unconscious = false;

	@Unique
	private boolean metacraft$revivalMenuOpen = false;

	@Unique
	private int metacraft$timeUntilDeath = 0;

	@Unique
	private int metacraft$timeUntilRevival = RevivalConfig.getConfig().reviveDuration();



	@Unique
	private DamageSource metacraft$prevSource = null;

	@Unique
	private Component metacraft$deathMessage = null;

	@Unique
	private Component metacraft$revivalStatus = null;

	@Unique
	private Player metacraft$reviver = null;

	@Unique
	private void quitReviving() {
		if (metacraft$reviver != null) {
			metacraft$reviver.displayClientMessage(
					Component.translatableWithFallback(
							"dialog.metacraft.revival.abort",
							"Revival aborted, leaving " + getDisplayName().getString() + " to die",
							getDisplayName()
					),
					true
			);
		}
		resetReviver();
	}

	@Unique
	private void resetReviver() {
		metacraft$reviver = null;
	}

	@Override
	public void metacraft$resetRevivalState() {
		metacraft$deathMessage = null;
		metacraft$prevSource = null;
		metacraft$unconscious = false;
		metacraft$revivalStatus = null;
		metacraft$revivalMenuOpen = false;
		metacraft$timeUntilDeath = 0;
		metacraft$timeUntilRevival = RevivalConfig.getConfig().reviveDuration();
		resetReviver();
		removeMovementFreeze();
	}

	@Override
	public void metacraft$setUnconscious(boolean unconscious) {
		metacraft$unconscious = unconscious;
	}

	@Override
	public Component metacraft$getDeathMessage() {
		return metacraft$deathMessage;
	}

	@Override
	public boolean metacraft$isUnconscious() {
		return metacraft$unconscious;
	}

	@Override
	public void metacraft$setRevivalMenuOpen(boolean open) {
		metacraft$revivalMenuOpen = open;
	}

	@Override
	public boolean metacraft$isRevivalMenuOpen() {
		return metacraft$revivalMenuOpen;
	}

	@ModifyReturnValue(method = "mayInteract", at = @At("RETURN"))
	public boolean mayInteract(boolean original) {
		if (metacraft$isUnconscious()) {
			return false;
		}
		return original;
	}

	@ModifyReturnValue(
		method = "isInvulnerableTo", at = @At("RETURN")
	)
	public boolean isInvulnerableTo(boolean original, @Local(argsOnly = true) DamageSource damageSource) {
		if (metacraft$unconscious && !damageSource.is(RevivalTags.Damage.BYPASSES_REVIVAL)) return true;
		return original;
	}

	@Inject(method = "disconnect", at = @At("RETURN"))
	public void disconnect(CallbackInfo ci) {
		if (metacraft$unconscious) RevivalHelper.playerAcceptedFate((ServerPlayer) (Object) this);
	}

	@Unique
	private void updateRevivalStatus() {
		var prevMessage = metacraft$revivalStatus;

		if (metacraft$reviver == null) {
			if (metacraft$timeUntilDeath >= 0) {
				var seconds = getSeconds(metacraft$timeUntilDeath);
				metacraft$revivalStatus = Component.translatableWithFallback(
						"dialog.metacraft.revival.time_until_death",
						seconds + " seconds left until you die",
						seconds
				);
			} else {
				metacraft$revivalStatus = Component.literal("");
			}
		} else {
			var seconds = getSeconds(metacraft$timeUntilRevival);
			metacraft$revivalStatus = Component.translatableWithFallback(
					"dialog.metacraft.revival.time_until_revival",
					 "Waking up in " + seconds + " seconds",
					seconds
			);
		}

		if (!metacraft$isRevivalMenuOpen()) {
			displayClientMessage(metacraft$revivalStatus, true);
		}

		if (!Objects.equals(prevMessage, metacraft$revivalStatus)) {
			RevivalHelper.updateRevivalMenu((ServerPlayer) (Object) this);
		}
	}

	@Unique
	private LootContext createContext() {
		return new LootContext.Builder(
				new LootParams.Builder(level()).withParameter(
						LootContextParams.ORIGIN, position()
				).withParameter(
						LootContextParams.THIS_ENTITY, this
				).create(LootContextParamSets.ADVANCEMENT_ENTITY)
		).create(Optional.empty());
	}

	@Unique
	private void revive() {
		metacraft$reviver.displayClientMessage(
				Component.translatableWithFallback(
						"dialog.metacraft.revival.success",
						"Successfully revived " + getDisplayName().getString() + "!",
						getDisplayName()
				),
				true
		);
		metacraft$resetRevivalState();

		var effects = RevivalConfig.getConfig().reviveEffects();
		setHealth(effects.health());
		var ctx = createContext();
		int currentAir = getAirSupply();
		int targetAir = effects.air().clamp(ctx, currentAir);
		if (currentAir != targetAir) {
			setAirSupply(targetAir);
		}

		int currentHunger = foodData.getFoodLevel();
		int targetHunger = effects.hunger().clamp(ctx, currentHunger);
		if (currentHunger != targetHunger) {
			foodData.setFoodLevel(targetHunger);
		}

		var saturationRange = effects.saturation();
		if (!saturationRange.matches(foodData.getSaturationLevel())) {
			float min = saturationRange.min().orElse((double) Float.MIN_VALUE).floatValue();
			float max = saturationRange.max().orElse((double) Float.MAX_VALUE).floatValue();
			foodData.setSaturation(
					Mth.clamp(foodData.getSaturationLevel(), min, max)
			);
		}

		for (var effect : effects.effects()) {
			addEffect(new MobEffectInstance(effect));
		}

		connection.send(ClientboundClearDialogPacket.INSTANCE);
	}

	@Unique
	private int getSeconds(int ticks) {
		return (ticks / 20) + 1;
	}

	@Unique
	private void removeMovementFreeze() {
		getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FREEZE.id());
		getAttribute(Attributes.JUMP_STRENGTH).removeModifier(FREEZE.id());
	}

	@Unique
	private void nullifyAttribute(AttributeInstance attribute) {
		if (attribute.getValue() != 0) {
			attribute.removeModifier(FREEZE.id());
			attribute.addTransientModifier(FREEZE);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	public void tick(CallbackInfo ci) {
		if (metacraft$unconscious) {
			updateRevivalStatus();
			nullifyAttribute(getAttribute(Attributes.MOVEMENT_SPEED));
			nullifyAttribute(getAttribute(Attributes.JUMP_STRENGTH));

			if (isPassenger()) {
				stopRiding();
			}

			if (metacraft$reviver == null) {
				if (metacraft$timeUntilDeath > 0) {
					metacraft$timeUntilDeath--;
				}
				if (metacraft$timeUntilDeath == 0) {
					RevivalHelper.playerAcceptedFate((ServerPlayer) (Object) this);
				}
				if (metacraft$timeUntilRevival < RevivalConfig.getConfig().reviveDuration()) {
					metacraft$timeUntilRevival++;
				}
			} else {
				var result = ProjectileUtil.getHitResultOnViewVector(
						metacraft$reviver, EntitySelector.CAN_BE_PICKED, metacraft$reviver.entityInteractionRange()
				);
				var seconds = getSeconds(metacraft$timeUntilRevival);
				metacraft$reviver.displayClientMessage(
						Component.translatableWithFallback(
								"dialog.metacraft.revival.time_until_revival_other",
								"Reviving " + getDisplayName().getString() + ": " + seconds + " seconds left",
								getDisplayName(), seconds
						), true
				);
				if (result.getType() == HitResult.Type.ENTITY) {
					var entity = ((EntityHitResult) result).getEntity();
					if (entity != this) {
						quitReviving();
					}
				} else {
					quitReviving();
				}

				if (metacraft$timeUntilRevival > 0) {
					metacraft$timeUntilRevival--;
				}
				if (metacraft$timeUntilRevival == 0) {
					revive();
				}
			}
		}
	}

	@Inject(
			method = "die",
			at = @At("HEAD"),
			cancellable = true
	)
	public void checkRevival(DamageSource damageSource, CallbackInfo ci) {
		if (!damageSource.is(RevivalDamageTypes.ACCEPTED_FATE)) metacraft$prevSource = null;
		if (damageSource.is(RevivalTags.Damage.BYPASSES_REVIVAL)) return;
		var p = (ServerPlayer) (Object) this;
		if (RevivalHelper.hasRevival(p)) {
			if (this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
				Component component = this.getCombatTracker().getDeathMessage();
				metacraft$deathMessage = component;
				Team team = this.getTeam();
				if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
					this.server.getPlayerList().broadcastSystemMessage(component, false);
				} else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
					this.server.getPlayerList().broadcastSystemToTeam(this, component);
				} else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
					this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, component);
				}
			}
			metacraft$prevSource = damageSource;
			ci.cancel();
			setHealth(0.5f);
			metacraft$unconscious = true;
			metacraft$timeUntilDeath = RevivalConfig.getConfig().maxWaitTime().orElse(-1);
			RevivalHelper.openRevivalMenu(p);
		}
	}

	@ModifyVariable(
			method = "die",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"
			),
			ordinal = 0,
			argsOnly = true
	)
	public DamageSource resetDamageSourceIfDeathAccepted(DamageSource value) {
		if (value.is(RevivalDamageTypes.ACCEPTED_FATE) && metacraft$prevSource != null) {
			return metacraft$prevSource;
		}
		return value;
	}

	@Inject(method = "die", at = @At("RETURN"))
	public void onDeath(DamageSource damageSource, CallbackInfo ci) {
		if (damageSource.is(RevivalDamageTypes.ACCEPTED_FATE)) {
			metacraft$resetRevivalState();
		}
	}

	@Override
	public Component metacraft$getRevivalStatus() {
		return metacraft$revivalStatus;
	}

	@Override
	public void metacraft$setReviver(Player reviver) {
		metacraft$reviver = reviver;
	}

	@Override
	public Player metacraft$getReviver() {
		return metacraft$reviver;
	}

	@Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
	public void openMenu(MenuProvider menu, CallbackInfoReturnable<OptionalInt> cir) {
		if (metacraft$unconscious) {
			cir.setReturnValue(OptionalInt.empty());
			RevivalHelper.openRevivalMenu((ServerPlayer) (Object) this);
		}
	}

	@Inject(method = {"openHorseInventory", "openItemGui"}, at = @At("HEAD"), cancellable = true)
	public void openMenu(CallbackInfo ci) {
		if (metacraft$unconscious) {
			ci.cancel();
			RevivalHelper.openRevivalMenu((ServerPlayer) (Object) this);
		}
	}
}
