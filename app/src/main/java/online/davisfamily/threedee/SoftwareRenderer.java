package online.davisfamily.threedee;

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.mouse.MouseHandler;
import online.davisfamily.threedee.scene.Scene;
import online.davisfamily.threedee.testing.TestScene;


public class SoftwareRenderer extends JPanel {
// -- canvas extents ------------------
	private final int width;
	private final int height;
// ------------------------------------
	public int getWidth() {return width;}
	public int getHeight() {return height;}
			
	private JFrame frame;
	private Canvas canvas;
	JRootPane root;
	private Scene scene;
	private BufferStrategy bufferStrategy;
	
	public SoftwareRenderer (int width, int height, int minX, int minY, int maxX, int maxY) throws AWTException{
		this.width = width;
		this.height = height;

		frame = new JFrame("Software Renderer");
		canvas = new Canvas();
		canvas.setSize(width, height);
		canvas.setIgnoreRepaint(true);

		frame.add(canvas);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		canvas.createBufferStrategy(2);
		bufferStrategy = canvas.getBufferStrategy();
		root = frame.getRootPane();

		ViewDimensions vd = new ViewDimensions(width, height, minX, minY, maxX, maxY);
		scene = new TestScene(root, vd);
		MouseHandler mh = new MouseHandler(scene, canvas);
		canvas.addMouseListener(mh);
		canvas.addMouseMotionListener(mh);
		setPreferredSize(new Dimension(width, height));
	}
	
	public Canvas getCanvas() {return canvas;}
	public JRootPane getRootPane() {return root;}
		
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.drawImage(scene.getImage(),  0,  0,  null);
	}
			
	public static void main(String[] args){		
		SwingUtilities.invokeLater(() -> {
			try {
				int w = 960, h = 540;
				int minX = 0, minY = 0, maxX = w, maxY = h;
				SoftwareRenderer renderer = new SoftwareRenderer(w,h, minX, minY, maxX, maxY);
				boolean running = true;
				new Thread(() -> {
					long last = System.nanoTime();

					while (running) {
					    long now = System.nanoTime();
					    double dt = (now - last) / 1_000_000_000.0;
					    last = now;

					    if (dt > 0.1) dt = 0.1;

					    renderer.scene.renderFrame(dt);
					    
					    do {
					        do {
					            Graphics g = renderer.bufferStrategy.getDrawGraphics();
					            try {
					                g.drawImage(renderer.scene.getImage(), 0, 0, null);
					            } finally {
					                g.dispose();
					            }
					        } while (renderer.bufferStrategy.contentsRestored());

					        renderer.bufferStrategy.show();
					    } while (renderer.bufferStrategy.contentsLost());

					    Toolkit.getDefaultToolkit().sync();
					}
				}).start();

			} catch (AWTException awt) {
				System.out.println(awt.getMessage());
			}
		});
	}
}
