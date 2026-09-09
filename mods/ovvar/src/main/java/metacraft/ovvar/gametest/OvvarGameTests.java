package metacraft.ovvar.gametest;

import metacraft.ovvar.content.Spot;
import metacraft.ovvar.sewing.StandAim;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * {@code ./gradlew runGametest}. The aiming tests hold the sewing aim to the forward mapping:
 * for every cell, on stands in several poses and facings, a ray straight at the cell's centre
 * must resolve to that cell — near faces from outside, far faces (sneaking) from the other
 * side of the part.
 */
public final class OvvarGameTests {
    private static final Rotations REST = new Rotations(0, 0, 0);
    private static final Rotations ARMS_OUT_R = new Rotations(0, 0, 60), ARMS_OUT_L = new Rotations(0, 0, -60);
    private static final Rotations LEGS_APART_R = new Rotations(0, 0, 25), LEGS_APART_L = new Rotations(0, 0, -25);

    @GameTest
    public void aimEveryCellAtRest(GameTestHelper helper) {
        aimEveryCell(helper, 0, REST, REST, REST, REST, spot -> true);
    }

    @GameTest
    public void aimEveryCellPosedAndTurned(GameTestHelper helper) {
        aimEveryCell(helper, 137, ARMS_OUT_R, ARMS_OUT_L, LEGS_APART_R, LEGS_APART_L, spot -> true);
    }

    @GameTest
    public void aimFarFacesWhenSneaking(GameTestHelper helper) {
        ArmorStand stand = stand(helper, 210, REST, REST, REST, REST);
        List<String> wrong = new ArrayList<>();
        for (Spot spot : Spot.values()) {
            if (spot == Spot.SEAT) continue;
            // Look at the cell from just inside its part: the ray exits through this face, which
            // sneaking picks (the far face of the part it is in).
            StandAim.CellPoint cell = StandAim.cell(stand, spot);
            Vec3 eye = cell.centre().subtract(cell.normal().scale(0.15));
            StandAim.Hit hit = StandAim.aim(eye, cell.normal(), stand, true, 6);
            if (hit == null || hit.spot() != spot) wrong.add(spot.id() + " → " + (hit == null ? "miss" : hit.spot()));
        }
        if (!wrong.isEmpty()) helper.fail("sneak-aim off for " + wrong.size() + " cell(s): " + wrong);
        helper.succeed();
    }

    private static void aimEveryCell(GameTestHelper helper, float yaw, Rotations rightArm, Rotations leftArm, Rotations rightLeg, Rotations leftLeg,
                                     Predicate<Spot> reachable) {
        ArmorStand stand = stand(helper, yaw, rightArm, leftArm, rightLeg, leftLeg);
        List<String> wrong = new ArrayList<>();
        for (Spot spot : Spot.values()) {
            if (spot == Spot.SEAT || !reachable.test(spot)) continue;
            StandAim.CellPoint cell = StandAim.cell(stand, spot);
            Vec3 eye = cell.centre().add(cell.normal().scale(0.3));   // close in: nothing else in the way
            StandAim.Hit hit = StandAim.aim(eye, cell.normal().scale(-1), stand, false, 6);
            if (hit == null || hit.spot() != spot) wrong.add(spot.id() + " → " + (hit == null ? "miss" : hit.spot()));
        }
        if (!wrong.isEmpty()) helper.fail("aim off for " + wrong.size() + " cell(s): " + wrong);
        helper.succeed();
    }

    private static ArmorStand stand(GameTestHelper helper, float yaw, Rotations rightArm, Rotations leftArm, Rotations rightLeg, Rotations leftLeg) {
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
        stand.setYRot(yaw);
        stand.setYBodyRot(yaw);
        stand.yBodyRotO = yaw;
        stand.setRightArmPose(rightArm);
        stand.setLeftArmPose(leftArm);
        stand.setRightLegPose(rightLeg);
        stand.setLeftLegPose(leftLeg);
        stand.setNoGravity(true);
        return stand;
    }
}
