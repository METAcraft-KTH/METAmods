package metacraft.pirkko.mixin;

import java.util.Optional;
import metacraft.pirkko.PirkkoCommands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Pirkko shader can't tell floor from wall heads, so wall heads are sent the {@code _wall} skin.
 * The server-side block entity keeps the floor skin; only the tags sent to clients are rewritten.
 */
@Mixin(SkullBlockEntity.class)
abstract class SkullBlockEntityMixin {
	private static final String PREFIX = "pirkko:entity/";

	/** Chunk-send path (and anything else using the update tag). */
	@Inject(method = "getUpdateTag", at = @At("RETURN"))
	private void pirkko$wallSkin(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
		pirkko$rewrite(cir.getReturnValue());
	}

	/** Single-block update path; the default packet builder bypasses getUpdateTag. */
	@Inject(method = "getUpdatePacket()Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;", at = @At("HEAD"), cancellable = true)
	private void pirkko$wallSkinPacket(CallbackInfoReturnable<ClientboundBlockEntityDataPacket> cir) {
		cir.setReturnValue(ClientboundBlockEntityDataPacket.create((SkullBlockEntity) (Object) this, (be, registries) -> {
			var tag = be.saveWithoutMetadata(registries);
			pirkko$rewrite(tag);
			return tag;
		}));
	}

	private void pirkko$rewrite(CompoundTag tag) {
		var self = (SkullBlockEntity) (Object) this;
		if (!(self.getBlockState().getBlock() instanceof WallSkullBlock)) return;
		Optional<CompoundTag> profile = tag.getCompound("profile");
		if (profile.isEmpty()) return;
		var texture = profile.get().getString("texture").orElse("");
		if (texture.startsWith(PREFIX) && PirkkoCommands.VARIANTS.contains(texture.substring(PREFIX.length()))) {
			profile.get().putString("texture", texture + "_wall");
		}
	}
}
