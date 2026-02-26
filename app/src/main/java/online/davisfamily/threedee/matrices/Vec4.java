package online.davisfamily.threedee.matrices;

public class Vec4 {
	public float x, y, z, w;
	
	public Vec4(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = 1.0f;
	}
	
	public Vec4(float x, float y, float z, float w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}
	
	public String toString() {
		String f = "(%.3f, %.3f, %.3f, %.3f)";
		return String.format(f, x, y, z, w); 
	}
	
	public Vec4 multiplyMat (Mat4 a) {
		float rx = a.m[0] * x + a.m[1]*y + a.m[2] * z + a.m[3] * w;
		float ry = a.m[4] * x + a.m[5]*y + a.m[6] * z + a.m[7] * w; 
		float rz = a.m[8] * x + a.m[9]*y + a.m[10] * z + a.m[11] * w;
		float rw = a.m[12] * x + a.m[13]*y + a.m[14] * z + a.m[15] * w;
		x = rx; y = ry; z = rz; w = rw;
		return this;
	}
}
