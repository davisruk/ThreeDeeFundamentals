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

	public RenderableObject(TriangleRenderer triangleRenderer, Mesh mesh, ObjectTransformation transform, ColourPickerStrategy colourPicker, List<RenderableObject> childObjects) {
		this.tr = triangleRenderer;
		this.mesh = mesh;
		this.transformation = transform;
		this.colourPicker = colourPicker;
		if (childObjects != null) {
			children = childObjects;
		}
	}

	public void draw(Camera cam, Mat4 perspective, float[]zBuffer, DirectionalLight lightDirection, Mat4 parentModel) {
		transformation.setupModel();
	    Mat4 worldModel = new Mat4();
	    if (parentModel == null) parentModel = Mat4.identity();
	    worldModel.set(parentModel);
	    worldModel.mutableMultiply(transformation.model);

	    tr.drawMesh(this, cam, perspective, zBuffer, lightDirection, worldModel);		

	    for (RenderableObject ro: children) ro.draw(cam, perspective, zBuffer, lightDirection,worldModel);
	}
	
	public int getColour(int triangleIndex) {
		return  colourPicker.chooseColour(triangleIndex);
	}

}
