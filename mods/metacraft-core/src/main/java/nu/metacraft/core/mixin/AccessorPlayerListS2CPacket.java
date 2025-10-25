package nu.metacraft.core.mixin;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PlayerListS2CPacket.class)
public interface AccessorPlayerListS2CPacket {

	@Accessor
	@Mutable
	void setEntries(List<PlayerListS2CPacket.Entry> entries);

}
