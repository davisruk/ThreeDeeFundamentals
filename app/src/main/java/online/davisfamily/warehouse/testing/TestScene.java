package online.davisfamily.warehouse.testing;

import java.util.List;

import javax.swing.JRootPane;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSceneBuilder;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.RouteTrackFactory;
import online.davisfamily.threedee.behaviour.routing.transfer.TransferZone;
import online.davisfamily.threedee.behaviour.transformation.SpinBehaviour;
import online.davisfamily.threedee.camera.CameraPosition;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.cylinder.CylinderFactory;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.rendering.lights.DirectionalLight;
import online.davisfamily.threedee.scene.BaseScene;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.objects.sensors.Sensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensorAreaImpl;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.sim.sensor.MembershipSensor;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferZoneController;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.strategy.AlwaysTransferStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.ToggleStrategy;

public class TestScene extends BaseScene{	

	private RenderableObject rTote;
	private DirectionalLight lightDirection;

	
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		super(pane, dimensions,	CameraPosition.aboveLeft());
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		ToteGeometry t = setupTote();
		setupOvalTrack(t);
		//setupParallelTracks(t);
		//setupCylinder();
	}
		
	@Override
	public void executeChildRenderOperations(double dtSeconds) {
//		if (inputState.isSet(Mode.SHOW_PATH))
//			debug.drawPathForObject(rTote, camera.getView(), projection);
		drawObject(objects, dtSeconds, lightDirection);
	}
	
	private ToteGeometry setupTote() {
		ToteGeometry tote = new ToteGeometry();
		rTote = RenderableToteFactory.createRenderableTote("Tote", tr, tote, true);
		objects.add(rTote);
		return tote;
	}

	private void setupOvalTrack(ToteGeometry tote) {

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
	    float linkOpeningLength = specToteLengthWise.getGuideJoinOpeningLength();

	    // Path geometry
	    float leftX = 0f;
	    float link1X = 3f;
	    float link2X = 5f;
	    float rightX = 8f;
	    float topZ = 0f;
	    float bottomZ = -3f;

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

	    float topLinkClearance = 0.2f;
	    float bottomLinkClearance = 0.0f;

	    RouteSegment link1 = builder.segment("link1", new LinearSegment3(
	            new Vec3(link1X, 0f, topZ - topLinkClearance),
	            new Vec3(link1X, 0f, bottomZ + bottomLinkClearance)
	    ));

	    RouteSegment link2 = builder.segment("link2", new LinearSegment3(
	            new Vec3(link2X, 0f, topZ - topLinkClearance),
	            new Vec3(link2X, 0f, bottomZ + bottomLinkClearance)
	    ));

	    // Visual trim on the link segments themselves
	    float linkTrimStart = 0.0f;
	    float linkTrimEnd = 0.195f;

	    link1.setRenderTrimStartDistance(linkTrimStart);
	    link1.setRenderTrimEndDistance(linkTrimEnd);

	    link2.setRenderTrimStartDistance(linkTrimStart);
	    link2.setRenderTrimEndDistance(linkTrimEnd);

	    // Connection clearances on neighbouring segments.
	    // These are now explicit rather than implicit.
	    //
	    // Top side: pull the source guides back where totes transfer into the links.
	    // Bottom side: pull the target guides back where the trimmed links join the bottom run.
	    float topConnectionClearance = topLinkClearance;
	    float bottomConnectionClearance = linkTrimEnd;

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

	    // Link into bottom:
	    // openingLength controls the guide opening itself
	    // bottomConnectionClearance controls how far the bottom guides pull back
	    builder.connectLinkInto(
	            link1,
	            bottom,
	            5.0f,
	            GuideSide.RIGHT,
	            linkOpeningLength,
	            bottomConnectionClearance
	    ).connectLinkInto(
	            link2,
	            bottom,
	            3.0f,
	            GuideSide.RIGHT,
	            linkOpeningLength,
	            bottomConnectionClearance
	    );

	    // Transfers from top into links:
	    // sourceOpeningLength controls the opening in the top guides
	    // topConnectionClearance controls how far the top guides pull back visually
	    builder.addTransferToLink(
	            "transfer_1",
	    		top,
	            link1,
	            link1X,
	            toteLength,
	            linkOpeningLength,
	            GuideSide.RIGHT,
	            GuideSide.LEFT,
	            false,
	            topConnectionClearance
	    ).addTransferToLink(
	            "transfer_2",
	    		top,
	            link2,
	            link2X,
	            toteLength,
	            linkOpeningLength,
	            GuideSide.RIGHT,
	            GuideSide.LEFT,
	            true,
	            topConnectionClearance
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
	    RouteFollower rtf = new RouteFollower(rTote.id, top, 0f, 2.0f);
	    Tote st = new Tote(rTote.id, rtf, rTote.transformation);
	    
        // below is not enough for transfer zones
        // at the moment detection is done via current route segment of the trackable object
        // transfer zones are not route segments, they are contained by route segments
        // need to compute if the current position on the segment is within the transfer zone's geometry
        // best to create a WindowSensor that takes in the route segment, zoneid, startwindow, end window
        // can use transferzone getStartDistance and getEndDistance for this.
        // if the current position of the snapshot within the segment is within the start / end distances
        // then sensor can fire an detectionevent
	    float member_start = 1f;
	    for (TransferZone tz: top.getTransferZones()) {
        	String id = tz.getId();
    	    WindowSensorAreaImpl m_wsai = new WindowSensorAreaImpl(id + "_member_sensor_area", top, member_start, tz.getStartDistance());
    	    Sensor tzms = new MembershipSensor(id + "_member_sensor", m_wsai);
    	    WindowSensorAreaImpl w_wsai = new WindowSensorAreaImpl(id + "_window_sensor_area", top, tz.getStartDistance(), tz.getEndDistance());
    	    Sensor tzws = new WindowSensor(tz.getId() + "_sensor", w_wsai);
    	    TransferZoneMachine tzm = new TransferZoneMachine("Transfer_Machine_" + tz.getId(), tzms.getId(), tzws.getId());
    	    TransferZoneController tzc = new TransferZoneController(tzm, new ToggleStrategy(false));
        	sim.addController(tzc);
    	    sim.registerListener(DetectionEvent.class, tzc);
    	    sim.addSensor(tzms);
    	    sim.addSensor(tzws);
    	    // hack as we know there are only 2 tzs
    	    member_start = tz.getEndDistance();
        }
        
	    sim.addTrackableObject(st);
/*	    
	    List<RenderableObject> tzl = new ArrayList<>();
	    tzl = processTransferZones(tracks, tzl);
	    for (RenderableObject tz : tzl) {
	    	System.out.println(tz.id);
	    }
*/
	    for (RenderableObject track : tracks) {
	        objects.add(track);
	    }
	}
	
	
	public List<RenderableObject> processTransferZones(List<RenderableObject> rol, List<RenderableObject> result) {
	    for (RenderableObject ro : rol) {
	    	RenderableObject.traverseAndExtractAllWithIdStartsWith(ro, "transfer_", result);
	    }
	    return result;
	}
	
	private void setupParallelTracks(ToteGeometry tote){
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
		        "transfer_1",
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
		        "transfer_2",
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
/*
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
*/	
	    RouteFollower rtf = new RouteFollower(rTote.id, upper, 0f, 2.0f);
	    Tote st = new Tote("tote1", rtf, rTote.transformation);
		
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}		
	}
	
	private void setupCylinder() {
		Mesh m = CylinderFactory.buildCylinder(1f, 3f, 360, true);
		ObjectTransformation ot = new ObjectTransformation(
				0.0f,0.0f,0f, // rotation xyz
				0f,0f,-5f, // translation xyz
				new Mat4()
			);
		RenderableObject rc = RenderableObject.createWithBehaviours(
				"cylinder",
				tr,
				m, // mesh
				ot, // transform
				new OneColourStrategyImpl(0xFF0000FF),
				//FORWARD_DIRECTION.NEGATIVE_X,
				true,
				new SpinBehaviour(0f,0f,0f)
			);
		objects.add(rc);
	}
}
