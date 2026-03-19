package online.davisfamily.threedee.rendering.tote;

import java.util.List;

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

		// lid transformations
		ObjectTransformation tLidLeft = new ObjectTransformation(
		    0f, 0f, 0f, // rotation xyz
		    -halfWidth, tote.outerHeight, 0f, // translation xyz
		    new Mat4()
		);

		ObjectTransformation tLidRight = new ObjectTransformation(
		    0f, 0f, 0f, // rotation xyz
		    +halfWidth, tote.outerHeight, 0f, // translation xyz
		    new Mat4()
		);

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

		Mesh mLeftLid = lids.leftMesh;
		Mesh mRightLid = lids.rightMesh;
		
		//renderable lids with open / close behaviours
		RenderableObject rLidRight =  RenderableObject.createWithBehaviours(
			tr,
			mRightLid, // mesh
			tLidRight, // transform
			redColour,
			List.of(new PingPongRotationBehaviour(Axis.Z, -110f, 0f, 90f))
		);
		
		RenderableObject rLidLeft =  RenderableObject.createWithBehaviours(
			tr,
			mLeftLid, // mesh
			tLidLeft, // transform
			yellowColour,
			List.of(new PingPongRotationBehaviour(Axis.Z, 0f, 110f, 90f))
		);
		
		// tote transformation (will apply to lid transformations so no need to add pathing to the lids)
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
			List.of(rLidRight, rLidLeft), //children
			List.of(pathFollower), // behaviours
			FORWARD_DIRECTION.NEGATIVE_X
		);
		
		return rTote;
	}
}
