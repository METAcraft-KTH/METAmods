package se.datasektionen.mc.zones.zone.types;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.mutable.MutableBoolean;
import se.datasektionen.mc.zones.util.ZoneCommandUtils;
import se.datasektionen.mc.zones.ZoneManagementCommand;
import se.datasektionen.mc.zones.zone.Zone;
import se.datasektionen.mc.zones.zone.ZoneRegistry;

import static net.minecraft.server.command.CommandManager.argument;

public class BiomeZone extends ZoneType {

	private static final Codec<Either<RegistryEntry<Biome>, TagKey<Biome>>> BIOME_CODEC = Codec.either(
			RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC, false), TagKey.codec(RegistryKeys.BIOME)
	); //For some reason, the vanilla biome registry entry codec allows inline definitions, despite the fact that this breaks the game...

	protected Either<RegistryEntry<Biome>, TagKey<Biome>> biome;
	protected boolean alwaysCheckSourceDim;

	public static final MapCodec<BiomeZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BIOME_CODEC.fieldOf("biome").forGetter(zone -> zone.biome),
			Codec.BOOL.fieldOf("alwaysCheckSourceDim").orElse(false).forGetter(zone -> zone.alwaysCheckSourceDim)
	).apply(instance, BiomeZone::new));


	private static final DynamicCommandExceptionType BIOME_FAIL = new DynamicCommandExceptionType(id -> Text.literal(id + " is not a valid biome or tag!"));

	public static ArgumentBuilder<ServerCommandSource, ?> createCommand(
			ArgumentBuilder<ServerCommandSource, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
				argument("biome", RegistryPredicateArgumentType.registryPredicate(RegistryKeys.BIOME)).executes(ctx -> {
					return runCommand(ctx, false, addZone);
				}).then(
					argument("alwaysCheckSourceDim", BoolArgumentType.bool()).executes(ctx -> {
						return runCommand(ctx, BoolArgumentType.getBool(ctx, "alwaysCheckSourceDim"), addZone);
					})
				)
		);
	}

	private static int runCommand(CommandContext<ServerCommandSource> ctx, boolean alwaysCheckSourceDim, ZoneManagementCommand.ZoneAdder addZone) throws CommandSyntaxException {
		var biome = RegistryPredicateArgumentType.getPredicate(ctx, "biome", RegistryKeys.BIOME, BIOME_FAIL);
		MutableBoolean error = new MutableBoolean(false);
		Either<RegistryEntry<Biome>, TagKey<Biome>> mapped = biome.getKey().mapLeft(
				key -> ctx.getSource().getServer().getRegistryManager().get(RegistryKeys.BIOME)
						.getEntry(key).orElseGet(() -> {
							error.setTrue();
							return null;
						})
		);
		if (error.booleanValue()) {
			throw ZoneCommandUtils.OTHER_ERROR.create(
					biome.asString() + " is not a valid biome!"
			);
		}
		return addZone.add(() -> new BiomeZone(
			mapped,
			alwaysCheckSourceDim
		), ctx);
	}

	public BiomeZone(Either<RegistryEntry<Biome>, TagKey<Biome>> biome, boolean alwaysCheckSourceDim) {
		this.biome = biome;
		this.alwaysCheckSourceDim = alwaysCheckSourceDim;
	}

	@Override
	public boolean contains(BlockPos pos) {
		var actualBiome = getZoneRef().getWorld().getBiome(pos);
		return biome.map(
				biome -> actualBiome.getKeyOrValue().equals(biome.getKeyOrValue()),
				actualBiome::isIn
		);
	}

	@Override
	public double getSize() {
		double width = getZoneRef().getWorld().getWorldBorder().getSize();
		int biomeCount = getZoneRef().getWorld().getRegistryManager().get(RegistryKeys.BIOME).size();
		return width * width * getZoneRef().getWorld().getHeight() / (biomeCount * biomeCount * biomeCount);
	}

	@Override
	public void setZoneRef(Zone zone) {
		if (this.getZoneRef() == null || !alwaysCheckSourceDim) {
			super.setZoneRef(zone);
		}
	}


	@Override
	public ZoneType copy() {
		if (alwaysCheckSourceDim) {
			var zone = new BiomeZone(biome, true);
			zone.setZoneRef(getZoneRef());
			return zone;
		} else {
			return new BiomeZone(biome, false);
		}
	}

	@Override
	public ZoneRegistry.ZoneTypeType<?> getType() {
		return ZoneRegistry.biome;
	}

	@Override
	public String toString() {
		return "Biome[" + biome.map(entry -> entry.getKeyOrValue().map(
				key -> key.getValue().toString(),
				Object::toString
		), tag -> tag.id().toString()) + "]";
	}
}
