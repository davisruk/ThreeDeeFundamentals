package online.davisfamily.threedee.behaviour;

import java.util.EnumSet;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.Path3;
import online.davisfamily.threedee.rendering.RenderableObject;

public class PathFollowerBehaviour implements Behaviour {

	public enum WrapMode { CLAMP, LOOP, PING_PONG }
	public enum OrientationMode {NONE, YAW, PITCH}
	private final Path3 path;
	private final float unitsPerSecond;
	private final WrapMode wrapMode;
	private float direction = 1f;
	private float distanceAlongPath = 0f;
	private final EnumSet<OrientationMode> orientationModes;
	
	public PathFollowerBehaviour(Path3 path, float unitsPerSecond, WrapMode wrapMode) {
		this(path, unitsPerSecond, wrapMode, 0f, true, EnumSet.of(OrientationMode.YAW, OrientationMode.PITCH));
	}

	public PathFollowerBehaviour(Path3 path, float unitsPerSecond, WrapMode wrapMode, float startDistance, boolean alignToPath, EnumSet<OrientationMode> orientations) {
		this.path = path;
		this.unitsPerSecond = unitsPerSecond;
		this.wrapMode = wrapMode;
		this.distanceAlongPath = startDistance;
		this.orientationModes = orientations;
	}
	
	public Path3 getPath() {return path;}
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
		
		if (orientationModes.contains(OrientationMode.NONE)) return;

		Vec3 d = path.sampleTangentByDistance(distanceAlongPath);
		if (orientationModes.contains(OrientationMode.YAW)) {
			float yaw = Vec3.yawFromDirection(d)+object.yawOffsetRadians;
			object.transformation.setAxisRotation(Axis.Y, yaw);
		}
		if (orientationModes.contains(OrientationMode.PITCH)) {
			float pitch = d.pitch();
			object.transformation.setAxisRotation(Axis.X, pitch);
		}

	}
}
