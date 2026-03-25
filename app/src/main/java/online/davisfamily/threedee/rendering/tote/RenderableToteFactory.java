package online.davisfamily.threedee.rendering.tote;

import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.transformation.PingPongRotationBehaviour;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.tote.LidFactory;
import online.davisfamily.threedee.model.tote.Tote;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.RenderableObject.FORWARD_DIRECTION;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;

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
}
