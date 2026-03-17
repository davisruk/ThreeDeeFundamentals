package online.davisfamily.threedee.testing;

import java.util.Arrays;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.LidFactory;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.OneColourStrategyImpl;
import online.davisfamily.threedee.model.RenderableObject;
import online.davisfamily.threedee.model.Tote;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private ObjectTransformation tTote, tLidLeft, tLidRight;
	private Mat4 toteModel, vp, lidLeftModel, lidRightModel;
	private Tote tote;
	private RenderableObject rTote, rLidRight, rLidLeft;
	private DirectionalLight lightDirection;
	private OneColourStrategyImpl blueColour, yellowColour, redColour;
	
	private float lidAngle = 0.0f;
	private boolean opening = true;

	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		blueColour = new OneColourStrategyImpl(0xFF0000FF);
		yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		redColour = new OneColourStrategyImpl(0xFFFF0000);
		tote = new Tote();
		
		LidFactory.InterlockingLids lids = LidFactory.buildInterlockingAngularLids(
			    0.371f,  // openingWidth
			    0.547f,  // openingDepth
			    0.020f,  // thickness
			    3,       // toothCount
			    0.010f,  // seamAmplitude
			    0.24f,   // valleyFlatFraction
			    0.24f    // peakFlatFraction
			);

		Mesh mLeftLid = lids.leftMesh;
		Mesh mRightLid = lids.rightMesh;
		lidLeftModel = new Mat4();
		lidRightModel = new Mat4();
		
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
		
		rLidRight =  new RenderableObject(tr,mRightLid,tLidRight,redColour);
		rLidLeft =  new RenderableObject(tr,mLeftLid,tLidLeft,yellowColour);

		toteModel = new Mat4();
		tTote = new ObjectTransformation(0.0f,0.0f,0f,0f,0f,-5f,0f,0f,-3.0f, toteModel);
		Mesh m = new Mesh(tote.v4Vertices, tote.triangles);
		rTote = new RenderableObject(tr, m,tTote, blueColour, Arrays.asList(rLidRight, rLidLeft));

		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		this.vp = new Mat4();
	}
		
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	// allows iteration through multiple RenderableObjects
	private void drawObject(RenderableObject ro) {
		ro.draw(camera, perspective, zBuffer, lightDirection, null);
	}

	private void testFilledObjects(double tSeconds) {
		drawObject(rTote);
	}
	
	private void transformAndRotate (double tSeconds) {
		float angularSpeedX = 0.6f;   // radians per second
	    float angularSpeedY = 0.3f;

	    tTote.angleX += angularSpeedX * tSeconds;
	    tTote.angleY += angularSpeedY * tSeconds;
	    tTote.zTranslation += tTote.zTranslationInc * tSeconds;
	    
	    if (tTote.zTranslation < -10) {
			tTote.zTranslationInc = 3.0f;
		} else if (tTote.zTranslation > -3) {
			tTote.zTranslationInc = -3.0f;
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

	boolean hasPrinted = false;
	@Override
	public void renderFrame(double tSeconds) {
	    if (!hasPrinted) {
	    	rTote.children.stream().forEach(ro -> System.out.println(ro.mesh));
	    	hasPrinted = true;
	    }
	    
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
