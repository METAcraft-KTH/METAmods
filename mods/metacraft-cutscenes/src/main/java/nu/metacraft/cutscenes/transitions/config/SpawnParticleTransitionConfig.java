package nu.metacraft.cutscenes.transitions.config;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.SpawnParticleTransition;
import nu.metacraft.cutscenes.transitions.Transition;

public record SpawnParticleTransitionConfig(
		Either<ParticleEffect, ParticleProvider> particle, boolean force, boolean important, PositionRef pos, int count, Vec3d delta, double speed, int spawnInterval
) implements TransitionConfig {

	public static final Codec<Either<ParticleEffect, ParticleProvider>> PARTICLE_CODEC = Codec.either(
			ParticleTypes.TYPE_CODEC, ParticleProvider.CODEC
	);

	public static final MapCodec<SpawnParticleTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PARTICLE_CODEC.fieldOf("particle").forGetter(t -> t.particle),
					Codec.BOOL.optionalFieldOf("force", false).forGetter(t -> t.force),
					Codec.BOOL.optionalFieldOf("important", false).forGetter(t -> t.important),
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					Codecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(t -> t.count),
					Vec3d.CODEC.optionalFieldOf("delta", Vec3d.ZERO).forGetter(t -> t.delta),
					Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("speed", 1.0).forGetter(t -> t.speed),
					Codecs.POSITIVE_INT.optionalFieldOf("spawn_interval", 1).forGetter(t -> t.spawnInterval)
			).apply(instance, SpawnParticleTransitionConfig::new)
	);

	@Override
	public Transition create() {
		return new SpawnParticleTransition(this);
	}

	@Override
	public TransitionConfigType<?> getConfigType() {
		return TransitionConfigRegistry.PARTICLE;
	}

	public record ParticleProvider(NbtCompound data, Identifier storage, String path) {

		public static final Codec<ParticleProvider> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						NbtCompound.CODEC.fieldOf("data").forGetter(ParticleProvider::data),
						Identifier.CODEC.fieldOf("storage").forGetter(ParticleProvider::storage),
						Codec.STRING.optionalFieldOf("path", "").forGetter(ParticleProvider::path)
				).apply(instance, ParticleProvider::new)
		);

		public DataResult<ParticleEffect> get(MinecraftServer server) {
			try {
				var dataSource = server.getDataCommandStorage().get(this.storage);
				NbtCompound dataToAdd;
				if (path.isEmpty()) {
					dataToAdd = dataSource;
				} else {
					var found = NbtPathArgumentType.NbtPath.parse(path).get(dataSource).stream().filter(
							element -> element instanceof NbtCompound
					).map(element -> (NbtCompound) element).findAny();
					if (found.isEmpty()) {
						return DataResult.error(() -> "Path did not point to compound data");
					}
					dataToAdd = found.get();
				}
				return ParticleTypes.TYPE_CODEC.parse(
						server.getRegistryManager().getOps(NbtOps.INSTANCE), data.copy().copyFrom(dataToAdd)
				);
			} catch (CommandSyntaxException err) {
				return DataResult.error(err::getMessage);
			}
		}
	}
}
