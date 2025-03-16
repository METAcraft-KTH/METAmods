package se.datasektionen.mc.metacraft_core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.datasektionen.mc.metacraft_core.METAcraftCore;
import se.datasektionen.mc.metacraft_core.extensions.CommandBossBarExtension;
import se.datasektionen.mc.metacraft_core.music.BossBarMusicHandler;
import se.datasektionen.mc.metacraft_core.music.MusicEntry;

@Mixin(CommandBossBar.class)
public class MixinCommandBossBar implements CommandBossBarExtension {

	@Unique
	private static final String MUSIC = "metacraft:music";

	@Unique
	private final BossBarMusicHandler handler = new BossBarMusicHandler((CommandBossBar) (Object) this);

	@Inject(method = "addPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("RETURN"))
	public void addPlayer(ServerPlayerEntity player, CallbackInfo ci) {
		handler.onPlayerAdded(player);
	}

	@Inject(method = "removePlayer", at = @At("RETURN"))
	public void removePlayer(ServerPlayerEntity player, CallbackInfo ci) {
		handler.onPlayerRemoved(player);
	}

	@ModifyReturnValue(method = "toNbt", at = @At("RETURN"))
	public NbtCompound save(NbtCompound nbt, @Local(argsOnly = true) RegistryWrapper.WrapperLookup registries) {
		handler.getMusic().flatMap(music -> MusicEntry.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), music).resultOrPartial(
				METAcraftCore.LOGGER::error
		)).ifPresent(music -> {
			nbt.put(MUSIC, music);
		});
		return nbt;
	}

	@ModifyReturnValue(method = "fromNbt", at = @At("RETURN"))
	private static CommandBossBar load(
			CommandBossBar bossBar, @Local(argsOnly = true) NbtCompound nbt,
			@Local(argsOnly = true) RegistryWrapper.WrapperLookup registries
	) {
		var musicHandler = ((CommandBossBarExtension) bossBar).metacraft_core$getMusicHandler();
		if (nbt.contains(MUSIC)) {
			MusicEntry.CODEC.parse(registries.getOps(NbtOps.INSTANCE), nbt.get(MUSIC)).resultOrPartial(
					METAcraftCore.LOGGER::error
			).ifPresent(musicHandler::setMusic);
		} else {
			musicHandler.setMusic(null);
		}
		return bossBar;
	}

	@Override
	public BossBarMusicHandler metacraft_core$getMusicHandler() {
		return handler;
	}
}
