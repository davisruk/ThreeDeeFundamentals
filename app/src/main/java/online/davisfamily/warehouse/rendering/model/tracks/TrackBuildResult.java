package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.List;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;

public class TrackBuildResult {

	public final Mesh deckMesh;
	public final Mesh guideMesh;
	public final List<Mat4.ObjectTransformation> rollerTransforms;
	public TrackBuildResult(Mesh deckMesh, Mesh guideMesh, List<ObjectTransformation> rollerTransforms) {
		super();
		this.deckMesh = deckMesh;
		this.guideMesh = guideMesh;
		this.rollerTransforms = rollerTransforms;
	}
	
}
