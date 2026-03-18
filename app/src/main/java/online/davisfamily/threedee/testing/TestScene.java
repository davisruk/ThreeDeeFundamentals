package online.davisfamily.threedee.testing;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.tote.RenderableToteFactory;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private RenderableObject rTote;
	private DirectionalLight lightDirection;
	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		rTote = RenderableToteFactory.createRenderableTote(tr);
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
	}
		
	@Override
	public void executeChildRenderOperations(double tSeconds) {
		drawObject(rTote, tSeconds, lightDirection);
	}
}
