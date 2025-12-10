package nu.metacraft.cutscenes.util;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public record SerialisedStructure(CompoundTag data) {
	public static final Codec<SerialisedStructure> CODEC = CompoundTag.CODEC.xmap(
			SerialisedStructure::new, SerialisedStructure::data
	);

	public SerialisedStructure(StructureTemplate structure) {
		this(structure.save(new CompoundTag()));
	}

	private static ListTag convert(int[] array) {
		var list = new ListTag();
		for (int i : array) {
			list.add(IntTag.valueOf(i));
		}
		return list;
	}

	public StructureTemplate parse(HolderLookup.Provider lookup) {
		StructureTemplate template = new StructureTemplate();
		//StructureTemplate#readNbt only accepts an int list, but codecs sometimes like to replace it with an int array.
		var size = data.get(StructureTemplate.SIZE_TAG);
		if (size instanceof IntArrayTag array) {
			data.put(StructureTemplate.SIZE_TAG, convert(array.getAsIntArray()));
		}
		data.getList(StructureTemplate.BLOCKS_TAG).ifPresent(blocks -> {
			for (var b : blocks) {
				if (b instanceof CompoundTag block) {
					var blockPos = block.get(StructureTemplate.BLOCK_TAG_POS);
					if (blockPos instanceof IntArrayTag array) {
						block.put(StructureTemplate.BLOCK_TAG_POS, convert(array.getAsIntArray()));
					}
				}
			}
		});
		data.getList(StructureTemplate.ENTITIES_TAG).ifPresent(entities -> {
			for (var e : entities) {
				if (e instanceof CompoundTag entity) {
					var blockPos = entity.get(StructureTemplate.ENTITY_TAG_BLOCKPOS);
					if (blockPos instanceof IntArrayTag array) {
						entity.put(StructureTemplate.ENTITY_TAG_BLOCKPOS, convert(array.getAsIntArray()));
					}
				}
			}
		});
		template.load(lookup.lookupOrThrow(Registries.BLOCK), data);
		return template;
	}
}
