package online.davisfamily.threedee.model;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.triangles.TriangleRenderer;

public class RenderableObject {
	public TriangleRenderer tr;
	public ObjectTransformation transformation;
	public Mesh mesh;
	public List<RenderableObject> children;
	public ColourPickerStrategy colourPicker;
	
	public RenderableObject(TriangleRenderer triangleRenderer, Mesh mesh, ObjectTransformation transform, ColourPickerStrategy colourPicker) {
		this.tr = triangleRenderer;
		this.mesh = mesh;
		this.transformation = transform;
		this.colourPicker = colourPicker;
		children = new ArrayList<RenderableObject>();
	}

	public RenderableObject(TriangleRenderer triangleRenderer, Mesh mesh, ObjectTransformation transform, int[] colours, List<RenderableObject> childObjects, ColourPickerStrategy colourPicker) {
		this.tr = triangleRenderer;
		this.mesh = mesh;
		this.transformation = transform;
		this.colourPicker = colourPicker;
		if (childObjects != null) {
			children = childObjects;
		}
	}

	public void draw(Camera cam, Mat4 perspective, float[]zBuffer, DirectionalLight lightDirection) {
		tr.drawMesh(this, cam, perspective, zBuffer, lightDirection);
		for (RenderableObject ro: children) ro.draw(cam, perspective, zBuffer, lightDirection);
	}
	
	public int getColour(int triangleIndex) {
		return  colourPicker.chooseColour(triangleIndex);
	}

}
