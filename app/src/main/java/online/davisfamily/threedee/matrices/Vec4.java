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
}
