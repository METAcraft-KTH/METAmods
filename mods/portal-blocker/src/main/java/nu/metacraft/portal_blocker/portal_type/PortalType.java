package nu.metacraft.portal_blocker.portal_type;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Portal;
import nu.metacraft.portal_blocker.Commands;
import nu.metacraft.portal_blocker.PortalBlocker;
import nu.metacraft.portal_blocker.PortalState;

import java.util.Optional;
import java.util.function.Predicate;

public class PortalType {

	public static final SimpleCommandExceptionType INVALID_PORTAL = new SimpleCommandExceptionType(
			Component.literal("Invalid portal type")
	);

	private final Predicate<Portal> affectsPortal;
	private final Component creationMessage;
	private final Component travelMessage;

	public PortalType(Portal blockedPortal, Component creationMessage, Component travelMessage) {
		this(portal -> portal == blockedPortal, creationMessage, travelMessage);
	}

	public PortalType(Predicate<Portal> affectsPortal, Component creationMessage, Component travelMessage) {
		this.affectsPortal = affectsPortal;
		this.creationMessage = creationMessage;
		this.travelMessage = travelMessage;
	}

	public void onGlobalStateChange(MinecraftServer server, boolean newState, PortalState.BlockingType type) {}

	public final Identifier getID() {
		var id = PortalTypeRegistry.REGISTRY.getKey(this);
		if (id != null) {
			return id;
		} else {
			return PortalBlocker.getID("missingno");
		}
	}

	public static RequiredArgumentBuilder<CommandSourceStack, Identifier> argument(String name) {
		return net.minecraft.commands.Commands.argument(name, IdentifierArgument.id()).suggests((context, builder) -> {
			PortalTypeRegistry.REGISTRY.forEach(value -> {
				builder.suggest(Commands.getIDAsString(value.getID()));
			});
			return builder.buildFuture();
		});
	}

	public static PortalType getArgument(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
		var type = PortalTypeRegistry.REGISTRY.getValue(IdentifierArgument.getId(context, name));
		if (type == null) {
			throw INVALID_PORTAL.create();
		}
		return type;
	}

	public boolean affectsPortal(Portal portal) {
		return affectsPortal.test(portal);
	}

	public Optional<Component> getTravelMessage() {
		return Optional.ofNullable(travelMessage);
	}

	public Optional<Component> getCreationMessage() {
		return Optional.ofNullable(creationMessage);
	}

	@Override
	public String toString() {
		return getID().toString();
	}
}
