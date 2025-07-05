package nu.metacraft.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nu.metacraft.core.extensions.CommandBossBarExtension;
import nu.metacraft.core.extensions.CommandBossBarSerializedExtension;
import nu.metacraft.core.music.BossBarMusicHandler;
import nu.metacraft.core.music.MusicEntry;

import java.util.Optional;

@Mixin(CommandBossBar.class)
public class MixinCommandBossBar implements CommandBossBarExtension {

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

	@ModifyReturnValue(method = "toSerialized", at = @At("RETURN"))
	public CommandBossBar.Serialized toSerialized(CommandBossBar.Serialized original) {
		((CommandBossBarSerializedExtension) (Object) original).metacraft_core$setMusic(handler.getMusic());
		return original;
	}

	@ModifyReturnValue(method = "fromSerialized", at = @At("RETURN"))
	private static CommandBossBar fromSerialized(CommandBossBar original, @Local(argsOnly = true) CommandBossBar.Serialized serialized) {
		((CommandBossBarSerializedExtension) (Object) serialized).metacraft_core$getMusic().ifPresent(music -> {
			((CommandBossBarExtension) original).metacraft_core$getMusicHandler().setMusic(music);
		});
		return original;
	}

	@Override
	public BossBarMusicHandler metacraft_core$getMusicHandler() {
		return handler;
	}


	@Mixin(CommandBossBar.Serialized.class)
	private static class Serialized implements CommandBossBarSerializedExtension {

		@Unique
		private static final MapCodec<Optional<MusicEntry>> MUSIC = MusicEntry.CODEC.optionalFieldOf("metacraft:music");

		private Optional<MusicEntry> music = Optional.empty();

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
							((CommandBossBarSerializedExtension) bossBar.getFirst()).metacraft_core$setMusic(music);
							return bossBar;
						});
					});
				}

				@Override
				public <T> DataResult<T> encode(O o, DynamicOps<T> dynamicOps, T t) {
					return original.encode(o, dynamicOps, t).flatMap(data -> {
						var music = ((CommandBossBarSerializedExtension) o).metacraft_core$getMusic();
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
		public Optional<MusicEntry> metacraft_core$getMusic() {
			return music;
		}

		@Override
		public void metacraft_core$setMusic(Optional<MusicEntry> music) {
			this.music = music;
		}
	}
}
