package online.davisfamily.threedee.rendering.selection;

import online.davisfamily.threedee.matrices.Vec3;

public class PickRay {
	public final Vec3 origin = new Vec3();
	public final Vec3 direction = new Vec3();

	public PickRay set(Vec3 origin, Vec3 direction) {
		this.origin.set(origin);
		this.direction.set(direction);
		return this;
	}
}
