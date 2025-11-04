package nu.metacraft.cutscenes.transitions.config;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;
import nu.metacraft.core.position_ref.PositionRef;
import nu.metacraft.core.registry.PositionRefRegistry;
import nu.metacraft.cutscenes.registry.TransitionConfigRegistry;
import nu.metacraft.cutscenes.transitions.SpawnParticleTransition;
import nu.metacraft.cutscenes.transitions.Transition;

public record SpawnParticleTransitionConfig(
		Either<ParticleOptions, ParticleProvider> particle, boolean force, boolean important, PositionRef pos, int count, Vec3 delta, double speed, int spawnInterval
) implements TransitionConfig {

	public static final Codec<Either<ParticleOptions, ParticleProvider>> PARTICLE_CODEC = Codec.either(
			ParticleTypes.CODEC, ParticleProvider.CODEC
	);

	public static final MapCodec<SpawnParticleTransitionConfig> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					PARTICLE_CODEC.fieldOf("particle").forGetter(t -> t.particle),
					Codec.BOOL.optionalFieldOf("force", false).forGetter(t -> t.force),
					Codec.BOOL.optionalFieldOf("important", false).forGetter(t -> t.important),
					PositionRefRegistry.CODEC.fieldOf("pos").forGetter(t -> t.pos),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(t -> t.count),
					Vec3.CODEC.optionalFieldOf("delta", Vec3.ZERO).forGetter(t -> t.delta),
					Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("speed", 1.0).forGetter(t -> t.speed),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("spawn_interval", 1).forGetter(t -> t.spawnInterval)
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

	public record ParticleProvider(CompoundTag data, ResourceLocation storage, String path) {

		public static final Codec<ParticleProvider> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						CompoundTag.CODEC.fieldOf("data").forGetter(ParticleProvider::data),
						ResourceLocation.CODEC.fieldOf("storage").forGetter(ParticleProvider::storage),
						Codec.STRING.optionalFieldOf("path", "").forGetter(ParticleProvider::path)
				).apply(instance, ParticleProvider::new)
		);

		public DataResult<ParticleOptions> get(MinecraftServer server) {
			try {
				var dataSource = server.getCommandStorage().get(this.storage);
				CompoundTag dataToAdd;
				if (path.isEmpty()) {
					dataToAdd = dataSource;
				} else {
					var found = NbtPathArgument.NbtPath.of(path).get(dataSource).stream().filter(
							element -> element instanceof CompoundTag
					).map(element -> (CompoundTag) element).findAny();
					if (found.isEmpty()) {
						return DataResult.error(() -> "Path did not point to compound data");
					}
					dataToAdd = found.get();
				}
				return ParticleTypes.CODEC.parse(
						server.registryAccess().createSerializationContext(NbtOps.INSTANCE), data.copy().merge(dataToAdd)
				);
			} catch (CommandSyntaxException err) {
				return DataResult.error(err::getMessage);
			}
		}
	}
}
