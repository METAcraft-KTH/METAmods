package nu.metacraft.revival;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.tags.DialogTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import nu.metacraft.lib.custom_message.CodecMessageHandler;
import nu.metacraft.lib.custom_message.CustomMessageHandler;
import nu.metacraft.lib.custom_message.CustomMessageRegistry;
import nu.metacraft.lib.util.PotentialPlayer;
import nu.metacraft.lib.util.helper.DialogHelper;
import nu.metacraft.revival.extension.ServerPlayerExtension;
import nu.metacraft.revival.util.helper.RevivalHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class RevivalDialogs {

	public static final ResourceKey<Dialog> REOPEN_REVIVAL = create("reopen_revival");

	private static ResourceKey<Dialog> create(String id) {
		return ResourceKey.create(Registries.DIALOG, METAcraftRevival.getID(id));
	}

	protected static final Component REVIVAL_EXTERNAL_TITLE = Component.translatableWithFallback(
			"dialog.metacraft.revival.revival", "Revival"
	);

	public enum Option implements StringRepresentable {
		DIE(
				"die",
				(server, player) -> player.getPlayer(server).ifPresent(
						RevivalHelper::playerAcceptedFate
				)
		),
		OPEN(
				"open",
				(server, player) -> player.getPlayer(server).ifPresent(p -> {
					if (((ServerPlayerExtension) p).metacraft$isUnconscious()) {
						RevivalHelper.openRevivalMenu(p);
					} else {
						p.closeContainer();
						p.sendOverlayMessage(
								Component.translatableWithFallback(
										"dialog.metacraft.revival.not_dead","You're not dead!"
								)
						);
					}
				})
		),
		CLOSE(
				"close",
				(server, player) -> player.getPlayer(server).ifPresent(p -> {
					((ServerPlayerExtension) p).metacraft$setRevivalMenuOpen(false);
					var quickActions = !DialogHelper.hasManyQuickActions(server.registryAccess()) ? Component.translatable(
							"dialog.metacraft.revival.reopen.info.quickactions", Component.keybind("key.quickActions")
					) : Component.translatable(
							"dialog.metacraft.revival.reopen.info.quickactions_multiple",
							Component.keybind("key.quickActions"), REVIVAL_EXTERNAL_TITLE
					);
					p.sendSystemMessage(
							Component.translatableWithFallback(
									"dialog.metacraft.revival.reopen.info",
									"Click this text to reopen the GUI",
									quickActions
							).withStyle(
									style -> style.withClickEvent(
											CodecMessageHandler.createSimpleClickEvent(
													REVIVAL_MENU, Option.OPEN,
													p.registryAccess()::createSerializationContext
											)
									)
							), false
					);
				})
		);

		public static final Codec<Option> CODEC = StringRepresentable.fromEnum(Option::values);

		final String name;
		final BiConsumer<MinecraftServer, PotentialPlayer> action;

		Option(String name, BiConsumer<MinecraftServer, PotentialPlayer> action) {
			this.name = name;
			this.action = action;
		}

		@Override
		public @NotNull String getSerializedName() {
			return name;
		}

		public void accept(MinecraftServer server, PotentialPlayer player) {
			action.accept(server, player);
		}
	}

	public static final Holder.Reference<CodecMessageHandler<Option>> REVIVAL_MENU = registerMessageHandler(
			"revival_menu", new CodecMessageHandler<>(Option::accept, Option.CODEC)
	);


	public static Holder<Dialog> createRevivalDialog(Player player) {
		var ext = (ServerPlayerExtension) player;
		var deathMessage = ext.metacraft$getDeathMessage();
		var revivalStatus = ext.metacraft$getRevivalStatus();

		var status = new PlainMessage(revivalStatus != null ? revivalStatus : CommonComponents.EMPTY, 200);
		var spacer = new PlainMessage(CommonComponents.EMPTY , 1);

		List<DialogBody> deadList = List.of(
				new PlainMessage(Component.translatable("deathScreen.title") , 200),
				new PlainMessage(deathMessage != null ? deathMessage : CommonComponents.EMPTY, 200),
				new PlainMessage(
						Component.translatable(
								"deathScreen.score.value",
								Component.literal(
										Integer.toString(player.getScore())
								).withStyle(ChatFormatting.YELLOW)
						), 200
				),
				status,
				spacer,
				spacer
		);

		List<DialogBody> revivingList = List.of(
				new PlainMessage(
						Component.translatableWithFallback(
								"dialog.metacraft.revival.being_revived",
								"You're being revived!"
						) , 200
				),
				status,
				spacer,
				spacer
		);

		return Holder.direct(
				new MultiActionDialog(
						new CommonDialogData(
								CommonComponents.EMPTY,
								Optional.empty(), false,
								false, DialogAction.CLOSE, ext.metacraft$getReviver() == null ? deadList : revivingList, List.of()
						),
						List.of(
								new ActionButton(
										new CommonButtonData(
												Component.translatableWithFallback(
														"dialog.metacraft.revival.die",
														"Accept Your Fate"
												),
												Optional.empty(),
												200
										),
										Optional.of(
												new StaticAction(
														CodecMessageHandler.createSimpleClickEvent(
																REVIVAL_MENU, Option.DIE,
																player.registryAccess()::createSerializationContext
														)
												)
										)
								),
								new ActionButton(
										new CommonButtonData(
												Component.translatableWithFallback(
														"dialog.metacraft.revival.close",
														"Access Chat"
												),
												Optional.empty(),
												200
										),
										Optional.of(
												new StaticAction(
														CodecMessageHandler.createSimpleClickEvent(
																REVIVAL_MENU, Option.CLOSE,
																player.registryAccess()::createSerializationContext
														)
												)
										)
								)
						),
						Optional.empty(),
						1
				)
		);
	}

	public static void init() {

	}

	private static <T extends CustomMessageHandler> Holder.Reference<T> registerMessageHandler(String id, T handler) {
		return Registry.registerForHolder(CustomMessageRegistry.REGISTRY, METAcraftRevival.getID(id), handler);
	}

}
