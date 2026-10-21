package se.metacraft.kth_auth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import se.metacraft.kth_auth.verify.PendingVerification;
import se.metacraft.kth_auth.verify.VerificationDetails;
import se.metacraft.kth_auth.verify.Verifier;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class KTHAuthTask implements ConfigurationTask {
	public static final Type TYPE = new Type("kth_auth:verify");
	public static final Identifier DISCONNECT = Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "verify/disconnect");
	public static final Identifier SUBMIT_EMAIL = Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "verify/submit_email");
	public static final Identifier SUBMIT_CODE = Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "verify/submit_code");
	public static final Identifier ACCEPT_GDPR = Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "verify/accept_gdpr");
	public static final Identifier NO_KTH_EMAIL = Identifier.fromNamespaceAndPath(KTHAuth.NAMESPACE, "verify/no_kth_email");
	public static final Pattern KTH_EMAIL_REGEX = Pattern.compile("^[a-zA-Z0-9]+@kth[.]se$");
	public static final int DISCONNECT_PING_ID = 1837641381; // Magic value that disconnects clients on pong.

	public record EmailData(String email) {
		public static final Codec<EmailData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("email").forGetter(EmailData::email)
		).apply(instance, EmailData::new));
	}

	public record VerificationCodeData(String code) {
		public static final Codec<VerificationCodeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("code").forGetter(VerificationCodeData::code)
		).apply(instance, VerificationCodeData::new));
	}

	private final Verifier verifier;
	private final MailSender mailSender;
	private final ServerConfigurationPacketListenerImpl listener;
	private PendingVerification code;

	public KTHAuthTask(Verifier verifier, MailSender mailSender, ServerConfigurationPacketListenerImpl listener) {
		this.verifier = verifier;
		this.mailSender = mailSender;
		this.listener = listener;
	}

	@Override
	public @NotNull Type type() {
		return TYPE;
	}

	@Override
	public void start(@NotNull Consumer<Packet<?>> connection) {
		if (this.verifier.isDisabled()) {
			this.complete();
			return;
		}
		Optional<VerificationDetails> details = this.verifier.getVerificationDetails(this.getUUID());
		if (details.isPresent() && !details.get().hasAcceptedGdpr()) {
			this.sendAcceptGdprDialog();
		} else {
			this.sendVerificationDialog();
		}
	}

	public UUID getUUID() {
		return this.listener.getOwner().id();
	}

	public String getPlayerName() {
		return this.listener.getOwner().name();
	}

	public void complete() {
		this.listener.completeTask(TYPE);
		this.verifier.removeInProgressVerification(this);
	}

	private void sendVerificationDialog() {
		CommonDialogData dialogData = new CommonDialogData(
			Component.literal("Welcome to METAcraft! - Verification"),
			Optional.empty(),
			false,
			false,
			DialogAction.CLOSE,
			List.of(new PlainMessage(Component.literal("Enter your KTH email."), 200)),
			List.of(new Input("email", new TextInput(
				200,
				Component.literal("KTH Email"),
				true,
				"kth-id@kth.se",
				20,
				Optional.empty()
			)))
		);
		ActionButton nextAction = new ActionButton(
			new CommonButtonData(Component.literal("Next"), 200),
			Optional.of(new CustomAll(SUBMIT_EMAIL, Optional.empty()))
		);
		ActionButton noKthEmailAction = new ActionButton(
			new CommonButtonData(Component.literal("I do not have a KTH Email"), 200),
			Optional.of(new CustomAll(NO_KTH_EMAIL, Optional.empty()))
		);
		ActionButton disconnectAction = new ActionButton(
			new CommonButtonData(Component.translatable("menu.disconnect"), 150),
			Optional.of(new CustomAll(DISCONNECT, Optional.empty()))
		);
		this.listener.send(new ClientboundShowDialogPacket(Holder.direct(
			new MultiActionDialog(dialogData, List.of(nextAction, noKthEmailAction), Optional.of(disconnectAction), 1)
		)));
	}

	public void sendAcceptGdprDialog() {
		CommonDialogData dialogData = new CommonDialogData(
			Component.literal("Accept GDPR Statement"),
			Optional.empty(),
			false,
			false,
			DialogAction.NONE,
			List.of(new PlainMessage(Component.literal("By clicking Accept, you agree to METAcraft's ")
				.append(link("GDPR Statement", KTHAuth.GDPR_LINK))
				.append("."), 200)),
			List.of()
		);
		ActionButton acceptAction = new ActionButton(
			new CommonButtonData(Component.literal("Accept"), 200),
			Optional.of(new CustomAll(ACCEPT_GDPR, Optional.empty()))
		);
		ActionButton disconnectAction = new ActionButton(
			new CommonButtonData(Component.translatable("menu.disconnect"), 150),
			Optional.of(new CustomAll(DISCONNECT, Optional.empty()))
		);
		this.listener.send(new ClientboundShowDialogPacket(Holder.direct(
			new MultiActionDialog(dialogData, List.of(acceptAction), Optional.of(disconnectAction), 1)
		)));
	}

	public void onEmailSubmitted(MinecraftServer server, String email) {
		email = email.toLowerCase(Locale.ROOT);
		if (!isKthEmail(email)) {
			this.disconnect(Component.literal(
				"Please enter a valid KTH email address."
			));
			return;
		}

		if (this.code != null) {
			this.disconnect(Component.literal(
				"You have already submitted an email."
			));
			return;
		}

		if (this.verifier.getVerificationDetails(this.getUUID()).isPresent()) {
			this.disconnect(Component.literal(
				"You are already verified!"
			));
			return;
		}

		if (this.verifier.getByEmail(email).size() >= Verifier.MAX_ACCOUNTS_PER_EMAIL) {
			this.disconnect(Component.literal(
				"This email has been used too many times. Please contact the moderation team on Discord."
			));
			return;
		}

		this.code = this.verifier.createCode(this.getUUID(), this.getPlayerName(), email);
		this.mailSender.sendMail(email, KTHAuth.EMAIL_SUBJECT, "<p>Verification code: " + code.verificationCode() + "</p>").exceptionallyAsync(ex -> {
			KTHAuth.LOGGER.error("Failed to send verification email", ex);
			this.disconnect(Component.literal("Failed to send email. Please try again or let us know on Discord."));
			return null;
		}, server); // Will run on server thread, so not async despite the method name.

		CommonDialogData dialogData = new CommonDialogData(
			Component.literal("Enter verification code"),
			Optional.empty(),
			false,
			false,
			// Clicking links in the body also runs the after action, so CLOSE would dismiss the dialog
			DialogAction.NONE,
			List.of(
				new PlainMessage(Component.literal("Check the inbox of " + email + " for your verification code: ")
					.append(link("https://webmail.kth.se/", "https://webmail.kth.se/")), 200),
				new PlainMessage(Component.literal("By clicking Submit, you agree to METAcraft's ")
					.append(link("GDPR Statement", KTHAuth.GDPR_LINK))
					.append("."), 200)
			),
			List.of(new Input("code", new TextInput(
				200,
				Component.literal("Verification Code"),
				true,
				"",
				20,
				Optional.empty()
			)))
		);
		ActionButton nextAction = new ActionButton(
			new CommonButtonData(Component.literal("Submit"), 200),
			Optional.of(new CustomAll(SUBMIT_CODE, Optional.empty()))
		);
		ActionButton disconnectAction = new ActionButton(
			new CommonButtonData(Component.translatable("menu.disconnect"), 150),
			Optional.of(new CustomAll(DISCONNECT, Optional.empty()))
		);

		listener.send(new ClientboundShowDialogPacket(Holder.direct(
			new MultiActionDialog(dialogData, List.of(nextAction), Optional.of(disconnectAction), 1)
		)));
	}

	public void disconnect(Component reason) {
		// Clear dialog first to ensure the client can see the disconnected screen.
		this.listener.send(ClientboundClearDialogPacket.INSTANCE);
		// Send a ping and wait for pong to ensure the dialog is cleared before disconnecting.
		((PendingDisconnect) this.listener).kth_auth$disconnectOnPong(reason);
		this.listener.send(new ClientboundPingPacket(DISCONNECT_PING_ID));
		this.verifier.removeInProgressVerification(this);
	}

	private static Component link(String text, String url) {
		return Component.literal(text).withStyle(style -> style
			.withColor(ChatFormatting.BLUE)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
	}

	public static boolean isKthEmail(String email) {
		return KTH_EMAIL_REGEX.matcher(email).find();
	}

	public void onCodeSubmitted(String code) {
		if (this.code == null || !this.code.verificationCode().equals(code) || this.code.hasExpired()) {
			this.disconnect(Component.literal("Invalid verification code. Please try again."));
			return;
		}

		this.verifier.verifyByEmail(this.code);
		this.complete();
	}

	public void onGdprAccepted() {
		this.listener.send(ClientboundClearDialogPacket.INSTANCE);
		this.verifier.getVerificationDetails(this.listener.getOwner().id()).ifPresentOrElse(
			details -> {
				this.verifier.acceptGdpr(details);
				this.complete();
			},
			() -> this.disconnect(Component.literal("No verification details found. Please try again."))
		);
	}

	public void showNoKthEmailDialog() {
		CommonDialogData dialogData = new CommonDialogData(
			Component.literal("No KTH Email"),
			Optional.empty(),
			false,
			false,
			DialogAction.CLOSE,
			List.of(
				new PlainMessage(Component.literal("Please ask your friend who does have a KTH email to run the command /+1 " + this.getPlayerName()), 200)
			),
			List.of()
		);

		ActionButton disconnect = new ActionButton(
			new CommonButtonData(Component.translatable("menu.disconnect"), 150),
			Optional.of(new CustomAll(DISCONNECT, Optional.empty()))
		);

		this.listener.send(new ClientboundShowDialogPacket(Holder.direct(
			new NoticeDialog(dialogData, disconnect)
		)));
	}
}