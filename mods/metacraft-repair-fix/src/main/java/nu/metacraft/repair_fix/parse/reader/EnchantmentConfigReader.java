package nu.metacraft.repair_fix.parse.reader;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import nu.metacraft.repair_fix.RepairFixConfig;
import nu.metacraft.repair_fix.parse.EnchantmentParse;
import nu.metacraft.repair_fix.util.NumberHelper;

public class EnchantmentConfigReader extends StringConfigParser<RegistryEntry<Enchantment>, EnchantmentParse> {

	public EnchantmentConfigReader() {
		super(() -> RepairFixConfig.getConfig().enchantmentCompatOverrides);
	}

	@Override
	protected EnchantmentParse readLine(boolean allow, String[] splitLine) {
		return new EnchantmentParse(
				EnchantmentParse.fromString(splitLine[0]),
				EnchantmentParse.fromString(splitLine[1]),
				allow,
				allow && splitLine.length > 2 ? NumberHelper.getInt(splitLine[2]).orElse(0) : 0
		);
	}
}
