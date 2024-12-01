package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureTemplate;

public record SerialisedStructure(NbtCompound data) {
	public static final Codec<SerialisedStructure> CODEC = NbtCompound.CODEC.xmap(
			SerialisedStructure::new, SerialisedStructure::data
	);

	public SerialisedStructure(StructureTemplate structure) {
		this(structure.writeNbt(new NbtCompound()));
	}

	private static NbtList convert(int[] array) {
		var list = new NbtList();
		for (int i : array) {
			list.add(NbtInt.of(i));
		}
		return list;
	}

	public StructureTemplate parse(RegistryWrapper.WrapperLookup lookup) {
		StructureTemplate template = new StructureTemplate();
		//StructureTemplate#readNbt only accepts an int list, but codecs sometimes like to replace it with an int array.
		if (data.contains(StructureTemplate.SIZE_KEY, NbtElement.INT_ARRAY_TYPE)) {
			data.put(StructureTemplate.SIZE_KEY, convert(data.getIntArray(StructureTemplate.SIZE_KEY)));
		}
		if (data.contains(StructureTemplate.BLOCKS_KEY, NbtElement.LIST_TYPE)) {
			var blocks = data.getList(StructureTemplate.BLOCKS_KEY, NbtElement.COMPOUND_TYPE);
			for (var b : blocks) {
				var block = ((NbtCompound) b);
				if (block.contains(StructureTemplate.BLOCKS_POS_KEY, NbtElement.INT_ARRAY_TYPE)) {
					block.put(StructureTemplate.BLOCKS_POS_KEY, convert(block.getIntArray(StructureTemplate.BLOCKS_POS_KEY)));
				}
			}
		}
		if (data.contains(StructureTemplate.ENTITIES_KEY, NbtElement.LIST_TYPE)) {
			var entities = data.getList(StructureTemplate.ENTITIES_KEY, NbtElement.COMPOUND_TYPE);
			for (var e : entities) {
				var entity = ((NbtCompound) e);
				if (entity.contains(StructureTemplate.ENTITIES_BLOCK_POS_KEY, NbtElement.LIST_TYPE)) {
					entity.put(StructureTemplate.ENTITIES_BLOCK_POS_KEY, convert(entity.getIntArray(StructureTemplate.ENTITIES_BLOCK_POS_KEY)));
				}
			}
		}
		template.readNbt(lookup.getOrThrow(RegistryKeys.BLOCK), data);
		return template;
	}
}
