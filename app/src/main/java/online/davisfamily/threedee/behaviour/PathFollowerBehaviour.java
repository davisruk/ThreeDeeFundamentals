package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.Path3;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PathFollowerBehaviour implements Behaviour {

	public enum WrapMode { CLAMP, LOOP, PING_PONG }
	
	private final Path3 path;
	private final float unitsPerSecond;
	private final WrapMode wrapMode;
	private float u;
	private float direction = 1f;
	
	
	public PathFollowerBehaviour(Path3 path, float unitsPerSecond, WrapMode wrapMode) {
		this(path, unitsPerSecond, wrapMode, 0f);
	}

	public PathFollowerBehaviour(Path3 path, float unitsPerSecond, WrapMode wrapMode, float startU) {
		this.path = path;
		this.unitsPerSecond = unitsPerSecond;
		this.wrapMode = wrapMode;
		this.u = startU;
	}
	
	@Override
	public void update(RenderableObject object, double dtSeconds) {
		u += direction * unitsPerSecond * (float) dtSeconds;
		switch (wrapMode) {
			case CLAMP -> {
				if (u < 0f) u = 0f;
				if (u > 1f) u = 1f;
			}
			case LOOP -> {
				while (u > 1f) u-=1f;
				while (u < 0f) u+=1f;
			}
			case PING_PONG -> {
				if (u > 1f) {
					u = 1f;
					direction = -1f;
				} else if (u < 0f) {
					u = 0f;
					direction = 1f;
				}
			}
		}
		
		Vec3 p = path.samplePosition(u);
		object.transformation.setTranslation(p);
	}
}
