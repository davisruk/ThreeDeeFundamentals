package online.davisfamily.threedee.triangles;

public class TriangleRenderer {
	
	int minX, minY, maxX, maxY; // the viewport on the cavas
	int[] pixels; // the world canvas
	int scanlineWidth;
	int worldHeight;
	
	public TriangleRenderer(int[] canvas, int canvasWidth, int minX, int minY, int maxX, int maxY) {
		pixels = canvas;
		scanlineWidth = canvasWidth;
		worldHeight = pixels.length / scanlineWidth;
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
	}
	
	// unsafe sort - make sure [] length is % 2 and >= 4
	private int[] unsafeSortPolygonVerticesByY (int[] v) {		
		boolean stillSwapping = true;
		while (stillSwapping) {
			stillSwapping = false;
			for (int c = 1; c <= v.length - 3; c+=2) {
				if (v[c] > v[c+2]) {
					int tx = v[c+1];
					int ty = v[c+2];
					v [c+1] = v[c-1];
					v [c+2] = v[c];
					v[c-1]=tx;
					v[c]=ty;
					stillSwapping = true;
				}
			}
		}
		return v;
	}
	
	private final static int Ax = 0;
	private final static int Ay = 1;
	private final static int Bx = 2;
	private final static int By = 3;
	private final static int Cx = 4;
	private final static int Cy = 5;
	
	private int setEdgeBoundsForX(int x) {
		if (x < minX) return minX;
		if (x > maxX) return maxX;
		return x;
	}
	
	private int setEdgeBoundsForY(int y) {
		if (y < minY) return minY;
		if (y > maxY) return maxY;
		return y;
	}
	
	public void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
		;
		// Flat vertices array, triangle is formed from 3 x,y points
		int v[] = new int[] {x0, y0, x1, y1, x2, y2};
		// sort the vertices in Y order ascending
		v = unsafeSortPolygonVerticesByY(v);

		
		// array now represents coords / points as A(x,y),B(x,y),C(x,y) where Ay<=By<=Cy
		// A = v[0],v[1]; B=v[2],v[3];c=v[4],v[5]
		// Edges are A->C, A->B, B->C, therefore
		// AC = v[0], v[1], v[4], v[5]
		// AB = v[0], v[1], v[2], v[3]
		// BC = v[2], v[3], v[4], v[5]
		
		// find the x increment for each edge on a scanline (slope)
		int abdx = v[Bx] - v[Ax]; // delta x
		int abdy = v[By] - v[Ay]; // delta y
		float abXinc = (abdy != 0) ? (float)abdx / (float)abdy : 0f;

		int acdx = v[Cx] - v[Ax];
		int acdy = v[Cy] - v[Ay];
		float acXinc = (acdy != 0) ? (float)acdx / (float)acdy : 0f;

		int bcdx = v[Cx] - v[Bx];
		int bcdy = v[Cy] - v[By];
		float bcXinc = (bcdy != 0) ? (float)bcdx / (float)bcdy : 0f;
		

		// fill top half of triangle using AC and AB
		// don't render for flat top or if no part of this half is in the viewport
		if (v[Ay] < v[By])
			fillHalfTriangle(v[Ax],v[Ay], acXinc, v[Ax], v[By], abXinc, colour);

		// fill bottom half using AC and BC
		// For ACx start point we must calculate based on the last y position in the top half of the triangle 
		// = ABy delta * x increment of AC + Ax
		float edge1xIntersection = abdy * acXinc + v[Ax];
		// For ACy start point use the By coord as it is the bottom of top half
		// Don't render for flat bottom or if no part of this half is in the viewport
		if (v[By] < v[Cy])
			fillHalfTriangle(Math.round(edge1xIntersection),v[By], acXinc, v[Bx], v[Cy], bcXinc, colour);
	}
	
	// scanline fill for half a triangle using 2 edges
	// need to provide the xy coords for each edge plus scanline x increment for each edge
	// i.e. when y increases by 1 how many pixels will the x advance by based on the slope
	private void fillHalfTriangle(int e1x, int e1y, float e1xInc, int e2x, int e2y, float e2xInc, int colour) {
		
		int startY = Math.max(e1y, minY);
		int endY = Math.min(e2y, maxY + 1);
		if (startY >= endY) return; // no visible lines
		int ySkip = startY - e1y;
		float edge1xIntersection = ySkip * e1xInc + e1x;
		float edge2xIntersection = ySkip * e2xInc + e2x;
		
		// for each scanline fill in the pixels between the x intersections
		for (int y=startY; y<endY;y++) {
			int leftx = Math.round(edge1xIntersection);
			int rightx = Math.round(edge2xIntersection);

			// ensure we always go left to right
			if(rightx < leftx) {
				int t=leftx;
				leftx = rightx;
				rightx = t;
			}
			
			if (!(rightx < minX || leftx > maxX)) {
				if (leftx < minX) leftx = minX;
				if (rightx > maxX) rightx = maxX;
	
				int row = y * scanlineWidth;
				for (int x = leftx; x<=rightx; x++)
					pixels[row+x] = colour;
	
			}
			// work out the intersections for the next scanline
			edge1xIntersection += e1xInc;
			edge2xIntersection += e2xInc;
			leftx = Math.round(edge1xIntersection);
			rightx = Math.round(edge2xIntersection);
		}		
	}
}
