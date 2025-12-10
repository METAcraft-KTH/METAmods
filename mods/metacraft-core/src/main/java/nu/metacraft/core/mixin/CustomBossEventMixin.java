package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import nu.metacraft.core.music.PlayerMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.extensions.CommandBossBarExtension;
import nu.metacraft.core.extensions.CommandBossBarPackedExtension;
import nu.metacraft.core.music.BossBarMusicHandler;

import java.util.Optional;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;

@Mixin(CustomBossEvent.class)
public class CustomBossEventMixin implements CommandBossBarExtension {

	@Unique
	private final BossBarMusicHandler handler = new BossBarMusicHandler((CustomBossEvent) (Object) this);

	@Inject(method = "addPlayer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
	public void addPlayer(ServerPlayer player, CallbackInfo ci) {
		handler.onPlayerAdded(player);
	}

	@Inject(method = "removePlayer", at = @At("RETURN"))
	public void removePlayer(ServerPlayer player, CallbackInfo ci) {
		handler.onPlayerRemoved(player);
	}

	@ModifyReturnValue(method = "pack", at = @At("RETURN"))
	public CustomBossEvent.Packed toSerialized(CustomBossEvent.Packed original) {
		((CommandBossBarPackedExtension) (Object) original).metacraft_core$setMusic(handler.getMusic());
		return original;
	}

	@ModifyReturnValue(method = "load", at = @At("RETURN"))
	private static CustomBossEvent fromSerialized(CustomBossEvent original, @Local(argsOnly = true) CustomBossEvent.Packed serialized) {
		((CommandBossBarPackedExtension) (Object) serialized).metacraft_core$getMusic().ifPresent(music -> {
			((CommandBossBarExtension) original).metacraft_core$getMusicHandler().setMusic(music);
		});
		return original;
	}

	@Override
	public BossBarMusicHandler metacraft_core$getMusicHandler() {
		return handler;
	}


	@Mixin(CustomBossEvent.Packed.class)
	private static class Packed implements CommandBossBarPackedExtension {

		@Unique
		private static final MapCodec<Optional<PlayerMusic>> MUSIC = PlayerMusic.EASY_CODEC.optionalFieldOf("metacraft:music");

		private Optional<PlayerMusic> music = Optional.empty();

		@ModifyExpressionValue(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;create(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
			)
		)
		private static <O> Codec<O> init(Codec<O> original) {
			return new Codec<>() {
				@Override
				public <T> DataResult<Pair<O, T>> decode(DynamicOps<T> dynamicOps, T t) {
					return original.decode(dynamicOps, t).flatMap(bossBar -> {
						return dynamicOps.getMap(t).flatMap(
								map -> {
									return MUSIC.decode(dynamicOps, map);
								}
						).map(music -> {
							((CommandBossBarPackedExtension) bossBar.getFirst()).metacraft_core$setMusic(music);
							return bossBar;
						});
					});
				}

				@Override
				public <T> DataResult<T> encode(O o, DynamicOps<T> dynamicOps, T t) {
					return original.encode(o, dynamicOps, t).flatMap(data -> {
						var music = ((CommandBossBarPackedExtension) o).metacraft_core$getMusic();
						return MUSIC.encode(music, dynamicOps, dynamicOps.mapBuilder()).build(data);
					});
				}

				@Override
				public String toString() {
					return "CommandBossBarMusicExtensionCodec[" + original.toString() + "]";
				}
			};
		}

		@Override
		public Optional<PlayerMusic> metacraft_core$getMusic() {
			return music;
		}

		@Override
		public void metacraft_core$setMusic(Optional<PlayerMusic> music) {
			this.music = music;
		}
	}
}
