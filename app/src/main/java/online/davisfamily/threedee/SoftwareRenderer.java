package online.davisfamily.threedee;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper.LineClipResults;


public class SoftwareRenderer extends JPanel {
	// window defs
	private final int width;
	private final int height;
	
	// viewport defs
	private int vpMinX;
	private int vpMinY;
	private int vpMaxXExclusive;
	private int vpMaxYExclusive;
	

	private final BufferedImage image;
	private final int[] pixels; //pixel argb values - multiply y coord by width and add x coord for pixel argb value
	
	private CohenSutherlandLineClipper clipper;
	private LineClipResults lcResults;
	
	public SoftwareRenderer (int width, int height, int minX, int minY, int maxX, int maxY) {
		this.width = width;
		this.height = height;
		
		this.vpMinX = minX;
		this.vpMinY = minY;
		this.vpMaxXExclusive = maxX;
		this.vpMaxYExclusive = maxY;
		
		this.image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
		this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		this.clipper = new CohenSutherlandLineClipper(vpMinX, vpMinY, vpMaxXExclusive-1, vpMaxYExclusive-1);
		this.lcResults = new LineClipResults();
		setPreferredSize(new Dimension(width, height));
	}
	
	private void clear (int argb) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
	}
	
/*
	private void setPixel(int x, int y, int argb) {
		if (x < vpMinX || x >= vpMaxXExclusive || y < vpMaxYExclusive || y >= vpMaxYExclusive) return;
		pixels[y*width + x] = argb;
	}
	
	private void setPixelUnsafe (int x, int y, int argb) {
		// can be called if algorithm guarantees pixel is within the image bounds
		pixels[y*width + x] = argb;
	}
*/	
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.drawImage(image,  0,  0,  null);
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
		int idx = y0 * width + x0;
		for (int x = x0; x <= x1; x++) {
			pixels[idx] = colour;
			idx+=1;
			if (yDecision >= 0) {
				idx += yi * width;
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
		int idx = y0 * width + x0;
		for (int y = y0; y <= y1; y++) {
			pixels[idx] = colour;
			idx+=width;
			if (xDecision >= 0) {
				idx += xi;
				xDecision += (2 * (deltaX - deltaY));
			} else {
				xDecision += 2 * deltaX;
			}
		}
	}
	
	public void drawLineBresenhamUnsafeClipped(int x0, int y0, int x1, int y1, int colour) {
		
		boolean accepted = clipper.computeViewportLine(x0, y0, x1, y1, lcResults);
		if (!accepted) return;

		int deltaX = Math.abs(lcResults.x1 - lcResults.x0);
		int deltaY = Math.abs(lcResults.y1 - lcResults.y0);
		
		if (deltaY < deltaX) {
			if (lcResults.x0 > lcResults.x1)
				lineLow(lcResults.x1, lcResults.y1, lcResults.x0, lcResults.y0, colour);
			else
				lineLow(lcResults.x0, lcResults.y0, lcResults.x1, lcResults.y1, colour);
		} else {
			if (lcResults.y0 > lcResults.y1)
				lineHigh(lcResults.x1, lcResults.y1, lcResults.x0, lcResults.y0, colour);
			else
				lineHigh(lcResults.x0, lcResults.y0, lcResults.x1, lcResults.y1, colour);
		}
	}

	private void drawTriangle (int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
		
		drawLineBresenhamUnsafeClipped(x0, y0, x1, y1, colour);
		drawLineBresenhamUnsafeClipped(x1, y1, x2, y2, colour);
		drawLineBresenhamUnsafeClipped(x2, y2, x0, y0, colour);
	}
	
	private String verticesAsString (int []v) {
		StringBuffer b = new StringBuffer();
		for (int i = 0; i < v.length; i+=2) {
			b.append(String.format("(%d, %d),", v[i], v[i+1]));
		}
		b.setLength(b.length()-1);
		return b.toString();
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
	
	private void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
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
		if (v[Ay] < v[By])
			fillHalfTriangle(v[Ax],v[Ay], acXinc, v[Ax], v[By], abXinc, colour);

		// fill bottom half using AC and BC
		// For ACx start point we must calculate based on the last y position in the top half of the triangle 
		// = ABy delta * x increment of AC + Ax
		float edge1xIntersection = abdy * acXinc + v[Ax];
		// For ACy start point use the By coord as it is the bottom of top half
		if (v[By] < v[Cy])
			fillHalfTriangle(Math.round(edge1xIntersection),v[By], acXinc, v[Bx], v[Cy], bcXinc, colour);
	}
	
	// scanline fill for half a triangle using 2 edges
	// need to provide the xy coords for each edge plus scanline x increment for each edge
	// i.e. when y increases by 1 how many pixels will the x advance by based on the slope
	private void fillHalfTriangle(int e1x, int e1y, float e1xInc, int e2x, int e2y, float e2xInc, int colour) {
		int leftx = e1x;
		int rightx = e2x;
		
		float edge1xIntersection = (float) leftx;
		float edge2xIntersection = (float) rightx;
		
		// for each scanline fill in the pixels between the x intersections
		for (int y=e1y; y<e2y;y++) {

			// ensure we always go left to right
			if(rightx < leftx) {
				int t=leftx;
				leftx = rightx;
				rightx = t;
			}

			for (int x = leftx; x<=rightx; x++)
				pixels[y*width+x] = colour;

			// work out the intersections for the next scanline
			edge1xIntersection += e1xInc;
			edge2xIntersection += e2xInc;
			leftx = Math.round(edge1xIntersection);
			rightx = Math.round(edge2xIntersection);
		}		
	}
		
	private void renderFrame(double tSeconds) {
		stressEdgeCrossing();
	}
	
	private void testLineDrawTriangle() {
		int colour = 0xFFFFCC00;
		clear(0xFF101018);
		
		int dY = vpMaxYExclusive - vpMinY;
		int dX = vpMaxXExclusive - vpMinX;
		 
		// triangle with no clip
		drawTriangle (vpMinX, vpMinY, vpMinX, vpMinY + (dY / 2), vpMinX + (dX / 2), vpMinY + (dY / 2), colour);	
	}
	
	
	private void generalFillTriangleTests() {
		// Viewport: 960x540
		// Non-overlapping triangles, covering: general, flat-top, flat-bottom, vertical edge (flat left/right), skinny, steep, etc.

		// 1) General (no flat edges, mixed slopes)
		fillTriangle(80, 80, 220, 140, 120, 260, 0xFFFF0000);   // red

		// 2) Flat-top (two vertices share the same Y)
		fillTriangle(320, 90, 440, 90, 380, 220, 0xFF00FF00);   // green

		// 3) Flat-bottom (two vertices share the same Y)
		fillTriangle(560, 80, 500, 240, 620, 240, 0xFF0000FF);  // blue

		// 4) Left edge vertical (flat left / constant X on one edge)
		fillTriangle(90, 320, 90, 460, 220, 400, 0xFFFFFF00);   // yellow

		// 5) Right edge vertical (flat right / constant X on one edge)
		fillTriangle(320, 320, 460, 380, 460, 500, 0xFFFF00FF); // magenta

		// 6) Skinny / acute (very narrow triangle)
		fillTriangle(620, 320, 628, 500, 700, 420, 0xFF00FFFF); // cyan

		// 7) Steep long edge (one edge with large dy, small dx)
		fillTriangle(780, 80, 820, 500, 900, 200, 0xFFFF8000);  // orange

		// 8) Near-horizontal long edge (shallow slope, tests precision)
		fillTriangle(740, 360, 920, 390, 760, 510, 0xFF8000FF); // purple

		// 9) “Flat-ish” left side via two close X values (stress rounding)
		fillTriangle(520, 320, 540, 520, 560, 350, 0xFF80FF80); // light green

		// 10) Inverted orientation (points in arbitrary order; should still work after sort)
		fillTriangle(220, 520, 140, 380, 300, 420, 0xFF0080FF); // sky blue

	}
	
	private void stressEdgeCrossing() {
		// Viewport: 960x540
		// These are designed to stress: edge crossing within a half, very steep slopes,
		// very shallow slopes, extreme flat-top/flat-bottom, and “near-degenerate but visible”.
		// They’re placed in separate regions so they shouldn’t overlap.

		// -----------------------------
		// A) Edge-crossing within a half
		// -----------------------------
		// A1: Edges cross in the TOP half (AB goes right, AC goes left)
		fillTriangle(200, 60, 320, 220, 80, 260, 0xFFB71C1C);   // deep red

		// A2: Edges cross in the BOTTOM half (BC goes left, AC goes right)
		fillTriangle(520, 60, 420, 260, 640, 220, 0xFF1B5E20);  // deep green

		// -----------------------------
		// B) Very steep slopes
		// -----------------------------
		// B1: Long, steep edge (tiny dx over big dy)
		fillTriangle(80, 300, 95, 520, 240, 360, 0xFF0D47A1);   // deep blue

		// B2: Another steep case, opposite direction
		fillTriangle(320, 300, 305, 520, 460, 420, 0xFF4A148C); // deep purple

		// -----------------------------
		// C) Very shallow slopes
		// -----------------------------
		// C1: Long shallow top edge + tall height
		fillTriangle(520, 300, 860, 318, 620, 520, 0xFFF57F17); // amber

		// C2: Shallow-ish long edge but different orientation/order
		fillTriangle(700, 340, 920, 360, 760, 520, 0xFF006064); // teal

		// -----------------------------
		// D) Extreme flat-top / flat-bottom
		// -----------------------------
		// D1: Very wide flat-top
		fillTriangle(60, 280, 280, 280, 160, 520, 0xFF263238);  // blue grey

		// D2: Very wide flat-bottom
		fillTriangle(520, 280, 620, 520, 900, 520, 0xFF3E2723); // brown

		// -----------------------------
		// E) Near-degenerate but still visible (tests rounding + precision)
		// -----------------------------
		// E1: Skinny but not a line; short top half then long bottom half
		fillTriangle(320, 60, 360, 120, 340, 520, 0xFF1565C0);  // mid blue

		// E2: Skinny, steep, and “tilted” the other way
		fillTriangle(900, 60, 860, 120, 880, 520, 0xFF2E7D32);  // mid green

	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			int w = 960, h = 540;
			//int minX = 80, minY = 80, maxX = 120, maxY = 120;
			int minX = 0, minY = 0, maxX = w, maxY = h;
			SoftwareRenderer panel = new SoftwareRenderer(w,h, minX, minY, maxX, maxY);
			JFrame frame = new JFrame("Software Renderer");
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setContentPane(panel);
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
			
			long startNanos = System.nanoTime();
			
			new Timer(16, e -> {
				double t = (System.nanoTime() - startNanos) / 1_000_000_000.0;
				panel.renderFrame(t);
				panel.repaint();
			}).start();
		});
	}
}
