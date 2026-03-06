package online.davisfamily.threedee;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.KeyBindings;
import online.davisfamily.threedee.input.mouse.MouseHandler;
import online.davisfamily.threedee.testing.TestScene;
import online.davisfamily.threedee.triangles.TriangleRenderer;


public class SoftwareRenderer extends JPanel {
// -- canvas extents ------------------
	private final int width;
	private final int height;
// ------------------------------------
	public int getWidth() {return width;}
	public int getHeight() {return height;}
	
// -- viewport extents within canvas --
	private int vpMinX;
	private int vpMinY;
	private int vpMaxXExclusive;
	private int vpMaxYExclusive;
// ------------------------------------
	
	private final BufferedImage image;
	private final int[] pixels; // [x][y] coords of pixels represented in a single dimension - x values are x*y + x
// -- z-buffer	
//	private final int[] colourBuffer; // colour of each pixel using same xy convention as pixels
	private final float[] depthBuffer; // records nearest value previously stored at pixel [x][y] 
	
	// wireframe drawing utility classes
	private CohenSutherlandLineClipper clipper;
	private BresenhamLineUtilities bl;
	private TriangleRenderer tr;
	private long lastRenderTime;
	
	public SoftwareRenderer (int width, int height, int minX, int minY, int maxX, int maxY) throws AWTException{
		this.width = width;
		this.height = height;
		this.vpMinX = minX;
		this.vpMinY = minY;
		this.vpMaxXExclusive = maxX;
		this.vpMaxYExclusive = maxY;
		this.image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
		this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		this.depthBuffer = new float[pixels.length];
		Arrays.fill(this.depthBuffer, Float.NEGATIVE_INFINITY);
		this.clipper = new CohenSutherlandLineClipper(vpMinX, vpMinY, vpMaxXExclusive-1, vpMaxYExclusive-1);
		this.bl = new BresenhamLineUtilities(pixels, width, clipper);
		this.tr = new TriangleRenderer(pixels, width, vpMinX, vpMinY, vpMaxXExclusive-1, vpMaxYExclusive-1);
		setPreferredSize(new Dimension(width, height));
		lastRenderTime = System.nanoTime();
	}
	
	public CohenSutherlandLineClipper getClipper() {return clipper;}
	public BresenhamLineUtilities getBresenhamLineImpl() {return bl;}
	public TriangleRenderer getTriangleRenderer() {return tr;}
	public int[] getPixels() {return pixels;}
	
	private void clear (int argb) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.drawImage(image,  0,  0,  null);
	}
			
	private void renderFrame(Scene scene) {
		long current = System.nanoTime(); 
		double t = (current - lastRenderTime) / 1_000_000_000.0;
		lastRenderTime = current;
		scene.renderFrame(t);
	}
	
	public static void main(String[] args){		
		SwingUtilities.invokeLater(() -> {
			try {
				int w = 960, h = 540;
				int minX = 0, minY = 0, maxX = w, maxY = h;
				SoftwareRenderer renderer = new SoftwareRenderer(w,h, minX, minY, maxX, maxY);
				JFrame frame = new JFrame("Software Renderer");
				frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
				frame.setContentPane(renderer);
				frame.pack();
				frame.setLocationRelativeTo(null);
				frame.setVisible(true);
				
				ViewDimensions vd = new ViewDimensions(w, h, minX, minY, maxX, maxY);
				TestScene ts = new TestScene(vd, renderer);
				MouseHandler mh = new MouseHandler(ts, frame);
				frame.addMouseListener(mh);
				frame.addMouseMotionListener(mh);
				new Timer(16, e -> {
					renderer.renderFrame(ts);
					renderer.repaint();
				}).start();
			} catch (AWTException awt) {
				System.out.println(awt.getMessage());
			}
		});
	}
}
