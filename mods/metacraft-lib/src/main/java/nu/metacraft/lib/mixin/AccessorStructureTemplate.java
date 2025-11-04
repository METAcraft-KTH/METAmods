package nu.metacraft.lib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

@Mixin(StructureTemplate.class)
public interface AccessorStructureTemplate {

	@Accessor
	void setSize(Vec3i size);

	@Accessor
	List<StructureTemplate.Palette> getPalettes();

	@Invoker
	static void callAddToLists(
			StructureTemplate.StructureBlockInfo blockInfo,
			List<StructureTemplate.StructureBlockInfo> fullBlocks,
			List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
			List<StructureTemplate.StructureBlockInfo> otherBlocks
	) {}

	@Invoker
	static List<StructureTemplate.StructureBlockInfo> callBuildInfoList(
			List<StructureTemplate.StructureBlockInfo> fullBlocks,
			List<StructureTemplate.StructureBlockInfo> blocksWithNbt,
			List<StructureTemplate.StructureBlockInfo> otherBlocks
	) {
		return null;
	}

	@Mixin(StructureTemplate.Palette.class)
	interface AccessorPalettedBlockInfoList {

		@Invoker("<init>")
		static StructureTemplate.Palette init(List<StructureTemplate.StructureBlockInfo> infos) {
			return null;
		}

	}

}
