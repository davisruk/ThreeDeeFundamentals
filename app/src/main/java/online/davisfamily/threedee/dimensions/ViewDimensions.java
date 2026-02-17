package online.davisfamily.threedee.dimensions;

public class ViewDimensions {
	public int width;
	public int height;
	public int vpMinX;
	public int vpMinY;
	public int vpMaxXExclusive;
	public int vpMaxYExclusive;
	
	public ViewDimensions (int width, int height, int minX, int minY, int maxX, int maxY) {
		this.width = width;
		this.height = height;
		this.vpMinX = minX;
		this.vpMinY = minY;
		this.vpMaxXExclusive = maxX;
		this.vpMaxYExclusive = maxY;
	}
}
