package online.davisfamily.threedee.matrices;

public class Mat4 {
	// flat array for storing 4x4 matrix
	public float m[] = new float[16];
	
	/*
		translation matrix
	 	[1,0,0,tx,
	 	 0,1,0,ty,
	 	 0,0,1,tz,
	 	 0,0,0,1]
	 */
	public static Mat4 translation (float tx, float ty, float tz) {
		Mat4 t = new Mat4();
		t.m[0] = 1; t.m[5] = 1; t.m[10] = 1; t.m[15] = 1;
		t.m[3] = tx; t.m[7] = ty; t.m[11] = tz;
		
		return t;
	}
	
	/*
		identity matrix
 		[1,0,0,0,
 	 	 0,1,0,0,
 	 	 0,0,1,0,
 	 	 0,0,0,1]
	 */

	public static Mat4 identity () {
		Mat4 i = new Mat4();
		i.m[0] = 1; i.m[5] = 1; i.m[10] = 1; i.m[15] = 1;
		return i;
	}

	public static Mat4 rotationX (double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		Mat4 r = Mat4.identity();
		r.m[5] = c;
		r.m[6] = -s;
		r.m[9] = s;
		r.m[10] = c;
		return r;
	}

	public static Mat4 rotationY (double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		Mat4 r = Mat4.identity();
		r.m[0] = c;
		r.m[2] = s;
		r.m[8] = -s;
		r.m[10] = c;
		return r;
	}
	
	public static Mat4 rotationZ (double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		Mat4 r = Mat4.identity();
		r.m[0] = c;
		r.m[1] = -s;
		r.m[4] = s;
		r.m[5] = c;
		return r;
	}
	
	public static Mat4 rotationYXZ (double ay, double ax, double az) {
		float cx = (float)Math.cos(ax);
		float sx = (float)Math.sin(ax);
		float cy = (float)Math.cos(ay);
		float sy = (float)Math.sin(ay);
		float cz = (float)Math.cos(az);
		float sz = (float)Math.sin(az);
		Mat4 r = Mat4.identity();
		r.m[0] = cy*cz + sy*sx*sz;
		r.m[1] = -cy*sz + sy*sx*cz;
		r.m[2] = sy*cx;
		
		r.m[4] = cx*sz;
		r.m[5] = cx*cz;
		r.m[6] = -sx;
		
		r.m[8] = -sy*cz + cy*sx*sz;
		r.m[9] = sy*sz + cy*sx*cz;
		r.m[10] = cy * cx;
		return r;
		
	}

	public static Mat4 perspective (float fovY, float aspect, float near, float far) {
		float f = 1.0f / (float)Math.tan(fovY / 2.0f);
		Mat4 p = new Mat4();
		p.m[0] = f / aspect;
		p.m[5] = f;
		p.m[10] = (far + near) / (near - far);
		p.m[11] = (2f * far * near) / (near - far);
		p.m[14] = -1f;
		return p;
	}
	
	// multiply this by 4 point vector by matrix
	// each element of result is dot product of the ith row in this(m) and the jth column of b 
	public Vec4 multiplyVec (Vec4 b) {
		return new Vec4(
			m[0] * b.x + m[1]*b.y + m[2] * b.z + m[3] * b.w,
			m[4] * b.x + m[5]*b.y + m[6] * b.z + m[7] * b.w,
			m[8] * b.x + m[9]*b.y + m[10] * b.z + m[11] * b.w,
			m[12] * b.x + m[13]*b.y + m[14] * b.z + m[15] * b.w
		);
	}
	
	// multiply this by another matrix
	// each element of result is dot product of the ith row in this(m) and the jth column of b (right hand side)
	// e.g. r.x = (a.x * b.x) + (a.y * b.y) + (a.z * b.z) + (a.w * b.w)
	public Mat4 multiplyMatrix (Mat4 b) {
		Mat4 r = new Mat4();
		for (int row = 0, rowOffset = 0; row < 4; row++, rowOffset += 4) {
			for (int col = 0; col < 4; col++) {
				r.m[rowOffset + col] =
						m[rowOffset] * b.m[col] +
						m[rowOffset+1] * b.m[col+4] +
						m[rowOffset+2] * b.m[col+8] + 
						m[rowOffset+3] * b.m[col+12];
			}
		}
		
		return r;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer("[\r\n");
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				buf.append(" " + String.format("%.3f",  m[row*4+col]));
				if (col < 3) buf.append (",");
			}
			if (row < 4) buf.append("\r\n");			
		}
		buf.append("]");
		return buf.toString();
	}
}
