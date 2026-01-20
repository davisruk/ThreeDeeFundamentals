package online.davisfamily.threedee.cohensutherland;

public class CohenSutherlandLineClip {
	
	static final int INSIDE = 0;
	static final int LEFT   = 1;
	static final int RIGHT  = 2;
	static final int BOTTOM = 4;
	static final int TOP    = 8;

	private int minX;
	private int minY;
	private int maxX;
	private int maxY;

	public CohenSutherlandLineClip(int minX, int minY, int maxX, int maxY) {
		  this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
	}
	
	public static class CohenSutherlandLineClipResult {
		public int x0, y0, x1, y1;
		public boolean accept;
		
		public CohenSutherlandLineClipResult (int x0, int y0, int x1, int y1) {
			this.x0 = x0;this.y0=y0;this.x1=x1;this.y1=y1;accept=false;
		}
/*		
		public String linePointsAsString() {
			return "(" + x0 + ", " + y0 + ")(" + x1 + ", " + y1 + ")";
		}
*/
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
	
	public CohenSutherlandLineClipResult computeViewportLine (int x0, int y0, int x1, int y1) {
		int p0Outcode = computeOutcode(x0, y0);
		int p1Outcode = computeOutcode(x1, y1);
		CohenSutherlandLineClipResult retVal = new CohenSutherlandLineClipResult(x0, y0, x1, y1);

		while (true) {
			if ((p0Outcode | p1Outcode) == 0) {
				// both points are inside the viewport
				// algorithm guarantees that at some point this will happen
				// if any part of the line is inside the viewport
				retVal.accept = true;
				break;
			} else if ((p0Outcode & p1Outcode) != 0) {
				// both points are outside the viewport
				retVal.accept = false; // not strictly necessary
				break;
			} else {
				// some part of the line is within the viewport so clip it from the point of entry
				double x = 0, y = 0;
				double dx = retVal.x1 - retVal.x0;
				double dy = retVal.y1 - retVal.y0;
				int outcodeOut = p0Outcode != 0 ? p0Outcode : p1Outcode;
				
				// find the intersection point of line and viewport
				if ((outcodeOut & TOP) != 0) {
					x = (dy == 0) ? retVal.x0 : retVal.x0 + dx * (minY - retVal.y0) / dy;
					y = minY;
				} else if ((outcodeOut & BOTTOM) != 0) {
					x = (dy == 0) ? retVal.x0 : retVal.x0 + dx * (maxY - retVal.y0) / dy;
					y = maxY;
				} else if ((outcodeOut & LEFT) != 0) {
					y = (dx == 0) ? retVal.y0 : retVal.y0 + dy * (minX - retVal.x0) / dx;
					x = minX;
				} else {
					// RIGHT
					y = (dx == 0) ? retVal.y0 : retVal.y0 + dy * (maxX - retVal.x0) / dx;
					x = maxX;
				}
				
				if (outcodeOut == p0Outcode) {
					retVal.x0 = clamp((int) Math.round(x), minX, maxX);
					retVal.y0 = clamp((int) Math.round(y), minY, maxY);
					p0Outcode = computeOutcode(retVal.x0, retVal.y0); 
				} else {
					retVal.x1 = clamp((int) Math.round(x), minX, maxX);
					retVal.y1 = clamp((int) Math.round(y), minY, maxY);
					p1Outcode = computeOutcode(retVal.x1, retVal.y1);
				}
			}
		}
		return retVal;
	}
}
