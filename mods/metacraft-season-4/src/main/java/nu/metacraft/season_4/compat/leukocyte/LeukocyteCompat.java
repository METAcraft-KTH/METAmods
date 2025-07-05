package nu.metacraft.season_4.compat.leukocyte;

import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.rule.ProtectionRule;

public class LeukocyteCompat {

	public static final ProtectionRule CAMPUS_LODESTONE = ProtectionRule.register("campus_lodestone");

	public static void init() {
		Leukocyte.registerRuleEnforcer(Season4Enforcer.getInstance());
	}

}
