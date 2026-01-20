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

import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClip;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClip.CohenSutherlandLineClipResult;


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
	
	private CohenSutherlandLineClip clipper;
	
	public SoftwareRenderer (int width, int height, int minX, int minY, int maxX, int maxY) {
		this.width = width;
		this.height = height;
		
		this.vpMinX = minX;
		this.vpMinY = minY;
		this.vpMaxXExclusive = maxX;
		this.vpMaxYExclusive = maxY;
		
		this.image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
		this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		this.clipper = new CohenSutherlandLineClip(vpMinX, vpMinY, vpMaxXExclusive-1, vpMaxYExclusive-1);		
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
	private void renderFrame(double tSeconds) {
		int colour = 0xFFFFCC00;
		clear(0xFF101018);
		
		int dY = vpMaxYExclusive - vpMinY;
		int dX = vpMaxXExclusive - vpMinX;
		 
		// triangle with no clip
		drawTriangle (vpMinX, vpMinY, vpMinX, vpMinY + (dY / 2), vpMinX + (dX / 2), vpMinY + (dY / 2), colour);
	}
	
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
		
		CohenSutherlandLineClipResult viewportLine = clipper.computeViewportLine(x0, y0, x1, y1);
		if (!viewportLine.accept) return;

		int deltaX = Math.abs(viewportLine.x1 - viewportLine.x0);
		int deltaY = Math.abs(viewportLine.y1 - viewportLine.y0);
		
		if (deltaY < deltaX) {
			if (viewportLine.x0 > viewportLine.x1)
				lineLow(viewportLine.x1, viewportLine.y1, viewportLine.x0, viewportLine.y0, colour);
			else
				lineLow(viewportLine.x0, viewportLine.y0, viewportLine.x1, viewportLine.y1, colour);
		} else {
			if (viewportLine.y0 > viewportLine.y1)
				lineHigh(viewportLine.x1, viewportLine.y1, viewportLine.x0, viewportLine.y0, colour);
			else
				lineHigh(viewportLine.x0, viewportLine.y0, viewportLine.x1, viewportLine.y1, colour);
		}
	}

	private void drawTriangle (int x0, int y0, int x1, int y1, int x2, int y2, int colour) {
		drawLineBresenhamUnsafeClipped(x0, y0, x1, y1, colour);
		drawLineBresenhamUnsafeClipped(x1, y1, x2, y2, colour);
		drawLineBresenhamUnsafeClipped(x2, y2, x0, y0, colour);
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			int w = 960, h = 540;
			int minX = 80, minY = 80, maxX = 120, maxY = 120; 
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
