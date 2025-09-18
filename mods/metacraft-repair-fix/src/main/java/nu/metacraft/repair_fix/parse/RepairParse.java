package nu.metacraft.repair_fix.parse;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.featuretoggle.FeatureSet;
import nu.metacraft.repair_fix.RepairFix;

import java.util.Optional;
import java.util.function.Predicate;

public class RepairParse extends ParseBase<ItemStack> {

	private final int repairItemsUntilFull;

	public RepairParse(Predicate<ItemStack> tool, Predicate<ItemStack> repairMaterial, boolean combine, int repairItemsUntilFull) {
		super(tool, repairMaterial, combine);
		this.repairItemsUntilFull = repairItemsUntilFull;
	}

	public int getRepairItemsUntilFull() {
		return repairItemsUntilFull;
	}

	public static Optional<Predicate<ItemStack>> parseStack(
			String stack, RegistryWrapper.WrapperLookup access, FeatureSet features
	) {
		try {
			return Optional.of(ItemPredicateArgumentType.itemPredicate(
					CommandRegistryAccess.of(access, features)
			).parse(new StringReader(stack)));
		} catch (CommandSyntaxException e) {
			RepairFix.getLogger().error("Unable to read item " + stack + " --> " + e.getMessage());
		}
		return Optional.empty();
	}

}
