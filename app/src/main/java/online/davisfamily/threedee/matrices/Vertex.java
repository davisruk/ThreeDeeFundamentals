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

	private static ClippedTriangles ct = new ClippedTriangles();
	private static Vertex[] t1 = new Vertex[3];
	private static Vertex[] t2 = new Vertex[3];
	private static Vertex[] poly = new Vertex[4];
	private static Vertex i01 = new Vertex();
	private static Vertex i12 = new Vertex();
	private static Vertex i20 = new Vertex();

	// clip triangle to near on Z plane
	// empty result == exclude all vertices
	// returns 2 triangles if 2 vertices cross the Z plane 
	public static ClippedTriangles clipTriangleNear(Vertex v0, Vertex v1, Vertex v2, float near) {
		ct.t1 = null; ct.t2 = null;
		int polyCount = 0;

		boolean in0 = Vertex.isInsideNear(v0, near);
		boolean in1 = Vertex.isInsideNear(v1, near);
		boolean in2 = Vertex.isInsideNear(v2, near);

		if (in0) {
			poly[polyCount++] = v0;
		}
		if (in0 != in1) {
			poly[polyCount++] = Vertex.intersectNear(v0, v1, near, i01);
		}

		if (in1) {
			poly[polyCount++] = v1;
		}
		if (in1 != in2) {
			poly[polyCount++] = Vertex.intersectNear(v1, v2, near, i12);
		}

		if (in2) {
			poly[polyCount++] = v2;
		}
		if (in2 != in0) {
			poly[polyCount++] = Vertex.intersectNear(v2, v0, near, i20);
		}

		if (polyCount < 3) {
			return ct;
		}

		t1[0] = poly[0];
		t1[1] = poly[1];
		t1[2] = poly[2];
		ct.t1 = t1;

		if (polyCount == 4) {
			t2[0] = poly[0];
			t2[1] = poly[2];
			t2[2] = poly[3];
			ct.t2 = t2;
		}

		return ct;
	}	
}
