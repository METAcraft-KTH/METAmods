package nu.metacraft.cutscenes.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class ParsedStructure {

	private final Either<ResourceLocation, SerialisedStructure> structure;

	public static final Codec<ParsedStructure> CODEC = Codec.either(
			ResourceLocation.CODEC, SerialisedStructure.CODEC
	).xmap(
			ParsedStructure::new,
			structure -> structure.structure
	);

	public ParsedStructure(Either<ResourceLocation, SerialisedStructure> structure) {
		this.structure = structure;
	}

	public ParsedStructure(ResourceLocation id) {
		this(Either.left(id));
	}

	public ParsedStructure(SerialisedStructure structure) {
		this(Either.right(structure));
	}

	public ParsedStructure(StructureTemplate structure) {
		this(new SerialisedStructure(structure));
	}

	public Optional<StructureTemplate> get(
			StructureTemplateManager manager, HolderLookup.Provider lookup
	) {
		return structure.map(
				manager::get,
				serialized -> Optional.of(serialized.parse(lookup))
		);
	}

}
