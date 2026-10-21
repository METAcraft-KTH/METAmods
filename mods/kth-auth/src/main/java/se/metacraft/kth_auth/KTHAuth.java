package se.metacraft.kth_auth;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import nu.metacraft.lib.custom_message.CodecMessageHandler;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.metacraft.kth_auth.command.KTHAuthCommand;
import se.metacraft.kth_auth.command.PlusOneCommand;
import se.metacraft.kth_auth.verify.VerificationDetails;
import se.metacraft.kth_auth.verify.Verifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class KTHAuth implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger("kth-auth");
	public static final String USER_AGENT = "KTH-Auth for METAcraft";
	public static final String NAMESPACE = "kth_auth";
	public static final String EMAIL_SUBJECT = "METAcraft Verification";
	public static final String GDPR_LINK = "https://docs.google.com/document/d/1Dx5m2MxCQm5Pl5V8-qRZPXMsRn2SJyKm9EmBvPuE7T0/edit?usp=sharing";

	@Override
	public void onInitialize() {
		KTHAuthConfig config = KTHAuthConfig.getConfig();
		MailSender mailSender = new MailSender(config);
		WebhookSender webhookSender = new WebhookSender(config);
		Path dataDirectoryPath = Path.of("kth-auth");
		Verifier verifier = new Verifier(dataDirectoryPath, webhookSender);

		Registry.register(CustomMessageRegistry.REGISTRY, KTHAuthTask.DISCONNECT,
			(_, _, player) -> player.getPlayerID().flatMap(verifier::getInProgressVerification).ifPresent(
				task -> task.disconnect(Component.translatable("multiplayer.disconnect.generic"))
			)
		);

		Registry.register(CustomMessageRegistry.REGISTRY, KTHAuthTask.SUBMIT_EMAIL,
			new CodecMessageHandler<>((data, server, player) ->
				player.getPlayerID().flatMap(verifier::getInProgressVerification).ifPresent(
					task -> task.onEmailSubmitted(server, data.email())
				), KTHAuthTask.EmailData.CODEC
			)
		);

		Registry.register(CustomMessageRegistry.REGISTRY, KTHAuthTask.SUBMIT_CODE,
			new CodecMessageHandler<>((data, _, player) ->
				player.getPlayerID().flatMap(verifier::getInProgressVerification).ifPresent(
					task -> task.onCodeSubmitted(data.code())
				), KTHAuthTask.VerificationCodeData.CODEC
			)
		);

		Registry.register(CustomMessageRegistry.REGISTRY, KTHAuthTask.ACCEPT_GDPR,
			(_, _, player) ->
				player.getPlayerID().flatMap(verifier::getInProgressVerification)
					.ifPresent(KTHAuthTask::onGdprAccepted)
		);

		Registry.register(CustomMessageRegistry.REGISTRY, KTHAuthTask.NO_KTH_EMAIL,
			(_, _, player) ->
				player.getPlayerID().flatMap(verifier::getInProgressVerification)
					.ifPresent(KTHAuthTask::showNoKthEmailDialog)
		);

		ServerConfigurationConnectionEvents.DISCONNECT.register((listener, _) ->
			verifier.getInProgressVerification(listener.getOwner().id()).ifPresent(verifier::removeInProgressVerification)
		);

		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((listener, _) -> {
			if (verifier.isDisabled()) {
				return;
			}
			UUID uuid = listener.getOwner().id();
			Optional<VerificationDetails> details = verifier.getVerificationDetails(uuid);
			if (details.isPresent() && details.get().hasAcceptedGdpr()) {
				verifier.touch(details.get());
				return;
			}
			KTHAuthTask task = new KTHAuthTask(verifier, mailSender, listener);
			verifier.addInProgressVerification(uuid, task);
			listener.addTask(task);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
			PlusOneCommand plusOneCommand = new PlusOneCommand(verifier);
			plusOneCommand.register(dispatcher);
			KTHAuthCommand kthAuthCommand = new KTHAuthCommand(verifier);
			kthAuthCommand.register(dispatcher);
		});

		ServerLifecycleEvents.BEFORE_SAVE.register((_, _, _) -> {
			if (verifier.isDirty()) {
				try {
					verifier.save();
				} catch (IOException e) {
					LOGGER.error("Failed to save verification details", e);
				}
			}
		});
	}
}
