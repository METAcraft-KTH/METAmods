package nu.metacraft.core.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.soundpatcher.api.SoundPatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Makes a Polymer block sound like itself. The client plays step, mining, fall, break and place
 * sounds from the vanilla block it is shown (a note block for a textured full block), and no packet
 * can change that. Where those differ from the block's own, polymer-sound-patcher silences the
 * client's sound and the server sends ours instead: on every break and mining hit (the patcher
 * watches {@code levelEvent}), step, fall and placement.
 * <p>
 * Our own sound type is converted too. On a break polymer 0.18.4 sends ours only when the client's
 * block state sounds converted, not the break event's state, so a block that only breaks as
 * something else needs ours; and the placer's client predicts ours from the client item, which
 * would otherwise play twice. Blast radius: every vanilla block sharing a converted sound type gets that sound from
 * the server as well (the same sound, a tick later).
 * <p>
 * Call it once per block, while registering (the patcher's pack entries are made then).
 */
public final class PolymerBlockSounds {
	private PolymerBlockSounds() {}

	public static void patch(Block block) {
		if (!(block instanceof PolymerBlock polymer)) return;
		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			SoundType ours = state.getSoundType();
			boolean differs = false;
			for (BlockState shown : List.of(clientState(state), breakEventClientState(polymer, state))) {
				if (!sameSounds(shown.getSoundType(), ours)) {
					convert(shown.getSoundType());
					differs = true;
				}
			}
			if (differs) convert(ours);
		}
	}

	/** The vanilla state the client sees for {@code state}. */
	public static BlockState clientState(BlockState state) {
		return PolymerBlockUtils.getPolymerBlockState(state, null);
	}

	/** The vanilla state whose break sound and particles the client shows when {@code state} breaks. */
	public static BlockState breakEventClientState(PolymerBlock polymer, BlockState state) {
		BlockState sent = PolymerBlockUtils.getBlockBreakBlockStateSafely(polymer, state, PolymerBlockUtils.NESTED_DEFAULT_DISTANCE, null);
		return PolymerBlockUtils.getPolymerBlockState(sent, null);
	}

	private static boolean sameSounds(SoundType a, SoundType b) {
		return a == b || events(a).equals(events(b));
	}

	private static List<Identifier> events(SoundType type) {
		return List.of(type.getStepSound().location(), type.getHitSound().location(), type.getFallSound().location(),
				type.getBreakSound().location(), type.getPlaceSound().location());
	}

	private static void convert(SoundType type) {
		for (SoundEvent event : List.of(type.getStepSound(), type.getHitSound(), type.getFallSound(), type.getBreakSound(), type.getPlaceSound())) {
			// the patcher only moves vanilla sounds; a modded one is never predicted by the client anyway
			if (event.location().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
				SoundPatcher.convertIntoServerSound(event);
			}
		}
	}
}
