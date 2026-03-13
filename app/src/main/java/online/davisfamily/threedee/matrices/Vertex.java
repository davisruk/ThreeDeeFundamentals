package online.davisfamily.threedee.matrices;

public class Vertex {
	public static class ClippedTriangles {
		public Vertex[] t1 = new Vertex[3];
		public Vertex[] t2 = new Vertex[3];
	}

	public float x, y, z, w;
	
	public Vertex() {};
	public Vertex(Vec4 v){
		x = v.x;
		y = v.y;
		z = v.z;
		w = v.w;
	}
	
	public Vertex(float x, float y, float z, float w){
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}
	
	// determine if this vertex is inside the Z plane
	public static boolean isInsideNear(Vertex v, float near) {
		return v.z < -near;
	}
	
	// return a Vertex clipped to near on Z plane
	public static Vertex intersectNear(Vertex a, Vertex b, float near, Vertex out) {
		float planeZ = -near;
		float t = (planeZ - a.z) / (b.z - a.z);
		out.x = a.x + t * (b.x - a.x);
		out.y = a.y + t * (b.y - a.y);
		out.z = planeZ;
		out.w = a.w + t * (b.w - a.w);
		return out;
	}

	// mutable Objects for clipTriangleNear
	// stops rampant object creation for
	// complex scenes
	private static Vertex[] in = new Vertex[3];
	private static Vertex[] out = new Vertex[3];
	private static Vertex[] verts = new Vertex[3];
	private static ClippedTriangles ct = new ClippedTriangles();
	private static Vertex[] t1 = new Vertex[3];
	private static Vertex[] t2 = new Vertex[3];
	private static Vertex ab = new Vertex();
	private static Vertex ac = new Vertex();
	private static Vertex bc = new Vertex();

	// clip triangle to near on Z plane
	// empty result == exclude all vertices
	// returns 2 triangles if 2 vertices cross the Z plane 
	public static ClippedTriangles clipTriangleNear(Vertex v0, Vertex v1, Vertex v2, float near) {
		in[0] = in[1] = in[2] = out[0] = out[1] = out[2] = null; 
		int inCount = 0, outCount = 0;
		verts[0] = v0; verts[1] = v1; verts[2] = v2;
		for (Vertex v: verts) {
			if (Vertex.isInsideNear(v, near)) in[inCount++] = v;
			else out[outCount++] = v;
		}
		
		ct.t1 = null; ct.t2 = null;
		if (inCount == 0) return ct; // none inside
		if (inCount == 3) {
			t1[0] = v0; t1[1] = v1; t1[2]=v2; // all inside
			ct.t1 = t1;
			return ct;
		}
		
		// 1 vertex inside so clip to ab & ac
		if (inCount == 1) {
			Vertex a = in[0];
			Vertex b = out[0];
			Vertex c = out[1];
			ab = Vertex.intersectNear(a,b, near, ab);
			ac = Vertex.intersectNear(a,c, near, ac);
			t1[0] = a; t1[1] = ab; t1[2]=ac;			
			ct.t1 = t1;
			return ct;
		}

		// 2 vertex inside so clip to ac & bc
		Vertex a = in[0];
		Vertex b = in[1];
		Vertex c = out[0];
		ac = Vertex.intersectNear(a,c, near, ac);
		bc = Vertex.intersectNear(b,c, near, bc);
		t1[0] = a; t1[1] = b; t1[2]=bc;
		t2[0] = a; t2[1] = bc; t2[2]=ac;
		ct.t1 = t1;
		ct.t2 = t2;
		return ct;
	}	
}