package online.davisfamily.threedee.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.swing.JRootPane;

import online.davisfamily.threedee.behaviour.Behaviour.OrientationMode;
import online.davisfamily.threedee.behaviour.Behaviour.WrapMode;
import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour;
import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSceneBuilder;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.RouteTrackFactory;
import online.davisfamily.threedee.behaviour.routing.transfer.AlwaysTransferStrategy;
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
	private DirectionalLight lightDirection;
	private List<RenderableObject> objects;
	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions,	CameraPosition.aboveLeft());
		objects = new ArrayList<RenderableObject>();
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		Tote t = setupTote();
		setupOvalTrack(t);
//		setupParallelTracks(t);
	}
		
	@Override
	public void executeChildRenderOperations(double tSeconds) {
		if (inputState.isSet(Mode.SHOW_PATH))
			debug.drawPathForObject(rTote, camera.getView(), projection);
		drawObject(objects, tSeconds, lightDirection);
	}
	
	private Tote setupTote() {
		Tote tote = new Tote();
		rTote = RenderableToteFactory.createRenderableTote(tr, tote);
		objects.add(rTote);
		return tote;
	}

	private void setupOvalTrack(Tote tote) {

		if (tote == null) tote = setupTote();
		
		ToteEnvelope widthToteEnvelope = new ToteEnvelope(
		        tote.getOuterBottomWidth(),
		        tote.getOuterBottomDepth(),
		        tote.getOuterHeight()
		);

		TrackSpec specToteWidthWise = new TrackSpec(
		        widthToteEnvelope,
		        0.030f,
		        0.040f,
		        0.000f,
		        true,
		        0.050f,
		        0.010f,
		        0.005f,
		        0.5f,
		        1.0f,
		        true,
		        0.080f,
		        0.010f,
		        0.025f,
		        0.018f,
		        0.080f
		);

		ToteEnvelope lengthToteEnvelope = new ToteEnvelope(
		        tote.getOuterBottomDepth(),
		        tote.getOuterBottomWidth(),
		        tote.getOuterHeight()
		);

		TrackSpec specToteLengthWise = new TrackSpec(
		        lengthToteEnvelope,
		        0.030f,
		        0.040f,
		        0.000f,
		        true,
		        0.050f,
		        0.010f,
		        0.005f,
		        0.5f,
		        0.5f,
		        true,
		        0.080f,
		        0.010f,
		        0.025f,
		        0.018f,
		        0.080f
		);

		float toteLength = tote.getOuterBottomDepth();
		float linkOpeningLength = specToteLengthWise.getRunningWidth();

		// Path geometry
		float leftX = 0f;
		float link1X = 3f;
		float link2X = 5f;
		float rightX = 8f;
		float topZ = 0f;
		float bottomZ = -3f;
		float linkClearance = 0.2f;

		RouteSceneBuilder builder = new RouteSceneBuilder();

		RouteSegment top = builder.segment("top", new LinearSegment3(
		        new Vec3(leftX, 0f, topZ),
		        new Vec3(rightX, 0f, topZ)
		));

		RouteSegment rightReturn = builder.segment("rightReturn", new BezierSegment3(
		        new Vec3(rightX, 0f, topZ),
		        new Vec3(rightX + 2f, 0f, topZ),
		        new Vec3(rightX + 2f, 0f, bottomZ),
		        new Vec3(rightX, 0f, bottomZ)
		));

		RouteSegment bottom = builder.segment("bottom", new LinearSegment3(
		        new Vec3(rightX, 0f, bottomZ),
		        new Vec3(leftX, 0f, bottomZ)
		));

		RouteSegment leftReturn = builder.segment("leftReturn", new BezierSegment3(
		        new Vec3(leftX, 0f, bottomZ),
		        new Vec3(leftX - 2f, 0f, bottomZ),
		        new Vec3(leftX - 2f, 0f, topZ),
		        new Vec3(leftX, 0f, topZ)
		));

		RouteSegment link1 = builder.segment("link1", new LinearSegment3(
		        new Vec3(link1X, 0f, topZ - linkClearance),
		        new Vec3(link1X, 0f, bottomZ + linkClearance)
		));

		RouteSegment link2 = builder.segment("link2", new LinearSegment3(
		        new Vec3(link2X, 0f, topZ - linkClearance),
		        new Vec3(link2X, 0f, bottomZ + linkClearance)
		));

		// Rendering specs per segment
		builder.renderWith(top, specToteWidthWise)
		       .renderWith(rightReturn, specToteWidthWise)
		       .renderWith(bottom, specToteWidthWise)
		       .renderWith(leftReturn, specToteWidthWise)
		       .renderWith(link1, specToteLengthWise)
		       .renderWith(link2, specToteLengthWise);

		// Main loop
		builder.connectLoop(top, rightReturn)
		       .connectLoop(rightReturn, bottom)
		       .connectLoop(bottom, leftReturn)
		       .connectLoop(leftReturn, top);

		// Link into bottom (target opening only)
		builder.connectLinkInto(link1, bottom, 5.0f, GuideSide.RIGHT, linkOpeningLength)
		       .connectLinkInto(link2, bottom, 3.0f, GuideSide.RIGHT, linkOpeningLength);

		// Transfers from top into links
		builder.addTransferToLink(
		        top,
		        link1,
		        link1X,
		        toteLength,
		        linkOpeningLength,
		        GuideSide.RIGHT,
		        GuideSide.LEFT,
		        false
		).addTransferToLink(
		        top,
		        link2,
		        link2X,
		        toteLength,
		        linkOpeningLength,
		        GuideSide.RIGHT,
		        GuideSide.LEFT,
		        true
		);

		OneColourStrategyImpl deckColour = new OneColourStrategyImpl(0xFF00FF00);
		OneColourStrategyImpl guidesColour = new OneColourStrategyImpl(0xFFFF00FF);
		OneColourStrategyImpl rollersColour = new OneColourStrategyImpl(0xFF00FFFF);

		TrackAppearance appearance = new TrackAppearance(
		        deckColour,
		        rollersColour,
		        guidesColour,
		        new OneColourStrategyImpl(0xFFFF8800)
		);

		List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
		        tr,
		        builder.getSpecsAndSegments(),
		        appearance
		);

		float rollerYOffset = specToteLengthWise.includeRollers ? specToteLengthWise.rollerHeight : 0f;

		GraphFollowerBehaviour follower = new GraphFollowerBehaviour(
			    top,         					// startSegment
			    null,        					// defaultDecisionProvider
			    2.0f,        					// unitsPerSecond
			    WrapMode.LOOP,  				// wrapMode
			    EnumSet.of(OrientationMode.YAW),// orientationModes
			    0f,        						// yawOffsetRadians
			    rollerYOffset,					// yOffset - raise object on track above the rollers
			    0f,         					// startDistanceAlongSegment,
			    TravelDirection.FORWARD,  		// startDirection
			    tote.getOuterBottomDepth()
			);		
	
		rTote.addBehaviour(follower);
		
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}

	}
	
	private void setupParallelTracks(Tote tote){
		if (tote == null) tote = setupTote();
		
		ToteEnvelope toteEnvelope = new ToteEnvelope(
		        tote.getOuterBottomWidth(),
		        tote.getOuterBottomDepth(),
		        tote.getOuterHeight()
		);

		TrackSpec spec = new TrackSpec(
				toteEnvelope,
		        0.030f,
		        0.040f,
		        0.000f,
		        true,
		        0.050f,
		        0.010f,
		        0.000f,
		        0.5f,
		        1.0f,
		        true,
		        0.080f,
		        0.010f,
		        0.025f,
		        0.018f,
		        0.080f
		);		
		float toteLength = tote.getOuterBottomDepth();
		float openingLength = spec.getOverallWidth();
		// =====================
		// Parallel track layout
		// =====================

		float leftX = 0f;
		float rightX = 8f;

		float epsilon = 0.01f;
		float trackSpacing = spec.getOverallWidth() + epsilon;

		// upper track at Z = 0 (travels +X)
		float upperZ = 0f;

		// lower track below it (travels -X)
		float lowerZ = -trackSpacing;
		// Upper track: +X
		PathSegment3 upperGeometry = new LinearSegment3(
		    new Vec3(leftX, 0f, upperZ),
		    new Vec3(rightX, 0f, upperZ)
		);

		// Lower track: -X (reverse direction)
		PathSegment3 lowerGeometry = new LinearSegment3(
		    new Vec3(rightX, 0f, lowerZ),
		    new Vec3(leftX, 0f, lowerZ)
		);		
		RouteSceneBuilder builder = new RouteSceneBuilder();
		RouteSegment upper = builder.segment("upper", upperGeometry);
		RouteSegment lower = builder.segment("lower", lowerGeometry);

		// transfer near the right end of the upper track
		float transferCentre = 7.5f;

		// land near the start of the lower track
		float targetEntry = 0.5f;

		AlwaysTransferStrategy always = new AlwaysTransferStrategy();
		builder.addDirectTransfer(
		        upper,
		        lower,
		        transferCentre,
		        toteLength,
		        targetEntry,
		        GuideSide.RIGHT,   // open lower-facing rail on upper
		        GuideSide.RIGHT,    // open upper-facing rail on lower
		        always
		);


		float lowerTransferInset = 0.25f;
		float lowerTransferCentre =
		        lower.getGeometry().getTotalLength() - lowerTransferInset - (toteLength * 0.5f);

		float lowerTransferX = rightX - lowerTransferCentre;
		float upperEntryDistance = lowerTransferX - leftX;

		builder.addDirectTransfer(
		        lower,
		        upper,
		        lowerTransferCentre,
		        toteLength,
		        upperEntryDistance,
		        GuideSide.RIGHT,
		        GuideSide.RIGHT,
		        always
		);
	
		builder.renderWith(upper, spec)
	       .renderWith(lower, spec);
		
		OneColourStrategyImpl deckColour = new OneColourStrategyImpl(0xFF00FF00);
		OneColourStrategyImpl guidesColour = new OneColourStrategyImpl(0xFFFF00FF);
		OneColourStrategyImpl rollersColour = new OneColourStrategyImpl(0xFF00FFFF);

		TrackAppearance appearance = new TrackAppearance(
		        deckColour,
		        rollersColour,
		        guidesColour,
		        new OneColourStrategyImpl(0xFFFF8800)
		);

		List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
		        tr,
		        builder.getSpecsAndSegments(),
		        appearance
		);

		float rollerYOffset = spec.includeRollers ? spec.rollerHeight : 0f;

		GraphFollowerBehaviour follower = new GraphFollowerBehaviour(
			    upper,         					// startSegment
			    null,        					// defaultDecisionProvider
			    2.0f,        					// unitsPerSecond
			    WrapMode.PING_PONG,  				// wrapMode
			    EnumSet.of(OrientationMode.YAW),// orientationModes
			    0f,        						// yawOffsetRadians
			    rollerYOffset,					// yOffset - raise object on track above the rollers
			    0f,         					// startDistanceAlongSegment,
			    TravelDirection.FORWARD,  		// startDirection
			    tote.getOuterBottomDepth()
			);		
	
		rTote.addBehaviour(follower);
		
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}		
	}
}
