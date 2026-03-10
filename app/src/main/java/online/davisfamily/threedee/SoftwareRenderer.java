package online.davisfamily.threedee;

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.dimensions.ViewDimensions;
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
	private JFrame frame;
	private Canvas canvas;
	JRootPane root;
	
	
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
		this.tr = new TriangleRenderer(pixels, width, vpMinX, vpMinY, vpMaxXExclusive-1, vpMaxYExclusive-1, this.bl);
		setPreferredSize(new Dimension(width, height));
		lastRenderTime = System.nanoTime();
	}
	
	public BufferedImage getImage() {return image;}
	public Canvas getCanvas() {return canvas;}
	public JRootPane getRootPane() {return root;}
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
				Canvas canvas = new Canvas();
				canvas.setSize(w, h);
				canvas.setIgnoreRepaint(true);

				frame.add(canvas);
				frame.pack();
				frame.setLocationRelativeTo(null);
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setVisible(true);
				canvas.createBufferStrategy(2);
				renderer.canvas = canvas;
				renderer.frame = frame;
				renderer.root = frame.getRootPane();

				BufferStrategy bufferStrategy = canvas.getBufferStrategy();
				boolean running = true;
				ViewDimensions vd = new ViewDimensions(w, h, minX, minY, maxX, maxY);
				TestScene ts = new TestScene(vd, renderer);
				MouseHandler mh = new MouseHandler(ts, canvas);
				canvas.addMouseListener(mh);
				canvas.addMouseMotionListener(mh);
				new Thread(() -> {
					long last = System.nanoTime();

					while (running) {
					    long now = System.nanoTime();
					    double dt = (now - last) / 1_000_000_000.0;
					    last = now;

					    if (dt > 0.1) dt = 0.1;

					    ts.renderFrame(dt);

					    do {
					        do {
					            Graphics g = bufferStrategy.getDrawGraphics();
					            try {
					                g.drawImage(renderer.image, 0, 0, null);
					            } finally {
					                g.dispose();
					            }
					        } while (bufferStrategy.contentsRestored());

					        bufferStrategy.show();
					    } while (bufferStrategy.contentsLost());

					    Toolkit.getDefaultToolkit().sync();
					}
				}).start();

			} catch (AWTException awt) {
				System.out.println(awt.getMessage());
			}
		});
	}
}
