package nu.metacraft.faster_minecarts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import nu.metacraft.lib.config.ObjectStorage;
import nu.metacraft.lib.config.container.ConfigContainer;
import nu.metacraft.lib.config.container.ServerAware;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class FasterMinecartsConfig {

	private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FasterMinecarts.NAMESPACE + ".json");

	public static final Codec<FasterMinecartsConfig> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.BOOL.fieldOf("global_faster_minecarts").forGetter(c -> c.globalFasterMinecarts),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("max_minecart_speed").forGetter(c -> c.maxMinecartSpeed),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("max_minecart_speed_underwater").forGetter(c -> c.maxMinecartSpeedUnderwater),
					Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("dangerous_minecart_speed").forGetter(c -> c.dangerousMinecartSpeed),
					Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("damage_factor").forGetter(c -> c.damageFactor),
					ExperimentalMinecartMode.CODEC.fieldOf("experimental_minecart_mode").forGetter(c -> c.experimentalMinecartMode),
					ObjectStorage.createCodec(MinecartModifier.CODEC.listOf()).fieldOf("minecart_modifiers").forGetter(c -> c.minecartModifiers),
					ObjectStorage.createCodec(EntityDamageList.CODEC).fieldOf("entity_damage_list").forGetter(c -> c.entityDamageList),
					ObjectStorage.createCodec(BlockBooster.CODEC.listOf()).fieldOf("block_boosters").forGetter(c -> c.blockBoosters)
			).apply(instance, FasterMinecartsConfig::new)
	);

	private static final ServerAware<ConfigContainer<FasterMinecartsConfig>, Loaded> CONTAINER = ConfigContainer.Builder.create(
			CODEC, FasterMinecartsConfig::createDefault
	).buildRegistryAware(
			configPath,
			(config, server) -> new Loaded(
					config, server.getRegistryManager()
			)
	);

	private static FasterMinecartsConfig createDefault() {
		return new FasterMinecartsConfig(
				false, 60, 45,
				Optional.of(30 / 3.6 / 20), 2.16 * 20,
				ExperimentalMinecartMode.EXPERIMENTAL,
				ObjectStorage.fromValue(
						MinecartModifier.CODEC.listOf(), List.of(
								new MinecartModifier(EntityPredicate.Builder.create().build(), Optional.empty(), Optional.of(0.8))
						)
				),
				ObjectStorage.fromValue(
						EntityDamageList.CODEC, new EntityDamageList(
								List.of(
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.CHEST_MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.COMMAND_BLOCK_MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.FURNACE_MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.HOPPER_MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.TNT_MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.SPAWNER_MINECART
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.ITEM
										).build(),
										EntityPredicate.Builder.create().type(
												Registries.ENTITY_TYPE, EntityType.EXPERIENCE_ORB
										).build(),
										EntityPredicate.Builder.create().vehicle(
												EntityPredicate.Builder.create().type(
														Registries.ENTITY_TYPE, EntityType.MINECART
												)
										).build()
								),
								EntityDamageList.Mode.IGNORE
						)
				),
				ObjectStorage.fromValue(
						BlockBooster.CODEC.listOf(), List.of(
								new BlockBooster(BlockPredicate.Builder.create().blocks(
										Registries.BLOCK, Blocks.ICE
								).build(), 5),
								new BlockBooster(BlockPredicate.Builder.create().blocks(
										Registries.BLOCK, Blocks.PACKED_ICE
								).build(), 10),
								new BlockBooster(BlockPredicate.Builder.create().blocks(
										Registries.BLOCK, Blocks.BLUE_ICE
								).build(), 20)
						)
				)
		);
	}

	public static FasterMinecartsConfig getConfig() {
		return CONTAINER.getContainer().get();
	}

	public static FasterMinecartsConfig.Loaded getConfig(MinecraftServer server) {
		return CONTAINER.get(server);
	}

	private boolean globalFasterMinecarts;

	private double maxMinecartSpeed;

	private double maxMinecartSpeedUnderwater;

	private Optional<Double> dangerousMinecartSpeed;

	private double damageFactor;

	private ExperimentalMinecartMode experimentalMinecartMode;

	private ObjectStorage<List<MinecartModifier>> minecartModifiers;

	private ObjectStorage<EntityDamageList> entityDamageList;

	private ObjectStorage<List<BlockBooster>> blockBoosters;

	public FasterMinecartsConfig(
			boolean globalFasterMinecarts, double maxMinecartSpeed,
			double maxMinecartSpeedUnderwater, Optional<Double> dangerousMinecartSpeed,
			double damageFactor, ExperimentalMinecartMode experimentalMinecartMode,
			ObjectStorage<List<MinecartModifier>> minecartModifiers,
			ObjectStorage<EntityDamageList> entityDamageList,
			ObjectStorage<List<BlockBooster>> blockBoosters
	) {
		this.globalFasterMinecarts = globalFasterMinecarts;
		this.maxMinecartSpeed = maxMinecartSpeed;
		this.maxMinecartSpeedUnderwater = maxMinecartSpeedUnderwater;
		this.dangerousMinecartSpeed = dangerousMinecartSpeed;
		this.damageFactor = damageFactor;
		this.experimentalMinecartMode = experimentalMinecartMode;
		this.minecartModifiers = minecartModifiers;
		this.entityDamageList = entityDamageList;
		this.blockBoosters = blockBoosters;
	}

	public boolean globalFasterMinecarts() {
		return globalFasterMinecarts;
	}

	public double maxMinecartSpeed() {
		return maxMinecartSpeed;
	}

	public double maxMinecartSpeedUnderwater() {
		return maxMinecartSpeedUnderwater;
	}

	public ExperimentalMinecartMode experimentalMinecartMode() {
		return experimentalMinecartMode;
	}

	public Optional<Double> dangerousMinecartSpeed() {
		return dangerousMinecartSpeed;
	}

	public double damageFactor() {
		return damageFactor;
	}

	public record MinecartModifier(EntityPredicate minecartPredicate, Optional<Double> topSpeedFactor, Optional<Double> poweredRailAccelerationFactor) {
		public static final Codec<MinecartModifier> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						EntityPredicate.CODEC.fieldOf("predicate").forGetter(MinecartModifier::minecartPredicate),
						Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("top_speed").forGetter(MinecartModifier::topSpeedFactor),
						Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("powered_rail_acceleration_factor").forGetter(MinecartModifier::poweredRailAccelerationFactor)
				).apply(instance, MinecartModifier::new)
		);
	}

	public record EntityDamageList(List<EntityPredicate> predicates, Mode mode) {

		public static final Codec<EntityDamageList> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						EntityPredicate.CODEC.listOf().fieldOf("predicates").forGetter(EntityDamageList::predicates),
						Mode.CODEC.fieldOf("mode").forGetter(EntityDamageList::mode)
				).apply(instance, EntityDamageList::new)
		);

		public boolean isIncluded(Vec3d pos, Entity entity) {
			if (entity.getEntityWorld() instanceof ServerWorld sw) {
				for (var predicate : predicates) {
					if (predicate.test(sw, pos, entity)) {
						return mode == Mode.ONLY;
					}
				}
			}
			return mode == Mode.IGNORE;
		}

		public enum Mode implements StringIdentifiable {
			ONLY("only"),
			IGNORE("ignore");

			public static final Codec<Mode> CODEC = StringIdentifiable.createCodec(Mode::values);

			private final String name;

			Mode(String name) {
				this.name = name;
			}

			@Override
			public String asString() {
				return name;
			}
		}
	}

	public record BlockBooster(BlockPredicate predicate, double topSpeedIncrease) {
		public static final Codec<BlockBooster> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
						BlockPredicate.CODEC.fieldOf("predicate").forGetter(BlockBooster::predicate),
						Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("top_speed_increase").forGetter(BlockBooster::topSpeedIncrease)
				).apply(instance, BlockBooster::new)
		);
	}

	public enum ExperimentalMinecartMode implements StringIdentifiable {
		LEGACY(false, "legacy"),
		EXPERIMENTAL(true, "experimental");

		public static final Codec<ExperimentalMinecartMode> CODEC = StringIdentifiable.createCodec(ExperimentalMinecartMode::values);

		private final boolean enabled;
		private final String name;

		ExperimentalMinecartMode(boolean enabled, String name) {
			this.enabled = enabled;
			this.name = name;
		}

		public boolean isEnabled() {
			return enabled;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	public static class Loaded {

		private final List<MinecartModifier> minecartModifiers;

		private final EntityDamageList entityDamageList;

		private final List<BlockBooster> blockBoosters;

		public Loaded(FasterMinecartsConfig config, RegistryWrapper.WrapperLookup lookup) {
			this.minecartModifiers = config.minecartModifiers.parse(lookup).resultOrPartial().orElse(new ArrayList<>());
			this.entityDamageList = config.entityDamageList.parse(lookup).resultOrPartial().orElse(new EntityDamageList(new ArrayList<>(), EntityDamageList.Mode.IGNORE));
			this.blockBoosters = config.blockBoosters.parse(lookup).resultOrPartial().orElse(new ArrayList<>());
		}

		public boolean shouldDamageEntity(Vec3d pos, Entity entity) {
			return entityDamageList.isIncluded(pos, entity);
		}

		public double getBlockBoost(ServerWorld world, BlockPos pos) {
			double amount = 0;
			for (var boosters : blockBoosters) {
				if (boosters.predicate.test(world, pos)) {
					amount += boosters.topSpeedIncrease;
				}
			}
			return amount;
		}

		public Stream<MinecartModifier> getRelevantModifiers(AbstractMinecartEntity minecart) {
			return minecartModifiers.stream().filter(modifier -> modifier.minecartPredicate.test((ServerWorld) minecart.getEntityWorld(), minecart.getEntityPos(), minecart));
		}

	}
}
