package se.datasektionen.mc.metacraft_core.compat.leukocyte;

import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.rule.ProtectionRule;

public class LeukocyteCompat {

	public static final ProtectionRule CAMPUS_LODESTONE = ProtectionRule.register("campus_lodestone");

	public static void init() {
		Leukocyte.registerRuleEnforcer(METAcraftEnforcer.getInstance());
	}

}
