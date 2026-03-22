package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.rendering.RenderableObject;

public interface Behaviour {
	public enum WrapMode { CLAMP, LOOP, PING_PONG }
	public enum OrientationMode {NONE, YAW, PITCH}
	public void update(RenderableObject object, double dtSeconds);
}

