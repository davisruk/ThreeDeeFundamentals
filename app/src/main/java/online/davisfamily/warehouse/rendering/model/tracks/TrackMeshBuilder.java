package online.davisfamily.warehouse.rendering.model.tracks;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;

public class TrackMeshBuilder {

	private final List<Vec4> vertices = new ArrayList<>();
	private final List<int[]> triangles = new ArrayList<>();
	
	public int addVertex(float x, float y, float z) {
		vertices.add(new Vec4(x, y, z, 1f));
		return vertices.size() - 1;
	}
	
	public void addTriangle(int a, int b, int c) {
		triangles.add(new int[] {a,b,c});
	}
	
	public void addQuad(int a, int b, int c, int d) {
		addTriangle(a,b,c);
		addTriangle(a,c,d);
	}
	
	public Mesh build(String name) {
		return new Mesh(vertices.toArray(new Vec4[0]), triangles.toArray(new int[0][]),name);
	}
}
