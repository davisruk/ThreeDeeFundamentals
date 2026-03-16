package online.davisfamily.threedee.testing;

import java.util.Arrays;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.Cube;
import online.davisfamily.threedee.model.LidFactory;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.OneColourStrategyImpl;
import online.davisfamily.threedee.model.RenderableObject;
import online.davisfamily.threedee.model.SquareBasedStrategyImpl;
import online.davisfamily.threedee.model.Tote;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private ObjectTransformation tCube, tTote, tLidLeft, tLidRight;
	private Mat4 cubeModel, toteModel, vp, lidLeftModel, lidRightModel;
	private Cube cube;
	private Tote tote;
	private Tote.Lid lid;
	private RenderableObject rCube, rTote, rLidRight, rLidLeft;
	private DirectionalLight lightDirection;
	private OneColourStrategyImpl blueColour, yellowColour, redColour;
	
	private float lidAngle = 0.0f;
	private boolean opening = true;
	// probably need to add this to Cube / Box using a decorator or similar pattern
	public int[] faceColours = {
	    0xFFFF0000, // bottom - red
	    0xFF00FF00, // top - green
	    0xFF0000FF, // front - blue
	    0xFFFFFF00, // right - yellow
	    0xFFFF00FF, // back - magenta
	    0xFF00FFFF  // left - cyan
	};
	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		blueColour = new OneColourStrategyImpl(0xFF0000FF);
		yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		redColour = new OneColourStrategyImpl(0xFFFF0000);
		tote = new Tote();
		lid = tote.new Lid();
		//Mesh mLid = new Mesh(lid.v4Vertices, lid.triangles);
		LidFactory.InterlockingLids lids = LidFactory.buildInterlockingAngularLids(
			    0.371f,  // openingWidth
			    0.547f,  // openingDepth
			    0.020f,  // thickness
			    5,       // toothCount
			    0.010f,  // seamAmplitude
			    0.24f,   // valleyFlatFraction
			    0.24f    // peakFlatFraction
			);

			Mesh mLeftLid = lids.leftMesh;
			Mesh mRightLid = lids.rightMesh;
		lidLeftModel = new Mat4();
		lidRightModel = new Mat4();
		
		float testOpen = (float)Math.toRadians(45.0);

		float openingWidth = 0.371f;   // same value passed into LidFactory
		float halfWidth = openingWidth / 2f;

		tLidLeft = new ObjectTransformation(
		    0f, 0f, 0f,
		    -halfWidth,
		    tote.outerHeight,
		    0f,
		    0f, 0f, 0f,
		    lidLeftModel
		);

		tLidRight = new ObjectTransformation(
		    0f, 0f, 0f,
		    +halfWidth,
		    tote.outerHeight,
		    0f,
		    0f, 0f, 0f,
		    lidRightModel
		);
		
//		tLidLeft = new ObjectTransformation(0.0f,0.0f,+testOpen,-lid.innerTopWidth / 2f,tote.outerHeight,0f,0f,0f,0f, lidLeftModel);
//		tLidRight = new ObjectTransformation(0.0f,(float)Math.PI,-testOpen,+lid.innerTopWidth / 2f, tote.outerHeight,0f,0f,0f,0f, lidRightModel);
		
		rLidRight =  new RenderableObject(tr,mRightLid,tLidRight,redColour);
		rLidLeft =  new RenderableObject(tr,mLeftLid,tLidLeft,yellowColour);

		toteModel = new Mat4();
		//this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-3.0f, cubeModel1);
		tTote = new ObjectTransformation(0.0f,0.0f,0f,0f,0f,-5f,0f,0f,-3.0f, toteModel);
		Mesh m = new Mesh(tote.v4Vertices, tote.triangles);
		rTote = new RenderableObject(tr, m,tTote, blueColour, Arrays.asList(rLidRight, rLidLeft));

		cube = new Cube();
		cubeModel = new Mat4();
		tCube = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,3.0f,0f,0f, cubeModel);
		m = new Mesh(cube.v4Vertices, cube.triangles);
		rCube = new RenderableObject(tr, m,tCube,new SquareBasedStrategyImpl(faceColours));
		
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		this.vp = new Mat4();
	}
		
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	private void drawObject(RenderableObject ro) {
		//ro.transformation.setupModel();
		ro.draw(camera, perspective, zBuffer, lightDirection, null);
		//tr.drawMesh(ro, camera, perspective, zBuffer, lightDirection);
	}
	private void testFilledObjects(double tSeconds) {
		drawObject(rTote);
//		drawObject(cubeRender);
	}
	
	private void transformAndRotate (double tSeconds) {
		float angularSpeedX = 0.6f;   // radians per second
	    float angularSpeedY = 0.3f;

	    //tTote.angleX += angularSpeedX * tSeconds;
	    tTote.angleY += angularSpeedY * tSeconds;
	    //tTote.zTranslation += tTote.zTranslationInc * tSeconds;
	    
	    if (tTote.zTranslation < -10) {
			tTote.zTranslationInc = 3.0f;
		} else if (tTote.zTranslation > -3) {
			tTote.zTranslationInc = -3.0f;
		}

	    tCube.angleX += angularSpeedX * tSeconds;
	    tCube.angleY += angularSpeedY * tSeconds;
	    tCube.xTranslation += tCube.xTranslationInc * tSeconds;
		if (tCube.xTranslation > 4 || tCube.xTranslation < -4) tCube.xTranslationInc *= -1.0f;

		if (tCube.xTranslation > 4) {
			tCube.xTranslationInc = -2.0f;
		} else if (tCube.xTranslation < -4) {
			tCube.xTranslationInc = 2.0f;
		}
		
		float speed = 0.002f;

		if (opening) {
		    lidAngle += speed;
		    if (lidAngle > Math.toRadians(110.0)) {
		        lidAngle = (float)Math.toRadians(110.0);
		        opening = false;
		    }
		} else {
		    lidAngle -= speed;
		    if (lidAngle < 0.0f) {
		        lidAngle = 0.0f;
		        opening = true;
		    }
		}

		tLidLeft.angleZ = +lidAngle;
		tLidRight.angleZ = -lidAngle;		
	}

	@Override
	public void renderFrame(double tSeconds) {
	    if (!inputState.isSet(Mode.PAUSE_ALL)) {
	    	updateCamera();
			updatePosition(tSeconds);
		    this.clear(0xFF000000);
			buildVP();
			testFilledObjects(tSeconds);
			if (!inputState.isSet(Mode.PAUSE_TRANSFORMS))
				transformAndRotate(tSeconds);
			updateDebug(tSeconds);
	    }
	}
}
