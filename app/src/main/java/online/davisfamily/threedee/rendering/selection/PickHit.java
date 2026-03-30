package online.davisfamily.threedee.rendering.selection;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PickHit {
	public final Vec3 localHitPoint = new Vec3();
	public final Vec3 worldHitPoint = new Vec3();
	public RenderableObject object;
	public float distance = Float.POSITIVE_INFINITY;
	public int triangleIndex = -1;
	
	public void reset() {
		localHitPoint.setXYZ(0f, 0f,0f);
		worldHitPoint.setXYZ(0f, 0f,0f);
		object = null;
		distance = Float.POSITIVE_INFINITY;
		triangleIndex = -1;
	}
	
	public boolean hasHit() {
		return object != null;
	}
}
