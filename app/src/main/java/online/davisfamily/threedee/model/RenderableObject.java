package online.davisfamily.threedee.model;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;

public class RenderableObject {
	public ObjectTransformation transformation;
	public Mesh mesh;
	public int[] faceColours;
	
	public RenderableObject(Mesh mesh, ObjectTransformation transform, int[] colours) {
		this.mesh = mesh;
		this.transformation = transform;
		this.faceColours = colours;
	}
}
