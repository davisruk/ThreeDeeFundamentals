package online.davisfamily.threedee.rendering;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.lights.DirectionalLight;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.ColourPickerStrategy;
import online.davisfamily.threedee.model.Mesh;

/** Class that contains all information needed to render an object
 * 
 * Mesh: 			Structure represented as Vertices and triangles
 * Transformation:	Used to transform the model in world space
 * Behaviours:		Used to apply specific transformation implementations against the Transformation instance 
 * Children:		Dependent RenderableObjects, transformations are applied relative to parent
 *
 **/
public class RenderableObject {
	public TriangleRenderer tr;
	public ObjectTransformation transformation;
	public Mesh mesh;
	public List<RenderableObject> children;
	public ColourPickerStrategy colourPicker;
	public List<Behaviour> behaviours;
	
	public RenderableObject(TriangleRenderer triangleRenderer, Mesh mesh, ObjectTransformation transform, ColourPickerStrategy colourPicker) {
		this.tr = triangleRenderer;
		this.mesh = mesh;
		this.transformation = transform;
		this.colourPicker = colourPicker;
		children = new ArrayList<RenderableObject>();
		behaviours = new ArrayList<Behaviour>();
	}

	public RenderableObject(TriangleRenderer triangleRenderer, Mesh mesh, ObjectTransformation transform, ColourPickerStrategy colourPicker, List<RenderableObject> childObjects) {
		this.tr = triangleRenderer;
		this.mesh = mesh;
		this.transformation = transform;
		this.colourPicker = colourPicker;
		behaviours = new ArrayList<Behaviour>();
		if (childObjects != null) {
			children = childObjects;
		}
	}

	public void addBehaviour(Behaviour behaviour) {
		behaviours.add(behaviour);
	}
	
	public void update(double dtSeconds) {
		for (Behaviour b: behaviours)
			b.update(this, dtSeconds);
		
		for (RenderableObject r: children)
			r.update(dtSeconds);
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
