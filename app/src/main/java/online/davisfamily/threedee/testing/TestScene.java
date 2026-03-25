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
import online.davisfamily.threedee.behaviour.routing.transfer.ToggleTransferStrategy;
import online.davisfamily.threedee.camera.CameraPosition;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.tote.Tote;
import online.davisfamily.threedee.model.tote.ToteEnvelope;
import online.davisfamily.threedee.model.tracks.GuideSide;
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
		Tote tote = new Tote();
		rTote = RenderableToteFactory.createRenderableTote(tr, tote);
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
/*
		ToteEnvelope toteEnvelope = new ToteEnvelope(
				0.320f, // bottom width
			    0.500f, // bottom depth
			    0.310f  // height
			);
*/
		ToteEnvelope widthToteEnvelope = new ToteEnvelope(
			    tote.getOuterBottomWidth(),
			    tote.getOuterBottomDepth(),
			    tote.getOuterHeight()
		);

		TrackSpec specToteWidthWise = new TrackSpec(
			widthToteEnvelope,
		    0.030f,  // sideClearance
		    0.040f,  // deckThickness
		    0.000f,  // deckTopY
		    true,    // includeGuides
		    0.050f,  // guideHeight
		    0.010f,  // guideThickness
		    0.005f,  // guideGap
		    0.4f,
		    0.4f,
		    true,    // includeRollers
		    0.080f,  // rollerPitch
		    0.010f,  // rollerWidthInset
		    0.025f,  // rollerHeight
		    0.018f,  // rollerDepthAlongPath
		    0.080f   // sampleStep
		);

		ToteEnvelope lengthToteEnvelope = new ToteEnvelope(
			    tote.getOuterBottomDepth(),
			    tote.getOuterBottomWidth(),
			    tote.getOuterHeight()
		);

		TrackSpec specToteLengthWise = new TrackSpec(
			lengthToteEnvelope,
		    0.030f,  // sideClearance
		    0.040f,  // deckThickness
		    0.000f,  // deckTopY
		    true,    // includeGuides
		    0.050f,  // guideHeight
		    0.010f,  // guideThickness
		    0.005f,  // guideGap
		    0.4f,
		    0.4f,
		    true,    // includeRollers
		    0.080f,  // rollerPitch
		    0.010f,  // rollerWidthInset
		    0.025f,  // rollerHeight
		    0.018f,  // rollerDepthAlongPath
		    0.080f   // sampleStep
		);

		OneColourStrategyImpl yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		float toteLength = tote.getOuterBottomDepth();
		// Path Geometry
		float leftX = 0f;
		float link1X = 3f;
		float link2X = 5f;
		float rightX = 8f;
		float topZ = 0f;
		float bottomZ = -3f;
		float linkClearance = 0.2f; // tweak visually
		
		// Top straight: +X
		PathSegment3 topGeometry = new LinearSegment3(
		    new Vec3(leftX, 0f, topZ),
		    new Vec3(rightX, 0f, topZ)
		);

		// Right return curve: top -> bottom
		PathSegment3 rightReturnGeometry = new BezierSegment3(
		    new Vec3(rightX, 0f, topZ),
		    new Vec3(rightX + 2f, 0f, topZ),
		    new Vec3(rightX + 2f, 0f, bottomZ),
		    new Vec3(rightX, 0f, bottomZ)
		);

		// Bottom straight: -X
		PathSegment3 bottomGeometry = new LinearSegment3(
		    new Vec3(rightX, 0f, bottomZ),
		    new Vec3(leftX, 0f, bottomZ)
		);

		// Left return curve: bottom -> top
		PathSegment3 leftReturnGeometry = new BezierSegment3(
		    new Vec3(leftX, 0f, bottomZ),
		    new Vec3(leftX - 2f, 0f, bottomZ),
		    new Vec3(leftX - 2f, 0f, topZ),
		    new Vec3(leftX, 0f, topZ)
		);

		// Middle link 1: top -> bottom at x=3 (trimmed)
		PathSegment3 link1Geometry = new LinearSegment3(
		    new Vec3(link1X, 0f, topZ - linkClearance),
		    new Vec3(link1X, 0f, bottomZ + linkClearance)
		);

		// Middle link 2: top -> bottom at x=5 (trimmed)
		PathSegment3 link2Geometry = new LinearSegment3(
		    new Vec3(link2X, 0f, topZ - linkClearance),
		    new Vec3(link2X, 0f, bottomZ + linkClearance)
		);

		// =====================
		// Route segments
		// =====================

		RouteSegment top = new RouteSegment("top", topGeometry);
		RouteSegment rightReturn = new RouteSegment("rightReturn", rightReturnGeometry);
		RouteSegment bottom = new RouteSegment("bottom", bottomGeometry);
		RouteSegment leftReturn = new RouteSegment("leftReturn", leftReturnGeometry);
		RouteSegment link1 = new RouteSegment("link1", link1Geometry);
		RouteSegment link2 = new RouteSegment("link2", link2Geometry);

		// =====================
		// Main oval loop
		// =====================

		top.connectTo(rightReturn);
		rightReturn.connectTo(bottom);
		bottom.connectTo(leftReturn);
		leftReturn.connectTo(top);

		// =====================
		// Link connections into bottom
		// =====================

		// bottom runs from x=8 to x=0 at z=-3
		// so entry distance is (8 - linkX)

		float opening = 0.4f; // test value

		link1.connectTo(
		        bottom,
		        link1.getGeometry().getTotalLength(),
		        5.0f,
		        null,
		        GuideSide.RIGHT,
		        toteLength);

		link2.connectTo(
		        bottom,
		        link2.getGeometry().getTotalLength(),
		        3.0f,
		        null,
		        GuideSide.RIGHT,
		        toteLength);
		// =====================
		// Transfer zones on top
		// =====================

		// top runs from x=0 to x=8, so distance along top == x

		TransferZone zone1 = new TransferZone(
		    link1X - (toteLength * 0.5f),
		    toteLength,
			link1,
		    0.0f,
	        GuideSide.RIGHT,
	        GuideSide.LEFT,
		    new ToggleTransferStrategy(false)
		);

		TransferZone zone2 = new TransferZone(
		    link2X - (toteLength * 0.5f),
		    toteLength,
			link2,
		    0.0f,
	        GuideSide.RIGHT,
	        GuideSide.LEFT,
		    new ToggleTransferStrategy(true)
		);

		top.addTransferZone(zone1);
		top.addTransferZone(zone2);
		
		OneColourStrategyImpl deckColour = new OneColourStrategyImpl(0xFF00FF00); // green
		OneColourStrategyImpl guidesColour = new OneColourStrategyImpl(0xFFFF00FF); // magenta
		OneColourStrategyImpl rollersColour = new OneColourStrategyImpl(0xFF00FFFF); // cyan
	    
		TrackAppearance appearance = new TrackAppearance(
			deckColour,
			rollersColour,
			guidesColour,
			new OneColourStrategyImpl(0xFFFF8800)
		);
		
		List<RouteTrackFactory.SpecAndSegment> specsAndSegs = List.of(
			RouteTrackFactory.SpecAndSegment.createSpecAndSegment(specToteWidthWise, top),
			RouteTrackFactory.SpecAndSegment.createSpecAndSegment(specToteWidthWise, rightReturn),
			RouteTrackFactory.SpecAndSegment.createSpecAndSegment(specToteWidthWise, bottom),
			RouteTrackFactory.SpecAndSegment.createSpecAndSegment(specToteWidthWise, leftReturn),
			RouteTrackFactory.SpecAndSegment.createSpecAndSegment(specToteLengthWise, link1),
			RouteTrackFactory.SpecAndSegment.createSpecAndSegment(specToteLengthWise, link2)
		);
		
		List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
		    tr,
		    specsAndSegs,
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
			    GraphFollowerBehaviour.TravelDirection.FORWARD
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
