package online.davisfamily.threedee.testing;

import javax.swing.JRootPane;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.OneColourStrategyImpl;
import online.davisfamily.threedee.model.tote.ToteEnvelope;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.tote.RenderableToteFactory;
import online.davisfamily.threedee.rendering.track.RenderableTrackFactory;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private RenderableObject rTote;
	private RenderableObject rTrack;
	private DirectionalLight lightDirection;

	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		rTote = RenderableToteFactory.createRenderableTote(tr);
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		ToteEnvelope toteEnvelope = new ToteEnvelope(
			    0.320f, // bottom width
			    0.500f, // bottom depth
			    0.310f  // height
			);

			TrackSpec spec = new TrackSpec(
			    toteEnvelope,
			    0.030f,  // sideClearance
			    0.040f,  // deckThickness
			    0.000f,  // deckTopY
			    true,    // includeGuides
			    0.050f,  // guideHeight
			    0.010f,  // guideThickness
			    0.005f,  // guideGap
			    true,    // includeRollers
			    0.055f,  // rollerPitch
			    0.010f,  // rollerWidthInset
			    0.010f,  // rollerHeight
			    0.020f,  // rollerDepthAlongPath
			    0.080f   // sampleStep
			);
		OneColourStrategyImpl yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		//rTrack = RenderableTrackFactory.createRenderableTrack(tr, null, spec, yellowColour)
	}
		
	@Override
	public void executeChildRenderOperations(double tSeconds) {
		if (inputState.isSet(Mode.SHOW_PATH))
			debug.drawPathForObject(rTote, camera.getView(), projection);
		drawObject(rTote, tSeconds, lightDirection);
	}
}
