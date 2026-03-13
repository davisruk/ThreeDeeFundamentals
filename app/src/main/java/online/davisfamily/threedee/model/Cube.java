package online.davisfamily.threedee.model;

import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;
import online.davisfamily.threedee.triangles.TriangleRenderer;

public class Cube {
	// -- 3D models  ------
	public Vec4[] v4CubeVertices = {
		// bottom square
		new Vec4 (-0.5f, -0.5f, -0.5f),
		new Vec4 (-0.5f, -0.5f, 0.5f),
		new Vec4 (0.5f, -0.5f, 0.5f),
		new Vec4 (0.5f, -0.5f, -0.5f),
		// top square
		new Vec4 (-0.5f, 0.5f, -0.5f),
		new Vec4 (-0.5f, 0.5f, 0.5f),
		new Vec4 (0.5f, 0.5f, 0.5f),
		new Vec4 (0.5f, 0.5f, -0.5f),
	};

	public int[][] cubeEdges = {
		{0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom square edges
		{4, 5}, {5, 6}, {6, 7}, {7, 4}, // top square edges
		{0, 4}, {1, 5}, // left vertical edges
		{2, 6}, {3, 7}, // right vertical edges
	};	

	public int[] cubeFaceColours = {
	    0xFFFF0000, // bottom - red
	    0xFF00FF00, // top - green
	    0xFF0000FF, // front - blue
	    0xFFFFFF00, // right - yellow
	    0xFFFF00FF, // back - magenta
	    0xFF00FFFF  // left - cyan
	};
	
	// must make sure these are CCW 
	public int[][] cubeTriangles = {
	    // bottom (y = -0.5) outward normal -Y
	    {0, 2, 1}, {0, 3, 2},

	    // top (y = +0.5) outward normal +Y
	    {4, 5, 6}, {4, 6, 7},

	    // front (z = +0.5) outward normal +Z
	    {1, 2, 6}, {1, 6, 5},

	    // right (x = +0.5) outward normal +X
	    {2, 3, 7}, {2, 7, 6},

	    // back (z = -0.5) outward normal -Z
	    {0, 4, 7}, {0, 7, 3},

	    // left (x = -0.5) outward normal -X
	    {0, 1, 5}, {0, 5, 4},
	};
	
	Vertex[] viewVerts;
	
	public Cube() {
		viewVerts = new Vertex[v4CubeVertices.length];
	}
	
	public void draw (TriangleRenderer tr, Camera cam, Mat4 model, Mat4 perspective, float[] zBuff) {
		Mat4 mv = new Mat4();
		mv.set(cam.getView());
		mv.mutableMultiply(model);
		for(int v=0; v<v4CubeVertices.length;v++) {
			viewVerts[v] = new Vertex(mv.multiplyVec(v4CubeVertices[v]));
		}
		
		for (int i=0; i<cubeTriangles.length;i++) {
			int[] t = cubeTriangles[i];
			Vertex v0 = viewVerts[t[0]];
			Vertex v1 = viewVerts[t[1]];
			Vertex v2 = viewVerts[t[2]];
			Vertex.ClippedTriangles ct =  Vertex.clipTriangleNear(v0,v1,v2,0.1f);
			if (ct.t1 != null) tr.drawProjectedTriangle(perspective, ct.t1[0], ct.t1[1], ct.t1[2], cubeFaceColours[i/2], zBuff);
			if (ct.t2 != null) tr.drawProjectedTriangle(perspective, ct.t2[0], ct.t2[1], ct.t2[2], cubeFaceColours[i/2], zBuff);
		}
	}	
}
