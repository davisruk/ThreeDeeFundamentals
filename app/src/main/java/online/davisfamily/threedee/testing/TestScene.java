package online.davisfamily.threedee.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.swing.JRootPane;

import online.davisfamily.threedee.behaviour.Behaviour.OrientationMode;
import online.davisfamily.threedee.behaviour.routing.FirstRouteDecisionProvider;
import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.RouteTrackFactory;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.tote.ToteEnvelope;
import online.davisfamily.threedee.model.tracks.TrackAppearance;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.path.TransferSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.rendering.lights.DirectionalLight;
import online.davisfamily.threedee.rendering.tote.RenderableToteFactory;
import online.davisfamily.threedee.scene.BaseScene;

public class TestScene extends BaseScene{	

	private RenderableObject rTote;
	private RenderableObject rTrack;
	private DirectionalLight lightDirection;
	private List<RenderableObject> objects;
	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions);
		objects = new ArrayList<RenderableObject>();
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
			    0.080f,  // rollerPitch
			    0.010f,  // rollerWidthInset
			    0.025f,  // rollerHeight
			    0.018f,  // rollerDepthAlongPath
			    0.080f   // sampleStep
			);
		OneColourStrategyImpl yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		
		float startX = 0f;
		float endX = 8f;
		float topZ = 0f;
		float bottomZ = -3f;

		// top straight: +X
		PathSegment3 seg1 = new LinearSegment3(
		    new Vec3(startX, 0f, topZ),
		    new Vec3(endX,   0f, topZ)
		);

		// right-side transfer: move across in -Z, keep tote facing +X
		PathSegment3 seg2 = new TransferSegment3(
		    new Vec3(endX, 0f, topZ),
		    new Vec3(endX, 0f, bottomZ),
		    new Vec3(1f, 0f, 0f)
		);

		// bottom straight: -X
		PathSegment3 seg3 = new LinearSegment3(
		    new Vec3(endX,   0f, bottomZ),
		    new Vec3(startX, 0f, bottomZ)
		);

		// left-side return curve: bottom lane back to top lane
		PathSegment3 seg4 = new BezierSegment3(
		    new Vec3(startX, 0f, bottomZ),   // start
		    new Vec3(startX - 2f, 0f, bottomZ), // control 1
		    new Vec3(startX - 2f, 0f, topZ),    // control 2
		    new Vec3(startX, 0f, topZ)       // end
		);
		
		RouteSegment rs1 = new RouteSegment(0, "top", seg1);
		RouteSegment rs2 = new RouteSegment(1, "transfer-right", seg2);
		RouteSegment rs3 = new RouteSegment(2, "bottom", seg3);
		RouteSegment rs4 = new RouteSegment(3, "return-left", seg4);
		rs1.addNext(rs2);
		rs2.addNext(rs3);
		rs3.addNext(rs4);
		
		
		OneColourStrategyImpl deckColour = new OneColourStrategyImpl(0xFF00FF00); // green
		OneColourStrategyImpl guidesColour = new OneColourStrategyImpl(0xFFFF00FF); // magenta
		OneColourStrategyImpl rollersColour = new OneColourStrategyImpl(0xFF00FFFF); // cyan
	    
		TrackAppearance appearance = new TrackAppearance(
				deckColour,
				guidesColour,
				rollersColour
		);
		
		List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
			    tr,
			    List.of(rs1, rs2, rs3, rs4),
			    spec,
			    appearance
		);
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}
		objects.add(rTote);

		GraphFollowerBehaviour follower = new GraphFollowerBehaviour(
			rs1,
		    new FirstRouteDecisionProvider(),
		    2.0f,
		    GraphFollowerBehaviour.WrapMode.LOOP,
		    EnumSet.of(OrientationMode.YAW),
		    0f
		);
		rTote.addBehaviour(follower);
	}
		
	@Override
	public void executeChildRenderOperations(double tSeconds) {
		if (inputState.isSet(Mode.SHOW_PATH))
			debug.drawPathForObject(rTote, camera.getView(), projection);
		drawObject(objects, tSeconds, lightDirection);
	}
}
