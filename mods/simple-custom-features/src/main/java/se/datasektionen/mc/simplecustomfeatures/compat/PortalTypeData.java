package se.datasektionen.mc.simplecustomfeatures.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.Optional;

public record PortalTypeData(
		Optional<Text> blockedCreationMessage, Optional<Text> blockedTravelMessage
) {
	public static final MapCodec<PortalTypeData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TextCodecs.CODEC.optionalFieldOf("blocked_creation_message").forGetter(PortalTypeData::blockedCreationMessage),
					TextCodecs.CODEC.optionalFieldOf("blocked_travel_message").forGetter(PortalTypeData::blockedTravelMessage)
			).apply(instance, PortalTypeData::new)
	);
}
