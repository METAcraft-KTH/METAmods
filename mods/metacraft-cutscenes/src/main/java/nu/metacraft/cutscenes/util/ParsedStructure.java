package nu.metacraft.cutscenes.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class ParsedStructure {

	private final Either<Identifier, SerialisedStructure> structure;

	public static final Codec<ParsedStructure> CODEC = Codec.either(
			Identifier.CODEC, SerialisedStructure.CODEC
	).xmap(
			ParsedStructure::new,
			structure -> structure.structure
	);

	public ParsedStructure(Either<Identifier, SerialisedStructure> structure) {
		this.structure = structure;
	}

	public ParsedStructure(Identifier id) {
		this(Either.left(id));
	}

	public ParsedStructure(SerialisedStructure structure) {
		this(Either.right(structure));
	}

	public ParsedStructure(StructureTemplate structure) {
		this(new SerialisedStructure(structure));
	}

	public Optional<StructureTemplate> get(
			StructureTemplateManager manager, RegistryWrapper.WrapperLookup lookup
	) {
		return structure.map(
				manager::getTemplate,
				serialized -> Optional.of(serialized.parse(lookup))
		);
	}

}
