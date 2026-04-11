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
import online.davisfamily.threedee.rendering.utilities.lines.BresenhamLineUtilities;
import online.davisfamily.threedee.rendering.utilities.lines.BresenhamLineUtilities.ClippedLine;
import online.davisfamily.threedee.testing.DebugUtils;

public class TriangleRenderer {

	private final int A=0, B=1, C=2;

	private BresenhamLineUtilities bl;
	private InputState is;
	private boolean[] selectedMask;
	int minX, minY, maxX, maxY; // the viewport on the cavas
	int[] pixels; // the world canvas
	int pw;
	int ph;
	DebugUtils dbg;
	
	private final Vec4[] bboxLocal = {
		    new Vec4(), new Vec4(), new Vec4(), new Vec4(),
		    new Vec4(), new Vec4(), new Vec4(), new Vec4()
		};

		private final Vec4[] bboxWorld = {
		    new Vec4(), new Vec4(), new Vec4(), new Vec4(),
		    new Vec4(), new Vec4(), new Vec4(), new Vec4()
		};

		private static final int[][] BBOX_EDGES = {
		    {0,1}, {1,2}, {2,3}, {3,0},
		    {4,5}, {5,6}, {6,7}, {7,4},
		    {0,4}, {1,5}, {2,6}, {3,7}
		};

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
		selectedMask = new boolean[pw * ph];
	}

	public void clearSelectedMask() {
	    Arrays.fill(selectedMask, false);
	}
	
	private class ScreenCoord {
		public ScreenCoord() {}
		public ScreenCoord(float x, float y, float z) {
			this.x = x; this.y = y; this.z = z;
		}
		float x, y;
		float z;
	}

	// Fills a triangle given the x,y,z coords for that triangle
	// Triangle is determined as A(x,y) B(x,y) C(x,y)
	// Edges are determined as AB, AC, BC
	public void fillTriangle(ScreenCoord p1, ScreenCoord p2, ScreenCoord p3, int colour, float[] zBuff, boolean selected) {
		ScreenCoord[] coords = new ScreenCoord[] {p1,p2,p3};
		// Sort coords into y order ascending
		Arrays.sort(coords, Comparator.comparingDouble(i -> i.y));
		// Array is now effectively in y order ascending for each edge
		// find the x & z increment for each edge on a scanline (slope)
		float abdx = coords[B].x - coords[A].x; // delta x
		float abdy = coords[B].y - coords[A].y; // delta y
		float abdz = coords[B].z - coords[A].z; // delta z
		float abXinc = (abdy != 0f) ? abdx / abdy : 0f; // ABx slope
		float abZinc = (abdy != 0) ? abdz / abdy : 0f; // ABz slope 
		
		float acdx = coords[C].x - coords[A].x;
		float acdy = coords[C].y - coords[A].y;
		float acdz = coords[C].z - coords[A].z;
		float acXinc = (acdy != 0f) ? acdx / acdy : 0f;
		float acZinc = (acdy != 0) ? acdz / acdy : 0f;
		
		float bcdx = coords[C].x - coords[B].x;
		float bcdy = coords[C].y - coords[B].y;
		float bcdz = coords[C].z - coords[B].z;
		float bcXinc = (bcdy != 0f) ? bcdx / bcdy : 0f;
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
				zBuff,
				selected
			);
		
		// fill bottom half of triangle (edges AC BC)
		// first half of AB edge has been filled so need to
		// find where x & z will be, based on the starting y position of BC (so B.y)
		float edge1xIntersection = abdy * acXinc + coords[A].x;
		float edge1zIntersection = abdy * acZinc + coords[A].z;
		ScreenCoord acbyIntersection = new ScreenCoord(
				edge1xIntersection, // ACx intersection at By
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
				zBuff,
				selected
			);
	}
	
	// fills scan lines between 2 points when those 2 points intersect the edges a triangle
	// p1.y must always be equal or less than p2.y
	// for a single triangle call twice, once for top and once for bottom
	private void fillHalfTriangle(ScreenCoord p1, float e1xInc, float e1zInc, ScreenCoord p2, float e2xInc,
			float e2zInc, int colour, float[] zBuff, boolean selected) {

		int startY = Math.max((int)Math.ceil(p1.y), minY);
		int endY = Math.min((int)Math.ceil(p2.y), maxY + 1);
		if (startY >= endY)
			return;

		float ySkip = startY - p1.y;
		float p2ySkip = p2.y - startY;

		float edge1xIntersection = ySkip * e1xInc + p1.x;
		float edge1zIntersection = ySkip * e1zInc + p1.z;
		float edge2xIntersection = p2.x - p2ySkip * e2xInc;
		float edge2zIntersection = p2.z - p2ySkip * e2zInc;

		float leftz = edge1zIntersection;
		float rightz = edge2zIntersection;
		float leftx = edge1xIntersection;
		float rightx = edge2xIntersection;

		for (int y = startY; y < endY; y++) {
			if (rightx < leftx) {
				float t = leftx;
				leftx = rightx;
				rightx = t;

				float tz = leftz;
				leftz = rightz;
				rightz = tz;
			}

			float unclippedLeftx = leftx;
			float dlx = rightx - leftx;
			float zInc = (dlx != 0) ? (rightz - leftz) / dlx : 0f;
			int rasterLeftX = (int)Math.ceil(leftx);
			int rasterRightX = (int)Math.floor(rightx);

			if (!(rasterRightX < minX || rasterLeftX > maxX)) {
				if (rasterLeftX < minX)
					rasterLeftX = minX;
				if (rasterRightX > maxX)
					rasterRightX = maxX;

				float z = leftz + (rasterLeftX - unclippedLeftx) * zInc;
				int row = y * pw;

				for (int x = rasterLeftX; x <= rasterRightX; x++) {
					int idx = row + x;

					if (z > zBuff[idx]) {
						zBuff[idx] = z;
						pixels[idx] = colour;

						if (selected) {
							selectedMask[idx] = true;
						} else {
							selectedMask[idx] = false;
						}
					}

					z += zInc;
				}
			}

edge1xIntersection += e1xInc;
edge1zIntersection += e1zInc;
edge2xIntersection += e2xInc;
edge2zIntersection += e2zInc;

leftx = edge1xIntersection;
rightx = edge2xIntersection;
leftz = edge1zIntersection;
rightz = edge2zIntersection;
}
}
	// mutable instances for drawProjectedTriangle
	Vec4 clipA = new Vec4();
	Vec4 clipB = new Vec4();
	Vec4 clipC = new Vec4();
	Vec4 wireClipA = new Vec4();
	Vec4 wireClipB = new Vec4();
	ScreenCoord screenA = new ScreenCoord();
	ScreenCoord screenB = new ScreenCoord();
	ScreenCoord screenC = new ScreenCoord();
	public void drawProjectedTriangle(Mat4 perspective, Vertex[] v, int colour, float[] zBuff, boolean selected) {
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

		screenA.x = (ndcAx * 0.5f + 0.5f) * (pw - 1);
		screenA.y = (-ndcAy * 0.5f + 0.5f) * (ph - 1);
		screenB.x = (ndcBx * 0.5f + 0.5f) * (pw - 1);
		screenB.y = (-ndcBy * 0.5f + 0.5f) * (ph - 1);
		screenC.x = (ndcCx * 0.5f + 0.5f) * (pw - 1);
		screenC.y = (-ndcCy * 0.5f + 0.5f) * (ph - 1);
		if (is.isSet(Mode.FILL_MODEL)) {
			fillTriangle(screenA, screenB, screenC, colour, zBuff,selected);
		}
		
		if (is.isSet(Mode.SHOW_WIREFRAME)) {
			drawClippedWireframeEdge(clipA, clipB);
			drawClippedWireframeEdge(clipB, clipC);
			drawClippedWireframeEdge(clipC, clipA);
		}

	}

	private void drawClippedWireframeEdge(Vec4 a, Vec4 b) {
		if (!clipLineToHomogeneousFrustum(a, b, wireClipA, wireClipB)) {
			return;
		}

		float ndcAx = wireClipA.x / wireClipA.w;
		float ndcAy = wireClipA.y / wireClipA.w;
		float ndcBx = wireClipB.x / wireClipB.w;
		float ndcBy = wireClipB.y / wireClipB.w;

		if (!Float.isFinite(ndcAx) || !Float.isFinite(ndcAy) ||
				!Float.isFinite(ndcBx) || !Float.isFinite(ndcBy)) {
			return;
		}

		int sx0 = Math.round((ndcAx * 0.5f + 0.5f) * (pw - 1));
		int sy0 = Math.round((-ndcAy * 0.5f + 0.5f) * (ph - 1));
		int sx1 = Math.round((ndcBx * 0.5f + 0.5f) * (pw - 1));
		int sy1 = Math.round((-ndcBy * 0.5f + 0.5f) * (ph - 1));

		bl.drawLineUnsafeClipped(sx0, sy0, sx1, sy1, 0xFFFFFFFF);
	}

	private float clipT0;
	private float clipT1;

	private boolean clipLineToHomogeneousFrustum(Vec4 a, Vec4 b, Vec4 outA, Vec4 outB) {
		float dx = b.x - a.x;
		float dy = b.y - a.y;
		float dz = b.z - a.z;
		float dw = b.w - a.w;

		clipT0 = 0f;
		clipT1 = 1f;

		if (!clipLinePlane(a.x + a.w, dx + dw)) return false; // x >= -w
		if (!clipLinePlane(a.w - a.x, dw - dx)) return false; // x <= w
		if (!clipLinePlane(a.y + a.w, dy + dw)) return false; // y >= -w
		if (!clipLinePlane(a.w - a.y, dw - dy)) return false; // y <= w
		if (!clipLinePlane(a.z + a.w, dz + dw)) return false; // z >= -w
		if (!clipLinePlane(a.w - a.z, dw - dz)) return false; // z <= w

		outA.x = a.x + dx * clipT0;
		outA.y = a.y + dy * clipT0;
		outA.z = a.z + dz * clipT0;
		outA.w = a.w + dw * clipT0;

		outB.x = a.x + dx * clipT1;
		outB.y = a.y + dy * clipT1;
		outB.z = a.z + dz * clipT1;
		outB.w = a.w + dw * clipT1;

		return true;
	}

	private boolean clipLinePlane(float f0, float fd) {
		if (fd == 0f) {
			return f0 >= 0f;
		}

		float t = -f0 / fd;
		if (fd > 0f) {
			if (t > clipT0) {
				clipT0 = t;
			}
		} else {
			if (t < clipT1) {
				clipT1 = t;
			}
		}

		return clipT0 <= clipT1;
	}

	private Vec3 cullAb = new Vec3();
	private Vec3 cullAc = new Vec3();
	private boolean isFrontFacingViewSpace(Vertex[] tri) {
		cullAb.setXYZ(
				tri[1].x - tri[0].x,
				tri[1].y - tri[0].y,
				tri[1].z - tri[0].z);
		cullAc.setXYZ(
				tri[2].x - tri[0].x,
				tri[2].y - tri[0].y,
				tri[2].z - tri[0].z);
		cullAb.mutableCross(cullAc);
		float facing = cullAb.x * tri[0].x + cullAb.y * tri[0].y + cullAb.z * tri[0].z;
		return facing < 0f;
	}
	
	public void drawMesh(RenderableObject ro, Camera cam, Mat4 projection, float[] zBuff, DirectionalLight light, Mat4 worldModel, boolean selected) {
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
			litColour = applyFlatLighting(v0, v1, v2, litColour, light);
			Vertex.ClippedTriangles ct =  Vertex.clipTriangleNear(v0,v1,v2,0.1f);

			if (ct.t1 != null && isFrontFacingViewSpace(ct.t1)) {
				drawProjectedTriangle(projection, ct.t1, litColour, zBuff, selected);
			}
			if (ct.t2 != null && isFrontFacingViewSpace(ct.t2)) {
				drawProjectedTriangle(projection, ct.t2, litColour, zBuff, selected);
			}
		}
	}
	
	private static final int SELECTION_OUTLINE_COLOUR = 0xFFFFFF00;

	public void drawSelectedOutlineFromMask() {
	    for (int y = 1; y < ph - 1; y++) {
	        int row = y * pw;

	        for (int x = 1; x < pw - 1; x++) {
	            int idx = row + x;

	            if (!selectedMask[idx]) {
	                continue;
	            }

	            boolean isEdge =
	                    !selectedMask[idx - 1] ||
	                    !selectedMask[idx + 1] ||
	                    !selectedMask[idx - pw] ||
	                    !selectedMask[idx + pw] ||
	                    !selectedMask[idx - pw - 1] ||
	                    !selectedMask[idx - pw + 1] ||
	                    !selectedMask[idx + pw - 1] ||
	                    !selectedMask[idx + pw + 1];

	            if (isEdge) {
	                pixels[idx] = SELECTION_OUTLINE_COLOUR;
	            }
	        }
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
	
	public void drawBoundingBox(RenderableObject ro, Camera cam, Mat4 projection, Mat4 worldModel) {
	    if (ro == null || ro.mesh == null) return;

	    float minX = ro.mesh.minX;
	    float minY = ro.mesh.minY;
	    float minZ = ro.mesh.minZ;
	    float maxX = ro.mesh.maxX;
	    float maxY = ro.mesh.maxY;
	    float maxZ = ro.mesh.maxZ;

	    bboxLocal[0].set(minX, minY, minZ, 1f);
	    bboxLocal[1].set(maxX, minY, minZ, 1f);
	    bboxLocal[2].set(maxX, maxY, minZ, 1f);
	    bboxLocal[3].set(minX, maxY, minZ, 1f);

	    bboxLocal[4].set(minX, minY, maxZ, 1f);
	    bboxLocal[5].set(maxX, minY, maxZ, 1f);
	    bboxLocal[6].set(maxX, maxY, maxZ, 1f);
	    bboxLocal[7].set(minX, maxY, maxZ, 1f);

	    for (int i = 0; i < 8; i++) {
	        bboxWorld[i] = worldModel.multiplyVec(bboxLocal[i], bboxWorld[i]);
	    }

	    Mat4 view = cam.getView();

	    for (int[] edge : BBOX_EDGES) {
	        drawLineWorldSpace(view, projection,
	                bboxWorld[edge[0]],
	                bboxWorld[edge[1]],
	                0xFFFFFF00);
	    }
	}
	
	private void drawLineWorldSpace(Mat4 view, Mat4 projection, Vec4 a, Vec4 b, int colour) {
	    Vec4 viewA4 = view.multiplyVec(a);
	    Vec4 viewB4 = view.multiplyVec(b);
	    
	    Vertex viewA = new Vertex(viewA4);
	    Vertex viewB = new Vertex(viewB4);
	    
	    // clip against the near plane
	    
	    float near = 0.1f;
	    ClippedLine clippedLine = ClippedLine.clipLineNear(viewA, viewB, near);
	    if (!clippedLine.visible) return;
	    
	    Vec4 clipA = projection.multiplyVec(new Vec4(clippedLine.a));
	    Vec4 clipB = projection.multiplyVec(new Vec4(clippedLine.b));
	    
	    
	    float epsilon = 0.001f;
	    if (clipA.w <= epsilon || clipB.w <= epsilon) {
	        return;
	    }

	    float ndcAx = clipA.x / clipA.w;
	    float ndcAy = clipA.y / clipA.w;

	    float ndcBx = clipB.x / clipB.w;
	    float ndcBy = clipB.y / clipB.w;

	    // reject non-infinite results
	    if (!Float.isFinite(ndcAx) || !Float.isFinite(ndcAy) ||
	        !Float.isFinite(ndcBx) || !Float.isFinite(ndcBy)) {
	        return;
	    }
	    
	    //map to screen
	    int sx0 = Math.round((ndcAx * 0.5f + 0.5f) * (maxX - 1));
	    int sy0 = Math.round((-ndcAy * 0.5f + 0.5f) * (maxY - 1));

	    int sx1 = Math.round((ndcBx * 0.5f + 0.5f) * (maxX - 1));
	    int sy1 = Math.round((-ndcBy * 0.5f + 0.5f) * (maxY - 1));

	    bl.drawLineUnsafeClipped(sx0, sy0, sx1, sy1, colour);
	}
	
	public void drawCapturedSelectionOverlay(RenderableObject ro, Mat4 worldModel, Camera cam, Mat4 projection) {
	    if (ro == null || worldModel == null) return;
        drawBoundingBox(ro, cam, projection, worldModel);
	}
	
}
