package online.davisfamily.threedee.matrices;

// specialised 1x3 matrix class
// not really useful for projection but good for position
public class Vec3 {
	public float x, y, z;
	
	public Vec3(float x, float y, float z){
		this.x = x; this.y = y; this.z = z;
	}

	public Vec3 setXYZ(float x, float y, float z){
		this.x = x; this.y = y; this.z = z;
		return this;
	}
	
	public Vec3 normalize() {
		float mag = (float)Math.sqrt(x*x+y*y+z*z);
		// guard against divide by 0
		if (mag == 0f) {
			return new Vec3(0,0,0);
		}
		float inv = 1f / mag;
		// could do x/mag,y/mag,z/mag but div is more expensive than mult
		// in tight loops 1 div and 3 mults is cheaper than 3 divs
		return new Vec3(x * inv, y * inv, z * inv);
	}
	
	public Vec3 mutableNormalize() {
		float mag = (float)Math.sqrt(x*x+y*y+z*z);
		// guard against divide by 0
		if (mag == 0f) {
			x=0; y=0;z=0;
			return this;
		}
		float inv = 1f / mag;
		// could do x/mag,y/mag,z/mag but div is more expensive than mult
		// in tight loops 1 div and 3 mults is cheaper than 3 divs
		x*=inv;y*=inv;z*=inv;
		return this;
	}
	
	// given 2 vectors a and b the cross product is
	// (ay*bz - by*az, bx*az - ax*bz, ax*by - bx*ay) 
	public Vec3 cross (Vec3 b) {
		float i = y*b.z - b.y*z;
		float j = b.x*z - x*b.z;
		float k = x*b.y - b.x*y;
		return new Vec3(i, j, k);
	}
	
	public Vec3 mutableCross(Vec3 b) {
		float i = y*b.z - b.y*z;
		float j = b.x*z - x*b.z;
		float k = x*b.y - b.x*y;
		x = i; y = j; z = k;
		return this;
	}
	
	public Vec3 add(Vec3 v) {
		return new Vec3(x + v.x, y + v.y, z + v.z);
	}
	
	public Vec3 mutableAdd(Vec3 v) {
		x += v.x; y += v.y; z += v.z;
		return this;
	}
	
	public Vec3 subtract(Vec3 v) {
		return new Vec3(x - v.x, y - v.y, z - v.z);
	}

	public Vec3 mutableSubtract(Vec3 v) {
		x -= v.x; y -= v.y; z -= v.z;
		return this;
	}
	
	public Vec3 scale(float s) {
		return new Vec3(x * s, y*s, z*s);
	}
	
	public Vec3 mutableScale(float s) {
		x *= s; y*=s; z*=s;
		return this;
	}

	public float lengthSquared() {
		return x*x + y*y + z*z;
	}
	
	public float length() {
		return (float)Math.sqrt((double)x*x + y*y + z*z);
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
	
	public String toString() {
		String f = "(%.3f, %.3f, %.3f)";
		return String.format(f, x, y, z); 
	}
	
	public Vec3 mutableMult (Vec3 a) {
		float rx = a.x * x + a.y*y + a.z * z;
		float ry = a.y * x + a.y*y + a.y * z; 
		float rz = a.z * x + a.z*y + a.z * z;
		x = rx; y = ry; z = rz;
		return this;
	}

	public Vec3 immutableMult (Vec3 a) {
		return new Vec3(
				a.x * x + a.y*y + a.z * z, 
				a.y * x + a.y*y + a.y * z,
				a.z * x + a.z*y + a.z * z
		);
	}
	
	public float dot(Vec3 a) {
		return a.x * x + a.y*y + a.z * z;
	}
}
