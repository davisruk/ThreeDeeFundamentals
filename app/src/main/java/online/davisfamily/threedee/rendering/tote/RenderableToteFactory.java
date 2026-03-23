package online.davisfamily.threedee.rendering.tote;

import java.util.EnumSet;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.Behaviour.OrientationMode;
import online.davisfamily.threedee.behaviour.PingPongRotationBehaviour;
import online.davisfamily.threedee.behaviour.routing.FirstRouteDecisionProvider;
import online.davisfamily.threedee.behaviour.routing.GraphFollowerBehaviour;
import online.davisfamily.threedee.behaviour.routing.PreferredRouteIdDecisionProvider;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.OneColourStrategyImpl;
import online.davisfamily.threedee.model.tote.LidFactory;
import online.davisfamily.threedee.model.tote.Tote;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.CompositePath3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.RenderableObject.FORWARD_DIRECTION;
import online.davisfamily.threedee.rendering.TriangleRenderer;

public class RenderableToteFactory {

	public static RenderableObject createRenderableTote(TriangleRenderer tr) {
		OneColourStrategyImpl blueColour = new OneColourStrategyImpl(0xFF0000FF);
		OneColourStrategyImpl yellowColour = new OneColourStrategyImpl(0xFFFFFF00);
		OneColourStrategyImpl redColour = new OneColourStrategyImpl(0xFFFF0000);

		Tote tote = new Tote();		
		float openingWidth = 0.371f;   // same value passed into LidFactory
		float halfWidth = openingWidth / 2f;

		// lid meshes
		LidFactory.InterlockingLids lids = LidFactory.buildInterlockingAngularLids(
		    0.371f,  // openingWidth
		    0.547f,  // openingDepth
		    0.020f,  // thickness
		    5,       // toothCount
		    0.010f,  // seamAmplitude
		    0.24f,   // valleyFlatFraction
		    0.24f    // peakFlatFraction
		);

		// left lid
		RenderableObject rLidLeft = createRenderableLid(lids.leftMesh, tote.outerHeight, -halfWidth, tr, yellowColour, true);
		// right lid
		RenderableObject rLidRight = createRenderableLid(lids.rightMesh, tote.outerHeight, +halfWidth, tr, redColour, false);
		
		// tote transformation
		ObjectTransformation tTote = new ObjectTransformation(
			0.0f,0.0f,0f, // rotation xyz
			0f,0f,-5f, // translation xyz
			new Mat4()
		);

		// tote mesh
		Mesh mTote = new Mesh(tote.v4Vertices, tote.triangles);
/*
		// tote path
		PathFollowerBehaviour pathFollower = new PathFollowerBehaviour(
				createCompositePath(),
				2.0f, // unitsPerSecond / speed
				Behaviour.WrapMode.LOOP
			);
*/
		//GraphFollowerBehaviour pathFollower = createGraphFollowerBehaviour();
		
		//System.out.println(pathFollower.describeGraph());
		
		// renderable tote
		RenderableObject rTote = RenderableObject.createWithChildren(
			tr,
			mTote, // mesh
			tTote, // transform
			blueColour,
			List.of(rLidRight, rLidLeft), // children
//			List.of(pathFollower), // behaviours
			FORWARD_DIRECTION.NEGATIVE_X
		);
		
		return rTote;
	}
	

	private static RenderableObject createRenderableLid(Mesh lidMesh, float yOffset, float lidWidth, TriangleRenderer tr, OneColourStrategyImpl colour, boolean isLeft) {
		ObjectTransformation tLid = new ObjectTransformation(
			    0f, 0f, 0f, // rotation xyz
			    lidWidth, yOffset, 0f, // translation xyz
			    new Mat4()
			);

		Behaviour openClose = isLeft ? 
				new PingPongRotationBehaviour(Axis.Z, 0f, 255f, 90f):
				new PingPongRotationBehaviour(Axis.Z, -255f, 0f, 90f);
		
		return RenderableObject.createWithBehaviours(
			tr,
			lidMesh, // mesh
			tLid, // transform
			colour,
			List.of(openClose)
		);
	}
	
	private static CompositePath3 createCompositePath() {
		Vec3 frontLeft  = new Vec3(-4f, 0f,  -4f);
		Vec3 frontRight = new Vec3( 4f, 0f,  -4f);
		Vec3 backRight  = new Vec3( 4f, 5f, -14f);
		Vec3 backLeft   = new Vec3(-4f, 5f, -14f);

		// side sections first
		BezierSegment3 rightSide = new BezierSegment3(
		    frontRight,
		    new Vec3( 6.5f, 1.2f, -6.5f),
		    new Vec3( 6.5f, 3.8f, -11.5f),
		    backRight
		);

		BezierSegment3 leftSide = new BezierSegment3(
		    backLeft,
		    new Vec3(-6.5f, 3.8f, -11.5f),
		    new Vec3(-6.5f, 1.2f, -6.5f),
		    frontLeft
		);

		// connectors built from neighbouring tangents
		BezierSegment3 topConnector = BezierSegment3.createSmoothConnector(
		    backRight,
		    backLeft,
		    rightSide,
		    leftSide,
		    2.5f,
		    2.5f
		);

		BezierSegment3 bottomConnector = BezierSegment3.createSmoothConnector(
		    frontLeft,
		    frontRight,
		    leftSide,
		    rightSide,
		    2.0f,
		    2.0f
		);

		CompositePath3 loopPath = new CompositePath3(
		    bottomConnector,
		    rightSide,
		    topConnector,
		    leftSide
		);
		return loopPath;
	}
	
	private static GraphFollowerBehaviour createGraphFollowerBehaviour() {
		// =========================
		// POINTS
		// =========================

		Vec3 p0 = new Vec3(0f, 0f,  0f);
		Vec3 p1 = new Vec3(4f, 0f,  0f);
		Vec3 p2 = new Vec3(6f, 0f,  0f);   // end of junctionA
		Vec3 p3 = new Vec3(14f, 0f, 0f);   // start of junctionB
		Vec3 p4 = new Vec3(16f, 0f, 0f);   // end of junctionB
		Vec3 p5 = new Vec3(20f, 0f, 0f);

		// Alternative route shape
		Vec3 c1End        = new Vec3(7.0f,  0f, -0.7f);
		Vec3 diagInEnd    = new Vec3(8.2f,  0f, -2.6f);
		Vec3 topStart     = new Vec3(9.3f,  0f, -3f);
		Vec3 topEnd       = new Vec3(10.7f, 0f, -3f);
		Vec3 diagOutStart = new Vec3(11.8f, 0f, -2.6f);
		Vec3 c4Start      = new Vec3(13.0f, 0f, -0.7f);
		float topCurveHandleLength = 0.7f;
		float bottomCurveHandleLength = 0.9f;
		// =========================
		// MAIN ROUTE SEGMENTS
		// =========================

		RouteSegment mainIn = new RouteSegment(
		    0,
			"mainIn",
		    new LinearSegment3(p0, p1)
		);

		RouteSegment junctionA = new RouteSegment(
		    1, 
		    "junctionA",
		    new LinearSegment3(p1, p2)
		);

		RouteSegment mainMiddle = new RouteSegment(
		    2,
		    "mainMiddle",
		    new LinearSegment3(p2, p3)
		);

		RouteSegment junctionB = new RouteSegment(
		    3,
		    "junctionB",
		    new LinearSegment3(p3, p4)
		);

		RouteSegment mainOut = new RouteSegment(
		    4,
		    "mainOut",
		    new LinearSegment3(p4, p5)
		);

		// =========================
		// SUPPORT GEOMETRIES
		// =========================

		LinearSegment3 diagInGeom = new LinearSegment3(c1End, diagInEnd);
		LinearSegment3 intoTopGeom = new LinearSegment3(diagInEnd, topStart);
		LinearSegment3 topGeom = new LinearSegment3(topStart, topEnd);
		LinearSegment3 outOfTopGeom = new LinearSegment3(topEnd, diagOutStart);
		LinearSegment3 diagOutGeom = new LinearSegment3(diagOutStart, c4Start);

		// =========================
		// ALTERNATIVE ROUTE SEGMENTS
		// =========================

		// Curve 1: turnout from main (bottom-right style)
		RouteSegment branchCurve1 = new RouteSegment(
		    5, "branchCurve1",
		    BezierSegment3.createSmoothConnector(
		        p2,
		        c1End,
		        junctionA.getGeometry(),
		        diagInGeom,
		        bottomCurveHandleLength,
		        bottomCurveHandleLength
		    )
		);

		// Diagonal in
		RouteSegment altDiagIn = new RouteSegment(
		    6,
		    "altDiagIn",
		    diagInGeom
		);

		// Curve 2: level out onto top straight (top-left style)
		RouteSegment branchCurve2 = new RouteSegment(
		   7,
		   "branchCurve2",
		    BezierSegment3.createSmoothConnector(
		        diagInEnd,
		        topStart,
		        diagInGeom,
		        topGeom,
		        topCurveHandleLength,
		        topCurveHandleLength
		    )
		);

		// Short top straight
		RouteSegment altStraight = new RouteSegment(
		    8,
		    "altStraight",
		    topGeom
		);

		// Curve 3: peel off from top straight (top-right style)
		RouteSegment rejoinCurve1 = new RouteSegment(
		    9,
		    "rejoinCurve1",
		    BezierSegment3.createSmoothConnector(
		        topEnd,
		        diagOutStart,
		        topGeom,
		        diagOutGeom,
		        topCurveHandleLength,
		        topCurveHandleLength
		    )
		);

		// Diagonal out
		RouteSegment altDiagOut = new RouteSegment(
		    10,
		    "altDiagOut",
		    diagOutGeom
		);

		// Curve 4: smooth rejoin to main (bottom-left style)
		RouteSegment rejoinCurve2 = new RouteSegment(
		    11,
		    "rejoinCurve2",
		    BezierSegment3.createSmoothConnector(
		        c4Start,
		        p3,
		        diagOutGeom,
		        junctionB.getGeometry(),
		        bottomCurveHandleLength,
		        bottomCurveHandleLength
		    )
		);

		// =========================
		// CONNECTIVITY
		// =========================

		// Main line
		mainIn.addNext(junctionA);
		junctionA.addNext(mainMiddle);
		mainMiddle.addNext(junctionB);
		junctionB.addNext(mainOut);

		// Alternative siding
		junctionA.addNext(branchCurve1);
		branchCurve1.addNext(altDiagIn);
		altDiagIn.addNext(branchCurve2);
		branchCurve2.addNext(altStraight);
		altStraight.addNext(rejoinCurve1);
		rejoinCurve1.addNext(altDiagOut);
		altDiagOut.addNext(rejoinCurve2);
		rejoinCurve2.addNext(junctionB);

		// =========================
		// LOCAL DECISION PROVIDERS
		// =========================

		// Forward at junctionA: choose siding.
		// Reverse at junctionA: return toward mainIn.
		junctionA.setDecisionProvider(
		    new PreferredRouteIdDecisionProvider(5, 0)
		);

		// Forward at junctionB: continue to mainOut.
		// Reverse at junctionB: choose rejoinCurve2 so reverse traversal goes back through the siding.
		junctionB.setDecisionProvider(
		    new PreferredRouteIdDecisionProvider(4, 11)
		);

		// =========================
		// FOLLOWER
		// =========================

		GraphFollowerBehaviour follower = new GraphFollowerBehaviour(
		    mainIn,
		    new FirstRouteDecisionProvider(),
		    2.0f,
		    GraphFollowerBehaviour.WrapMode.PING_PONG,
		    EnumSet.of(OrientationMode.YAW),
		    0f
		);

		// Optional debug
		System.out.println(follower.describeGraph());
		
		return follower;
	}
}
