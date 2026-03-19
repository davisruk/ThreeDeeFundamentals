package online.davisfamily.threedee.rendering.tote;

import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.PathFollowerBehaviour;
import online.davisfamily.threedee.behaviour.PingPongRotationBehaviour;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.LidFactory;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.OneColourStrategyImpl;
import online.davisfamily.threedee.model.Tote;
import online.davisfamily.threedee.path.LinearPath3;
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
		    7,       // toothCount
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

		// tote path
		LinearPath3 path = new LinearPath3(
			    new Vec3(0f, 0f, -3f),
			    new Vec3(2f, 0f, -5f),
			    new Vec3(0f, 0f, -10f),
			    new Vec3(-2f, 0f, -5f),
			    new Vec3(0f, 0f, -3f)
			);

		PathFollowerBehaviour pathFollower = new PathFollowerBehaviour(
			path,
			2.0f, // unitsPerSecond / speed
			PathFollowerBehaviour.WrapMode.LOOP
		);
		
		// renderable tote
		RenderableObject rTote = RenderableObject.createWithChildrenAndBehaviours(
			tr,
			mTote, // mesh
			tTote, // transform
			blueColour,
			List.of(rLidRight, rLidLeft), // children
			List.of(pathFollower), // behaviours
			FORWARD_DIRECTION.NEGATIVE_X
		);
		
		return rTote;
	}
	
	private static RenderableObject createRenderableLid(Mesh lidMesh, float yOffset, float lidWidth, TriangleRenderer tr, OneColourStrategyImpl colour, boolean isLeft) {
		double zRotation = isLeft ? 255d : -255d;
		float zRotationRadians = (float)Math.toRadians(zRotation);
		ObjectTransformation tLid = new ObjectTransformation(
			    0f, 0f, zRotationRadians, // rotation xyz
			    lidWidth, yOffset, 0f, // translation xyz
			    new Mat4()
			);
		return RenderableObject.create(
				tr,
				lidMesh, // mesh
				tLid, // transform
				colour
			);

/*
		ObjectTransformation tLid = new ObjectTransformation(
			    0f, 0f, 0f, // rotation xyz
			    lidWidth, yOffset, 0f, // translation xyz
			    new Mat4()
			);

		Behaviour openClose = isLeft ? 
				new PingPongRotationBehaviour(Axis.Z, 0f, 270f, 90f):
				new PingPongRotationBehaviour(Axis.Z, -270f, 0f, 90f);
		
		return RenderableObject.createWithBehaviours(
			tr,
			lidMesh, // mesh
			tLid, // transform
			colour,
			List.of(openClose)
		);
*/
	}	
}
