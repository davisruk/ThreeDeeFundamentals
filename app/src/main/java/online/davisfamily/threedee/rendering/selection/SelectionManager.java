package online.davisfamily.threedee.rendering.selection;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.rendering.RenderableObject;

public class SelectionManager {
	private RenderableObject selected;
	private Mat4 worldModel;

	public RenderableObject getSelected() {
		return selected;
	}

	public void setSelected(RenderableObject ro) {
		if (selected == ro) {
			clear();
		} else { 
			this.selected = ro;
		}
	}
	
	public void clear() {
		selected = null;
	}
	
	public boolean isSelected(RenderableObject ro) {
		return selected == ro;
	}

	public Mat4 getWorldModel() {
		return worldModel;
	}

	public void setWorldModel(Mat4 worldModel) {
		System.out.println("setWorldModel called for " + selected.id + " with worldModel = " + worldModel);
		this.worldModel = worldModel;
	}
	
}
