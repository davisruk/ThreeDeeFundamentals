package online.davisfamily.threedee.camera;

import online.davisfamily.threedee.matrices.Vec3;

public class CameraPosition {
	public Vec3 position;
	public float yaw;
	public float pitch;
	
	public CameraPosition() {
		position = new Vec3(0,0,0);
		yaw = 0f;
		pitch = 0f;
	}

	public CameraPosition(Vec3 position, float yaw, float pitch) {
		this.position = position;
		this.yaw = yaw;
		this.pitch = pitch;
	}
	
	public static CameraPosition aboveRight() {
		return new CameraPosition(new Vec3(5.746f, 3.190f, 4.589f), -0.278f, -0.516f);
	}
	
	public static CameraPosition aboveLeft() {
		return new CameraPosition(new Vec3(-2.535f, 3.190f, 2.305f), 0.818f, -0.554f);
	}

}
