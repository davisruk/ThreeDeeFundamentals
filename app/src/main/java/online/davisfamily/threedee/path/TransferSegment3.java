package online.davisfamily.threedee.path;

import online.davisfamily.threedee.matrices.Vec3;

public final class TransferSegment3 implements PathSegment3 {

	    private final Vec3 startPoint;
	    private final Vec3 endPoint;
	    private final Vec3 facingDirection;
	    private final float length;

	    public TransferSegment3(Vec3 startPoint, Vec3 endPoint, Vec3 facingDirection) {
	        this.startPoint = startPoint;
	        this.endPoint = endPoint;
	        this.facingDirection = facingDirection.normalize();
	        this.length = endPoint.subtract(startPoint).length();
	    }

	    @Override
	    public float getTotalLength() {
	        return length;
	    }

	    @Override
	    public Vec3 sampleByDistance(float distance) {
	        if (length == 0f) return startPoint;

	        float d = clamp(distance, 0f, length);
	        float t = d / length;
	        return lerp(startPoint, endPoint, t);
	    }

	    @Override
	    public Vec3 sampleTangentByDistance(float distance) {
	        Vec3 movement = endPoint.subtract(startPoint);
	        if (movement.lengthSquared() == 0f) {
	            return facingDirection;
	        }
	        return movement.normalize();
	    }

	    @Override
	    public Vec3 sampleOrientationDirectionByDistance(float distance) {
	        return facingDirection;
	    }

	    @Override
	    public Vec3 getStartPoint() {
	        return startPoint;
	    }

	    @Override
	    public Vec3 getEndPoint() {
	        return endPoint;
	    }

	    @Override
	    public Vec3 getStartTangent() {
	        return sampleTangentByDistance(0f);
	    }

	    @Override
	    public Vec3 getEndTangent() {
	        return sampleTangentByDistance(length);
	    }

	    private static float clamp(float value, float min, float max) {
	        return Math.max(min, Math.min(max, value));
	    }

	    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
	        return new Vec3(
	            a.x + (b.x - a.x) * t,
	            a.y + (b.y - a.y) * t,
	            a.z + (b.z - a.z) * t
	        );
	    }
	}