package online.davisfamily.threedee.rendering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.appearance.ColourPickerStrategy;
import online.davisfamily.threedee.rendering.lights.DirectionalLight;

/** Class that contains all information needed to render an object
 * 
 * Mesh: 			Structure represented as Vertices and triangles
 * Transformation:	Used to transform the model in world space
 * Behaviours:		Used to apply specific transformation implementations against the Transformation instance 
 * Children:		Dependent RenderableObjects, transformations are applied relative to parent
 *
 **/
public class RenderableObject {
	public String id;
	public TriangleRenderer tr;
	public ObjectTransformation transformation;
	public Mesh mesh;
	public List<RenderableObject> children;
	public ColourPickerStrategy colourPicker;
	public List<Behaviour> behaviours;

	// RayPicking metadata
	public boolean selectable;
	public RenderableObject selectionTarget;
	
	// use below to determine forward direction
	public float yawOffsetRadians;
	public enum FORWARD_DIRECTION{POSITIVE_X(0d), NEGATIVE_X(180d), POSITIVE_Z(90d), NEGATIVE_Z(270d);
		private double angleVal;
		
		FORWARD_DIRECTION(double val){
			this.angleVal=val;
		}
		public double getAngleVal() {
			return angleVal;
		}
	}

    private RenderableObject(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            List<Behaviour> behaviourObjects,
            FORWARD_DIRECTION forwardDirection) {

        this.tr = triangleRenderer;
        this.mesh = mesh;
        this.transformation = transform;
        this.colourPicker = colourPicker;
        this.yawOffsetRadians = (float) Math.toRadians(forwardDirection.getAngleVal());

        this.children = childObjects != null ? new ArrayList<>(childObjects) : new ArrayList<>();
        this.behaviours = behaviourObjects != null ? new ArrayList<>(behaviourObjects) : new ArrayList<>();
    }

    public static RenderableObject create(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                null,
                FORWARD_DIRECTION.POSITIVE_X);
    }

    public static RenderableObject create(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            FORWARD_DIRECTION forwardDirection) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                null,
                forwardDirection);
    }

    public static RenderableObject createWithChildren(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                childObjects,
                null,
                FORWARD_DIRECTION.POSITIVE_X);
    }

    public static RenderableObject createWithChildren(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            FORWARD_DIRECTION forwardDirection) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                childObjects,
                null,
                forwardDirection);
    }

    public static RenderableObject createWithBehaviours(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<Behaviour> behaviourObjects) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                behaviourObjects,
                FORWARD_DIRECTION.POSITIVE_X);
    }

    public static RenderableObject createWithBehaviours(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<Behaviour> behaviourObjects,
            FORWARD_DIRECTION forwardDirection) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                behaviourObjects,
                forwardDirection);
    }
    
    public static RenderableObject createWithBehaviours(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            Behaviour... behaviour) {

        List<Behaviour> behaviourObjects = Arrays.asList(behaviour);
    	return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                behaviourObjects,
                FORWARD_DIRECTION.POSITIVE_X);
    }
    
    public static RenderableObject createWithChildrenAndBehaviours(
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            List<Behaviour> behaviourObjects,
            FORWARD_DIRECTION forwardDirection) {

        return new RenderableObject(
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                childObjects,
                behaviourObjects,
                forwardDirection);
    }
    
	public void addBehaviour(Behaviour behaviour) {
		behaviours.add(behaviour);
	}
	
	public void addChild(RenderableObject child) {
		children.add(child);
	}
	
	public void addAllChildren(List<RenderableObject> moreChildren) {
		children.addAll(moreChildren);
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

	public boolean isSelectable() {
		return selectable;
	}

	public void setSelectable(boolean selectable) {
		this.selectable = selectable;
	}

	public RenderableObject getSelectionTarget() {
		return selectionTarget != null ? selectionTarget : this;
	}

	public void setSelectionTarget(RenderableObject selectionTarget) {
		this.selectionTarget = selectionTarget;
	}

}
