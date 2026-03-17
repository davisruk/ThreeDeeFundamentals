package online.davisfamily.threedee.model;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;

public class Mesh {
	public String name;
	public Vec4[] v4Vertices;
	public int[][] triangles;
	
	public Vertex[] viewVerts;
	
	
	public Mesh(Vec4[] vertices, int[][] triangles) {
		name = "none";
		v4Vertices = vertices;
		
		viewVerts = new Vertex[v4Vertices.length];
		for(int v=0; v<v4Vertices.length;v++) {
			viewVerts[v] = new Vertex();
		}

		this.triangles = triangles;
	}
	
	public Mesh(Vec4[] vertices, int[][] triangles, String name) {
		this.name = name;
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
	
	public String toString() {
		StringBuffer buff = new StringBuffer(name + "\r\nVertices:\r\n");
		for (Vec4 v:v4Vertices) {
			buff.append(v);
		}
		buff.append("\r\nTriangles:{");
		for (int i=0; i<triangles.length;i++) {
			buff.append(String.format("{%d, %d, %d},\r\n", triangles[i][0], triangles[i][1], triangles[i][2]));
		}
		buff.deleteCharAt(buff.length()-1);
		buff.append("\r\n}\r\n");
		return buff.toString();
	}
}
