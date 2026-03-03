package online.davisfamily.threedee.camera;

import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Vec3;

public class Camera {
	public Vec3 position;
	public float yaw; // radians
	public float pitch; // radians
	public float sensitivityRadiansPerPixel = 0.002f; // ~0.11 degrees per pixel
	
	private float clamp (float v, float min, float max) {
		if (v < min) return min;
		if (v > max) return max;
		return v;
	}
	public Camera () {
		this.position = new Vec3(0,0,0);
	}
	
	public Camera (Vec3 position) {
		this.position = position;
	}
	
	public void setSensitivity(float sensitivityRadiansPerPixel) {
		this.sensitivityRadiansPerPixel = sensitivityRadiansPerPixel;
	}
	
	public void mouseUpdate(MouseEventDetail md) {
		//yaw += (md.x - md.oldx) * sensitivityRadiansPerPixel;
		//pitch -= (md.y - md.oldy) * sensitivityRadiansPerPixel;
		yaw+=md.dx * sensitivityRadiansPerPixel;
		pitch-=md.dy * sensitivityRadiansPerPixel;
		pitch = clamp(pitch, -1.5533f, 1.5533f); // +-89 degrees in radians
	}
	
	public String toString () {
		return String.format("Yaw: %3f, Pitch: %3f, Sensitivity: %3f, Position:%s", yaw, pitch, sensitivityRadiansPerPixel, position);
	}
}
