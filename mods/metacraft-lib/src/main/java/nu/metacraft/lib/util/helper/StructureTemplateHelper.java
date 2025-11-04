package nu.metacraft.lib.util.helper;

import nu.metacraft.lib.mixin.StructureTemplateAccessor;

import java.util.List;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

@SuppressWarnings("unused")
public class StructureTemplateHelper {

	public static void setSize(StructureTemplate structure, Vec3i size) {
		((StructureTemplateAccessor) structure).setSize(size);
	}

	public static List<StructureTemplate.Palette> getBlockInfoLists(StructureTemplate structure) {
		return ((StructureTemplateAccessor) structure).getPalettes();
	}

	public static void categorize(
			StructureTemplate.StructureBlockInfo blockInfo,
			List<StructureTemplate.StructureBlockInfo> fullBlocks,
			List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
			List<StructureTemplate.StructureBlockInfo> otherBlocks
	) {
		StructureTemplateAccessor.callAddToLists(blockInfo, fullBlocks, blocksWithNbt, otherBlocks);
	}

	public static List<StructureTemplate.StructureBlockInfo> combineSorted(
			List<StructureTemplate.StructureBlockInfo> fullBlocks,
			List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
			List<StructureTemplate.StructureBlockInfo> otherBlocks
	) {
		return StructureTemplateAccessor.callBuildInfoList(fullBlocks, blocksWithNbt, otherBlocks);
	}

	public static StructureTemplate.Palette createPalettedBlockInfoList(
			List<StructureTemplate.StructureBlockInfo> infos
	) {
		return StructureTemplateAccessor.Palette.init(infos);
	}

}
