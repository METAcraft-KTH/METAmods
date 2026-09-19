package nu.metacraft.rivals;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import nu.metacraft.rivals.gun.InkHud;
import nu.metacraft.rivals.gun.InkOnScreen;
import nu.metacraft.rivals.gun.PaintBall;
import nu.metacraft.rivals.gun.PaintWeapon;
import nu.metacraft.rivals.gun.Recoil;
import nu.metacraft.rivals.gun.Roll;
import nu.metacraft.rivals.gun.SpecialTuning;
import nu.metacraft.rivals.gun.WeaponSelector;
import nu.metacraft.rivals.gun.WeaponTuning;
import nu.metacraft.rivals.paint.PaintBlocks;
import nu.metacraft.rivals.paint.Unpaintable;
import nu.metacraft.rivals.pack.RivalsPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rivals Paint: a Splatoon-style paint prototype for vanilla clients, via Polymer.
 *
 * Order matters: blocks and the entity first (the gun refers to both), then the weapons and the
 * tuning that overrides their numbers, then the pack (which must be required because a client without
 * it sees sculk veins instead of paint), then commands and score.
 */
public class Rivals implements ModInitializer {
	public static final String MOD_ID = "rivals-paint";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PaintBlocks.register();
		TeamNames.init();
		Unpaintable.init();
		PaintBall.register();
		PaintWeapon.register();
		WeaponSelector.register();
		PaintWeapon.init();
		Roll.init();
		WeaponTuning.load();
		SpecialTuning.load();
		RivalsPack.init();
		RivalsCommands.register();
		ScoreBars.init();
		Arena.init();
		Match.init();
		Lobby.init();
		Recoil.init();
		InkHud.init();
		InkOnScreen.init();
		PlayerTick.init();
		LOGGER.info("[{}] ready", MOD_ID);
	}
}
