package se.datasektionen.mc.simplecustomfeatures.mixin;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(StructureTemplate.class)
public interface AccessorStructureTemplate {

	@Accessor
	void setSize(Vec3i size);

	@Accessor
	List<StructureTemplate.PalettedBlockInfoList> getBlockInfoLists();

	@Invoker
	static void callCategorize(StructureTemplate.StructureBlockInfo blockInfo, List<StructureTemplate.StructureBlockInfo> fullBlocks, List<StructureTemplate.StructureBlockInfo> blocksWithNbt, List<StructureTemplate.StructureBlockInfo> otherBlocks) {

	}

	@Invoker
	static List<StructureTemplate.StructureBlockInfo> callCombineSorted(List<StructureTemplate.StructureBlockInfo> fullBlocks, List<StructureTemplate.StructureBlockInfo> blocksWithNbt, List<StructureTemplate.StructureBlockInfo> otherBlocks) {
		return null;
	}

	@Mixin(StructureTemplate.PalettedBlockInfoList.class)
	interface AccessorPalettedBlockInfoList {

		@Invoker("<init>")
		static StructureTemplate.PalettedBlockInfoList init(List<StructureTemplate.StructureBlockInfo> infos) {
			return null;
		}

	}

}
