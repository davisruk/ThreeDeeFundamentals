package online.davisfamily.threedee.testing;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.tote.RenderableToteFactory;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private Mat4 vp;
	private RenderableObject rTote;
	private DirectionalLight lightDirection;
	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		rTote = RenderableToteFactory.createRenderableTote(tr);
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		this.vp = new Mat4();
	}
		
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	// allows iteration through multiple RenderableObjects
	private void drawObject(RenderableObject ro, double dtSeconds) {
		if (!inputState.isSet(Mode.PAUSE_TRANSFORMS))
			ro.update(dtSeconds);

		ro.draw(camera, perspective, zBuffer, lightDirection, null);
	}

	private void testFilledObjects(double tSeconds) {
		drawObject(rTote, tSeconds);
	}
	
//	boolean hasPrinted = false;
	@Override
	public void renderFrame(double tSeconds) {
/*
		if (!hasPrinted) {
	    	rTote.children.stream().forEach(ro -> System.out.println(ro.mesh));
	    	hasPrinted = true;
	    }
*/	    
		if (!inputState.isSet(Mode.PAUSE_ALL)) {
	    	updateCamera();
			updatePosition(tSeconds);
		    this.clear(0xFF000000);
			buildVP();
			testFilledObjects(tSeconds);
			updateDebug(tSeconds);
	    }
	}
}
