package online.davisfamily.threedee.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.swing.JRootPane;

import online.davisfamily.threedee.behaviour.Behaviour.OrientationMode;
import online.davisfamily.threedee.behaviour.Behaviour.WrapMode;
import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.RouteTrackFactory;
import online.davisfamily.threedee.behaviour.routing.TransferZone;
import online.davisfamily.threedee.behaviour.routing.transfer.AlwaysTransferStrategy;
import online.davisfamily.threedee.behaviour.routing.transfer.ToggleTransferStrategy;
import online.davisfamily.threedee.camera.CameraPosition;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.tote.ToteEnvelope;
import online.davisfamily.threedee.model.tracks.TrackAppearance;
import online.davisfamily.threedee.model.tracks.TrackSpec;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.path.PathSegment3;
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
		super(pane, dimensions,	CameraPosition.aboveLeft());
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
		
		// Path Geometry
		
		float leftX = 0f;
		float midX = 5f;
		float rightX = 8f;
		float topZ = 0f;
		float bottomZ = -3f;

		// Top straight: +X
		PathSegment3 topGeometry = new LinearSegment3(
		    new Vec3(leftX, 0f, topZ),
		    new Vec3(rightX, 0f, topZ)
		);

		// Right return curve: top -> bottom
		PathSegment3 rightReturnGeometry = new BezierSegment3(
		    new Vec3(rightX, 0f, topZ),         // start
		    new Vec3(rightX + 2f, 0f, topZ),    // control 1
		    new Vec3(rightX + 2f, 0f, bottomZ), // control 2
		    new Vec3(rightX, 0f, bottomZ)       // end
		);

		// Bottom straight: -X
		PathSegment3 bottomGeometry = new LinearSegment3(
		    new Vec3(rightX, 0f, bottomZ),
		    new Vec3(leftX, 0f, bottomZ)
		);

		// Left return curve: bottom -> top
		PathSegment3 leftReturnGeometry = new BezierSegment3(
		    new Vec3(leftX, 0f, bottomZ),      // start
		    new Vec3(leftX - 2f, 0f, bottomZ), // control 1
		    new Vec3(leftX - 2f, 0f, topZ),    // control 2
		    new Vec3(leftX, 0f, topZ)          // end
		);

		// Middle link: top -> bottom
		PathSegment3 linkGeometry = new LinearSegment3(
		    new Vec3(midX, 0f, topZ),
		    new Vec3(midX, 0f, bottomZ)
		);

		// Route segments from path
		RouteSegment top = new RouteSegment(0, "top", topGeometry);
		RouteSegment rightReturn = new RouteSegment(1, "rightReturn", rightReturnGeometry);
		RouteSegment bottom = new RouteSegment(2, "bottom", bottomGeometry);
		RouteSegment leftReturn = new RouteSegment(3, "leftReturn", leftReturnGeometry);
		RouteSegment link = new RouteSegment(4, "link", linkGeometry);
		// Ordinary route connections
		
		top.addNext(rightReturn);
		rightReturn.addNext(bottom);
		bottom.addNext(leftReturn);
		leftReturn.addNext(top);
		link.addNext(bottom);
		// Transfer zone on the top segment

		TransferZone zone = new TransferZone(
			    4.5f,   // startDistance on top
			    1.0f,   // length
			    link,   // target segment
			    0.0f    // hand off at top of link
			);

		top.getTransferZones().add(zone);
		
		
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
			    List.of(top, rightReturn, bottom, leftReturn, link),
			    spec,
			    appearance
		);
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}
		objects.add(rTote);


		GraphFollowerBehaviour follower = new GraphFollowerBehaviour(
			    top,
			    null,
			    2.0f,
			    WrapMode.LOOP,
			    EnumSet.of(OrientationMode.YAW),
			    0f,
			    0f,
			    GraphFollowerBehaviour.TravelDirection.FORWARD,
			    new ToggleTransferStrategy()
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
