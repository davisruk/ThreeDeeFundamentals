package online.davisfamily.threedee.model;

import online.davisfamily.threedee.matrices.Vec4;

public class Tote {
	float s = 0.001f;

	// outer top from published external dimensions
	float outerTopWidth  = 400f * s;
	float outerTopDepth  = 600f * s;
	float outerHeight    = 310f * s;

	// inner top from published internal dimensions
	float innerTopWidth  = 371f * s;
	float innerTopDepth  = 547f * s;
	float innerHeight    = 290f * s;

	// inferred bottom dimensions
	float outerBottomWidth = 320f * s;
	float outerBottomDepth = 500f * s;

	// derived from same wall offsets as top
	float innerBottomWidth = 291f * s;
	float innerBottomDepth = 447f * s;

	float obhw = outerBottomWidth / 2f;
	float obhd = outerBottomDepth / 2f;

	float othw = outerTopWidth / 2f;
	float othd = outerTopDepth / 2f;

	float ibhw = innerBottomWidth / 2f;
	float ibhd = innerBottomDepth / 2f;

	float ithw = innerTopWidth / 2f;
	float ithd = innerTopDepth / 2f;

	public Vec4[] v4Vertices = new Vec4[] {
	    // -------------------------------------------------
	    // OUTER BOTTOM (Y = 0)
	    // -------------------------------------------------
	    new Vec4(-obhw, 0.0f, -obhd, 1.0f), // 0 back-left
	    new Vec4( obhw, 0.0f, -obhd, 1.0f), // 1 back-right
	    new Vec4( obhw, 0.0f,  obhd, 1.0f), // 2 front-right
	    new Vec4(-obhw, 0.0f,  obhd, 1.0f), // 3 front-left

	    // -------------------------------------------------
	    // OUTER TOP (Y = outerHeight)
	    // -------------------------------------------------
	    new Vec4(-othw, outerHeight, -othd, 1.0f), // 4 back-left
	    new Vec4( othw, outerHeight, -othd, 1.0f), // 5 back-right
	    new Vec4( othw, outerHeight,  othd, 1.0f), // 6 front-right
	    new Vec4(-othw, outerHeight,  othd, 1.0f), // 7 front-left

	    // -------------------------------------------------
	    // INNER BOTTOM (Y = outerHeight - innerHeight)
	    // = floor thickness above outer base
	    // -------------------------------------------------
	    new Vec4(-ibhw, outerHeight - innerHeight, -ibhd, 1.0f), // 8  back-left
	    new Vec4( ibhw, outerHeight - innerHeight, -ibhd, 1.0f), // 9  back-right
	    new Vec4( ibhw, outerHeight - innerHeight,  ibhd, 1.0f), // 10 front-right
	    new Vec4(-ibhw, outerHeight - innerHeight,  ibhd, 1.0f), // 11 front-left

	    // -------------------------------------------------
	    // INNER TOP (Y = outerHeight)
	    // -------------------------------------------------
	    new Vec4(-ithw, outerHeight, -ithd, 1.0f), // 12 back-left
	    new Vec4( ithw, outerHeight, -ithd, 1.0f), // 13 back-right
	    new Vec4( ithw, outerHeight,  ithd, 1.0f), // 14 front-right
	    new Vec4(-ithw, outerHeight,  ithd, 1.0f)  // 15 front-left
	};
	
	public int[][] triangles = new int[][] {
	    // =========================================================
	    // OUTER WALLS
	    // =========================================================

	    // front (+z)
	    {3, 2, 6},
	    {3, 6, 7},

	    // back (-z)
	    {1, 0, 4},
	    {1, 4, 5},

	    // left (-x)
	    {0, 3, 7},
	    {0, 7, 4},

	    // right (+x)
	    {2, 1, 5},
	    {2, 5, 6},

	    // =========================================================
	    // INNER WALLS
	    // normals point into the cavity
	    // =========================================================

	    // inner front
	    {10, 11, 15},
	    {10, 15, 14},

	    // inner back
	    {8, 9, 13},
	    {8, 13, 12},

	    // inner left
	    {11, 8, 12},
	    {11, 12, 15},

	    // inner right
	    {9, 10, 14},
	    {9, 14, 13},

	    // =========================================================
	    // TOP RIM
	    // =========================================================

	    // front rim
	    {7, 6, 14},
	    {7, 14, 15},

	    // back rim
	    {4, 12, 13},
	    {4, 13, 5},

	    // left rim
	    {7, 15, 12},
	    {7, 12, 4},

	    // right rim
	    {6, 5, 13},
	    {6, 13, 14},

	    // =========================================================
	    // BOTTOM SURFACES
	    // =========================================================

	    // outer underside
	    {0, 1, 2},
	    {0, 2, 3},

	    // inner floor top
	    {8, 11, 10},
	    {8, 10, 9}
	};
}
