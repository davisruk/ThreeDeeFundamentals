package online.davisfamily.threedee.camera;

import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;

public class Camera {
	public Vec3 position;
	public float yaw; // radians
	public float pitch; // radians
	public float sensitivityRadiansPerPixel = 0.002f; // ~0.11 degrees per pixel
	private Vec3 move, forward, right, worldUp, up, forwardXZ, rightXZ;
	private Mat4 view;
	
	public Camera () {
		this(new CameraPosition());
	}
	
	public Camera (CameraPosition camPos) {
		this.position = camPos.position;
		this.yaw = camPos.yaw;
		this.pitch = camPos.pitch;
		this.forward = new Vec3(0,0,-1);
		this.forwardXZ = new Vec3(0,0,-1);
		this.right = new Vec3(1,0,0);
		this.rightXZ = new Vec3(1,0,0);
		this.up = new Vec3(0,1,0);
		this.worldUp = new Vec3(0,1,0);
		this.view = Mat4.identity();
		this.move = new Vec3();
		updateBasis();
	}
	
	public Camera (Vec3 position) {
		this.position = position;
	}
	
	public void setSensitivity(float sensitivityRadiansPerPixel) {
		this.sensitivityRadiansPerPixel = sensitivityRadiansPerPixel;
	}
	
	public void mouseUpdate(MouseEventDetail md) {
		yaw+=md.dx * sensitivityRadiansPerPixel;
		pitch-=md.dy * sensitivityRadiansPerPixel;
		pitch = clamp(pitch, -1.5533f, 1.5533f); // +-89 degrees in radians
		updateBasis();
	}
	
	public void updateBasis() {
		forward
			.setXYZ(
				(float)Math.sin(yaw) * (float)Math.cos(pitch),
				(float)Math.sin(pitch),
				(float)-Math.cos(yaw) * (float)Math.cos(pitch))
			.mutableNormalize();
		
		right
			.set(forward.cross(worldUp))
			.mutableNormalize();
		
		up.set(right.cross(forward)).mutableNormalize();
		
		forwardXZ.setXYZ(forward.x, 0f, forward.z);
		if (forwardXZ.lengthSquared() > 0f)
			forwardXZ.mutableNormalize();
		
		rightXZ
			.set(forwardXZ.cross(worldUp))
			.mutableNormalize();
		
		view.setView(right, up, forward, position);
	}
	
	Vec3 mutableForwardXZ = new Vec3();
	Vec3 mutableRightXZ = new Vec3();
	Vec3 mutableUp = new Vec3();
	public void move(float forwardAmount, float strafeAmount, float verticalAmount, float speed, float dt) {
		move.setXYZ(0,0,0);
 
		if (forwardAmount != 0f) {
			move.mutableAdd(forwardXZ.scale(forwardAmount, mutableForwardXZ));
		}
		
		if (strafeAmount != 0f) {
			move.mutableAdd(rightXZ.scale(strafeAmount, mutableRightXZ));
		}

		if (verticalAmount != 0f) {
			move.mutableAdd(worldUp.scale(verticalAmount, mutableUp));
		}

		if (move.lengthSquared() > 0) {
		    move.mutableNormalize();
		    move.mutableScale(speed * dt);
		    position.mutableAdd(move);
		    updateBasis();
		}
	}
	
	public Vec3 getForward() {
		return forward;
	}
	
	public Vec3 getForwardXZ() {
		return forwardXZ;
	}
	
	public Vec3 getRightXZ() {
		return rightXZ;
	}
	
	public Vec3 getRight() {
		return right;
	}
	
	public Vec3 getUp() {
		return up;
	}
	
	public Vec3 getWorldUp() {
		return worldUp;
	}
	
	public Mat4 getView() {
		return view;
	}
	
	private float clamp (float v, float min, float max) {
		if (v < min) return min;
		if (v > max) return max;
		return v;
	}

	public String toString () {
		return String.format("Yaw: %3f, Pitch: %3f, Sensitivity: %3f, Position:%s", yaw, pitch, sensitivityRadiansPerPixel, position);
	}
}
