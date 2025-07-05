package nu.metacraft.portal_blocker.portal_type;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.block.Portal;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import nu.metacraft.portal_blocker.Commands;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalState;

import java.util.Optional;
import java.util.function.Predicate;

public class PortalType {

	public static final SimpleCommandExceptionType INVALID_PORTAL = new SimpleCommandExceptionType(
			Text.literal("Invalid portal type")
	);

	private final Predicate<Portal> affectsPortal;
	private final Text creationMessage;
	private final Text travelMessage;

	public PortalType(Portal blockedPortal, Text creationMessage, Text travelMessage) {
		this(portal -> portal == blockedPortal, creationMessage, travelMessage);
	}

	public PortalType(Predicate<Portal> affectsPortal, Text creationMessage, Text travelMessage) {
		this.affectsPortal = affectsPortal;
		this.creationMessage = creationMessage;
		this.travelMessage = travelMessage;
	}

	public void onGlobalStateChange(MinecraftServer server, boolean newState, PortalState.BlockingType type) {}

	public final Identifier getID() {
		var id = PortalTypeRegistry.REGISTRY.getId(this);
		if (id != null) {
			return id;
		} else {
			return PortalBlocker.getID("missingno");
		}
	}

	public static RequiredArgumentBuilder<ServerCommandSource, Identifier> argument(String name) {
		return CommandManager.argument(name, IdentifierArgumentType.identifier()).suggests((context, builder) -> {
			PortalTypeRegistry.REGISTRY.forEach(value -> {
				builder.suggest(Commands.getIDAsString(value.getID()));
			});
			return builder.buildFuture();
		});
	}

	public static PortalType getArgument(CommandContext<ServerCommandSource> context, String name) throws CommandSyntaxException {
		var type = PortalTypeRegistry.REGISTRY.get(IdentifierArgumentType.getIdentifier(context, name));
		if (type == null) {
			throw INVALID_PORTAL.create();
		}
		return type;
	}

	public boolean affectsPortal(Portal portal) {
		return affectsPortal.test(portal);
	}

	public Optional<Text> getTravelMessage() {
		return Optional.ofNullable(travelMessage);
	}

	public Optional<Text> getCreationMessage() {
		return Optional.ofNullable(creationMessage);
	}

	@Override
	public String toString() {
		return getID().toString();
	}
}
