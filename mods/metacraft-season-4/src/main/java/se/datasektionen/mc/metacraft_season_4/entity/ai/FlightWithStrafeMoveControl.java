package se.datasektionen.mc.metacraft_season_4.entity.ai;

import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class FlightWithStrafeMoveControl extends FlightMoveControl {

	private float upwardMovement;

	public FlightWithStrafeMoveControl(MobEntity entity, int maxPitchChange, boolean noGravity) {
		super(entity, maxPitchChange, noGravity);
	}

	public void strafeTo(float forward, float sideways, float upwards, float speed) {
		strafeTo(forward, sideways);
		this.upwardMovement = upwards;
		this.speed = speed;
	}

	@Override
	public void strafeTo(float forward, float sideways) {
		super.strafeTo(forward, sideways);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.state == MoveControl.State.STRAFE) {
			this.entity.setNoGravity(true);

			float f;
			if (this.entity.isOnGround()) {
				f = (float)(this.speed * this.entity.getAttributeValue(EntityAttributes.MOVEMENT_SPEED));
			} else {
				f = (float)(this.speed * this.entity.getAttributeValue(EntityAttributes.FLYING_SPEED));
			}
			float g = (float)this.speed * f;
			float h = this.forwardMovement;
			float i = this.sidewaysMovement;
			float up = this.upwardMovement;
			float j = MathHelper.sqrt(h * h + i * i + up * up);
			if (j < 1.0F) {
				j = 1.0F;
			}

			j = g / j;
			h *= j;
			i *= j;
			up *= j;
			float k = MathHelper.sin(this.entity.getYaw() * ((float)Math.PI / 180F));
			float l = MathHelper.cos(this.entity.getYaw() * ((float)Math.PI / 180F));
			float m = h * l - i * k;
			float n = i * l + h * k;
			if (!this.isPosSafe(m, up, n)) {
				this.forwardMovement = 1.0F;
				this.sidewaysMovement = 0.0F;
				this.upwardMovement = 0;
			}

			this.entity.setMovementSpeed(g);
			this.entity.setForwardSpeed(this.forwardMovement);
			this.entity.setSidewaysSpeed(this.sidewaysMovement);
			this.entity.setUpwardSpeed(this.upwardMovement);
			this.state = MoveControl.State.WAIT;

		}
	}

	protected boolean isPosSafe(float x, float y, float z) {
		EntityNavigation entityNavigation = this.entity.getNavigation();
		if (entityNavigation != null) {
			PathNodeMaker pathNodeMaker = entityNavigation.getNodeMaker();
			if (pathNodeMaker != null) {
				var nodeType = pathNodeMaker.getDefaultNodeType(
						this.entity,
						BlockPos.ofFloored(
								this.entity.getX() + (double)x,
								this.entity.getY() + y,
								this.entity.getZ() + (double)z
						)
				);
				if (entity.getPathfindingPenalty(nodeType) < 0) {
					return false;
				}
			}
		}

		return true;
	}

}
