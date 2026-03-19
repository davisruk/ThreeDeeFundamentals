package online.davisfamily.threedee.rendering.tote;

import java.util.Arrays;

import online.davisfamily.threedee.behaviour.PathFollowerBehaviour;
import online.davisfamily.threedee.behaviour.PingPongRotationBehaviour;
import online.davisfamily.threedee.behaviour.SpinBehaviour;
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
		    0f, 0f, 0f,
		    -halfWidth,
		    tote.outerHeight,
		    0f,
		    new Mat4()
		);

		ObjectTransformation tLidRight = new ObjectTransformation(
		    0f, 0f, 0f,
		    +halfWidth,
		    tote.outerHeight,
		    0f,
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
			mRightLid,
			tLidRight,
			redColour,
			new PingPongRotationBehaviour(Axis.Z, -110f, 0f, 90f)
		);
		
		RenderableObject rLidLeft =  RenderableObject.createWithBehaviours(
			tr,
			mLeftLid,
			tLidLeft,
			yellowColour,
			new PingPongRotationBehaviour(Axis.Z, 0f, 110f, 90f)
		);
		
		// tote transformation (will apply to lid transformations so no need to add pathing to the lids)
		ObjectTransformation tTote = new ObjectTransformation(0.0f,0.0f,0f,0f,0f,-5f, new Mat4());
		// tote mesh
		Mesh mTote = new Mesh(tote.v4Vertices, tote.triangles);
		// renderable tote
		RenderableObject rTote = RenderableObject.createWithChildren(
			tr,
			mTote,
			tTote,
			blueColour,
			Arrays.asList(rLidRight, rLidLeft), FORWARD_DIRECTION.NEGATIVE_X
		);
		
		// tote path
		LinearPath3 path = new LinearPath3(
			    new Vec3(0f, 0f, -3f),
			    new Vec3(2f, 0f, -5f),
			    new Vec3(0f, 0f, -10f),
			    new Vec3(-2f, 0f, -5f),
			    new Vec3(0f, 0f, -3f)
			);		
		rTote.addBehaviour(new PathFollowerBehaviour(path, 2.0f, PathFollowerBehaviour.WrapMode.LOOP));
		return rTote;
	}
}
