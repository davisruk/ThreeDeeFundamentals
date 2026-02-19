package online.davisfamily.threedee.triangles;

import java.util.Arrays;
import java.util.Comparator;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec4;

public class TriangleRenderer {

	private final static int Ax = 0;
	private final static int Ay = 1;
	private final static int Bx = 2;
	private final static int By = 3;
	private final static int Cx = 4;
	private final static int Cy = 5;
	
	int minX, minY, maxX, maxY; // the viewport on the cavas
	int[] pixels; // the world canvas
	int pw;
	int ph;
	float[] depthBuff;
	
	public TriangleRenderer(int[] canvas, float[] depthBuffer, int canvasWidth, int minX, int minY, int maxX, int maxY) {
		pixels = canvas;
		depthBuff = depthBuffer;
		pw = canvasWidth;
		ph = pixels.length / pw;
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
	
	private class ScreenCoord {
		public ScreenCoord(int x, int y, float z) {
			this.x = x; this.y = y; this.z = z;
		}
		int x, y;
		float z;
	}
	
	public void fillTriangle(ScreenCoord t0, ScreenCoord t1, ScreenCoord t2, int colour) {
		ScreenCoord[] coords = new ScreenCoord[] {t0, t1, t2};
		// Sort coords into y order ascending
		Arrays.sort(coords, Comparator.comparingInt(c -> c.y));
		
		// find the x & z increment for each edge on a scanline (slope)
		int abdx = coords[1].x - coords[0].x; // delta x
		int abdy = coords[1].y - coords[0].y; // delta y
		float abdz = coords[1].z - coords[0].z; // delta z
		float abXinc = (abdy != 0) ? (float)abdx / (float)abdy : 0f; // ABx slope
		float abZinc = (abdy != 0) ? abdz / abdy : 0f; // ABz slope 
		
		int acdx = coords[2].x - coords[0].x;
		int acdy = coords[2].y - coords[0].y;
		float acdz = coords[2].z - coords[0].z;
		float acXinc = (acdy != 0) ? (float)acdx / (float)acdy : 0f;
		float acZinc = (acdy != 0) ? acdz / acdy : 0f;
		
		int bcdx = coords[2].x - coords[1].x;
		int bcdy = coords[2].y - coords[1].y;
		float bcdz = coords[2].z - coords[1].z;
		float bcXinc = (bcdy != 0) ? (float)bcdx / (float)bcdy : 0f;
		float bcZinc = (bcdy != 0) ? bcdz / bcdy : 0f;
		
		// fill top half of triangle (edges AC AB)
		if (coords[0].y < coords[1].y)
			fillHalfTriangle(
				coords[0].x, 
				coords[0].y,
				coords[0].z,
				acXinc, acZinc,
				coords[1].x,
				coords[1].y,
				coords[1].z,
				abXinc, abZinc,
				colour
			);
		// fill bottom half of triangle (edges AC BC)
		// first half of AB edge has been filled so need to
		// find where x & z will be based on the starting y position of BC
		float edge1xIntersection = (coords[1].y - coords[0].y) * acXinc + coords[0].x;
		float edge1zIntersection = (coords[1].y - coords[0].y) * acZinc + coords[0].z;
		
		if (coords[1].y <= coords[2].y)
			fillHalfTriangle(
				Math.round(edge1xIntersection), // ACx intersection at By
				coords[1].y, // By 
				edge1zIntersection, // ACz intersection at By
				acXinc, // ACx slope - amount x advances on AC as we move up y
				acZinc, // ACz slope - amount z advances on AC as we move up y
				coords[2].x, // Cx
				coords[2].y, // Cy
				coords[2].z, // Cz
				bcXinc, // BCx slope - amount x advances on BC as we move up y
				bcZinc, // BCz slope - amount z advances on BC as we move up y
				colour
			);
	}
	
	// scanline fill for half a triangle using 2 edges
	// need to provide the xyz coords for each edge plus scanline x&z increment for each edge
	// i.e. when y increases by 1 how many pixels will x & z advance based on their slopes
	private void fillHalfTriangle(int e1x, int e1y, float e1z, float e1xInc, float e1zInc, int e2x, int e2y, float e2z, float e2xInc, float e2zInc, int colour) {
		int startY = Math.max(e1y, minY);
		int endY = Math.min(e2y, maxY + 1);
		if (startY >= endY) return; // no visible lines
		int ySkip = startY - e1y;
		float edge1xIntersection = ySkip * e1xInc + e1x;
		float edge1zIntersection = ySkip * e1zInc + e1z;
		float edge2xIntersection = e2x - (e2y - startY) * e2xInc;
		float edge2zIntersection = e2z - (e2y - startY) * e2zInc;		

		float leftz = edge1zIntersection;
		float rightz = edge2zIntersection;
		int leftx = Math.round(edge1xIntersection);
		int rightx = Math.round(edge2xIntersection);
		
		// for each scanline fill in the pixels between the x intersections
		for (int y=startY; y<endY;y++) {
			// ensure we always go left to right
			if(rightx < leftx) {
				int t=leftx;	
				leftx = rightx;
				rightx = t;
				float tz = leftz;
				leftz = rightz;
				rightz = tz;
			}
			int unclippedLeftx = leftx;
			if (!(rightx < minX || leftx > maxX)) {
				if (leftx < minX) leftx = minX;
				if (rightx > maxX) rightx = maxX;
				int row = y * pw;
				// z-buffer - calculate the zInc for x @ y
				float dlx = rightx - leftx;
				float zInc = (dlx != 0) ? (rightz - leftz) / dlx : 0f;
				// advance leftz if we clipped leftx to minX;
				leftz += (leftx - unclippedLeftx) * zInc;
				for (int x = leftx; x<=rightx; x++) {
					// if current z < depthBuffer replace depthBuffer value and write new colour to pixels
					// interpolating z from here is not possible at the moment
					// need a way to access the Vec4 being processed
					if (leftz < depthBuff[row+x]) {
						depthBuff[row+x]=leftz;
						pixels[row+x] = colour;
					}
					leftz += zInc;
				}
			}
			// work out the intersections for the next scanline
			edge1xIntersection += e1xInc;
			edge1zIntersection += e1zInc;
			edge2xIntersection += e2xInc;
			edge2zIntersection += e2zInc;		
			leftx = Math.round(edge1xIntersection);
			rightx = Math.round(edge2xIntersection);
			leftz = edge1zIntersection;
			rightz = edge2zIntersection;
		}		
	}	
	
	public void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
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
	// i.e. when y increases by 1 how many pixels will x advance based on the slope
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
	
				int row = y * pw;
				for (int x = leftx; x<=rightx; x++) {
					// z-buffer goes here - need to maintain nearest value last written to pixels
					// if current z < depthBuffer replace depthBuffer value and write new colour to pixels
					// interpolating z from here is not possible at the moment
					// need a way to access the Vec4 being processed
					
					pixels[row+x] = colour;
				}
	
			}
			// work out the intersections for the next scanline
			edge1xIntersection += e1xInc;
			edge2xIntersection += e2xInc;
			leftx = Math.round(edge1xIntersection);
			rightx = Math.round(edge2xIntersection);
		}		
	}

	private void clearZBuffer() {
		this.depthBuff = new float[pixels.length];
		Arrays.fill(this.depthBuff, 20.0f);
	}
	
	public void drawCube (Vec4[] vertices, int[][]triangles, float angleX, float angleY, float zTranslation, int[] colours) {
		clearZBuffer();
		float aspect = (float)pw / (float)ph;
		Mat4 model = Mat4.translation(0, 0, zTranslation)
				.multiplyMatrix(Mat4.rotationY(angleY))
				.multiplyMatrix(Mat4.rotationX(angleX));

		Mat4 view = Mat4.identity();
		Mat4 projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		Mat4 mvp = projection.multiplyMatrix(view).multiplyMatrix(model);
		
		int[] sx = new int[vertices.length];
		int[] sy = new int[vertices.length];
		float[] zDepth = new float[vertices.length];
		boolean[] visible = new boolean[vertices.length];
		for (int i = 0; i<vertices.length; i++) {
			Vec4 clip = mvp.multiplyVec(vertices[i]);
	
			if (clip.w <= 0.0001f) { visible[i] = false; continue; }
			
			// clip-space bounds test (conservative)
			if (clip.x < -clip.w || clip.x > clip.w ||
			    clip.y < -clip.w || clip.y > clip.w ||
			    clip.z < -clip.w || clip.z > clip.w) {
			    visible[i] = false;
			    continue;
			}
			
			float ndcX = clip.x / clip.w;
			float ndcY = clip.y / clip.w;
			float ndcZ = clip.z / clip.w;
			sx[i] = (int)((ndcX * 0.5f + 0.5f) * (pw - 1));
			sy[i] = (int)((-ndcY * 0.5f + 0.5f) * (ph - 1));
			//zDepth[i] = clip.z;
			zDepth[i] = ndcZ * 0.5f + 0.5f * 20;
			visible[i] = true;
		}
		
		for (int i = 0; i < triangles.length; i++ ) {
			int[] t = triangles[i];
			int a = t[0];
			int b = t[1];
			int c = t[2];

			if (!visible[a] || !visible[b] || !visible[c]) continue;

			// the vertices may well be within the projection, however
			// they may be part of a surface that is behind others
			// we need to cull any surfaces that need not be rendered
			int ax = sx[a], bx = sx[b], cx = sx[c];
			int ay = sy[a], by = sy[b], cy = sy[c];
			
			long abx = (long) (bx - ax), aby = (long) (by - ay);
			long acx = (long) (cx - ax), acy = (long)(cy - ay);
			
			long cross = abx * acy - aby * acx;
			
			if (cross >=0 ) continue;
			
			// need to provide the z here for z-buffering
			// may need to update fillTriangle to use Vec4 otherwise z interpolation for the pixel is tricky
//			fillTriangle(sx[a], sy[a], sx[b], sy[b], sx[c], sy[c], colours[i/2]);
			fillTriangle(new ScreenCoord(sx[a], sy[a], zDepth[a]), new ScreenCoord(sx[b], sy[b], zDepth[b]), new ScreenCoord(sx[c], sy[c], zDepth[c]), colours[i/2]);			
			//fillTriangle(sx[a], sy[a], zDepth[a], sx[b], sy[b], zDepth[b], sx[c], sy[c], zDepth[c], colours[i/2]);
		}
	}	
}
