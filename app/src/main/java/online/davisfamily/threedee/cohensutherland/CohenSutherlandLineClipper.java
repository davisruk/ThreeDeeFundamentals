package online.davisfamily.threedee.cohensutherland;

public class CohenSutherlandLineClipper {
	
	static final int INSIDE = 0;
	static final int LEFT   = 1;
	static final int RIGHT  = 2;
	static final int BOTTOM = 4;
	static final int TOP    = 8;

	private int minX;
	private int minY;
	private int maxX;
	private int maxY;

	public CohenSutherlandLineClipper(int minX, int minY, int maxX, int maxY) {
		  this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
	}
	
	public static class LineClipResults {
		public int x0, y0, x1, y1;
		
		public LineClipResults() {
		}
	}
	
	private int computeOutcode(int x, int y) {
		// works out if a point is inside the viewport
		// if not inside returns where on the outside
		
		int outCode = INSIDE;
		
		if (x < minX) outCode |= LEFT;
		else if (x > maxX) outCode |= RIGHT;
		
		if (y < minY) outCode |= TOP;
		else if (y > maxY) outCode |= BOTTOM;
		
		return outCode;
	}
	
/*
	private String linePointsAsString (int x0, int y0, int x1, int y1) {
		return "(" + x0 + ", " + y0 + ")(" + x1 + ", " + y1 + ")";
	}
*/
	
	// prevent +1 rounding issues
	private int clamp(int v, int lo, int hi) {
		return (v < lo) ? lo : (v > hi) ? hi : v;
	}
	
	public boolean computeViewportLine(int x0, int y0, int x1, int y1, LineClipResults results) {
		results.x0 = x0;results.y0 = y0;results.x1=x1;results.y1=y1;
		int p0Outcode = computeOutcode(x0, y0);
		int p1Outcode = computeOutcode(x1, y1);

		while (true) {
			if ((p0Outcode | p1Outcode) == 0) {
				// both points are inside the viewport
				// algorithm guarantees that at some point this will happen
				// if any part of the line is inside the viewport
				return true;
			} else if ((p0Outcode & p1Outcode) != 0) {
				// both points are outside the viewport
				return false; // not strictly necessary
			} else {
				// some part of the line is within the viewport so clip it from the point of entry
				double x = 0, y = 0;
				double dx = x1 - x0;
				double dy = y1 - y0;
				int outcodeOut = p0Outcode != 0 ? p0Outcode : p1Outcode;
				
				// find the intersection point of line and viewport
				if ((outcodeOut & TOP) != 0) {
					x = (dy == 0) ? x0 : x0 + dx * (minY - y0) / dy;
					y = minY;
				} else if ((outcodeOut & BOTTOM) != 0) {
					x = (dy == 0) ? x0 : x0 + dx * (maxY - y0) / dy;
					y = maxY;
				} else if ((outcodeOut & LEFT) != 0) {
					y = (dx == 0) ? y0 : y0 + dy * (minX - x0) / dx;
					x = minX;
				} else {
					// RIGHT
					y = (dx == 0) ? y0 : y0 + dy * (maxX - x0) / dx;
					x = maxX;
				}
				
				if (outcodeOut == p0Outcode) {
					x0 = clamp((int) Math.round(x), minX, maxX);
					y0 = clamp((int) Math.round(y), minY, maxY);
					p0Outcode = computeOutcode(x0, y0); 
				} else {
					x1 = clamp((int) Math.round(x), minX, maxX);
					y1 = clamp((int) Math.round(y), minY, maxY);
					p1Outcode = computeOutcode(x1, y1);
				}
			}
		}
	}
}
