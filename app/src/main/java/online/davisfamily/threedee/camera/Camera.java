package online.davisfamily.threedee.camera;

import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;

public class Camera {
	public Vec3 position;
	public float yaw; // radians
	public float pitch; // radians
	public float sensitivityRadiansPerPixel = 0.002f; // ~0.11 degrees per pixel
	private Vec3 forward, right, worldUp, up, forwardXZ, rightXZ;
	private Mat4 view;
	
	public Camera () {
		this.position = new Vec3(0,0,0);
		this.forward = new Vec3(0,0,-1);
		this.forwardXZ = new Vec3(0,0,-1);
		this.right = new Vec3(1,0,0);
		this.rightXZ = new Vec3(1,0,0);
		this.up = new Vec3(0,1,0);
		this.worldUp = new Vec3(0,1,0);
		this.view = Mat4.identity();
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
		forward.setXYZ(
				(float)Math.sin(yaw) * (float)Math.cos(pitch),
				(float)Math.sin(pitch),
				(float)-Math.cos(yaw) * (float)Math.cos(pitch));
		forwardXZ.setXYZ((float)Math.sin(yaw), 0f, (float)-Math.cos(yaw)).mutableNormalize();
		rightXZ.setXYZ((float)Math.cos(yaw), 0f, (float)Math.sin(yaw)).mutableNormalize();
		right = forward.cross(worldUp).normalize();
		up = right.cross(forward);
		//view.setView(right, up, forward, position);
		view.setView(rightXZ, up, forwardXZ, position);
	}
	
	public void computeBasis() {
		computeForward();
		computeRight();
		computeUp();
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
	
	public Vec3 computeForward() {
		forward.setXYZ(
				(float)Math.sin(yaw) * (float)Math.cos(pitch),
				(float)Math.sin(pitch),
				(float)-Math.cos(yaw) * (float)Math.cos(pitch));
		return forward;
	}

	public Vec3 computeForwardXZ() {
		return forwardXZ.setXYZ((float)Math.sin(yaw), 0f, (float)-Math.cos(yaw)).mutableNormalize();
	}
	
	public Vec3 computeRightXZ() {
		return rightXZ.setXYZ((float)Math.cos(yaw), 0f, (float)Math.sin(yaw)).mutableNormalize();
	}
	
	
	public Vec3 computeRight() {
		right = forward.cross(worldUp).normalize();
		return right;
	}
	
	public Vec3 computeUp() {
		up = right.cross(forward);
		return up;
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
