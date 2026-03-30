package online.davisfamily.threedee.rendering;

import java.util.Arrays;
import java.util.Comparator;

import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;
import online.davisfamily.threedee.rendering.lights.DirectionalLight;
import online.davisfamily.threedee.rendering.selection.SelectionManager;
import online.davisfamily.threedee.rendering.utilities.lines.BresenhamLineUtilities;
import online.davisfamily.threedee.testing.DebugUtils;

public class TriangleRenderer {

	private final int A=0, B=1, C=2;

	private BresenhamLineUtilities bl;
	private InputState is;
	private final SelectionManager selectionManager;
	int minX, minY, maxX, maxY; // the viewport on the cavas
	int[] pixels; // the world canvas
	int pw;
	int ph;
	DebugUtils dbg;
	
	public TriangleRenderer(int[] canvas, int canvasWidth, int minX, int minY, int maxX, int maxY, BresenhamLineUtilities lineDrawer, InputState input, DebugUtils debug, SelectionManager sm) {
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
		this.selectionManager = sm;
	}

	private class ScreenCoord {
		public ScreenCoord() {}
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

	// mutable instances for drawProjectedTriangle
	Vec4 clipA = new Vec4();
	Vec4 clipB = new Vec4();
	Vec4 clipC = new Vec4();
	ScreenCoord screenA = new ScreenCoord();
	ScreenCoord screenB = new ScreenCoord();
	ScreenCoord screenC = new ScreenCoord();
	public void drawProjectedTriangle(Mat4 perspective, Vertex[] v, int colour, float[] zBuff) {
		clipA = perspective.multiplyVec(v[0], clipA);
		clipB = perspective.multiplyVec(v[1], clipB);
		clipC = perspective.multiplyVec(v[2], clipC);
		
		if (clipA.w < 0.1f || clipB.w < 0.1f || clipC.w < 0.1f) return;
		// invW calcs for each point
		screenA.z = 1.0f / clipA.w;	
		screenB.z = 1.0f / clipB.w;
		screenC.z = 1.0f / clipC.w;
		
		float ndcAx = clipA.x * screenA.z;
		float ndcAy = clipA.y * screenA.z;
		
		float ndcBx = clipB.x * screenB.z;
		float ndcBy = clipB.y * screenB.z;

		float ndcCx = clipC.x * screenC.z;
		float ndcCy = clipC.y * screenC.z;

		screenA.x = Math.round((ndcAx * 0.5f + 0.5f) * (pw - 1));
		screenA.y = Math.round((-ndcAy * 0.5f + 0.5f) * (ph - 1));
		screenB.x = Math.round((ndcBx * 0.5f + 0.5f) * (pw - 1));
		screenB.y = Math.round((-ndcBy * 0.5f + 0.5f) * (ph - 1));
		screenC.x = Math.round((ndcCx * 0.5f + 0.5f) * (pw - 1));
		screenC.y = Math.round((-ndcCy * 0.5f + 0.5f) * (ph - 1));

		long abx = (long) (screenB.x - screenA.x), aby = (long) (screenB.y - screenA.y);
		long acx = (long) (screenC.x - screenA.x), acy = (long)(screenC.y - screenA.y);
	
		long cross = abx * acy - aby * acx;
		
		if (cross >=0 ) return;
		if (is.isSet(Mode.FILL_MODEL)) {
			fillTriangle(screenA, screenB, screenC, colour, zBuff);
		}
		
		if (is.isSet(Mode.SHOW_WIREFRAME)) {
		    bl.drawLineUnsafeClipped(screenA.x, screenA.y, screenB.x, screenB.y, 0xFFFFFFFF);
		    bl.drawLineUnsafeClipped(screenB.x, screenB.y, screenC.x, screenC.y, 0xFFFFFFFF);
		    bl.drawLineUnsafeClipped(screenC.x, screenC.y, screenA.x, screenA.y, 0xFFFFFFFF);
		}

	}
	
	public void drawMesh(RenderableObject ro, Camera cam, Mat4 projection, float[] zBuff, DirectionalLight light, Mat4 worldModel) {
		Mat4 mv = new Mat4();
		mv.set(cam.getView());
		mv.mutableMultiply(worldModel);
		Vertex[] viewVerts = ro.mesh.prepareVerticesWithModelView(mv);
		for (int i=0; i<ro.mesh.triangles.length;i++) {
			int[] t = ro.mesh.triangles[i];
			Vertex v0 = viewVerts[t[0]];
			Vertex v1 = viewVerts[t[1]];
			Vertex v2 = viewVerts[t[2]];
			// colour determination should come from the renderable object
			// based on the surface that is being rendered
			// eventually this is likely to be a texture but for raw colours
			// probably need a getColour(i) where i is the triangle index
			
			//int litColour = applyFlatLighting(v0,v1,v2,ro.faceColours[i/2], light);
			int litColour = ro.getColour(i);
			if (selectionManager != null && selectionManager.isSelected(ro)) {
				    litColour = 0xFFFFFF00; // yellow
			} else {
				litColour = applyFlatLighting(v0, v1, v2, litColour, light);
			}

			Vertex.ClippedTriangles ct =  Vertex.clipTriangleNear(v0,v1,v2,0.1f);
			if (ct.t1 != null) drawProjectedTriangle(projection, ct.t1, litColour, zBuff);
			if (ct.t2 != null) drawProjectedTriangle(projection, ct.t2, litColour, zBuff);
		}
	}
	
	private int multiplyColour(int argb, float brightness) {
	    int a = (argb >>> 24) & 0xFF;
	    int r = (argb >>> 16) & 0xFF;
	    int g = (argb >>> 8) & 0xFF;
	    int b = argb & 0xFF;

	    r = Math.min(255, Math.max(0, Math.round(r * brightness)));
	    g = Math.min(255, Math.max(0, Math.round(g * brightness)));
	    b = Math.min(255, Math.max(0, Math.round(b * brightness)));

	    return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private Vec3 ab = new Vec3(); 
	private Vec3 ac = new Vec3();
	public int applyFlatLighting(Vertex a, Vertex b, Vertex c, int baseColour, DirectionalLight light) {
	    ab.setXYZ(b.x - a.x, b.y - a.y, b.z - a.z);
	    ac.setXYZ(c.x - a.x, c.y - a.y, c.z - a.z);
	    ab.mutableCross(ac).mutableNormalize();
	    float diffuse = Math.max(0f, ab.dot(light.getDirection()));
	    float brightness = light.getAmbient() + light.getDiffuseStrength() * diffuse;
	    return multiplyColour(baseColour, Math.min(1f, brightness));
	}
}
