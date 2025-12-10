package nu.metacraft.zones.zone;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nu.metacraft.zones.zone.types.*;
import nu.metacraft.zones.METAcraftZones;
import nu.metacraft.zones.ZoneManagementCommand;

import java.util.List;
import java.util.function.Supplier;

public class ZoneRegistry {

	public static final Registry<ZoneTypeType<?>> REGISTRY = FabricRegistryBuilder.<ZoneTypeType<?>>createSimple(
			ResourceKey.createRegistryKey(METAcraftZones.getID("zones"))
	).buildAndRegister();

	public static final ZoneTypeType<BoxZone> box = register(
			"box", BoxZone.CODEC, BoxZone::createCommand,
			() -> new BoxZone(BoundingBox.fromCorners(Vec3i.ZERO, Vec3i.ZERO))
	);
	public static final ZoneTypeType<RegionZone> region = register(
			"region", RegionZone.CODEC, RegionZone::createCommand, () -> new RegionZone(0, 0, 0,0)
	);
	public static final ZoneTypeType<SphereZone> sphere = register(
			"sphere", SphereZone.getCodec(SphereZone::new),
			(zoneCreator, ctx) -> SphereZone.createCommand(zoneCreator, ctx, SphereZone::new),
			() -> new SphereZone(BlockPos.ZERO, 0)
	);
	public static final ZoneTypeType<CircleZone> circle = register(
			"circle", CircleZone.getCodec(CircleZone::new),
			(zoneCreator, ctx) -> SphereZone.createCommand(zoneCreator, ctx, CircleZone::new),
			() -> new CircleZone(BlockPos.ZERO, 0)
	);

	public static final ZoneTypeType<BiomeZone> biome = register(
			"biome", BiomeZone.CODEC, BiomeZone::createCommand,
			() -> new BiomeZone(Either.right(BiomeTags.IS_END), false)
	);
	public static final ZoneTypeType<UnionZone> union = register(
		"union", UnionZone.getCodec(UnionZone::new),
		(builder, addZone) -> UnionZone.createCommand(builder, addZone, UnionZone::new),
		() -> new UnionZone(new ZoneZone("missingno"))
	);
	public static final ZoneTypeType<IntersectZone> intersect = register(
			"intersect", IntersectZone.getCodec(IntersectZone::new),
			(builder, addZone) -> IntersectZone.createCommand(builder, addZone, IntersectZone::new),
			() -> new IntersectZone(new ZoneZone("missingno"))
	);
	public static final ZoneTypeType<NegateZone> negate = register(
			"negate", NegateZone.CODEC, NegateZone::createCommand,
			() -> new NegateZone(new ZoneZone("missingno"))
	);
	public static final ZoneTypeType<ZoneZone> zone = register(
			"zone", ZoneZone.CODEC, ZoneZone::createCommand,
			() -> new ZoneZone("missingno")
	);
	public static final ZoneTypeType<DimensionLimiter> dimension = register(
			"dimension", DimensionLimiter.CODEC, DimensionLimiter::createCommand,
			() -> new DimensionLimiter(Level.OVERWORLD)
	);

	public static final ZoneTypeType<EmptyZone> empty = register(
			"empty", EmptyZone.CODEC, EmptyZone::createCommand, () -> EmptyZone.INSTANCE
	);

	public static final ZoneTypeType<TriangleZone> triangle = register(
			"triangle", TriangleZone.CODEC, TriangleZone::createCommand,
			() -> new TriangleZone(new ColumnPos(0, 0), new ColumnPos(0, 0), new ColumnPos(0, 0))
	);

	public static final ZoneTypeType<PolygonZone> polygon = register(
			"polygon", PolygonZone.CODEC, PolygonZone::createCommand,
			() -> new PolygonZone(List.of(new ColumnPos(0, 0), new ColumnPos(0, 0), new ColumnPos(0, 0)))
	);



	public static void init() {
		//DO NOT REMOVE THIS!!! Necessary for things to load early enough.
	}


	private static <T extends ZoneType> ZoneTypeType<T> register(
			String name, MapCodec<T> codec, ZoneCommandCreator commandCreator, Supplier<T> defaultValue
	) {
		return Registry.register(REGISTRY, Identifier.withDefaultNamespace(name), new ZoneTypeType<>(codec, commandCreator, defaultValue));
	}

	private static <T extends ZoneType> ZoneTypeType<T> register(
			String name, MapCodec<T> codec, SimpleZoneCommandCreator commandCreator, Supplier<T> defaultValue
	) {
		return register(
				name, codec,
				(argumentBuilder, registryAccess, addZone) -> commandCreator.createCommand(argumentBuilder, addZone),
				defaultValue
		);
	}

	@FunctionalInterface
	public interface ZoneCommandCreator {
		ArgumentBuilder<CommandSourceStack, ?> createCommand(
				ArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
				CommandBuildContext registryAccess,
				ZoneManagementCommand.ZoneAdder addZone
		);
	}

	@FunctionalInterface
	public interface SimpleZoneCommandCreator {
		ArgumentBuilder<CommandSourceStack, ?> createCommand(
				ArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
				ZoneManagementCommand.ZoneAdder addZone
		);
	}

	public record ZoneTypeType<T extends ZoneType>(
			MapCodec<T> codec,
			ZoneCommandCreator commandCreator,
			Supplier<T> defaultValue
	) {}

}
