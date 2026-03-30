package online.davisfamily.threedee.rendering.selection;

import online.davisfamily.threedee.rendering.RenderableObject;

public class SelectionManager {
	private RenderableObject selected;

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
}
