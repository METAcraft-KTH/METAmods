package se.metacraft.config_gui.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.metacraft.config_gui.*;
import se.metacraft.config_gui.util.EditorGUIPreferences;

import java.util.Optional;
import java.util.function.UnaryOperator;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements DialogGUIExtension, ServerPlayerExtension {

	@Shadow
	public abstract void openDialog(Holder<Dialog> dialog);

	@Unique
	private static final String PREFERENCES = "metacraft:gui_editor_preferences";

	@Shadow
	public ServerGamePacketListenerImpl connection;
	@Unique
	@Nullable
	private DialogGUI gui;

	@Unique
	private EditorGUIPreferences preferences = EditorGUIPreferences.EMPTY;

	@Inject(method = "restoreFrom", at = @At("RETURN"))
	public void copyFrom(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
		preferences = ((ServerPlayerExtension) oldPlayer).metacraft$getEditorPreferences();
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void load(ValueInput input, CallbackInfo ci) {
		preferences = input.read(PREFERENCES, EditorGUIPreferences.CODEC).orElse(EditorGUIPreferences.EMPTY);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void save(ValueOutput output, CallbackInfo ci) {
		output.store(PREFERENCES, EditorGUIPreferences.CODEC, preferences);
	}

	@Override
	public EditorGUIPreferences metacraft$getEditorPreferences() {
		return preferences;
	}

	@Override
	public void metacraft$updateEditorPreferences(UnaryOperator<EditorGUIPreferences> modifier) {
		preferences = modifier.apply(preferences);
	}

	@Override
	public void metacraft$openGUI(DialogGUI gui) {
		this.gui = gui;
		if (gui == null) {
			metacraft$closeGUI();
		} else {
			var dialog = gui.createDialog((ServerPlayer) (Object) this);
			if (FabricLoader.getInstance().isDevelopmentEnvironment() && DialogClientClosables.clientClosable(dialog)) {
				throw new IllegalArgumentException("Dialog can be closed without notifying the server!");
			}
			openDialog(dialog);
		}
	}

	@Override
	public void metacraft$closeGUI() {
		boolean forceClose = gui != null && gui.forceClose();
		this.gui = null;
		// Unfortunately, the ClientboundClearDialogPacket does not close the WaitingForResponse screen, so we must send a stronger packet.
		if (forceClose) {
			connection.send(new ClientboundContainerClosePacket(-1));
		} else {
			connection.send(ClientboundClearDialogPacket.INSTANCE);
		}
	}

	@Override
	public Optional<DialogGUI> metacraft$getCurrentGUI() {
		return Optional.ofNullable(gui);
	}
}
