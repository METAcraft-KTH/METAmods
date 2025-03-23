package se.datasektionen.mc.cutscenes.util;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.*;
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
		var size = data.get(StructureTemplate.SIZE_KEY);
		if (size instanceof NbtIntArray array) {
			data.put(StructureTemplate.SIZE_KEY, convert(array.getIntArray()));
		}
		data.getList(StructureTemplate.BLOCKS_KEY).ifPresent(blocks -> {
			for (var b : blocks) {
				if (b instanceof NbtCompound block) {
					var blockPos = block.get(StructureTemplate.BLOCKS_POS_KEY);
					if (blockPos instanceof NbtIntArray array) {
						block.put(StructureTemplate.BLOCKS_POS_KEY, convert(array.getIntArray()));
					}
				}
			}
		});
		data.getList(StructureTemplate.ENTITIES_KEY).ifPresent(entities -> {
			for (var e : entities) {
				if (e instanceof NbtCompound entity) {
					var blockPos = entity.get(StructureTemplate.ENTITIES_BLOCK_POS_KEY);
					if (blockPos instanceof NbtIntArray array) {
						entity.put(StructureTemplate.ENTITIES_BLOCK_POS_KEY, convert(array.getIntArray()));
					}
				}
			}
		});
		template.readNbt(lookup.getOrThrow(RegistryKeys.BLOCK), data);
		return template;
	}
}
