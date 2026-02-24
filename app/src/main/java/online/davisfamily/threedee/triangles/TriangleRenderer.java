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
	
	public TriangleRenderer(int[] canvas, int canvasWidth, int minX, int minY, int maxX, int maxY) {
		pixels = canvas;
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

	private final int A=0, B=1, C=2;
	// Fills a triangle given the x,y,z coords for that triangle
	// Triangle is determined as A(x,y) B(x,y) C(x,y)
	// Edges are determined as AB, AC, BC
	public void fillTriangle(ScreenCoord p1, ScreenCoord p2, ScreenCoord p3, int colour, float[] zBuff) {
		ScreenCoord[] coords = new ScreenCoord[] {p1,p2,p3};
		// Sort coords into y order ascending
		Arrays.sort(coords, Comparator.comparingInt(i -> i.y));
		// Array is now effectively in y order ascending for each edge
		// find the x & z increment for each edge on a scanline (slope)
		int abdx = coords[B].x - coords[A].x; // delta x
		int abdy = coords[B].y - coords[A].y; // delta y
		float abdz = coords[B].z - coords[A].z; // delta z
		float abXinc = (abdy != 0) ? (float)abdx / (float)abdy : 0f; // ABx slope
		float abZinc = (abdy != 0) ? abdz / abdy : 0f; // ABz slope 
		
		int acdx = coords[C].x - coords[A].x;
		int acdy = coords[C].y - coords[A].y;
		float acdz = coords[C].z - coords[A].z;
		float acXinc = (acdy != 0) ? (float)acdx / (float)acdy : 0f;
		float acZinc = (acdy != 0) ? acdz / acdy : 0f;
		
		int bcdx = coords[C].x - coords[B].x;
		int bcdy = coords[C].y - coords[B].y;
		float bcdz = coords[C].z - coords[B].z;
		float bcXinc = (bcdy != 0) ? (float)bcdx / (float)bcdy : 0f;
		float bcZinc = (bcdy != 0) ? bcdz / bcdy : 0f;
		
		// fill top half of triangle (edges AC AB)
		// only need the A & B points as C will never be reached
		if (coords[A].y < coords[B].y)
			fillHalfTriangle(
				coords[A],
				acXinc, acZinc,
				coords[B],
				abXinc, abZinc,
				colour,
				zBuff
			);
		
		// fill bottom half of triangle (edges AC BC)
		// first half of AB edge has been filled so need to
		// find where x & z will be based on the starting y position of BC (so B.y)
		float edge1xIntersection = abdy * acXinc + coords[A].x;
		float edge1zIntersection = abdy * acZinc + coords[A].z;
		ScreenCoord acbyIntersection = new ScreenCoord(
				Math.round(edge1xIntersection), // ACx intersection at By
				coords[B].y, // By 
				edge1zIntersection // ACz intersection at By
		);
		if (coords[B].y <= coords[C].y)
			fillHalfTriangle(
				acbyIntersection,
				acXinc, // ACx slope - amount x advances on AC as we move up y
				acZinc, // ACz slope - amount z advances on AC as we move up y
				coords[2],
				bcXinc, // BCx slope - amount x advances on BC as we move up y
				bcZinc, // BCz slope - amount z advances on BC as we move up y
				colour,
				zBuff
			);
	}
	
	// fills scan lines between 2 points when those 2 points intersect the edges a triangle
	// p1.y must always be equal or less than p2.y
	// for a single triangle call twice, once for top and once for bottom
	private void fillHalfTriangle(ScreenCoord p1, float e1xInc, float e1zInc, ScreenCoord p2, float e2xInc, float e2zInc, int colour, float[] zBuff) {
		int startY = Math.max(p1.y, minY);
		int endY = Math.min(p2.y, maxY + 1);
		if (startY >= endY) return; // no visible lines
		int ySkip = startY - p1.y; // amount p1.y has been clipped
		float p2ySkip = p2.y - startY; // amount p2.y has been clipped
		
		float edge1xIntersection = ySkip * e1xInc + p1.x; // x intersection for first edge at starting y
		float edge1zIntersection = ySkip * e1zInc + p1.z; // z intersection for first edge at starting y
		float edge2xIntersection = p2.x - p2ySkip * e2xInc; // x intersection for second edge at starting y
		float edge2zIntersection = p2.z - p2ySkip * e2zInc;	// z intersection for second edge at starting y	

		// transitory fields, useful if edge1.x > edge2.x
		float leftz = edge1zIntersection;
		float rightz = edge2zIntersection;
		int leftx = Math.round(edge1xIntersection);
		int rightx = Math.round(edge2xIntersection);
		
		// for each scanline fill in the pixels between the x intersections
		for (int y=startY; y<endY;y++) {
			// ensure we always go left to right
			if(rightx < leftx) {
				// swap the x and z values
				int t=leftx;	
				leftx = rightx;
				rightx = t;
				float tz = leftz;
				leftz = rightz;
				rightz = tz;
			}

			// z-buffer - calculate the z pos for current x, y
			int unclippedLeftx = leftx;
			float dlx = rightx - leftx;
			float zInc = (dlx != 0) ? (rightz - leftz) / dlx : 0f;	

			if (!(rightx < minX || leftx > maxX)) {
				if (leftx < minX) leftx = minX;
				if (rightx > maxX) rightx = maxX;
				// advance z if we clipped leftx to minX;
				float z = leftz + (leftx - unclippedLeftx) * zInc;
				int row = y * pw;
				for (int x = leftx; x<=rightx; x++) {
					// store the current z (depth) if it is less than (nearer)
					// than the currently stored depth for this pixel
					// overwrite the colour of the pixel as this object is nearer
					if (z < zBuff[row+x]) {
						zBuff[row+x]=z;
						pixels[row+x] = colour;
					}
					z += zInc;
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


	public class CubeTransformation {
		public CubeTransformation(float xAngle, float yAngle, float zAngle, float xTrans, float yTrans, float zTrans, float xTransInc, float yTransInc, float zTransInc) {
			this.angleX = xAngle;
			this.angleY = yAngle;
			this.angleZ = zAngle;
			this.xTranslation = xTrans;
			this.yTranslation = yTrans;
			this.zTranslation = zTrans;
			this.xTranslationInc = xTransInc; // only used for scene calcs
			this.yTranslationInc = yTransInc;
			this.zTranslationInc = zTransInc;
		}
		public float angleX, angleY, angleZ, xTranslation, yTranslation, zTranslation, xTranslationInc, yTranslationInc, zTranslationInc;
	}
	
	public void drawCube (Vec4[] vertices, int[][]triangles, CubeTransformation tx, int[] colours, float[] zBuff) {
		float aspect = (float)pw / (float)ph;
		Mat4 model = Mat4.translation(tx.xTranslation, tx.yTranslation, tx.zTranslation)
				.multiplyMatrix(Mat4.rotationY(tx.angleY))
				.multiplyMatrix(Mat4.rotationX(tx.angleX))
				.multiplyMatrix(Mat4.rotationZ(tx.angleZ));

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
			zDepth[i] = ndcZ;
			//zDepth[i] = 1.0f / clip.w;
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
			
			fillTriangle(new ScreenCoord(sx[a], sy[a], zDepth[a]), new ScreenCoord(sx[b], sy[b], zDepth[b]), new ScreenCoord(sx[c], sy[c], zDepth[c]), colours[i/2], zBuff);			
		}
	}
}
