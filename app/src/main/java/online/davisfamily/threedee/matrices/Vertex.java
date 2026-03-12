package online.davisfamily.threedee.matrices;

public class Vertex {
	public static class ClippedTriangles {
		public Vertex[] t1;
		public Vertex[] t2;
	}

	public float x, y, z, w;
	
	
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
	public static Vertex intersectNear(Vertex a, Vertex b, float near) {
		float planeZ = -near;
		float t = (planeZ - a.z) / (b.z - a.z);
		return new Vertex(
					a.x + t * (b.x - a.x),
					a.y + t * (b.y - a.y),
					planeZ,
					a.w + t * (b.w - a.w)
				);
	}

	// clip triangle to near on Z plane
	// empty result == exclude all vertices
	// returns 2 triangles if 2 vertices cross the Z plane 
	public static ClippedTriangles clipTriangleNear(Vertex v0, Vertex v1, Vertex v2, float near) {
		Vertex [] in = new Vertex[3];
		Vertex [] out = new Vertex[3];
		int inCount = 0, outCount = 0;
		Vertex [] verts = {v0, v1, v2};
		for (Vertex v: verts) {
			if (Vertex.isInsideNear(v, near)) in[inCount++] = v;
			else out[outCount++] = v;
		}
		
		ClippedTriangles ct = new ClippedTriangles();
		if (inCount == 0) return ct; // none inside
		if (inCount == 3) {
			ct.t1 = new Vertex[] {v0, v1, v2}; // all inside
			return ct;
		}
		
		// 1 vertex inside so clip to ab & ac
		if (inCount == 1) {
			Vertex a = in[0];
			Vertex b = out[0];
			Vertex c = out[1];
			Vertex ab = Vertex.intersectNear(a,b, near);
			Vertex ac = Vertex.intersectNear(a,c, near);
			ct.t1 = new Vertex[] {a, ab, ac};
			return ct;
		}

		// 2 vertex inside so clip to ac & bc
		Vertex a = in[0];
		Vertex b = in[1];
		Vertex c = out[0];
		Vertex ac = Vertex.intersectNear(a,c, near);
		Vertex bc = Vertex.intersectNear(b,c, near);
		ct.t1 = new Vertex[] {a, b, bc};
		ct.t2 = new Vertex[] {a, bc, ac};
		return ct;
	}	
}