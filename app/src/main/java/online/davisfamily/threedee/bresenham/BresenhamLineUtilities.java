package online.davisfamily.threedee.bresenham;

import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper.LineClipResults;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.matrices.Vertex;

public class BresenhamLineUtilities {

	private int[] p;
	private int pw;
	private int ph;
	
	private CohenSutherlandLineClipper c;
	private LineClipResults r;
	
	public BresenhamLineUtilities(int[] pixels, int pixelsWidth, CohenSutherlandLineClipper clipper){
		if (pixels == null || pixelsWidth <= 0 || pixels.length % pixelsWidth !=0 )
			throw new UnsupportedOperationException("Error with pixels or pixelsWidth, pixels cannot be null and must be integer divisible by pixelsWidth");
		pw = pixelsWidth;
		p = pixels;
		ph = p.length / pw;
		c = clipper;
		r = new LineClipResults();
	}
	
	private void lineLow(int x0, int y0, int x1, int y1, int colour) {
		int deltaX = x1 - x0;
		int deltaY = y1 - y0;
		int yi = 1;
		if (deltaY < 0) {
			yi = -1;
			deltaY = deltaY * -1;
		}
		int yDecision = 2 * deltaY - deltaX;
		int idx = y0 * pw + x0;
		for (int x = x0; x <= x1; x++) {
			p[idx] = colour;
			idx+=1;
			if (yDecision >= 0) {
				idx += yi * pw;
				yDecision += (2 * (deltaY - deltaX));
			} else {
				yDecision += 2 * deltaY;
			}
		}
	}
	
	private void lineHigh(int x0, int y0, int x1, int y1, int colour) {
		int deltaX = x1 - x0;
		int deltaY = y1 - y0;
		int xi = 1;

		if (deltaX < 0) {
			xi = -1;
			deltaX *= -1;
		}

		int xDecision = 2 * deltaX - deltaY;
		int idx = y0 * pw + x0;
		for (int y = y0; y <= y1; y++) {
			p[idx] = colour;
			idx+=pw;
			if (xDecision >= 0) {
				idx += xi;
				xDecision += (2 * (deltaX - deltaY));
			} else {
				xDecision += 2 * deltaX;
			}
		}
	}
	
	public void drawLineUnsafeClipped(int x0, int y0, int x1, int y1, int colour) {
		
		boolean accepted = c.computeViewportLine(x0, y0, x1, y1, r);
		if (!accepted) return;

		if (r.x0 < 0 || r.x0 >= pw || r.y0 < 0 || r.y0 >= ph ||
			    r.x1 < 0 || r.x1 >= pw || r.y1 < 0 || r.y1 >= ph) {
			    throw new IllegalStateException(
			        "Clipper returned OOB endpoints: (" + r.x0 + "," + r.y0 + ") -> (" + r.x1 + "," + r.y1 + ")"
			    );
			}
		
		int deltaX = Math.abs(r.x1 - r.x0);
		int deltaY = Math.abs(r.y1 - r.y0);
		
		if (deltaY < deltaX) {
			if (r.x0 > r.x1)
				lineLow(r.x1, r.y1, r.x0, r.y0, colour);
			else
				lineLow(r.x0, r.y0, r.x1, r.y1, colour);
		} else {
			if (r.y0 > r.y1)
				lineHigh(r.x1, r.y1, r.x0, r.y0, colour);
			else
				lineHigh(r.x0, r.y0, r.x1, r.y1, colour);
		}
	}
	
	public void drawTriangle (int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
		drawLineUnsafeClipped(x0, y0, x1, y1, colour);
		drawLineUnsafeClipped(x1, y1, x2, y2, colour);
		drawLineUnsafeClipped(x2, y2, x0, y0, colour);
	}
	
	public void drawCube (Vec3[] vertices, int[][]edges, int colour) {
		for (int[] e: edges) {
			Vec3 a = vertices[e[0]];
			Vec3 b = vertices[e[1]];
			
			int x0 = a.projectX(a.x, pw, ph);
			int y0 = a.projectY(a.y, pw, ph);
			int x1 = a.projectX(b.x, pw, ph);
			int y1 = a.projectY(b.y, pw, ph);
			
			drawLineUnsafeClipped(x0, y0, x1, y1, colour);
		}
	}

	// this needs updating - create the mvp outside and pass in - object transformation is not part of drawing
	public void drawCube (Vec4[] vertices, int[][]edges, float angleX, float angleY, float zTranslation, int colour) {
		float aspect = (float)pw / (float)ph;
		Mat4 model = Mat4.translation(0, 0, zTranslation)
				.immutableMultiplyMatrix(Mat4.rotationY(angleY))
				.immutableMultiplyMatrix(Mat4.rotationX(angleX));

		Mat4 view = Mat4.identity();
		Mat4 projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		Mat4 mvp = projection.immutableMultiplyMatrix(view).immutableMultiplyMatrix(model);
		
		int[] sx = new int[vertices.length];
		int[] sy = new int[vertices.length];
		boolean[] visible = new boolean[vertices.length];
		for (int i = 0; i<vertices.length; i++) {
			Vec4 clip = mvp.multiplyVec(vertices[i]);
			
			// clip-space bounds test (conservative)
			if (clip.x < -clip.w || clip.x > clip.w ||
			    clip.y < -clip.w || clip.y > clip.w ||
			    clip.z < -clip.w || clip.z > clip.w) {
			    visible[i] = false;
			    continue;
			}
			
			float ndcX = clip.x / clip.w;
			float ndcY = clip.y / clip.w;
			sx[i] = (int)((ndcX * 0.5f + 0.5f) * (pw - 1));
			sy[i] = (int)((-ndcY * 0.5f + 0.5f) * (ph - 1));
			visible[i] = true;
		}
		
		for (int[] e: edges) {
			int a = e[0];
			int b = e[1];
			
			if (!visible[a] || !visible[b]) continue;
			
			drawLineUnsafeClipped(sx[a], sy[a], sx[b], sy[b], colour);
		}
	}
	
	public static class ClippedLine {
		public Vertex a;
		public Vertex b;
		public boolean visible;
		
		public static boolean isNear (Vertex v, float near) {
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
		
		public static ClippedLine clipLineNear(Vertex a, Vertex b, float near) {
			boolean aInside = isNear(a, near);
			boolean bInside = isNear(b, near);
			ClippedLine result = new ClippedLine();
			
			if (!aInside && !bInside) {
				result.visible = false;
				return result;
			}
			
			if (aInside && bInside) {
				result.visible = true;
				result.a = a;
				result.b = b;
				return result;
			}
			
			Vertex intersection = intersectNear(a, b, near);
			
			if (!aInside) {
				result.a = intersection;
				result.b = b;
			} else {
				result.a = a;
				result.b = intersection;
			}

			result.visible = true;
			return result;
		}
		
	}
}
