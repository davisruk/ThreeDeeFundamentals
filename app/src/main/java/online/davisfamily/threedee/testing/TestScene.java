package online.davisfamily.threedee.testing;

import java.util.Arrays;

import online.davisfamily.threedee.Scene;
import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.triangles.TriangleRenderer;

public class TestScene implements Scene {

	private ViewDimensions vd;
	private CohenSutherlandLineClipper clipper;
	private BresenhamLineUtilities bl;
	private TriangleRenderer tr;
	int[] pixels;
	float[] zBuffer;
	private ObjectTransformation t1;
	private ObjectTransformation t2;
	private Mat4 model1, model2, view, projection, perspective, mvp1, mvp2;
	private float aspect;

	public TestScene (ViewDimensions dimensions, int[] pixels, CohenSutherlandLineClipper clipper, BresenhamLineUtilities bresenhamUtils, TriangleRenderer triangleRenderer) {
		this.vd = dimensions;
		this.clipper = clipper;
		this.bl = bresenhamUtils;
		this.tr = triangleRenderer;
		this.pixels = pixels;
		this.zBuffer = new float[pixels.length];
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
		this.aspect = (float)vd.width / (float)vd.height;
		this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-0.05f);
		this.t2 = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,0.05f,0f,0f);
		this.perspective = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.model1 = new Mat4();
		this.model2 = new Mat4();
		this.view = Mat4.identity();
		this.mvp1 = new Mat4();
		this.mvp2 = new Mat4();
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
		
		final int[] CUBE1_FACE_COLOURS = new int[] {
			    0xFFFF0000, // Front  - Red
			    0xFFFFFFFF, // Back   - White
			    0xFFFF0000, // Left   - Red
			    0xFFFFFFFF, // Right  - White
			    0xFFFF0000, // Top    - Red
			    0xFFFFFFFF  // Bottom - White
		};
		
		final int[] CUBE2_FACE_COLOURS = new int[] {
			    0xFF0000FF, // Front  - Blue
			    0xFFFFFF00, // Back   - Yellow
			    0xFF0000FF, // Left   - Blue
			    0xFFFFFF00, // Right  - Yellow
			    0xFF0000FF, // Top    - Blue
			    0xFFFFFF00  // Bottom - Yellow
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
	
	private void clearZBuffer() {
		this.zBuffer = new float[pixels.length];
		Arrays.fill(this.zBuffer, Float.POSITIVE_INFINITY);
	}
	
	private Mat4 getImmutableMVP(ObjectTransformation tx) {
		float aspect = (float)vd.width / (float)vd.height;	
		Mat4 model = Mat4
				.translation(tx.xTranslation, tx.yTranslation, tx.zTranslation)
				.immutableMultiplyMatrix(Mat4.rotationYXZ(tx.angleY, tx.angleX, tx.angleZ)); // could do multiple Mat4.multiplyMatrix(Mat4.rotationY).multiplyMatrix(rotationX) etc.
		Mat4 view = Mat4.identity();
		Mat4 projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		Mat4 mvp = projection.immutableMultiplyMatrix(view).immutableMultiplyMatrix(model);
		return mvp;
	}
	
	private Mat4 getMVPMutable(Mat4 model, ObjectTransformation tx) {
		model.setModel(tx);
		projection.set(perspective);
		projection.mutableMultiply(view).mutableMultiply(model);
		return projection;
	}

	private void testFilledCube() {
		this.clear(0xFF000000);
		this.clearZBuffer();
		//Mat4 t1MVP = getImmutableMVP(t1);
		//Mat4 t2MVP = getImmutableMVP(t2);

		tr.drawCube(v4CubeVertices, cubeTriangles, getMVPMutable(model1, t1), cubeFaceColours, zBuffer);
		tr.drawCube(v4CubeVertices, cubeTriangles, getMVPMutable(model2, t2), cubeFaceColours, zBuffer);
		
		t1.angleX += 0.01f;
		t1.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		t1.angleX %= twoPI;
		t1.angleY %= twoPI;
		if (t1.zTranslation < -10) {
			t1.zTranslationInc = 0.05f;
		} else if (t1.zTranslation > -3) {
			t1.zTranslationInc = -0.05f;
		}
 
		t1.zTranslation += t1.zTranslationInc;

		t2.angleX += 0.01f;
		t2.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		t2.angleX %= twoPI;
		t2.angleY %= twoPI;
		if (t2.xTranslation > 4) {
			t2.xTranslationInc = -0.05f;
		} else if (t2.xTranslation < -4) {
			t2.xTranslationInc = 0.05f;
		}
 
		t2.xTranslation += t2.xTranslationInc;

	}

	public void renderFrame(double tSeconds) {
		//testWireframeCube();
		//testWireframeCubeWithMatrices();
		testFilledCube();
	}
}
