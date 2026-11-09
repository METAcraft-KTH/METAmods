package se.metacraft.skins;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import se.metacraft.skins.command.SkinsCommands;
import se.metacraft.skins.item.MobHat;
import se.metacraft.skins.item.SkinBox;
import se.metacraft.skins.item.SkinToken;
import se.metacraft.skins.pack.PackDelivery;
import se.metacraft.skins.ui.RightClick;
import se.metacraft.skins.ui.RollMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * metacraft-skins: claimable item skins for vanilla clients. This class only wires adapters to
 * the server's {@link Skins}, which is loaded from the server root when the server starts. A player
 * leaving ({@code ServerPlayerEvents.LEAVE}, before they're saved) settles any Roll still spinning.
 */
public final class MetacraftSkins implements ModInitializer {
	public static final String MOD_ID = "metacraft-skins";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile @Nullable Skins skins;

	public static Identifier getID(String id) {
		return Identifier.fromNamespaceAndPath(MOD_ID, id);
	}

	@Override
	public void onInitialize() {
		SkinMarker.init();
		SkinToken.init();
		SkinBox.init();
		MobHat.init();
		DyeSkinnedItemRecipe.init();
		// Every stack carrying the (Polymer-hidden) marker, vanilla items included, is rebuilt for
		// each viewer by Polymer; the live skin's Look and tooltip block go on that copy only. It runs
		// after every other mod's callback in the default phase (moredyes sets an animated colour from
		// its own), so a colour the drawing check takes off a copy stays off.
		Identifier drawing = Identifier.fromNamespaceAndPath(MOD_ID, "drawing_check");
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, drawing);
		PolymerItemUtils.ITEM_MODIFICATION_EVENT.register(drawing, (original, client, context) -> {
			Skins current = skins;
			return current == null ? client : DrawingCheck.draw(current, original, client, context);
		});
		// A token or a box opens its menu from its own use and useOn; these keep the offhand out of that click.
		UseItemCallback.EVENT.register((player, level, hand) -> RightClick.guardOffhand(player, hand));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> RightClick.guardOffhand(player, hand));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> RightClick.guardOffhand(player, hand));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Skins current = skins;
			if (current != null) current.seen(handler.player.nameAndId());
		});
		// Held and worn Perks follow what each player holds and wears, tick by tick, and a worn Dyeable Hat's colour is saved as its tint.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Skins current = skins;
			if (current == null) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				current.updateLastingPerks(player);
				current.rememberWornTint(player);
			}
		});
		// Event Perks: a melee hit (the killing blow too) and kill with the main-hand item, a block
		// broken with it, and any damage to a wearer. Skins decides what fires, and for whom.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			Skins current = skins;
			if (current == null || damage <= 0) return;
			ServerPlayer holder = meleeAttacker(source);
			if (holder != null && holder != entity) current.hit(holder, entity);
			if (entity instanceof ServerPlayer wearer) current.hurt(wearer, source.getEntity());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Skins current = skins;
			ServerPlayer holder = meleeAttacker(source);
			if (current == null || holder == null || holder == entity) return;
			current.hit(holder, entity);   // the killing blow is a hit too (Fabric's AFTER_DAMAGE skips it)
			current.killed(holder, entity);
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			Skins current = skins;
			// Fired before the tool takes its wear, so a tool that breaks on this block still counts.
			if (current != null && player instanceof ServerPlayer breaker) current.brokeBlock(breaker, pos);
		});
		// Before PlayerList.remove saves a leaving player (disconnect, server stop): a Roll still spinning gives its token now.
		ServerPlayerEvents.LEAVE.register(RollMenu::left);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Skins current = skins;
			if (current != null) current.left(handler.player);
		});
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Skins loaded = Skins.at(server.getServerDirectory(), server.getWorldPath(LevelResource.ROOT));
			DefaultSkins.installIfMissing(loaded.skinsFolder());
			DefaultSkins.installBoxesIfMissing(loaded.boxesFolder());
			try {
				loaded.reload();   // logs every rejection; a broken skin never stops the server
			} catch (RuntimeException e) {
				// Nor does anything else here: no skins until /itemskins reload succeeds.
				LOGGER.error("[{}] could not load the skins; fix the cause and run /itemskins reload", MOD_ID, e);
			}
			skins = loaded;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> skins = null);
		PackDelivery.init(MetacraftSkins::skins);
		SkinsCommands.init(MetacraftSkins::skins);
	}

	/**
	 * The player who dealt this damage in melee with their main-hand item, if anyone did: struck
	 * by the player themselves (no projectile, no thorns) as a vanilla swing, a mace smash, or the
	 * item's own damage type (a spear's jab and charge).
	 */
	private static @Nullable ServerPlayer meleeAttacker(DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) return null;
		if (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MACE_SMASH)) return player;
		Holder<DamageType> own = player.getMainHandItem().get(DataComponents.DAMAGE_TYPE);
		return own != null && own.unwrapKey().map(source::is).orElse(false) ? player : null;
	}

	/** The running server's skins; null while no server runs. */
	public static @Nullable Skins skins() {
		return skins;
	}

	/** Swaps the server's skins (a game test pointing it at a temporary root); returns the ones before. */
	public static @Nullable Skins use(@Nullable Skins replacement) {
		Skins previous = skins;
		skins = replacement;
		return previous;
	}
}
