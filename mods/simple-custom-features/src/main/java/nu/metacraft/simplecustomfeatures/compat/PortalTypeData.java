package nu.metacraft.simplecustomfeatures.compat;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public record PortalTypeData(
		Optional<Component> blockedCreationMessage, Optional<Component> blockedTravelMessage
) {
	public static final MapCodec<PortalTypeData> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ComponentSerialization.CODEC.optionalFieldOf("blocked_creation_message").forGetter(PortalTypeData::blockedCreationMessage),
					ComponentSerialization.CODEC.optionalFieldOf("blocked_travel_message").forGetter(PortalTypeData::blockedTravelMessage)
			).apply(instance, PortalTypeData::new)
	);
}
