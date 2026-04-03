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
import online.davisfamily.threedee.rendering.selection.SelectionManager;

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
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            List<Behaviour> behaviourObjects,
            FORWARD_DIRECTION forwardDirection,
            boolean selectable) {

        this.tr = triangleRenderer;
        this.mesh = mesh;
        this.transformation = transform;
        this.colourPicker = colourPicker;
        this.yawOffsetRadians = (float) Math.toRadians(forwardDirection.getAngleVal());

        this.children = childObjects != null ? new ArrayList<>(childObjects) : new ArrayList<>();
        this.behaviours = behaviourObjects != null ? new ArrayList<>(behaviourObjects) : new ArrayList<>();
        this.selectable = selectable;
        this.id = id;
    }

    public static RenderableObject create(
    		String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            boolean selectable) {

        return new RenderableObject(
        		id,
        		triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                null,
                FORWARD_DIRECTION.POSITIVE_X,
                selectable);
    }

    public static RenderableObject create(
    		String id,
            TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            FORWARD_DIRECTION forwardDirection,
            boolean selectable) {

        return new RenderableObject(
                id,
        		triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                null,
                forwardDirection,
                selectable);
    }

    public static RenderableObject createWithChildren(
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            boolean selectable) {

        return new RenderableObject(
        		id,
                triangleRenderer,
                mesh,
                transform,
                colourPicker,
                childObjects,
                null,
                FORWARD_DIRECTION.POSITIVE_X,
                selectable);
    }

    public static RenderableObject createWithChildren(
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            FORWARD_DIRECTION forwardDirection,
            boolean selectable) {

        return new RenderableObject(
                id,
        		triangleRenderer,
                mesh,
                transform,
                colourPicker,
                childObjects,
                null,
                forwardDirection,
                selectable);
    }

    public static RenderableObject createWithBehaviours(
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<Behaviour> behaviourObjects,
            boolean selectable) {

        return new RenderableObject(
                id,
        		triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                behaviourObjects,
                FORWARD_DIRECTION.POSITIVE_X,
                selectable);
    }

    public static RenderableObject createWithBehaviours(
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<Behaviour> behaviourObjects,
            FORWARD_DIRECTION forwardDirection,
            boolean selectable) {

        return new RenderableObject(
                id,
        		triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                behaviourObjects,
                forwardDirection,
                selectable);
    }
    
    public static RenderableObject createWithBehaviours(
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            boolean selectable,
            Behaviour... behaviour) {

        List<Behaviour> behaviourObjects = Arrays.asList(behaviour);
    	return new RenderableObject(
                id,
    			triangleRenderer,
                mesh,
                transform,
                colourPicker,
                null,
                behaviourObjects,
                FORWARD_DIRECTION.POSITIVE_X,
                selectable);
    }
    
    public static RenderableObject createWithChildrenAndBehaviours(
            String id,
    		TriangleRenderer triangleRenderer,
            Mesh mesh,
            ObjectTransformation transform,
            ColourPickerStrategy colourPicker,
            List<RenderableObject> childObjects,
            List<Behaviour> behaviourObjects,
            FORWARD_DIRECTION forwardDirection,
            boolean selectable) {

        return new RenderableObject(
                id,
        		triangleRenderer,
                mesh,
                transform,
                colourPicker,
                childObjects,
                behaviourObjects,
                forwardDirection,
                selectable);
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
	
	public void draw(Camera cam, Mat4 perspective, float[]zBuffer, DirectionalLight lightDirection, Mat4 parentModel, SelectionManager selectionManager) {
	    transformation.setupModel();

	    Mat4 worldModel = new Mat4();
	    if (parentModel == null) parentModel = Mat4.identity();
	    worldModel.set(parentModel);
	    worldModel.mutableMultiply(transformation.model);

	    boolean selected = selectionManager != null
	            && selectionManager.getSelected() != null
	            && selectionManager.getSelected() == getSelectionTarget();

	    tr.drawMesh(this, cam, perspective, zBuffer, lightDirection, worldModel, selected);
	    
	    for (RenderableObject ro: children) {
        ro.draw(cam, perspective, zBuffer, lightDirection, worldModel, selectionManager);
	    }
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
	
	public static List<RenderableObject> traverseAndExtractAllWithIdStartsWith(RenderableObject start, String id, List<RenderableObject> result) {
	    
        if (start.id.startsWith(id)) result.add(start);
        
        if (start.children == null || start.children.size() == 0)
        	return result;
        
        for (RenderableObject ro : start.children) {
        	traverseAndExtractAllWithIdStartsWith(ro, id, result);
        }

        return result;
    }	


}
