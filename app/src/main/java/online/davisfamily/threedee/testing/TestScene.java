package online.davisfamily.threedee.testing;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private ObjectTransformation t1;
	private ObjectTransformation t2;
	private Mat4 model1, model2, vp, mvp1, mvp2;

	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);

		this.vp = new Mat4();
		
		this.model1 = new Mat4();
		this.model2 = new Mat4();
		this.mvp1 = new Mat4();
		this.mvp2 = new Mat4();
		this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-3.0f);
		this.t2 = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,3.0f,0f,0f);
	}
	
	// -- 3D models  ------
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
	
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	private void buildMVP(Mat4 out, Mat4 model, ObjectTransformation tx) {
	    model.setModel(tx);
	    out.set(vp);
	    out.mutableMultiply(model);
	}
	
	private void testFilledCubes(double tSeconds) {
	    buildMVP(mvp1, model1, t1);
	    //tr.drawCube(v4CubeVertices, cubeTriangles, mvp1, cubeFaceColours, zBuffer);
	    buildMVP(mvp2, model2, t2);
	    //tr.drawCube(v4CubeVertices, cubeTriangles, mvp2, cubeFaceColours, zBuffer);

		tr.drawCube(camera, v4CubeVertices, cubeTriangles, model1, perspective, cubeFaceColours, zBuffer);
		tr.drawCube(camera, v4CubeVertices, cubeTriangles, model2, perspective, cubeFaceColours, zBuffer);

		transformAndRotate(tSeconds);
   	}
	
	private void transformAndRotate (double tSeconds) {
		float angularSpeedX = 0.6f;   // radians per second
	    float angularSpeedY = 0.3f;

	    t1.angleX += angularSpeedX * tSeconds;
	    t1.angleY += angularSpeedY * tSeconds;
	    t1.zTranslation += t1.zTranslationInc * tSeconds;
		
	    if (t1.zTranslation < -10) {
			t1.zTranslationInc = 3.0f;
		} else if (t1.zTranslation > -3) {
			t1.zTranslationInc = -3.0f;
		}

	    t2.angleX += angularSpeedX * tSeconds;
	    t2.angleY += angularSpeedY * tSeconds;
	    t2.xTranslation += t2.xTranslationInc * tSeconds;
	    
		if (t2.xTranslation > 4) {
			t2.xTranslationInc = -3.0f;
		} else if (t2.xTranslation < -4) {
			t2.xTranslationInc = 3.0f;
		}
		
	}

	@Override
	public void renderFrame(double tSeconds) {
	    updateCamera();
	    updatePosition(tSeconds);
		this.clear(0xFF000000);
	    buildVP();
		testFilledCubes(tSeconds);
		updateDebug(tSeconds);
	}
}
