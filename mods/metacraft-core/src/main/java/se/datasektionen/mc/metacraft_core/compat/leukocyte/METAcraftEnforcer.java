package se.datasektionen.mc.metacraft_core.compat.leukocyte;

import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.HitResult;
import se.datasektionen.mc.metacraft_core.block.METAcraftBlocks;
import xyz.nucleoid.leukocyte.rule.ProtectionRuleMap;
import xyz.nucleoid.leukocyte.rule.enforcer.ProtectionRuleEnforcer;
import xyz.nucleoid.stimuli.event.EventRegistrar;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;

public class METAcraftEnforcer implements ProtectionRuleEnforcer {

	private static final METAcraftEnforcer INSTANCE = new METAcraftEnforcer();

	public static METAcraftEnforcer getInstance() {
		return INSTANCE;
	}

	private METAcraftEnforcer() {}

	@Override
	public void applyTo(ProtectionRuleMap rules, EventRegistrar events) {
		this.forRule(events, rules.test(LeukocyteCompat.CAMPUS_LODESTONE)).applySimple(BlockUseEvent.EVENT, (rule) -> {
			return (player, hand, hitResult) -> {
				if (hitResult.getType() == HitResult.Type.BLOCK) {
					var pos = hitResult.getBlockPos();
					if (player.getWorld().getBlockState(pos).isOf(METAcraftBlocks.CAMPUS_LODESTONE)) {
						if (rule == ActionResult.FAIL) {
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
