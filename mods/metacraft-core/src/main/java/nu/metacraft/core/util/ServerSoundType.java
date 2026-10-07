package nu.metacraft.core.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

public class ServerSoundType extends SoundType {

	public ServerSoundType(float volume, float pitch, SoundEvent breakSound, SoundEvent stepSound, SoundEvent placeSound, SoundEvent hitSound, SoundEvent fallSound) {
		super(volume, pitch, breakSound, stepSound, placeSound, hitSound, fallSound);
	}

	public static ServerSoundType copyOf(SoundType type) {
		return new ServerSoundType(type.volume, type.pitch, type.getBreakSound(), type.getStepSound(), type.getPlaceSound(), type.getHitSound(), type.getFallSound());
	}

}
