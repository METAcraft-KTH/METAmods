package nu.metacraft.repair_fix.parse.reader;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.featuretoggle.FeatureSet;
import nu.metacraft.repair_fix.RepairFixConfig;
import nu.metacraft.repair_fix.parse.RepairParse;
import nu.metacraft.repair_fix.util.NumberHelper;

public class RepairConfigReader extends StringConfigParser<ItemStack, RepairParse> {

	private final RegistryWrapper.WrapperLookup lookup;
	private final FeatureSet features;

	public RepairConfigReader(
			RegistryWrapper.WrapperLookup lookup, FeatureSet features
	) {
		super(() -> RepairFixConfig.getConfig().repairItemCostBalancing);
		this.lookup = lookup;
		this.features = features;
	}

	@Override
	protected RepairParse readLine(boolean allow, String[] splitLine) {
		return RepairParse.parseStack(splitLine[0], lookup, features).flatMap(lhs -> {
			return RepairParse.parseStack(splitLine[1], lookup, features).map(rhs -> {
				return new RepairParse(
						lhs, rhs, allow,
						allow && splitLine.length > 2 ? NumberHelper.getInt(splitLine[2]).orElse(4) : 0
				);
			});
		}).orElse(null);
	}
}
