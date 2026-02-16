package online.davisfamily.threedee.matrices;

// specialised 1x3 matrix class
// not really useful for projection
public class Vec3 {
	public float x, y, z;
	
	public Vec3(float x, float y, float z){
		this.x = x; this.y = y; this.z = z;
	}
	
	public Vec3 add(Vec3 v) {
		return new Vec3(x + v.x, y + v.y, z + v.z);
	}
	
	public Vec3 subtract(Vec3 v) {
		return new Vec3(x - v.x, y - v.y, z - v.z);
	}
	
	public Vec3 scale(float s) {
		return new Vec3(x * s, y*s, z*s);
	}
	
	public static Vec3 rotateY(Vec3 v, double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		
		return new Vec3(
		v.x * c + v.z * s,
		v.y,
		-v.x * s + v.z * c 
		);
	}

	public static Vec3 rotateX(Vec3 v, double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		
		return new Vec3(
		v.x,
		v.y  * c - v.z * s,
		-v.y * s + v.z * c 
		);
	}
	
	public static Vec3 rotateZ(Vec3 v, double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		
		return new Vec3(
		v.x  * c - v.y * s,
		v.x * s + v.y * c,
		v.z
		);
	}

	public int projectX(float x, int maxX, int maxY) {
		float s = Math.min(maxX, maxY) * 0.45f;
		return (int) (x * s + maxX * 0.5f);
	}
	
	public int projectY(float y, int maxX, int maxY) {
		float s = Math.min(maxX, maxY) * 0.45f;
		return (int) (-y * s + maxY * 0.5f); // -y because Y goes down
	}
}
