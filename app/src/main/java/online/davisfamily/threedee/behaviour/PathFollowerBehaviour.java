package online.davisfamily.threedee.behaviour;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.Path3;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PathFollowerBehaviour implements Behaviour {

	public enum WrapMode { CLAMP, LOOP, PING_PONG }
	
	private final Path3 path;
	private final float unitsPerSecond;
	private final WrapMode wrapMode;
	private float direction = 1f;
	private float distanceAlongPath = 0f;
	
	public PathFollowerBehaviour(Path3 path, float unitsPerSecond, WrapMode wrapMode) {
		this(path, unitsPerSecond, wrapMode, 0f);
	}

	public PathFollowerBehaviour(Path3 path, float unitsPerSecond, WrapMode wrapMode, float startDistance) {
		this.path = path;
		this.unitsPerSecond = unitsPerSecond;
		this.wrapMode = wrapMode;
		this.distanceAlongPath = startDistance;
	}
	
	@Override
	public void update(RenderableObject object, double dtSeconds) {
		distanceAlongPath+= direction * unitsPerSecond * (float) dtSeconds;
		float totalLength = path.getTotalLength();

		switch (wrapMode) {
			case CLAMP -> {
				if (distanceAlongPath < 0f) distanceAlongPath = 0f;
				if (distanceAlongPath > totalLength) distanceAlongPath = totalLength;
			}
			case LOOP -> {
				while (distanceAlongPath > totalLength) distanceAlongPath-=totalLength;
				while (distanceAlongPath < 0f) distanceAlongPath+=totalLength;
			}
			case PING_PONG -> {
				if (distanceAlongPath > totalLength) {
					distanceAlongPath = totalLength;
					direction = -1f;
				} else if (distanceAlongPath < 0f) {
					distanceAlongPath = 0f;
					direction = 1f;
				}
			}
		}
		
		Vec3 p = path.sampleByDistance(distanceAlongPath);
		object.transformation.setTranslation(p);
	}
}
