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
	
	private void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
		// Flat vertices array, triangle is formed from points A,B,C
		// A = v[0],v[1]; B=v[2],v[3];c=v[4],v[5]
		// Edges are A->B, A->C, B->C
		// AB = v[0],v[1],v[2],v[3]
		// AC = v[0],v[1],v[4],v[5]
		// BC = v[2],v[3], v[4],v[5]
		int v[] = new int[] {x0, y0, x1, y1, x2, y2};
		// sort the vertices in Y order ascending
		v = unsafeSortPolygonVerticesByY(v);

		// description defines sorted edges as AB, AC, BC
		// do top half of triangle, so edges AB & AC 
		// AB is v[0],v[1] to v[2],v[3] & AC is v[0],v[1]   
		// find the x increment for each edge on the scanline
		int abdx = v[2] - v[0]; // delta x
		int abdy = v[3] - v[1]; // delta y
		float abX = (float)abdx / (float)abdy;

		int acdx = v[4] - v[0];
		int acdy = v[5] - v[1];
		float acX = (float)acdx / (float)acdy;

		// edge1xIntersection = number of pixels left edge moves as we move down y
		// edge2xIntersection = number of pixels until intersection from left as we move down y
		// both edges have the same intersection to start with
		float edge1xIntersection = (float)v[0];
		float edge2xIntersection = (float)v[0];		


/*
		// for each scanline x intersection fill in the pixels between
		for (int y=v[1]; y<v[3];y++) {
			// workout left and right intersections for line
			int leftx = Math.round(edge1xIntersection);
			int rightx = Math.round(edge2xIntersection);

			// ensure we always go left to right
			if(rightx < leftx) {
				int t=leftx;
				leftx = rightx;
				rightx = t;
			}

			for (int x = leftx; x<=rightx; x++)
				pixels[y*width+x] = colour;

			edge1xIntersection += abX;
			edge2xIntersection += acX;
		}
*/		
		int bcdx = v[4] - v[2];
		int bcdy = v[5] - v[3];
		float bcX = (float)bcdx / (float)bcdy;
		
		// left tracker is the AB y delta multiplied by the x increment of AC added to Ax 
		// one of the trackers is actually in the correct position so could calculate or
		// could do leftTracker = rightTracker < leftTracker ? rightTracker : leftTracker
		edge1xIntersection = abdy * acX + v[0];
//		edge2xIntersection = v[2];
		fillHalfTriangle(v[0],v[1], acX, v[2], v[3], abX, colour, true);
		fillHalfTriangle(Math.round(edge1xIntersection),v[3], acX, v[2], v[5], bcX, colour, false);
/*
		// for each scanline x intersection fill in the pixels between
		for (int y=v[3]; y<v[5];y++) {
			// workout left and right intersections for line
			int leftx = Math.round(edge1xIntersection);
			int rightx = Math.round(edge2xIntersection);

			// ensure we always go left to right
			if(rightx < leftx) {
				int t=leftx;
				leftx = rightx;
				rightx = t;
			}

			for (int x = leftx; x<=rightx; x++)
				pixels[y*width+x] = colour;

			edge1xIntersection += acX;
			edge2xIntersection += bcX;
		}
*/
	}
	
	private void fillHalfTriangle(int e1x, int e1y, float e1xInc, int e2x, int e2y, float e2xInc, int colour, boolean isTop) {
		// for each scanline x intersection fill in the pixels between
		int leftx = e1x;
		int rightx = isTop ? e1x: e2x;

		float edge1xIntersection = (float) leftx;
		float edge2xIntersection = (float) rightx;

		for (int y=e1y; y<e2y;y++) {
			// workout left and right intersections for line

			// ensure we always go left to right
			if(rightx < leftx) {
				int t=leftx;
				leftx = rightx;
				rightx = t;
			}

			for (int x = leftx; x<=rightx; x++)
				pixels[y*width+x] = colour;

			edge1xIntersection += e1xInc;
			edge2xIntersection += e2xInc;
			leftx = Math.round(edge1xIntersection);
			rightx = Math.round(edge2xIntersection);
		}		
	}
		
	private void renderFrame(double tSeconds) {
		int colour = 0xFFFFCC00;
		clear(0xFF101018);
		
		int dY = vpMaxYExclusive - vpMinY;
		int dX = vpMaxXExclusive - vpMinX;
		 
		// triangle with no clip
		//drawTriangle (vpMinX, vpMinY, vpMinX, vpMinY + (dY / 2), vpMinX + (dX / 2), vpMinY + (dY / 2), colour);
		//fillTriangle (vpMinX, vpMinY, vpMinX, vpMinY + (dY / 2), vpMinX + (dX / 2), vpMinY + (dY / 2), colour);
		fillTriangle (20, 20, 100, 400, 300, 200, colour);
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
