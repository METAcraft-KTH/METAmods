package se.datasektionen.mc.metacraft_lib.util.helper;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.Vec3i;
import se.datasektionen.mc.metacraft_lib.mixin.AccessorStructureTemplate;

import java.util.List;

@SuppressWarnings("unused")
public class StructureTemplateHelper {

	public static void setSize(StructureTemplate structure, Vec3i size) {
		((AccessorStructureTemplate) structure).setSize(size);
	}

	public static List<StructureTemplate.PalettedBlockInfoList> getBlockInfoLists(StructureTemplate structure) {
		return ((AccessorStructureTemplate) structure).getBlockInfoLists();
	}

	public static void categorize(
			StructureTemplate.StructureBlockInfo blockInfo,
			List<StructureTemplate.StructureBlockInfo> fullBlocks,
			List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
			List<StructureTemplate.StructureBlockInfo> otherBlocks
	) {
		AccessorStructureTemplate.callCategorize(blockInfo, fullBlocks, blocksWithNbt, otherBlocks);
	}

	public static List<StructureTemplate.StructureBlockInfo> combineSorted(
			List<StructureTemplate.StructureBlockInfo> fullBlocks,
			List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
			List<StructureTemplate.StructureBlockInfo> otherBlocks
	) {
		return AccessorStructureTemplate.callCombineSorted(fullBlocks, blocksWithNbt, otherBlocks);
	}

	public static StructureTemplate.PalettedBlockInfoList createPalettedBlockInfoList(
			List<StructureTemplate.StructureBlockInfo> infos
	) {
		return AccessorStructureTemplate.AccessorPalettedBlockInfoList.init(infos);
	}

}
