package online.davisfamily.threedee.triangles;

import java.util.Arrays;
import java.util.Comparator;

import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.testing.DebugUtils;

public class TriangleRenderer {

	private final int A=0, B=1, C=2;

	private BresenhamLineUtilities bl;
	private InputState is;
	int minX, minY, maxX, maxY; // the viewport on the cavas
	int[] pixels; // the world canvas
	int pw;
	int ph;
	DebugUtils dbg;
	
	public TriangleRenderer(int[] canvas, int canvasWidth, int minX, int minY, int maxX, int maxY, BresenhamLineUtilities lineDrawer, InputState input, DebugUtils debug) {
		pixels = canvas;
		pw = canvasWidth;
		ph = pixels.length / pw;
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
		this.bl = lineDrawer;
		this.dbg = debug;
		this.is = input;
	}

	public TriangleRenderer(int[] canvas, int canvasWidth, int minX, int minY, int maxX, int maxY, BresenhamLineUtilities lineDrawer) {
		pixels = canvas;
		pw = canvasWidth;
		ph = pixels.length / pw;
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
		this.bl = lineDrawer;
	}
	
// ******* Refactor required - debug and input state should be on constructor                              ******//
// ******* Currently TestScene creates both but SoftwareRenderer creates TriangleRenderer                  ******//
// ******* Either move TriangleRenderer creation to TestScene or Debug and Input State to SoftwareRenderer ******//	
	public void setDebug(DebugUtils du) {
		this.dbg = du;
	}
	
	public void setInputState(InputState is) {
		this.is = is;
	}

// ******
	
	private class ScreenCoord {
		public ScreenCoord(int x, int y, float z) {
			this.x = x; this.y = y; this.z = z;
		}
		int x, y;
		float z;
	}

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
		// find where x & z will be, based on the starting y position of BC (so B.y)
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
				coords[C],
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
					//if (z < zBuff[row+x]) {
					if (z > zBuff[row+x]) {
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


	public void drawCube (Vec4[] vertices, int[][]triangles, Mat4 mvp, int[] colours, float[] zBuff) {
		int[] sx = new int[vertices.length];
		int[] sy = new int[vertices.length];
		float[] sz = new float[vertices.length];
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

			float invW = 1.0f / clip.w;			
			float ndcX = clip.x * invW;
			float ndcY = clip.y * invW;
			float ndcZ = clip.z * invW;

			sx[i] = Math.round((ndcX * 0.5f + 0.5f) * (pw - 1));
			sy[i] = Math.round((-ndcY * 0.5f + 0.5f) * (ph - 1));
			sz[i] = ndcZ;
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
			
			fillTriangle(new ScreenCoord(sx[a], sy[a], sz[a]), new ScreenCoord(sx[b], sy[b], sz[b]), new ScreenCoord(sx[c], sy[c], sz[c]), colours[i/2], zBuff);
		}
	}

	private void drawProjectedTriangle(Mat4 perspective, Vertex a, Vertex b, Vertex c, int colour, float[] zBuff) {
		Vec4 clipA = perspective.multiplyVec(new Vec4(a.x, a.y, a.z, a.w));
		Vec4 clipB = perspective.multiplyVec(new Vec4(b.x, b.y, b.z, b.w));
		Vec4 clipC = perspective.multiplyVec(new Vec4(c.x, c.y, c.z, c.w));
		
		if (clipA.w < 0.1f || clipB.w < 0.1f || clipC.w < 0.1f) return;
		float invWA = 1.0f / clipA.w;	
		float invWB = 1.0f / clipB.w;
		float invWC = 1.0f / clipC.w;
		
		float ndcAx = clipA.x * invWA;
		float ndcAy = clipA.y * invWA;
		float ndcAz = clipA.z * invWA;
		
		float ndcBx = clipB.x * invWB;
		float ndcBy = clipB.y * invWB;
		float ndcBz = clipB.z * invWB;

		float ndcCx = clipC.x * invWC;
		float ndcCy = clipC.y * invWC;
		float ndcCz = clipC.z * invWC;

		int sxA = Math.round((ndcAx * 0.5f + 0.5f) * (pw - 1));
		int syA = Math.round((-ndcAy * 0.5f + 0.5f) * (ph - 1));
		int sxB = Math.round((ndcBx * 0.5f + 0.5f) * (pw - 1));
		int syB = Math.round((-ndcBy * 0.5f + 0.5f) * (ph - 1));
		int sxC = Math.round((ndcCx * 0.5f + 0.5f) * (pw - 1));
		int syC = Math.round((-ndcCy * 0.5f + 0.5f) * (ph - 1));

		long abx = (long) (sxB - sxA), aby = (long) (syB - syA);
		long acx = (long) (sxC - sxA), acy = (long)(syC - syA);
	
		long cross = abx * acy - aby * acx;
		
		if (cross >=0 ) return;
		if (is.isSet(Mode.FILL_MODEL)) {
		//fillTriangle(new ScreenCoord(sxA, syA, ndcAz), new ScreenCoord(sxB, syB, ndcBz), new ScreenCoord(sxC, syC, ndcCz), colour, zBuff);
		fillTriangle(new ScreenCoord(sxA, syA, invWA), new ScreenCoord(sxB, syB, invWB), new ScreenCoord(sxC, syC, invWC), colour, zBuff);
		}
		
		if (is.isSet(Mode.SHOW_WIREFRAME)) {
		    bl.drawLineUnsafeClipped(sxA, syA, sxB, syB, 0xFFFFFFFF);
		    bl.drawLineUnsafeClipped(sxB, syB, sxC, syC, 0xFFFFFFFF);
		    bl.drawLineUnsafeClipped(sxC, syC, sxA, syA, 0xFFFFFFFF);
		}

	}
	
	public void drawCube (Camera cam, Vec4[] vertices, int[][]triangles, Mat4 model, Mat4 perspective, int[] colours, float[] zBuff, boolean drawWireframe) {
		Mat4 mv = new Mat4();
		mv.set(cam.getView());
		mv.mutableMultiply(model);
	    Vertex[] viewVerts = new Vertex[vertices.length];
		for(int v=0; v<vertices.length;v++) {
			viewVerts[v] = new Vertex(mv.multiplyVec(vertices[v]));
		}
		
		for (int i=0; i<triangles.length;i++) {
			int[] t = triangles[i];
			Vertex v0 = viewVerts[t[0]];
			Vertex v1 = viewVerts[t[1]];
			Vertex v2 = viewVerts[t[2]];
			Vertex.ClippedTriangles ct =  Vertex.clipTriangleNear(v0,v1,v2,0.1f);
			if (ct.t1 != null) drawProjectedTriangle(perspective, ct.t1[0], ct.t1[1], ct.t1[2], colours[i/2], zBuff);
			if (ct.t2 != null) drawProjectedTriangle(perspective, ct.t2[0], ct.t2[1], ct.t2[2], colours[i/2], zBuff);
		}
	}

	// helpers for near plane clipping
	// need to refactor this to separate class
	public static class Vertex {
		public float x, y, z, w;
		
		public Vertex(Vec4 v){
			x = v.x;
			y = v.y;
			z = v.z;
			w = v.w;
		}
		
		public Vertex(float x, float y, float z, float w){
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
		}
		
		// determine if this vertex is inside the Z plane
		private static boolean isInsideNear(Vertex v, float near) {
			return v.z < -near;
		}
		
		// return a Vertex clipped to near on Z plane
		private static Vertex intersectNear(Vertex a, Vertex b, float near) {
			float planeZ = -near;
			float t = (planeZ - a.z) / (b.z - a.z);
			return new Vertex(
						a.x + t * (b.x - a.x),
						a.y + t * (b.y - a.y),
						planeZ,
						a.w + t * (b.w - a.w)
					);
		}

		private static class ClippedTriangles {
			Vertex[] t1;
			Vertex[] t2;
		}
		
		// clip triangle to near on Z plane
		// empty result == exclude all vertices
		// returns 2 triangles if 2 vertices cross the Z plane 
		private static ClippedTriangles clipTriangleNear(Vertex v0, Vertex v1, Vertex v2, float near) {
			Vertex [] in = new Vertex[3];
			Vertex [] out = new Vertex[3];
			int inCount = 0, outCount = 0;
			Vertex [] verts = {v0, v1, v2};
			for (Vertex v: verts) {
				if (isInsideNear(v, near)) in[inCount++] = v;
				else out[outCount++] = v;
			}
			
			ClippedTriangles ct = new ClippedTriangles();
			if (inCount == 0) return ct; // none inside
			if (inCount == 3) {
				ct.t1 = new Vertex[] {v0, v1, v2}; // all inside
				return ct;
			}
			
			// 1 vertex inside so clip to ab & ac
			if (inCount == 1) {
				Vertex a = in[0];
				Vertex b = out[0];
				Vertex c = out[1];
				Vertex ab = intersectNear(a,b, near);
				Vertex ac = intersectNear(a,c, near);
				ct.t1 = new Vertex[] {a, ab, ac};
				return ct;
			}

			// 2 vertex inside so clip to ac & bc
			Vertex a = in[0];
			Vertex b = in[1];
			Vertex c = out[0];
			Vertex ac = intersectNear(a,c, near);
			Vertex bc = intersectNear(b,c, near);
			ct.t1 = new Vertex[] {a, b, bc};
			ct.t2 = new Vertex[] {a, bc, ac};
			return ct;
		}

	}
}
