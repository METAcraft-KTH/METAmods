package nu.metacraft.zones.zone.types;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import nu.metacraft.zones.util.ZoneCommandUtils;
import nu.metacraft.zones.ZoneManagementCommand;
import nu.metacraft.zones.zone.Zone;
import nu.metacraft.zones.zone.ZoneRegistry;

import static net.minecraft.commands.Commands.argument;

public class BiomeZone extends ZoneType {

	private static final Codec<Either<Holder<Biome>, TagKey<Biome>>> BIOME_CODEC = Codec.either(
			RegistryFileCodec.create(Registries.BIOME, Biome.DIRECT_CODEC, false), TagKey.hashedCodec(Registries.BIOME)
	); //For some reason, the vanilla biome registry entry codec allows inline definitions, despite the fact that this breaks the game...

	protected Either<Holder<Biome>, TagKey<Biome>> biome;
	protected boolean alwaysCheckSourceDim;

	public static final MapCodec<BiomeZone> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BIOME_CODEC.fieldOf("biome").forGetter(zone -> zone.biome),
			Codec.BOOL.fieldOf("alwaysCheckSourceDim").orElse(false).forGetter(zone -> zone.alwaysCheckSourceDim)
	).apply(instance, BiomeZone::new));


	private static final DynamicCommandExceptionType BIOME_FAIL = new DynamicCommandExceptionType(id -> Component.literal(id + " is not a valid biome or tag!"));

	public static ArgumentBuilder<CommandSourceStack, ?> createCommand(
			ArgumentBuilder<CommandSourceStack, ?> builder, ZoneManagementCommand.ZoneAdder addZone
	) {
		return builder.then(
				argument("biome", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.BIOME)).executes(ctx -> {
					return runCommand(ctx, false, addZone);
				}).then(
					argument("alwaysCheckSourceDim", BoolArgumentType.bool()).executes(ctx -> {
						return runCommand(ctx, BoolArgumentType.getBool(ctx, "alwaysCheckSourceDim"), addZone);
					})
				)
		);
	}

	private static int runCommand(CommandContext<CommandSourceStack> ctx, boolean alwaysCheckSourceDim, ZoneManagementCommand.ZoneAdder addZone) throws CommandSyntaxException {
		var biome = ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, "biome", Registries.BIOME, BIOME_FAIL);
		MutableBoolean error = new MutableBoolean(false);
		Either<Holder<Biome>, TagKey<Biome>> mapped = biome.unwrap().mapLeft(
				key -> ctx.getSource().getServer().registryAccess().lookupOrThrow(Registries.BIOME)
						.get(key).orElseGet(() -> {
							error.setTrue();
							return null;
						})
		);
		if (error.booleanValue()) {
			throw ZoneCommandUtils.OTHER_ERROR.create(
					biome.asPrintable() + " is not a valid biome!"
			);
		}
		return addZone.add(() -> new BiomeZone(
			mapped,
			alwaysCheckSourceDim
		), ctx);
	}

	public BiomeZone(Either<Holder<Biome>, TagKey<Biome>> biome, boolean alwaysCheckSourceDim) {
		this.biome = biome;
		this.alwaysCheckSourceDim = alwaysCheckSourceDim;
	}

	@Override
	public boolean contains(BlockPos pos) {
		var actualBiome = getZoneRef().getWorld().getBiome(pos);
		return matchesBiome(actualBiome);
	}

	@Override
	public double getSize() {
		double width = getZoneRef().getWorld().getWorldBorder().getSize();
		int biomeCount = getZoneRef().getWorld().registryAccess().lookupOrThrow(Registries.BIOME).size();
		return width * width * getZoneRef().getWorld().getHeight() / (biomeCount * biomeCount * biomeCount);
	}

	private boolean matchesBiome(Holder<Biome> foundBiome) {
		return biome.map(
				biome -> foundBiome.unwrap().equals(biome.unwrap()),
				foundBiome::is
		);
	}

	@Override
	public InwardVector getInwardVector(Vec3 pos) {
		if (getZoneRef().getWorld() instanceof ServerLevel sl) {
			var biomePos = sl.findClosestBiome3d(
					this::matchesBiome,
					BlockPos.containing(pos),
					6400, 32, 64
			);
			if (biomePos != null) {
				var vecPos = biomePos.getFirst().getCenter();
				return InwardVector.createFrom(vecPos, pos);
			}
		}
		return InwardVector.ZERO;
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
		return "Biome[" + biome.map(entry -> entry.unwrap().map(
				key -> key.identifier().toString(),
				Object::toString
		), tag -> tag.location().toString()) + "]";
	}
}
