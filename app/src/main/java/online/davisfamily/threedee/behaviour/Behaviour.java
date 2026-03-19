package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.rendering.RenderableObject;

public interface Behaviour {
	public void update(RenderableObject object, double dtSeconds);
}
