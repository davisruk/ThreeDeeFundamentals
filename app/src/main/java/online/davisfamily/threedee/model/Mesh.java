package online.davisfamily.threedee.model;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;

public class Mesh {
	public String name;
	public Vec4[] v4Vertices;
	public int[][] triangles;
	public Vertex[] viewVerts;

	public Vec3 boundsCentre;
	public float boundsRadius;
	
	public Mesh(Vec4[] vertices, int[][] triangles) {
		name = "none";
		v4Vertices = vertices;
		
		viewVerts = new Vertex[v4Vertices.length];
		for(int v=0; v<v4Vertices.length;v++) {
			viewVerts[v] = new Vertex();
		}

		this.triangles = triangles;
		calculateBounds();
	}
	
	public Mesh(Vec4[] vertices, int[][] triangles, String name) {
		this.name = name;
		v4Vertices = vertices;
		
		viewVerts = new Vertex[v4Vertices.length];
		for(int v=0; v<v4Vertices.length;v++) {
			viewVerts[v] = new Vertex();
		}

		this.triangles = triangles;
		calculateBounds();
	}

	public Vertex[] prepareVerticesWithModelView(Mat4 modelView) {
		for(int v=0; v<v4Vertices.length;v++) {
			viewVerts[v] = modelView.multiplyVec(v4Vertices[v], viewVerts[v]);
		}
		return viewVerts;
	}
	
	private void calculateBounds() {
		if (v4Vertices == null || v4Vertices.length == 0) {
			boundsCentre = new Vec3(0f, 0f, 0f);
			boundsRadius = 0f;
			return;
		}
		
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		
		for (Vec4 v:v4Vertices) {
			if (v.x < minX) minX = v.x;
			if (v.y < minY) minY = v.y;
			if (v.z < minZ) minZ = v.z;
			if (v.x > maxX) maxX = v.x;
			if (v.y > maxY) maxY = v.y;
			if (v.z > maxZ) maxZ = v.z;
		}
		
		boundsCentre = new Vec3(
			(minX + maxX) * 0.5f,
			(minY + maxY) * 0.5f,
			(minZ + maxZ) * 0.5f
		);
		
		float maxDistSq = 0f;
		for (Vec4 v:v4Vertices) {
			float dx = v.x - boundsCentre.x;
			float dy = v.y - boundsCentre.y;
			float dz = v.z - boundsCentre.z;
			float distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > maxDistSq) maxDistSq = distSq;
		}
		
		boundsRadius = (float) Math.sqrt(maxDistSq);
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
