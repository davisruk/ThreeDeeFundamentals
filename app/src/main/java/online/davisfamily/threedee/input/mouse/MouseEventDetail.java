package online.davisfamily.threedee.input.mouse;

public class MouseEventDetail {
	public int oldx, x, oldy, y;
	public boolean consumed;
	public boolean hasOld;
	public int dx, dy;
	public boolean rightClick;
	public String toString() {
		return String.format("Old X: %d, Current X: %d, Old Y: %d, Current Y: %d", oldx, x, oldy, y);
	}

}
