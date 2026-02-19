package online.davisfamily.threedee.testing;

import online.davisfamily.threedee.Scene;
import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.triangles.TriangleRenderer;

public class TestScene implements Scene {

	private ViewDimensions vd;
	private CohenSutherlandLineClipper clipper;
	private BresenhamLineUtilities bl;
	private TriangleRenderer tr;
	int[] pixels;
	float[] db;

	public TestScene (ViewDimensions dimensions, int[] pixels, float[] depthBuffer, CohenSutherlandLineClipper clipper, BresenhamLineUtilities bresenhamUtils, TriangleRenderer triangleRenderer) {
		this.vd = dimensions;
		this.clipper = clipper;
		this.bl = bresenhamUtils;
		this.tr = triangleRenderer;
		this.pixels = pixels;
		this.db = depthBuffer;
		
		// testing vars
		this.steps = 270;
		this.step = 1;
		this.stepInc = 1;
		this.t = step / (float) steps;
		this.s = 1.0f - t;
		this.cx = vd.width/2;
		this.cy = vd.height/2;
		this.halfW = Math.round((vd.width / 2f) * s);
		this.halfH = Math.round((vd.height / 2f) * s);
		this.angleX = 0.4f;
		this.angleY = 0.6f;
		
	}

	// -- Testing variables --
	// these should be moved out to a test class

	// -- viewport testing variables ------
		private int steps, cx, cy, halfW, halfH, stepInc;
		private float t, s, step;
	// ------------------------------------

	// -- 3D models  ------

	// cube structure
	Vec3[] v3CubeVertices = {
			// bottom square
			new Vec3 (-0.5f, -0.5f, -0.5f),
			new Vec3 (-0.5f, -0.5f, 0.5f),
			new Vec3 (0.5f, -0.5f, 0.5f),
			new Vec3 (0.5f, -0.5f, -0.5f),
			// top square
			new Vec3 (-0.5f, 0.5f, -0.5f),
			new Vec3 (-0.5f, 0.5f, 0.5f),
			new Vec3 (0.5f, 0.5f, 0.5f),
			new Vec3 (0.5f, 0.5f, -0.5f),
		};
		
	Vec4[] v4CubeVertices = {
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

		int[][] cubeEdges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom square edges
				{4, 5}, {5, 6}, {6, 7}, {7, 4}, // top square edges
				{0, 4}, {1, 5}, // left vertical edges
				{2, 6}, {3, 7}, // right vertical edges
		};	
	
		int[] cubeFaceColours = {
			    0xFFFF0000, // bottom - red
			    0xFF00FF00, // top - green
			    0xFF0000FF, // front - blue
			    0xFFFFFF00, // right - yellow
			    0xFFFF00FF, // back - magenta
			    0xFF00FFFF  // left - cyan
		};
		
		// must make sure these are CCW 
		int[][] cubeTriangles = {
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
		
	private float angleX, angleY;

	private void clear (int argb) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
	}
	
	
	private void testLineDrawTriangle() {
		int colour = 0xFFFFCC00;
		clear(0xFF101018);
		
		int dY = vd.vpMaxYExclusive - vd.vpMinY;
		int dX = vd.vpMaxXExclusive - vd.vpMinX;
		 
		// triangle with no clip
		bl.drawTriangle (vd.vpMinX, vd.vpMinY, vd.vpMinX, vd.vpMinY + (dY / 2), vd.vpMinX + (dX / 2), vd.vpMinY + (dY / 2), colour);	
	}
	
	
	private void testTriangleClipping() {
		generalFillTriangleTests();
		
		if (step == steps && stepInc == 1) {
			stepInc = -1;
		}
		
		if (step == 0 && stepInc == -1) {
			stepInc = 1;
		}
		
		this.step+=(float)stepInc;
		this.t = step / (float) steps;
		this.s = 1.0f - t;
		this.halfW = Math.round((vd.width / 2f) * s);
		this.halfH = Math.round((vd.height / 2f) * s);
		vd.vpMinX = cx - halfW;
		vd.vpMaxXExclusive = cx + halfW - 1;
		vd.vpMinY = cy - halfH;
		vd.vpMaxYExclusive = cy + halfH - 1;

	}
	
	private void generalFillTriangleTests() {
		this.clear(0xFF000000);

		// Viewport: 960x540
		// Non-overlapping triangles, covering: general, flat-top, flat-bottom, vertical edge (flat left/right), skinny, steep, etc.

		// 1) General (no flat edges, mixed slopes)
		tr.fillTriangle(80, 80, 220, 140, 120, 260, 0xFFFF0000);   // red

		// 2) Flat-top (two vertices share the same Y)
		tr.fillTriangle(320, 90, 440, 90, 380, 220, 0xFF00FF00);   // green

		// 3) Flat-bottom (two vertices share the same Y)
		tr.fillTriangle(560, 80, 500, 240, 620, 240, 0xFF0000FF);  // blue

		// 4) Left edge vertical (flat left / constant X on one edge)
		tr.fillTriangle(90, 320, 90, 460, 220, 400, 0xFFFFFF00);   // yellow

		// 5) Right edge vertical (flat right / constant X on one edge)
		tr.fillTriangle(320, 320, 460, 380, 460, 500, 0xFFFF00FF); // magenta

		// 6) Skinny / acute (very narrow triangle)
		tr.fillTriangle(620, 320, 628, 500, 700, 420, 0xFF00FFFF); // cyan

		// 7) Steep long edge (one edge with large dy, small dx)
		tr.fillTriangle(780, 80, 820, 500, 900, 200, 0xFFFF8000);  // orange

		// 8) Near-horizontal long edge (shallow slope, tests precision)
		tr.fillTriangle(740, 360, 920, 390, 760, 510, 0xFF8000FF); // purple

		// 9) “Flat-ish” left side via two close X values (stress rounding)
		tr.fillTriangle(520, 320, 540, 520, 560, 350, 0xFF80FF80); // light green

		// 10) Inverted orientation (points in arbitrary order; should still work after sort)
		tr.fillTriangle(220, 520, 140, 380, 300, 420, 0xFF0080FF); // sky blue
		
	}
	
	private void stressEdgeCrossing() {

		// Viewport: 960x540
		// These are designed to stress: edge crossing within a half, very steep slopes,
		// very shallow slopes, extreme flat-top/flat-bottom, and “near-degenerate but visible”.
		// They’re placed in separate regions so they shouldn’t overlap.

		// -----------------------------
		// A) Edge-crossing within a half
		// -----------------------------
		// A1: Edges cross in the TOP half (AB goes right, AC goes left)
		tr.fillTriangle(200, 60, 320, 220, 80, 260, 0xFFB71C1C);   // deep red

		// A2: Edges cross in the BOTTOM half (BC goes left, AC goes right)
		tr.fillTriangle(520, 60, 420, 260, 640, 220, 0xFF1B5E20);  // deep green

		// -----------------------------
		// B) Very steep slopes
		// -----------------------------
		// B1: Long, steep edge (tiny dx over big dy)
		tr.fillTriangle(80, 300, 95, 520, 240, 360, 0xFF0D47A1);   // deep blue

		// B2: Another steep case, opposite direction
		tr.fillTriangle(320, 300, 305, 520, 460, 420, 0xFF4A148C); // deep purple

		// -----------------------------
		// C) Very shallow slopes
		// -----------------------------
		// C1: Long shallow top edge + tall height
		tr.fillTriangle(520, 300, 860, 318, 620, 520, 0xFFF57F17); // amber

		// C2: Shallow-ish long edge but different orientation/order
		tr.fillTriangle(700, 340, 920, 360, 760, 520, 0xFF006064); // teal

		// -----------------------------
		// D) Extreme flat-top / flat-bottom
		// -----------------------------
		// D1: Very wide flat-top
		tr.fillTriangle(60, 280, 280, 280, 160, 520, 0xFF263238);  // blue grey

		// D2: Very wide flat-bottom
		tr.fillTriangle(520, 280, 620, 520, 900, 520, 0xFF3E2723); // brown

		// -----------------------------
		// E) Near-degenerate but still visible (tests rounding + precision)
		// -----------------------------
		// E1: Skinny but not a line; short top half then long bottom half
		tr.fillTriangle(320, 60, 360, 120, 340, 520, 0xFF1565C0);  // mid blue

		// E2: Skinny, steep, and “tilted” the other way
		tr.fillTriangle(900, 60, 860, 120, 880, 520, 0xFF2E7D32);  // mid green

	}
	
	private void testWireframeCube() {
		this.clear(0xFF000000);
		Vec3[] t = new Vec3[8];
		this.angleX += 0.01f;
		this.angleY += 0.01f;
		for (int i = 0; i < 8; i++) {
			Vec3 v = v3CubeVertices[i];
			v = Vec3.rotateY(v, this.angleY);
			v = Vec3.rotateX(v, this.angleX);
			v = v.add(new Vec3(0,0,2));
			t[i] = v;
		}
		bl.drawCube(t, cubeEdges, 0xFF2E7D32);
	}
	
// -- Matrix tests
	private final float twoPI = (float)Math.PI * 2;
	private float zTranslation = -1;
	private float zTranslationInc = -0.05f;
	
	private void testWireframeCubeWithMatrices() {
		this.clear(0xFF000000);

		bl.drawCube(v4CubeVertices, cubeEdges, this.angleY, this.angleX, this.zTranslation, 0xFF2E7D32);
		this.angleX += 0.01f;
		this.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		this.angleX %= twoPI;
		this.angleY %= twoPI;
		if (this.zTranslation < -10) {
			this.zTranslationInc = 0.05f;
		} else if (this.zTranslation > -1) {
			this.zTranslationInc = -0.05f;
		}
 
		this.zTranslation += this.zTranslationInc;

	}

	private void testFilledCubeNoZBuffer() {
		this.clear(0xFF000000);

		tr.drawCube(v4CubeVertices, cubeTriangles, this.angleY, this.angleX, this.zTranslation, cubeFaceColours);
		this.angleX += 0.01f;
		this.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		this.angleX %= twoPI;
		this.angleY %= twoPI;
		if (this.zTranslation < -10) {
			this.zTranslationInc = 0.05f;
		} else if (this.zTranslation > -3) {
			this.zTranslationInc = -0.05f;
		}
 
		this.zTranslation += this.zTranslationInc;
		
	}

	public void renderFrame(double tSeconds) {
		//testWireframeCube();
		//testWireframeCubeWithMatrices();
		testFilledCubeNoZBuffer();
	}
}
