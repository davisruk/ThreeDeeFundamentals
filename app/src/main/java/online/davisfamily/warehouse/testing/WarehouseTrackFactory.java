package online.davisfamily.warehouse.testing;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteConnection;
import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.transformation.SpinBehaviour;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.cylinder.CylinderFactory;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.rendering.model.tracks.RouteTrackFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.ConveyorVisualSpeed;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.StraightConveyorSpec;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackDriveType;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.rendering.model.tracks.WarehouseRouteBuilder;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.transfer.TransferMotionConfig;
import online.davisfamily.warehouse.sim.transfer.TransferOutcome;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.mechanism.SteeringConveyorMechanism;
import online.davisfamily.warehouse.sim.transfer.strategy.AlwaysTransferStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.ToggleStrategy;

public class WarehouseTrackFactory {
	public static void setupOvalTrack(ToteGeometry tote, RenderableObject rTote, TriangleRenderer tr, SimulationWorld sim, List<RenderableObject> objects, SelectionInspectionRegistry inspectionRegistry) {

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

	    TrackSpec conveyorWidthWise = TrackSpec.conveyor(
	            widthToteEnvelope,
	            0.030f,
	            0.040f,
	            0.000f,
	            true,
	            0.050f,
	            0.010f,
	            0.005f,
	            0.5f,
	            0.5f,
	            0.012f,
	            0.025f,
	            0.090f,
	            0.250f,
	            0.010f,
	            0.002f,
	            false,
	            0.080f
	    );
	    TrackSpec bottomSupportSpec = new TrackSpec(
	            widthToteEnvelope,
	            conveyorWidthWise.sideClearance,
	            conveyorWidthWise.deckThickness,
	            conveyorWidthWise.deckTopY,
	            conveyorWidthWise.includeGuides,
	            conveyorWidthWise.guideHeight,
	            conveyorWidthWise.guideThickness,
	            conveyorWidthWise.guideGap,
	            conveyorWidthWise.connectionGuideCutback,
	            conveyorWidthWise.targetGuideOpeningLength,
	            TrackDriveType.NONE,
	            false,
	            conveyorWidthWise.rollerPitch,
	            conveyorWidthWise.rollerWidthInset,
	            conveyorWidthWise.rollerHeight,
	            conveyorWidthWise.rollerDepthAlongPath,
	            conveyorWidthWise.conveyorWidthInset,
	            conveyorWidthWise.conveyorBeltThickness,
	            conveyorWidthWise.conveyorReturnDepth,
	            conveyorWidthWise.conveyorMarkerPitch,
	            conveyorWidthWise.conveyorMarkerLength,
	            conveyorWidthWise.conveyorMarkerThickness,
	            conveyorWidthWise.suppressRollersInTransferZones,
	            conveyorWidthWise.suppressGuidesInTransferZones,
	            conveyorWidthWise.sampleStep);

	    float toteLength = tote.getOuterBottomDepth();
	    float toteSpeedUnitsPerSecond = 2.0f;
	    float linkOpeningLength = specToteLengthWise.getGuideJoinOpeningLength();

	    // Path geometry
	    float leftX = 0f;
	    float link1X = 3f;
	    float link2X = 5f;
	    float rightX = 8f;
	    float topZ = 0f;
	    float bottomZ = -3f;

	    WarehouseRouteBuilder builder = new WarehouseRouteBuilder();

	    RouteSegment top = builder.segment("top", new LinearSegment3(
	            new Vec3(leftX, 0f, topZ),
	            new Vec3(rightX, 0f, topZ),
	            false
	    ));

	    RouteSegment rightReturn = builder.segment("rightReturn", new BezierSegment3(
	            new Vec3(rightX, 0f, topZ),
	            new Vec3(rightX + 2f, 0f, topZ),
	            new Vec3(rightX + 2f, 0f, bottomZ),
	            new Vec3(rightX, 0f, bottomZ)
	    ));

	    RouteSegment bottom = builder.segment("bottom", new LinearSegment3(
	            new Vec3(rightX, 0f, bottomZ),
	            new Vec3(leftX, 0f, bottomZ),
	            false
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
	            new Vec3(link1X, 0f, bottomZ + bottomLinkClearance),
	            true
	    ));

	    RouteSegment link2 = builder.segment("link2", new LinearSegment3(
	            new Vec3(link2X, 0f, topZ - topLinkClearance),
	            new Vec3(link2X, 0f, bottomZ + bottomLinkClearance),
	            true
	    ));

	    // Visual trim on the link segments themselves
	    float linkTrimStart = 0.0f;
	    float linkTrimEnd = 0.195f;

	    builder.getMetadata(link1).setRenderTrimStartDistance(linkTrimStart);
	    builder.getMetadata(link1).setRenderTrimEndDistance(linkTrimEnd);

	    builder.getMetadata(link2).setRenderTrimStartDistance(linkTrimStart);
	    builder.getMetadata(link2).setRenderTrimEndDistance(linkTrimEnd);

	    // Connection clearances on neighbouring segments.
	    // These are now explicit rather than implicit.
	    //
	    // Top side: pull the source guides back where totes transfer into the links.
	    // Bottom side: pull the target guides back where the trimmed links join the bottom run.
	    float topConnectionClearance = topLinkClearance;
	    float bottomConnectionClearance = linkTrimEnd;
	    TransferMotionConfig tunedLinkTransfer = new TransferMotionConfig(0.35, 0.12f, 0.75f);

	    // Rendering specs per segment
	    builder.renderWith(top, specToteWidthWise)
	           .renderWith(rightReturn, specToteWidthWise)
	           .renderWith(bottom, bottomSupportSpec)
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
	            topConnectionClearance,
	            tunedLinkTransfer
	    ).addTransferToLink(
	            "transfer_2",
	    		top,
	            link2,
	            link2X,
	            toteLength,
	            linkOpeningLength,
	            GuideSide.RIGHT,
	            GuideSide.LEFT,
	            false,
	            topConnectionClearance
	    );
	    
	    PathSegment3 geometry = bottom.getGeometry(); 
	    System.out.println(String.format("Bottom Length: %.3f", bottom.length()));
	    System.out.println(String.format("Bottom Sample 0: %s", geometry.sampleByDistance(0f)));

	    for (RouteConnection rc: bottom.getPreviousConnections()) {
		    System.out.println(String.format("Bottom Entry Distance %s : %s", rc.getSegment().getLabel(), rc.getSegment().getGeometry().sampleByDistance(0f)));
	    }
	    
	    
	    OneColourStrategyImpl deckColour = new OneColourStrategyImpl(0xFF00FF00);
	    OneColourStrategyImpl guidesColour = new OneColourStrategyImpl(0xFFFF00FF);
	    OneColourStrategyImpl rollersColour = new OneColourStrategyImpl(0xFF00FFFF);
	    OneColourStrategyImpl beltColour = new OneColourStrategyImpl(0xFF2F2F2F);
	    OneColourStrategyImpl beltMarkerColour = new OneColourStrategyImpl(0xFFB8B8B8);

	    TrackAppearance appearance = new TrackAppearance(
	            deckColour,
	            rollersColour,
	            beltColour,
	            beltMarkerColour,
	            guidesColour,
	            new OneColourStrategyImpl(0xFFFF8800)
	    );

	    List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
	            tr,
	            builder.getSpecsAndSegments(),
	            appearance
	    );
	    float bottomConveyorWidth = conveyorWidthWise.getRunningWidth() - (2f * conveyorWidthWise.conveyorWidthInset);
	    RenderableObject bottomConveyor = StraightConveyorFactory.create(
	            "bottom_straight_conveyor",
	            tr,
	            new StraightConveyorSpec(
	                    bottom.length(),
	                    bottomConveyorWidth,
	                    0.018f,
	                    0.012f,
	                    0.042f,
	                    0.060f,
	                    0.002f,
	                    ConveyorVisualSpeed.matchTransport(toteSpeedUnitsPerSecond)),
	            appearance);
	    Vec3 bottomCentre = bottom.getGeometry().sampleByDistance(bottom.length() * 0.5f);
	    bottomConveyor.transformation.xTranslation = bottomCentre.x;
	    bottomConveyor.transformation.yTranslation = 0f;
	    bottomConveyor.transformation.zTranslation = bottomCentre.z;
	    bottomConveyor.transformation.angleY = localXYawFromDirection(
	            bottom.getGeometry().sampleOrientationDirectionByDistance(bottom.length() * 0.5f));

	    float rollerYOffset = specToteWidthWise.getLoadSurfaceHeight() + 0.02f;
	    RouteFollower rtf = new RouteFollower(rTote.id, top, 0f, toteSpeedUnitsPerSecond);
	    Vec3 toteRenderOffsets = new Vec3(0f, rollerYOffset, 0f); 
	    Tote st = new Tote(rTote.id, rtf, rTote, toteRenderOffsets, rTote.yawOffsetRadians);
	    inspectionRegistry.register(rTote, () -> List.of(
	    		"Type: Tote",
	    		"Id: " + st.getId(),
	    		"Motion: " + st.getInteractionMode(),
	    		"Reserved by: " + String.valueOf(st.getReservedByMachineId()),
	    		"Segment: " + (st.getLastSnapshot() == null ? "None" : st.getLastSnapshot().currentSegment().getLabel()),
	    		"Distance: " + formatDistance(st),
	    		"Travel dir: " + st.getRouteFollower().getTravelDirection()
	    ));
	    
	    float member_start = 1f;
	    for (TransferZone tz: builder.getMetadata(top).getTransferZones()) {
	    	attachSteeringMechanismForZone(tz, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
	    	TransferZoneMachine.createTransferZoneMachine(sim, top, member_start, tz, new ToggleStrategy(true));
    	    // hack as we know there are only 2 tzs
    	    member_start = tz.getEndDistance();
        }
        
	    sim.addTrackableObject(st);

	    for (RenderableObject track : tracks) {
	        objects.add(track);
	    }
	    objects.add(bottomConveyor);
	}
	
	public static void setupParallelTracks(ToteGeometry tote, RenderableObject rTote, TriangleRenderer tr, SimulationWorld sim, List<RenderableObject> objects, SelectionInspectionRegistry inspectionRegistry){
		
		ToteEnvelope toteEnvelope = new ToteEnvelope(
		        tote.getOuterBottomWidth(),
		        tote.getOuterBottomDepth(),
		        tote.getOuterHeight()
		);

		TrackSpec rollerSpec = new TrackSpec(
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
		// =====================
		// Parallel track layout
		// =====================

		float leftX = 0f;
		float rightX = 8f;

		float epsilon = 0.01f;
		float trackSpacing = rollerSpec.getOverallWidth() + epsilon;

		// upper track at Z = 0 (travels +X)
		float upperZ = 0f;

		// lower track below it (travels -X)
		float lowerZ = -trackSpacing;
		// Upper track: +X
		PathSegment3 upperGeometry = new LinearSegment3(
		    new Vec3(leftX, 0f, upperZ),
		    new Vec3(rightX, 0f, upperZ),
		    false
		);

		// Lower track: -X (reverse direction)
		PathSegment3 lowerGeometry = new LinearSegment3(
		    new Vec3(rightX, 0f, lowerZ),
		    new Vec3(leftX, 0f, lowerZ),
		    false
		);		
		WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
		RouteSegment upper = builder.segment("upper", upperGeometry);
		RouteSegment lower = builder.segment("lower", lowerGeometry);

		// transfer near the right end of the upper track
		float transferCentre = 7.5f;

		// land near the start of the lower track
		float targetEntry = 0.5f;
		TransferMotionConfig straightAcrossTransfer = new TransferMotionConfig(0.35, 0f, 0f);

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
		        always,
		        straightAcrossTransfer
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
		        always,
		        straightAcrossTransfer
		);
	
		builder.renderWith(upper, rollerSpec)
	       .renderWith(lower, rollerSpec);
		
		OneColourStrategyImpl deckColour = new OneColourStrategyImpl(0xFF00FF00);
		OneColourStrategyImpl guidesColour = new OneColourStrategyImpl(0xFFFF00FF);
		OneColourStrategyImpl rollersColour = new OneColourStrategyImpl(0xFF00FFFF);
		OneColourStrategyImpl beltColour = new OneColourStrategyImpl(0xFF2F2F2F);
		OneColourStrategyImpl beltMarkerColour = new OneColourStrategyImpl(0xFFB8B8B8);

		TrackAppearance appearance = new TrackAppearance(
		        deckColour,
		        rollersColour,
		        beltColour,
		        beltMarkerColour,
		        guidesColour,
		        new OneColourStrategyImpl(0xFFFF8800)
		);

		List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
		        tr,
		        builder.getSpecsAndSegments(),
		        appearance
		);

		float rollerYOffset = rollerSpec.getLoadSurfaceHeight();
	
	    RouteFollower rtf = new RouteFollower(rTote.id, upper, 0f, 2.0f);
	    Vec3 toteRenderOffsets = new Vec3(0f, rollerYOffset + 0.02f, 0f); 
	    Tote st = new Tote(rTote.id, rtf, rTote, toteRenderOffsets, rTote.yawOffsetRadians);
	    inspectionRegistry.register(rTote, () -> List.of(
	    		"Type: Tote",
	    		"Id: " + st.getId(),
	    		"Motion: " + st.getInteractionMode(),
	    		"Segment: " + (st.getLastSnapshot() == null ? "None" : st.getLastSnapshot().currentSegment().getLabel()),
	    		"Distance: " + formatDistance(st),
	    		"Travel dir: " + st.getRouteFollower().getTravelDirection()
	    ));

	    for (TransferZone tz : builder.getMetadata(upper).getTransferZones()) {
	    	attachSteeringMechanismForZone(tz, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
	    	TransferZoneMachine.createTransferZoneMachine(sim, upper, 0f, tz, tz.getDecisionStrategy());
	    }

	    for (TransferZone tz : builder.getMetadata(lower).getTransferZones()) {
	    	attachSteeringMechanismForZone(tz, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
	    	TransferZoneMachine.createTransferZoneMachine(sim, lower, 0f, tz, tz.getDecisionStrategy());
	    }

	    sim.addTrackableObject(st);
		
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}		
	}
	
	public static void setupCylinder(TriangleRenderer tr, SimulationWorld sim, List<RenderableObject> objects) {
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

	public static void setupStraightConveyorTest(TriangleRenderer tr, SimulationWorld sim, List<RenderableObject> objects) {
		TrackAppearance appearance = new TrackAppearance(
				new OneColourStrategyImpl(0xFF4E5A46),
				new OneColourStrategyImpl(0xFF2A2A2A),
				new OneColourStrategyImpl(0xFF2F2F2F),
				new OneColourStrategyImpl(0xFFB8B8B8),
				new OneColourStrategyImpl(0xFF4E5A46),
				new OneColourStrategyImpl(0xFF4E5A46)
		);

		RenderableObject conveyor = StraightConveyorFactory.create(
				"straight_conveyor_test",
				tr,
				new StraightConveyorSpec(
						6.0f,
						0.72f,
						0.18f,
						0.028f,
						0.42f,
						0.06f,
						0.006f,
						ConveyorVisualSpeed.fixed(1.2d)),
				appearance);
		conveyor.transformation.xTranslation = 0f;
		conveyor.transformation.yTranslation = 0.1f;
		conveyor.transformation.zTranslation = -2.5f;
		objects.add(conveyor);

		float smallScale = 0.03f / 0.18f;
		RenderableObject smallConveyor = StraightConveyorFactory.create(
				"straight_conveyor_small_test",
				tr,
				new StraightConveyorSpec(
						6.0f * smallScale,
						0.72f * smallScale,
						0.03f,
						0.028f * smallScale,
						0.42f * smallScale,
						0.06f * smallScale,
						0.006f * smallScale,
						ConveyorVisualSpeed.fixed(1.2d * smallScale)),
				appearance);
		smallConveyor.transformation.xTranslation = 0f;
		smallConveyor.transformation.yTranslation = 0.1f;
		smallConveyor.transformation.zTranslation = -1.6f;
		objects.add(smallConveyor);

		RenderableObject compactTransferConveyor = StraightConveyorFactory.create(
				"straight_conveyor_transfer_test",
				tr,
				new StraightConveyorSpec(
						0.18f,
						0.072f,
						0.018f,
						0.0028f,
						0.042f,
						0.006f,
						0.0006f,
						ConveyorVisualSpeed.fixed(0.12d)),
				appearance);
		compactTransferConveyor.transformation.xTranslation = 0f;
		compactTransferConveyor.transformation.yTranslation = 0.1f;
		compactTransferConveyor.transformation.zTranslation = -0.9f;
		objects.add(compactTransferConveyor);
	}

	private static void attachSteeringMechanismForZone(
			TransferZone zone,
			TriangleRenderer tr,
			List<RenderableObject> objects,
			SelectionInspectionRegistry inspectionRegistry,
			int bodyColourArgb,
			int markerColourArgb) {
		RenderableObject root = createSteeringMechanismRenderable(zone, tr, bodyColourArgb, markerColourArgb);
		float continueYaw = localXYawFromDirection(
				zone.getSourceSegment().getGeometry().sampleOrientationDirectionByDistance(zone.getCentrePoint()));
		Vec3 sourcePosition = zone.getSourceSegment().getGeometry().sampleByDistance(zone.getCentrePoint());
		Vec3 targetPosition = zone.getTargetSegment().getGeometry().sampleByDistance(zone.getTargetStartDistance());
		Vec3 branchVector = targetPosition.subtract(sourcePosition);
		float branchYaw = localXYawFromDirection(new Vec3(branchVector.x, 0f, branchVector.z));
		TransferOutcome initialOutcome =
				zone.getDecisionStrategy() instanceof AlwaysTransferStrategy
						? TransferOutcome.BRANCH
						: TransferOutcome.CONTINUE;
		SteeringConveyorMechanism mechanism = new SteeringConveyorMechanism(
				zone.getId() + "_steering",
				List.of(root),
				continueYaw,
				branchYaw,
				2.8f,
				0.04f,
				initialOutcome);
		zone.addMechanism(mechanism);
		registerTransferZoneInspection(inspectionRegistry, root, zone, mechanism);
		objects.addAll(mechanism.getRenderables());
	}

	private static RenderableObject createSteeringMechanismRenderable(
			TransferZone zone,
			TriangleRenderer tr,
			int bodyColourArgb,
			int markerColourArgb) {
		float centreDistance = zone.getCentrePoint();
		Vec3 centre = zone.getSourceSegment().getGeometry().sampleByDistance(centreDistance);
		float y = 0.01f;
		float mechanismWidth = 0.32f;
		float conveyorLength = Math.max(0.14f, Math.min(0.18f, zone.getLength() * 0.36f));
		float conveyorWidth = Math.min(conveyorLength * 0.4f, mechanismWidth * 0.28f);
		float gap = (mechanismWidth - (2f * conveyorWidth)) / 3f;
		float conveyorOffset = (conveyorWidth + gap) * 0.5f;
		float baseLength = conveyorLength + 0.024f;
		float baseWidth = (2f * conveyorWidth) + gap + 0.028f;
		float baseThickness = 0.004f;
		float rollerRadius = 0.018f;
		float scale = conveyorLength / 0.18f;
		TrackAppearance appearance = new TrackAppearance(
				new OneColourStrategyImpl(bodyColourArgb),
				new OneColourStrategyImpl(0xFF2A2A2A),
				new OneColourStrategyImpl(0xFF2F2F2F),
				new OneColourStrategyImpl(markerColourArgb),
				new OneColourStrategyImpl(bodyColourArgb),
				new OneColourStrategyImpl(bodyColourArgb)
		);

		ObjectTransformation rootTransform = new ObjectTransformation(
				0f, 0f, 0f,
				centre.x, y, centre.z,
				new Mat4());
		RenderableObject root = RenderableObject.create(
				zone.getId() + "_steering_root",
				tr,
				createInvisibleAnchorMesh(),
				rootTransform,
				triangleIndex -> 0,
				false);
		Mesh baseMesh = RollerMeshFactory.createBoxRollerMesh(baseLength, baseThickness, baseWidth);
		RenderableObject base = RenderableObject.create(
				zone.getId() + "_steering_base",
				tr,
				baseMesh,
				new ObjectTransformation(0f, 0f, 0f, 0f, -0.006f, 0f, new Mat4()),
				new OneColourStrategyImpl(markerColourArgb),
				true);
		base.setSelectionTarget(root);

		RenderableObject leftConveyor = StraightConveyorFactory.create(
				zone.getId() + "_steering_left_conveyor",
				tr,
				new StraightConveyorSpec(
						conveyorLength,
						conveyorWidth,
						rollerRadius,
						0.0028f * scale,
						0.042f * scale,
						0.006f * scale,
						0.0006f * scale,
						ConveyorVisualSpeed.fixed(0.12d)),
				appearance);
		leftConveyor.transformation.zTranslation = -conveyorOffset;

		RenderableObject rightConveyor = StraightConveyorFactory.create(
				zone.getId() + "_steering_right_conveyor",
				tr,
				new StraightConveyorSpec(
						conveyorLength,
						conveyorWidth,
						rollerRadius,
						0.0028f * scale,
						0.042f * scale,
						0.006f * scale,
						0.0006f * scale,
						ConveyorVisualSpeed.fixed(0.12d)),
				appearance);
		rightConveyor.transformation.zTranslation = conveyorOffset;

		root.addChild(base);
		root.addChild(leftConveyor);
		root.addChild(rightConveyor);
		return root;
	}

	private static Mesh createInvisibleAnchorMesh() {
		return new Mesh(
				new Vec4[] {
						new Vec4(0f, 0f, 0f, 1f),
						new Vec4(0f, 0f, 0f, 1f),
						new Vec4(0f, 0f, 0f, 1f)
				},
				new int[][] { {0, 1, 2} },
				"anchor");
	}

	private static float localXYawFromDirection(Vec3 direction) {
		Vec3 horizontal = new Vec3(direction.x, 0f, direction.z);
		if (horizontal.lengthSquared() == 0f) {
			return 0f;
		}
		horizontal.mutableNormalize();
		return Vec3.yawFromDirection(horizontal) - (float) (Math.PI / 2.0);
	}

	private static void registerTransferZoneInspection(
			SelectionInspectionRegistry inspectionRegistry,
			RenderableObject renderable,
			TransferZone zone,
			SteeringConveyorMechanism mechanism) {
		inspectionRegistry.register(renderable, () -> List.of(
				"Type: Steering transfer",
				"Id: " + zone.getId(),
				"Source segment: " + zone.getSourceSegment().getLabel(),
				"Target segment: " + zone.getTargetSegment().getLabel(),
				"Start distance: " + String.format("%.3f", zone.getStartDistance()),
				"End distance: " + String.format("%.3f", zone.getEndDistance()),
				"Decision strategy: " + zone.getDecisionStrategy().getClass().getSimpleName(),
				"Mechanism state: " + mechanism.getMotionState(),
				"Mechanism ready CONTINUE: " + mechanism.isReadyFor(TransferOutcome.CONTINUE),
				"Mechanism ready BRANCH: " + mechanism.isReadyFor(TransferOutcome.BRANCH)
		));
	}

	private static String formatDistance(Tote tote) {
		return tote.getLastSnapshot() == null
				? "None"
				: String.format("%.3f", tote.getLastSnapshot().distanceAlongSegment());
	}

}
