package online.davisfamily.threedee.testing;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.Cube;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.RenderableObject;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private ObjectTransformation t1;
	private ObjectTransformation t2;
	private Mat4 cubeModel1, cubeModel2, vp;
	private Cube cube;
	private RenderableObject cubeRender1, cubeRender2;
	private DirectionalLight lightDirection;
	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		this.cube = new Cube();

		this.cubeModel1 = new Mat4();
		this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-3.0f, cubeModel1);
		Mesh m = new Mesh(cube.v4CubeVertices, cube.cubeTriangles);
		cubeRender1 = new RenderableObject(m,t1,cube.cubeFaceColours);

		this.cubeModel2 = new Mat4();
		this.t2 = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,3.0f,0f,0f, cubeModel2);
		this.vp = new Mat4();
		cubeRender2 = new RenderableObject(m,t2,cube.cubeFaceColours);
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
	}
		
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	private void drawCube(RenderableObject ro) {
		ro.transformation.setupModel();
		tr.drawMesh(ro, camera, perspective, zBuffer, lightDirection);
	}
	private void testFilledCubes(double tSeconds) {
		drawCube(cubeRender1);
		drawCube(cubeRender2);
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
		if (t2.xTranslation > 4 || t2.xTranslation < -4) t2.xTranslationInc *= -1.0f;

		if (t2.xTranslation > 4) {
			t2.xTranslationInc = -2.0f;
		} else if (t2.xTranslation < -4) {
			t2.xTranslationInc = 2.0f;
		}
	}

	@Override
	public void renderFrame(double tSeconds) {
	    if (!inputState.isSet(Mode.PAUSE_ALL)) {
	    	updateCamera();
			updatePosition(tSeconds);
		    this.clear(0xFF000000);
			buildVP();
			testFilledCubes(tSeconds);
			if (!inputState.isSet(Mode.PAUSE_TRANSFORMS))
				transformAndRotate(tSeconds);
			updateDebug(tSeconds);
	    }
	}
}
