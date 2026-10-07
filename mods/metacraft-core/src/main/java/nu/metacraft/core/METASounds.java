package nu.metacraft.core;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;

public class METASounds {

	private static final Map<Identifier, SoundEvent> REPLACEMENT_TABLE = new HashMap<>();

	public static final SoundEvent WOOD_BREAK = redirectVanilla(SoundEvents.WOOD_BREAK);
	public static final SoundEvent WOOD_FALL = redirectVanilla(SoundEvents.WOOD_FALL);
	public static final SoundEvent WOOD_HIT = redirectVanilla(SoundEvents.WOOD_HIT);
	public static final SoundEvent WOOD_PLACE = redirectVanilla(SoundEvents.WOOD_PLACE);
	public static final SoundEvent WOOD_STEP = redirectVanilla(SoundEvents.WOOD_STEP);

	public static final SoundEvent STONE_BREAK = redirectVanilla(SoundEvents.STONE_BREAK);
	public static final SoundEvent STONE_FALL = redirectVanilla(SoundEvents.STONE_FALL);
	public static final SoundEvent STONE_HIT = redirectVanilla(SoundEvents.STONE_HIT);
	public static final SoundEvent STONE_PLACE = redirectVanilla(SoundEvents.STONE_PLACE);
	public static final SoundEvent STONE_STEP = redirectVanilla(SoundEvents.STONE_STEP);

	private static SoundEvent redirectVanilla(SoundEvent event) {
		var copy = new SoundEvent(METAcraftCore.getID(event.location().getPath()), event.fixedRange());
		REPLACEMENT_TABLE.put(event.location(), copy);
		return copy;
	}

	public static Holder<SoundEvent> replaceVanilla(Holder<SoundEvent> event) {
		var entry = REPLACEMENT_TABLE.get(event.value().location());
		if (entry != null) return Holder.direct(entry);
		return event;
	}

}
