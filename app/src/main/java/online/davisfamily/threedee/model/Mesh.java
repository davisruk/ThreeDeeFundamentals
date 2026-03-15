package online.davisfamily.threedee.model;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;

public class Mesh {
	public Vec4[] v4Vertices;
	public int[][] triangles;
	
	public Vertex[] viewVerts;
	
	
	public Mesh(Vec4[] vertices, int[][] triangles) {
		v4Vertices = vertices;
		
		viewVerts = new Vertex[v4Vertices.length];
		for(int v=0; v<v4Vertices.length;v++) {
			viewVerts[v] = new Vertex();
		}

		this.triangles = triangles;
	}
	
	public Vertex[] prepareVerticesWithModelView(Mat4 modelView) {
		for(int v=0; v<v4Vertices.length;v++) {
			viewVerts[v] = modelView.multiplyVec(v4Vertices[v], viewVerts[v]);
		}
		return viewVerts;
	}
}
