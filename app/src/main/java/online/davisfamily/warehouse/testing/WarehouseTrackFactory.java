package online.davisfamily.warehouse.testing;

import java.util.List;

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
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteEnvelope;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.warehouse.rendering.model.tracks.GuideOpening;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
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
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.transfer.mechanism.SteeringConveyorMechanism;
import online.davisfamily.warehouse.sim.transfer.strategy.AlwaysTransferStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.LegacyTransferDecisionStrategyAdapter;
import online.davisfamily.warehouse.sim.transfer.strategy.ToggleStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;

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
	    TrackSpec transferWidthWise = new TrackSpec(
	            widthToteEnvelope,
	            0.030f,
	            0.040f,
	            0.000f,
	            false,
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

	    float leftX = 0f;
	    float link1X = 3f;
	    float link2X = 5f;
	    float rightX = 8f;
	    float topZ = 0f;
	    float bottomZ = -3f;
	    float topLinkClearance = 0.2f;
	    float bottomLinkClearance = 0.0f;
	    float transfer1StartX = link1X - (toteLength * 0.5f);
	    float transfer1EndX = link1X + (toteLength * 0.5f);
	    float transfer2StartX = link2X - (toteLength * 0.5f);
	    float transfer2EndX = link2X + (toteLength * 0.5f);

	    WarehouseRouteBuilder builder = new WarehouseRouteBuilder();

	    RouteSegment topLeft = builder.segment("topLeft", new LinearSegment3(
	            new Vec3(leftX, 0f, topZ),
	            new Vec3(transfer1StartX, 0f, topZ),
	            false
	    ));
	    RouteSegment transfer1 = builder.segment("transfer1", new LinearSegment3(
	            new Vec3(transfer1StartX, 0f, topZ),
	            new Vec3(transfer1EndX, 0f, topZ),
	            false
	    ));
	    RouteSegment topMid = builder.segment("topMid", new LinearSegment3(
	            new Vec3(transfer1EndX, 0f, topZ),
	            new Vec3(transfer2StartX, 0f, topZ),
	            false
	    ));
	    RouteSegment transfer2 = builder.segment("transfer2", new LinearSegment3(
	            new Vec3(transfer2StartX, 0f, topZ),
	            new Vec3(transfer2EndX, 0f, topZ),
	            false
	    ));
	    RouteSegment topRight = builder.segment("topRight", new LinearSegment3(
	            new Vec3(transfer2EndX, 0f, topZ),
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

	    float linkTrimStart = 0.0f;
	    float linkTrimEnd = 0.195f;
	    builder.getMetadata(link1).setRenderTrimStartDistance(linkTrimStart);
	    builder.getMetadata(link1).setRenderTrimEndDistance(linkTrimEnd);
	    builder.getMetadata(link2).setRenderTrimStartDistance(linkTrimStart);
	    builder.getMetadata(link2).setRenderTrimEndDistance(linkTrimEnd);

	    TransferMotionConfig tunedLinkTransfer = new TransferMotionConfig(0.35, 0.12f, 0.75f);
	    TransferMotionConfig defaultLinkTransfer = new TransferMotionConfig(0.35, 0.10f, 0.80f);

	    builder.renderWith(topLeft, specToteWidthWise)
	           .renderWith(transfer1, transferWidthWise)
	           .renderWith(topMid, specToteWidthWise)
	           .renderWith(transfer2, transferWidthWise)
	           .renderWith(topRight, specToteWidthWise)
	           .renderWith(rightReturn, specToteWidthWise)
	           .renderWith(bottom, bottomSupportSpec)
	           .renderWith(leftReturn, specToteWidthWise)
	           .renderWith(link1, specToteLengthWise)
	           .renderWith(link2, specToteLengthWise);

	    builder.connectLoop(topLeft, transfer1)
	           .connectLoop(transfer1, topMid)
	           .connectLoop(topMid, transfer2)
	           .connectLoop(transfer2, topRight)
	           .connectLoop(topRight, rightReturn)
	           .connectLoop(rightReturn, bottom)
	           .connectLoop(bottom, leftReturn)
	           .connectLoop(leftReturn, topLeft);

	    builder.connectLinkInto(
	            link1,
	            bottom,
	            5.0f,
	            GuideSide.RIGHT,
	            linkOpeningLength,
	            linkTrimEnd
	    ).connectLinkInto(
	            link2,
	            bottom,
	            3.0f,
	            GuideSide.RIGHT,
	            linkOpeningLength,
	            linkTrimEnd
	    );

	    TransferTargetDecisionStrategy transfer1Strategy =
	            new LegacyTransferDecisionStrategyAdapter(new ToggleStrategy(true));
	    TransferTargetDecisionStrategy transfer2Strategy =
	            new LegacyTransferDecisionStrategyAdapter(new ToggleStrategy(true));
	    builder.addStandaloneTransfer(
	            "transfer_1",
	            transfer1,
	            List.of(new TransferTarget(link1, 0f, RouteFollower.TravelDirection.FORWARD)),
	            transfer1Strategy,
	            tunedLinkTransfer
	    ).addStandaloneTransfer(
	            "transfer_2",
	            transfer2,
	            List.of(new TransferTarget(link2, 0f, RouteFollower.TravelDirection.FORWARD)),
	            transfer2Strategy,
	            defaultLinkTransfer
	    );

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
	    RouteFollower rtf = new RouteFollower(rTote.id, topLeft, 0f, toteSpeedUnitsPerSecond);
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

	    for (TransferZone tz: builder.getMetadata(transfer1).getTransferZones()) {
	        attachSteeringMechanismForZone(tz, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
	        TransferZoneMachine.createTransferZoneMachine(sim, topLeft, 1.0f, transfer1, tz, tz.getTargetDecisionStrategy());
        }

	    for (TransferZone tz: builder.getMetadata(transfer2).getTransferZones()) {
	        attachSteeringMechanismForZone(tz, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
	        TransferZoneMachine.createTransferZoneMachine(sim, topMid, 0.0f, transfer2, tz, tz.getTargetDecisionStrategy());
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
		float transferSideClearance = 0.030f;
		float transferLength = tote.getOuterBottomDepth();
		ToteEnvelope transferEnvelope = new ToteEnvelope(
				tote.getOuterBottomWidth(),
				tote.getOuterBottomDepth(),
				tote.getOuterHeight()
		);
		TrackSpec transferSpec = new TrackSpec(
				transferEnvelope,
		        transferSideClearance,
		        0.040f,
		        0.000f,
		        false,
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

		float leftX = 0f;
		float rightX = 8f;
		float upperZ = 0f;
		float machineWidth = transferSpec.getOverallWidth();
		float transferEntryDistance = transferLength * 0.5f;

		SteeringTransferMachineGeometry leftMachine = SteeringTransferMachineGeometry.fromTransferWindow(
		        new Vec3(leftX + transferLength, 0f, upperZ),
		        new Vec3(leftX, 0f, upperZ),
		        transferLength,
		        machineWidth);
		float bodyRunningWidth = rollerSpec.getRunningWidth();
		Vec3 leftTargetEntry = leftMachine.leftTargetExitAttachmentPoint(bodyRunningWidth);
		float lowerZ = leftTargetEntry.z;
		SteeringTransferMachineGeometry rightMachine = SteeringTransferMachineGeometry.fromTransferWindow(
		        new Vec3(rightX - transferLength, 0f, lowerZ),
		        new Vec3(rightX, 0f, lowerZ),
		        transferLength,
		        machineWidth);
		Vec3 rightTargetEntry = rightMachine.leftTargetExitAttachmentPoint(bodyRunningWidth);

		WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
		RouteSegment upperBody = builder.segment("upperBody", new LinearSegment3(
		        rightTargetEntry,
		        leftMachine.sourceEntryAttachmentPoint(),
		        false
		));
		RouteSegment upperTransfer = builder.segment("upperTransfer", new LinearSegment3(
		        leftMachine.sourceEntryAttachmentPoint(),
		        leftMachine.sourceExitAttachmentPoint(),
		        false
		));
		RouteSegment lowerBody = builder.segment("lowerBody", new LinearSegment3(
		        leftTargetEntry,
		        rightMachine.sourceEntryAttachmentPoint(),
		        false
		));
		RouteSegment lowerTransfer = builder.segment("lowerTransfer", new LinearSegment3(
		        rightMachine.sourceEntryAttachmentPoint(),
		        rightMachine.sourceExitAttachmentPoint(),
		        false
		));

		builder.connectLoop(upperBody, upperTransfer)
		       .connectLoop(lowerBody, lowerTransfer);

		float machineFacingGapLength = rollerSpec.getGuideJoinOpeningLength();
		builder.getMetadata(upperBody).addGuideOpening(
		        upperBody,
		        new GuideOpening(
		                0f,
		                Math.min(machineFacingGapLength, upperBody.length()),
		                GuideSide.LEFT,
		                GuideOpening.GuideOpeningType.TRANSFER_SOURCE));
		builder.getMetadata(upperBody).addGuideOpening(
		        upperBody,
		        new GuideOpening(
		                Math.max(0f, upperBody.length() - machineFacingGapLength),
		                upperBody.length(),
		                GuideSide.LEFT,
		                GuideOpening.GuideOpeningType.TRANSFER_SOURCE));
		builder.getMetadata(lowerBody).addGuideOpening(
		        lowerBody,
		        new GuideOpening(
		                0f,
		                Math.min(machineFacingGapLength, lowerBody.length()),
		                GuideSide.LEFT,
		                GuideOpening.GuideOpeningType.TRANSFER_SOURCE));
		builder.getMetadata(lowerBody).addGuideOpening(
		        lowerBody,
		        new GuideOpening(
		                Math.max(0f, lowerBody.length() - machineFacingGapLength),
		                lowerBody.length(),
		                GuideSide.LEFT,
		                GuideOpening.GuideOpeningType.TRANSFER_SOURCE));

		TransferMotionConfig straightAcrossTransfer = new TransferMotionConfig(0.35, 0f, 0f);
		TransferTargetDecisionStrategy alwaysTransfer =
		        new LegacyTransferDecisionStrategyAdapter(new AlwaysTransferStrategy());

		builder.addStandaloneTransfer(
		        "transfer_1",
		        upperTransfer,
		        List.of(new TransferTarget(lowerBody, transferEntryDistance, RouteFollower.TravelDirection.FORWARD)),
		        alwaysTransfer,
		        straightAcrossTransfer
		);
		builder.addStandaloneTransfer(
		        "transfer_2",
		        lowerTransfer,
		        List.of(new TransferTarget(upperBody, transferEntryDistance, RouteFollower.TravelDirection.FORWARD)),
		        alwaysTransfer,
		        straightAcrossTransfer
		);

		builder.renderWith(upperBody, rollerSpec)
		       .renderWith(upperTransfer, transferSpec)
		       .renderWith(lowerBody, rollerSpec)
		       .renderWith(lowerTransfer, transferSpec);

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
		RouteFollower rtf = new RouteFollower(rTote.id, upperBody, 0f, 2.0f);
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

		for (TransferZone tz : builder.getMetadata(upperTransfer).getTransferZones()) {
		    attachSteeringMechanismForZone(tz, leftMachine, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
		    TransferZoneMachine.createTransferZoneMachine(
		            sim,
		            upperBody,
		            Math.max(0f, upperBody.length() - 2.0f),
		            upperTransfer,
		            tz,
		            tz.getTargetDecisionStrategy());
		}

		for (TransferZone tz : builder.getMetadata(lowerTransfer).getTransferZones()) {
		    attachSteeringMechanismForZone(tz, rightMachine, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
		    TransferZoneMachine.createTransferZoneMachine(
		            sim,
		            lowerBody,
		            Math.max(0f, lowerBody.length() - 2.0f),
		            lowerTransfer,
		            tz,
		            tz.getTargetDecisionStrategy());
		}

		sim.addTrackableObject(st);
		for (RenderableObject track : tracks) {
		    objects.add(track);
		}
	}

	public static void setupInlineTransferTargets(
			TriangleRenderer tr,
			SimulationWorld sim,
			List<RenderableObject> objects,
			SelectionInspectionRegistry inspectionRegistry) {

		ToteGeometry toteGeometry = new ToteGeometry();
		ToteEnvelope toteEnvelope = new ToteEnvelope(
				toteGeometry.getOuterBottomWidth(),
				toteGeometry.getOuterBottomDepth(),
				toteGeometry.getOuterHeight());

		TrackSpec sourceSpec = new TrackSpec(
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
				TrackDriveType.ROLLER,
				true,
				0.080f,
				0.010f,
				0.025f,
				0.018f,
				0.010f,
				0.012f,
				0.080f,
				0.220f,
				0.050f,
				0.004f,
				true,
				true,
				0.080f
		);
		TrackSpec branchSpec = new TrackSpec(
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
				TrackDriveType.ROLLER,
				true,
				0.080f,
				0.010f,
				0.025f,
				0.018f,
				0.010f,
				0.012f,
				0.080f,
				0.220f,
				0.050f,
				0.004f,
				true,
				false,
				0.080f
		);
		TrackSpec transferSpec = new TrackSpec(
				toteEnvelope,
				0.030f,
				0.040f,
				0.000f,
				false,
				0.050f,
				0.010f,
				0.000f,
				0.5f,
				1.0f,
				TrackDriveType.ROLLER,
				true,
				0.080f,
				0.010f,
				0.025f,
				0.018f,
				0.010f,
				0.012f,
				0.080f,
				0.220f,
				0.050f,
				0.004f,
				true,
				false,
				0.080f
		);

		WarehouseRouteBuilder builder = new WarehouseRouteBuilder();
		float transferLength = toteGeometry.getOuterBottomDepth();
		SteeringTransferMachineGeometry geometry = SteeringTransferMachineGeometry.fromTransferWindow(
				new Vec3(0f, 0f, -transferLength),
				new Vec3(0f, 0f, 0f));
		RouteSegment approach = builder.segment("inline_approach", new LinearSegment3(
				new Vec3(0f, 0f, -8f),
				geometry.transferWindowStartPoint(),
				false));
		RouteSegment transfer = builder.segment("inline_transfer_segment", new LinearSegment3(
				geometry.transferWindowStartPoint(),
				geometry.transferWindowEndPoint(),
				false));
		RouteSegment left = builder.segment("inline_left", new LinearSegment3(
				geometry.leftTargetAttachmentPoint(),
				new Vec3(-4f, 0f, geometry.leftTargetAttachmentPoint().z),
				false));
		RouteSegment right = builder.segment("inline_right", new LinearSegment3(
				geometry.rightTargetAttachmentPoint(),
				new Vec3(4f, 0f, geometry.rightTargetAttachmentPoint().z),
				false));

		builder.renderWith(approach, sourceSpec)
				.renderWith(transfer, transferSpec)
				.renderWith(left, branchSpec)
				.renderWith(right, branchSpec);
		builder.connectLoop(approach, transfer);

		TransferTarget leftTarget = new TransferTarget(left, 0f, RouteFollower.TravelDirection.FORWARD);
		TransferTarget rightTarget = new TransferTarget(right, 0f, RouteFollower.TravelDirection.FORWARD);
		AlternatingInlineTransferStrategy alternatingStrategy = new AlternatingInlineTransferStrategy(leftTarget, rightTarget);
		builder.addStandaloneTransfer(
				"inline_transfer",
				transfer,
				List.of(leftTarget, rightTarget),
				alternatingStrategy,
				new TransferMotionConfig(0.35, 0f, 0f));

		TrackAppearance appearance = new TrackAppearance(
				new OneColourStrategyImpl(0xFF00FF00),
				new OneColourStrategyImpl(0xFF00FFFF),
				new OneColourStrategyImpl(0xFF2F2F2F),
				new OneColourStrategyImpl(0xFFB8B8B8),
				new OneColourStrategyImpl(0xFFFF00FF),
				new OneColourStrategyImpl(0xFFFF8800)
		);
		List<RenderableObject> tracks = RouteTrackFactory.createRenderableTracks(
				tr,
				builder.getSpecsAndSegments(),
				appearance);

		for (TransferZone tz : builder.getMetadata(transfer).getTransferZones()) {
			SteeringConveyorMechanism mechanism =
					attachSteeringMechanismForZone(tz, geometry, tr, objects, inspectionRegistry, 0xFF444444, 0xFFB8B8B8);
			float leftYaw = yawForTransferTarget(leftTarget);
			float rightYaw = yawForTransferTarget(rightTarget);
			alternatingStrategy.bindMechanism(mechanism, leftYaw, rightYaw);
			TransferZoneMachine.createTransferZoneMachine(
					sim,
					approach,
					approach.length() - 2.0f,
					transfer,
					tz,
					tz.getTargetDecisionStrategy());
		}

		float rollerYOffset = sourceSpec.getLoadSurfaceHeight() + 0.02f;
		Tote firstTote = createDebugTote(
				"InlineToteA",
				tr,
				toteGeometry,
				approach,
				5.6f,
				rollerYOffset,
				objects,
				inspectionRegistry);
		Tote secondTote = createDebugTote(
				"InlineToteB",
				tr,
				toteGeometry,
				approach,
				2.0f,
				rollerYOffset,
				objects,
				inspectionRegistry);

		sim.addTrackableObject(firstTote);
		sim.addTrackableObject(secondTote);

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

	private static SteeringConveyorMechanism attachSteeringMechanismForZone(
			TransferZone zone,
			TriangleRenderer tr,
			List<RenderableObject> objects,
			SelectionInspectionRegistry inspectionRegistry,
			int bodyColourArgb,
			int markerColourArgb) {
		RenderableObject root = createSteeringMechanismRenderable(zone, null, tr, bodyColourArgb, markerColourArgb);
		float continueYaw = localXYawFromDirection(
				zone.getSourceSegment().getGeometry().sampleOrientationDirectionByDistance(zone.getCentrePoint()));
		Vec3 sourcePosition = zone.getSourceSegment().getGeometry().sampleByDistance(zone.getCentrePoint());
		Vec3 targetPosition = zone.getTargetSegment().getGeometry().sampleByDistance(zone.getTargetStartDistance());
		Vec3 branchVector = targetPosition.subtract(sourcePosition);
		float branchYaw = localXYawFromDirection(new Vec3(branchVector.x, 0f, branchVector.z));
		TransferOutcome initialOutcome =
				zone.usesLegacyAlwaysTransferStrategy()
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
		return mechanism;
	}

	private static SteeringConveyorMechanism attachSteeringMechanismForZone(
			TransferZone zone,
			SteeringTransferMachineGeometry geometry,
			TriangleRenderer tr,
			List<RenderableObject> objects,
			SelectionInspectionRegistry inspectionRegistry,
			int bodyColourArgb,
			int markerColourArgb) {
		RenderableObject root = createSteeringMechanismRenderable(zone, geometry, tr, bodyColourArgb, markerColourArgb);
		float continueYaw = localXYawFromDirection(
				zone.getSourceSegment().getGeometry().sampleOrientationDirectionByDistance(zone.getCentrePoint()));
		Vec3 sourcePosition = zone.getSourceSegment().getGeometry().sampleByDistance(zone.getCentrePoint());
		Vec3 targetPosition = zone.getTargetSegment().getGeometry().sampleByDistance(zone.getTargetStartDistance());
		Vec3 branchVector = targetPosition.subtract(sourcePosition);
		float branchYaw = localXYawFromDirection(new Vec3(branchVector.x, 0f, branchVector.z));
		TransferOutcome initialOutcome =
				zone.usesLegacyAlwaysTransferStrategy()
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
		return mechanism;
	}

	private static RenderableObject createSteeringMechanismRenderable(
			TransferZone zone,
			SteeringTransferMachineGeometry geometry,
			TriangleRenderer tr,
			int bodyColourArgb,
			int markerColourArgb) {
		float centreDistance = zone.getCentrePoint();
		Vec3 centre = geometry != null
				? geometry.transferWindowCenterPoint()
				: zone.getSourceSegment().getGeometry().sampleByDistance(centreDistance);
		float y = 0.01f;
		SteeringTransferMachineGeometry resolvedGeometry = geometry != null
				? geometry
				: SteeringTransferMachineGeometry.fromTransferWindow(
						new Vec3(centre.x, centre.y, centre.z - (zone.getLength() * 0.5f)),
						new Vec3(centre.x, centre.y, centre.z + (zone.getLength() * 0.5f)));
		float conveyorLength = resolvedGeometry.conveyorLength();
		float conveyorWidth = resolvedGeometry.conveyorWidth();
		float conveyorOffset = resolvedGeometry.conveyorOffset();
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

	private static float yawForTransferTarget(TransferTarget target) {
		Vec3 direction = target.segment().getGeometry().sampleOrientationDirectionByDistance(target.entryDistance());
		if (target.travelDirection() == RouteFollower.TravelDirection.REVERSE) {
			direction = direction.scale(-1f);
		}
		return localXYawFromDirection(direction);
	}

	private static void registerTransferZoneInspection(
			SelectionInspectionRegistry inspectionRegistry,
			RenderableObject renderable,
			TransferZone zone,
			SteeringConveyorMechanism mechanism) {
		String strategyName = zone.getPreferredStrategyName();
		inspectionRegistry.register(renderable, () -> List.of(
				"Type: Steering transfer",
				"Id: " + zone.getId(),
				"Source segment: " + zone.getSourceSegment().getLabel(),
				"Target segment: " + zone.getTargetSegment().getLabel(),
				"Start distance: " + String.format("%.3f", zone.getStartDistance()),
				"End distance: " + String.format("%.3f", zone.getEndDistance()),
				"Decision strategy: " + strategyName,
				"Mechanism state: " + mechanism.getMotionState(),
				"Mechanism ready CONTINUE: " + mechanism.isReadyFor(TransferOutcome.CONTINUE),
				"Mechanism ready BRANCH: " + mechanism.isReadyFor(TransferOutcome.BRANCH)
		));
	}

	private static Tote createDebugTote(
			String id,
			TriangleRenderer tr,
			ToteGeometry toteGeometry,
			RouteSegment segment,
			float startDistance,
			float rollerYOffset,
			List<RenderableObject> objects,
			SelectionInspectionRegistry inspectionRegistry) {
		RenderableObject renderable = RenderableToteFactory.createRenderableTote(id, tr, toteGeometry, true);
		objects.add(renderable);
		Tote tote = new Tote(
				id,
				new RouteFollower(id, segment, startDistance, 1.0d),
				renderable,
				new Vec3(0f, rollerYOffset, 0f),
				renderable.yawOffsetRadians);
		inspectionRegistry.register(renderable, () -> List.of(
				"Type: Tote",
				"Id: " + tote.getId(),
				"Motion: " + tote.getInteractionMode(),
				"Reserved by: " + String.valueOf(tote.getReservedByMachineId()),
				"Segment: " + (tote.getLastSnapshot() == null ? "None" : tote.getLastSnapshot().currentSegment().getLabel()),
				"Distance: " + formatDistance(tote),
				"Travel dir: " + tote.getRouteFollower().getTravelDirection()
		));
		return tote;
	}

	private static final class AlternatingInlineTransferStrategy implements TransferTargetDecisionStrategy {
		private final TransferTarget firstTarget;
		private final TransferTarget secondTarget;
		private SteeringConveyorMechanism mechanism;
		private float firstYawRadians;
		private float secondYawRadians;
		private boolean useFirst = true;

		private AlternatingInlineTransferStrategy(TransferTarget firstTarget, TransferTarget secondTarget) {
			this.firstTarget = firstTarget;
			this.secondTarget = secondTarget;
		}

		private void bindMechanism(
				SteeringConveyorMechanism mechanism,
				float firstYawRadians,
				float secondYawRadians) {
			this.mechanism = mechanism;
			this.firstYawRadians = firstYawRadians;
			this.secondYawRadians = secondYawRadians;
			this.mechanism.setBranchYawRadians(firstYawRadians);
		}

		@Override
		public java.util.Optional<TransferRoutingDecision> decide(Tote tote, TransferZoneMachine machine) {
			TransferTarget selected = useFirst ? firstTarget : secondTarget;
			if (mechanism != null) {
				mechanism.setBranchYawRadians(useFirst ? firstYawRadians : secondYawRadians);
			}
			useFirst = !useFirst;
			return java.util.Optional.of(TransferRoutingDecision.transferTo(selected));
		}
	}

	private static String formatDistance(Tote tote) {
		return tote.getLastSnapshot() == null
				? "None"
				: String.format("%.3f", tote.getLastSnapshot().distanceAlongSegment());
	}

}
