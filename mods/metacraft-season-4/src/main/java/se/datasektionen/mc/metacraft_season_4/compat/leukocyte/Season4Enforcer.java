package se.datasektionen.mc.metacraft_season_4.compat.leukocyte;

import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.HitResult;
import se.datasektionen.mc.metacraft_season_4.block.Season4Blocks;
import xyz.nucleoid.leukocyte.rule.ProtectionRuleMap;
import xyz.nucleoid.leukocyte.rule.enforcer.ProtectionRuleEnforcer;
import xyz.nucleoid.stimuli.event.EventRegistrar;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;

public class Season4Enforcer implements ProtectionRuleEnforcer {

	private static final Season4Enforcer INSTANCE = new Season4Enforcer();

	public static Season4Enforcer getInstance() {
		return INSTANCE;
	}

	private Season4Enforcer() {}

	@Override
	public void applyTo(ProtectionRuleMap rules, EventRegistrar events) {
		this.forRule(events, rules.test(LeukocyteCompat.CAMPUS_LODESTONE)).applySimple(BlockUseEvent.EVENT, (rule) -> {
			return (player, hand, hitResult) -> {
				if (hitResult.getType() == HitResult.Type.BLOCK) {
					var pos = hitResult.getBlockPos();
					if (player.getWorld().getBlockState(pos).isOf(Season4Blocks.CAMPUS_LODESTONE)) {
						if (rule == EventResult.DENY) {
							player.sendMessage(
									Text.translatableWithFallback(
											"block.metacraft.campus_lodestone.blocked",
											"Campus Lodestones do not work here"
									), true
							);
							return ActionResult.FAIL;
						} else {
							return ActionResult.PASS;
						}
					}
				}
				return ActionResult.PASS;
			};
		});
	}
}
