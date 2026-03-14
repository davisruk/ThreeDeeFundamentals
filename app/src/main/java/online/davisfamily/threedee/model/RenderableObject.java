package online.davisfamily.threedee.model;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;

public class RenderableObject {
	public ObjectTransformation transformation;
	public Mesh mesh;
	public int[] faceColours = {
		    0xFFFF0000, // bottom - red
		    0xFF00FF00, // top - green
		    0xFF0000FF, // front - blue
		    0xFFFFFF00, // right - yellow
		    0xFFFF00FF, // back - magenta
		    0xFF00FFFF  // left - cyan
		};
	
	public RenderableObject(Mesh mesh, ObjectTransformation transform, int[] colours) {
		this.mesh = mesh;
		this.transformation = transform;
		this.faceColours = colours;
	}
}
