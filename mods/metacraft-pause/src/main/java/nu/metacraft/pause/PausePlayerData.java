package nu.metacraft.pause;

import net.minecraft.world.phys.Vec3;

public interface PausePlayerData {

	void metacraft_pause$setBeforePauseVelocity(Vec3 velocity);

	Vec3 metacraft_pause$getBeforePauseVelocity();

}
