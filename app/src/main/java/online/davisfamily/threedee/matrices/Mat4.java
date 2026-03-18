package online.davisfamily.threedee.matrices;

public class Mat4 {
	
	public static class ObjectTransformation {
		public float angleX, angleY, angleZ, xTranslation, yTranslation, zTranslation;
		public Mat4 model;

		public ObjectTransformation(float xAngle, float yAngle, float zAngle, float xTrans, float yTrans, float zTrans) {
			this.angleX = xAngle;
			this.angleY = yAngle;
			this.angleZ = zAngle;
			this.xTranslation = xTrans;
			this.yTranslation = yTrans;
			this.zTranslation = zTrans;
		}
		
		public ObjectTransformation(float xAngle, float yAngle, float zAngle, float xTrans, float yTrans, float zTrans, Mat4 model) {
			this(xAngle, yAngle,zAngle, xTrans, yTrans, zTrans);
			this.model = model;
		}
		
		public void setupModel() {
			model.setModel(this);
		}
		
		public void setTranslation(Vec3 t) {
			xTranslation = t.x;
			yTranslation = t.y;
			zTranslation = t.z;
		}
	}
	
	// flat array for storing 4x4 matrix
	public float m[] = new float[16];
	
// ---------------------- Static Immutable Methods -----------------------
// -- These methods are expensive if using lots of matrix calculations  --
// -- Use the mutable equivalents to reduce GC and alloc stutter issues --
// -----------------------------------------------------------------------	

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
		// could start with identity but we only need the bottom right(15) setting to 1
		Mat4 r = new Mat4();
		r.m[0] = cy*cz + sy*sx*sz;
		r.m[1] = -cy*sz + sy*sx*cz;
		r.m[2] = sy*cx;
		
		r.m[4] = cx*sz;
		r.m[5] = cx*cz;
		r.m[6] = -sx;
		
		r.m[8] = -sy*cz + cy*sx*sz;
		r.m[9] = sy*sz + cy*sx*cz;
		r.m[10] = cy * cx;
		
		r.m[15] = 1;
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
	
// -------------------- End of Static Immutable Methods ---------------------
	
	public Mat4 set(Mat4 s) {
		for (int i = 0; i<m.length; i++) m[i] = s.m[i];
		return this;
	}
	
	public Mat4 setIdentity() {
		m[0] = 1; m[1] = 0; m[2] = 0; m[3] = 0;
		m[4] = 0; m[5] = 1; m[6] = 0; m[7] = 0;
		m[8] = 0; m[9] = 0; m[10]= 1; m[11]= 0;		
		m[12]= 0; m[13]= 0; m[14]= 0; m[15]= 1;
		return this;		
	}

	/*
		Translation Matrix
 		[1,0,0,tx,
 	 	 0,1,0,ty,
 	 	 0,0,1,tz,
 	 	 0,0,0,1]
	 */
	public Mat4 setTranslation(float tx, float ty, float tz) {
		// identity entries that won't be overwritten
		// translation entries
		m[0] = 1; m[1] = 0; m[2] = 0; m[3] = tx;
		m[4] = 0; m[5] = 1; m[6] = 0; m[7] = ty;
		m[8] = 0; m[9] = 0; m[10]= 1; m[11]= tz;		
		m[12]= 0; m[13]= 0; m[14]= 0; m[15]= 1;
		return this;		
	}

	/*
		Rotation X  matrix
		[1     ,0   ,  0,0,
	 	 cosine,-sin,  0,0,
	 	 sin   ,cosine,0,0,
	 	 0     ,0     ,0,1]
	 */	
	public Mat4 setRotationX (double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		// rotation entries
		m[0] = 1; m[1] = 0; m[2] =  0; m[3] = 0;
		m[4] = 0; m[5] = c; m[6] = -s; m[7] = 0; 
		m[8] = 0; m[9] = s;	m[10]=  c; m[11]= 0;
		m[12]= 0; m[13]= 0; m[14]=  0; m[15]= 1;
		return this;
	}

	/*
		Rotation Y  matrix
		[cosine,0,sin   ,0,
		 0     ,1,0     ,0,
		 -sin  ,0,cosine,0,
		 0     ,0,0     ,1]
	 */	
	public Mat4 setRotationY (double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		// identity entries that won't be overwritten
		m[1] = m[3] = m[4] = m[6] = m[7] = m[9] = m[11] = m[12] = m[13] = m[14] = 0;
		m[5] = m[15] = 1;	
		// rotation entries
		m[0] = c;
		m[2] = s;
		m[8] = -s;
		m[10] = c;
		return this;
	}
	
	/*
		Rotation Z  matrix
		[cosine,-sin  ,0,0,
		 sin   ,cosine,0,0,
		 -sin  ,0     ,1,0,
		 0     ,0     ,0,1]
	 */
	public Mat4 setRotationZ (double angle) {
		float c = (float)Math.cos(angle);
		float s = (float)Math.sin(angle);
		// rotation entries
		m[0] = c; m[1] = -s; m[2] = 0; m[3] = 0;
		m[4] = s; m[5] = c;  m[6] = 0; m[7] = 0;
		m[8] = 0; m[9] = 0;  m[10]= 1; m[11]= 0;
		m[12]= 0; m[13]= 0;  m[14]= 0; m[15]= 1;
		return this;
	}

	public Mat4 setRotationYXZ (double ay, double ax, double az) {
		float cx = (float)Math.cos(ax);
		float sx = (float)Math.sin(ax);
		float cy = (float)Math.cos(ay);
		float sy = (float)Math.sin(ay);
		float cz = (float)Math.cos(az);
		float sz = (float)Math.sin(az);

		// Y rotation
		m[0] = cy*cz + sy*sx*sz; m[1] = -cy*sz + sy*sx*cz; m[2] = sy*cx; m[3] = 0;
		// X rotation
		m[4] = cx*sz; m[5] = cx*cz; m[6] = -sx; m[7] = 0;
		//Z rotation
		m[8] = -sy*cz + cy*sx*sz; m[9] = sy*sz + cy*sx*cz; m[10] = cy * cx; m[11] = 0;
		
		// remaining identity entries
		m[12] = 0; m[13] = 0;m[14] = 0; m[15] = 1;
		return this;
	}
	
	// utility that sets rotation YXZ and translation xyz
	// combines individual rotation matrices and the translation matrix
	public Mat4 setModel (ObjectTransformation t) {
		float cx = (float)Math.cos(t.angleX);
		float sx = (float)Math.sin(t.angleX);
		float cy = (float)Math.cos(t.angleY);
		float sy = (float)Math.sin(t.angleY);
		float cz = (float)Math.cos(t.angleZ);
		float sz = (float)Math.sin(t.angleZ);

		// Y rotation and x translation
		m[0] = cy*cz + sy*sx*sz; m[1] = -cy*sz + sy*sx*cz; m[2] = sy*cx; m[3] = t.xTranslation;
		// X rotation and y translation
		m[4] = cx*sz; m[5] = cx*cz; m[6] = -sx; m[7] = t.yTranslation;
		//Z rotation and z translation
		m[8] = -sy*cz + cy*sx*sz; m[9] = sy*sz + cy*sx*cz; m[10] = cy * cx; m[11] = t.zTranslation;
		
		// remaining identity entries
		m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
		return this;
		
	}
	
	public Mat4 setView (Vec3 right, Vec3 up, Vec3 forward, Vec3 pos) {
		m[0] = right.x; m[1] = right.y; m[2] = right.z; m[3] = -right.dot(pos);
		m[4] = up.x; m[5] = up.y; m[6] = up.z; m[7] = -up.dot(pos);
		m[8] = -forward.x; m[9] = -forward.y; m[10] = -forward.z; m[11] = forward.dot(pos);
		m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
		return this;
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
	
	// mutable multiply that returns a Vertex rather than Vec4
	public Vertex multiplyVec (Vec4 b, Vertex out) {
		out.x = m[0] * b.x + m[1]*b.y + m[2] * b.z + m[3] * b.w; 
		out.y = m[4] * b.x + m[5]*b.y + m[6] * b.z + m[7] * b.w;
		out.z = m[8] * b.x + m[9]*b.y + m[10] * b.z + m[11] * b.w;
		out.w = m[12] * b.x + m[13]*b.y + m[14] * b.z + m[15] * b.w;
		return out;
	}

	// mutable multiply that returns a Vertex rather than Vec4
	public Vec4 multiplyVec (Vertex b, Vec4 out) {
		out.x = m[0] * b.x + m[1]*b.y + m[2] * b.z + m[3] * b.w; 
		out.y = m[4] * b.x + m[5]*b.y + m[6] * b.z + m[7] * b.w;
		out.z = m[8] * b.x + m[9]*b.y + m[10] * b.z + m[11] * b.w;
		out.w = m[12] * b.x + m[13]*b.y + m[14] * b.z + m[15] * b.w;
		return out;
	}
	
	// multiply this matrix by 4 point vector b and return result in b
	// each element of result is dot product of the ith row in this(m) and the jth column of b 
	// see Vec4 multiplyMat method for same thing
	public Vec4 multiplyVecNoAlloc (Vec4 b) {
		float rx = m[0] * b.x + m[1]*b.y + m[2] * b.z + m[3] * b.w;
		float ry = m[4] * b.x + m[5]*b.y + m[6] * b.z + m[7] * b.w;
		float rz = m[8] * b.x + m[9]*b.y + m[10] * b.z + m[11] * b.w;
		float rw = m[12] * b.x + m[13]*b.y + m[14] * b.z + m[15] * b.w;
		b.x = rx; b.y = ry; b.z = rz; b.w = rw;
		return b;
	}
	
	// multiplication of this by another matrix - expensive with lots of calculations
	// each element of result is dot product of the ith row in this(m) and the jth column of b (right hand side)
	// e.g. r.x = (a.x * b.x) + (a.y * b.y) + (a.z * b.z) + (a.w * b.w)
	public Mat4 immutableMultiplyMatrix (Mat4 b) {
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
	
	public Mat4 setPerspective (float fovY, float aspect, float near, float far) {
		float f = 1.0f / (float)Math.tan(fovY / 2.0f);
		m[0] = f / aspect; m[1] = 0; m[2] = 0; m[3] = 0;
		m[4] = 0; m[5] = f; m[6] = 0; m[7] = 0;
		m[8] = 0; m[9] = 0; m[10] = (far + near) / (near - far); m[11] = (2f * far * near) / (near - far);
		m[12] = 0; m[13] = 0; m[14] = -1f; m[15] = 0;
		return this;
	}

	// multiply this by another matrix
	// each element of result is dot product of the ith row in this(m) and the jth column of b (right hand side)
	// e.g. r.x = (a.x * b.x) + (a.y * b.y) + (a.z * b.z) + (a.w * b.w)
	public Mat4 mutableMultiply (Mat4 b) {
		if (this == b) throw new IllegalArgumentException ("Self multiplication not supported, use multiplyMatrix instead");
		for (int row = 0, rowOffset = 0; row < 4; row++, rowOffset += 4) {
			float c1 = m[rowOffset];
			float c2 = m[rowOffset + 1];
			float c3 = m[rowOffset + 2];
			float c4 = m[rowOffset + 3];
			for (int col = 0; col < 4; col++) {
				m[rowOffset + col] =
						c1 * b.m[col] +
						c2 * b.m[col+4] +
						c3 * b.m[col+8] + 
						c4 * b.m[col+12];
			}
		}
		
		return this;
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
