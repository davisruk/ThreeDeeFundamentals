package online.davisfamily.threedee.testing.cube;

import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;

public class Cube {
	// -- 3D models  ------
	public Vec4[] v4Vertices = {
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

	// these should not be here - this should b structure only
	// the renderable model should specify the colours
	public int[] faceColours = {
	    0xFFFF0000, // bottom - red
	    0xFF00FF00, // top - green
	    0xFF0000FF, // front - blue
	    0xFFFFFF00, // right - yellow
	    0xFFFF00FF, // back - magenta
	    0xFF00FFFF  // left - cyan
	};
	
	// must make sure these are CCW 
	public int[][] triangles = {
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
}
