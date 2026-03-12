package online.davisfamily.threedee.testing;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

import javax.swing.JRootPane;

import online.davisfamily.threedee.Scene;
import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.cohensutherland.CohenSutherlandLineClipper;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.CommandBindings;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.input.keyboard.KeyBindings;
import online.davisfamily.threedee.input.mouse.MouseEventConsumer;
import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.triangles.TriangleRenderer;

public class TestScene implements Scene, MouseEventConsumer{

	private ViewDimensions vd;
	private BresenhamLineUtilities bl;
	private TriangleRenderer tr;
	int[] pixels;
	float[] zBuffer;
	private ObjectTransformation t1;
	private ObjectTransformation t2;
	private Mat4 model1, model2, projection, perspective, vp, mvp1, mvp2;
	private BufferedImage image;
	private float aspect;

	// Camera variables
	private MouseEventDetail mouseInfo;
	private Camera camera;
	private Vec3 move;
	private float speed = 3.0f;
	private DebugUtils debug;
	
	private InputState inputState;
	private JRootPane root;
	public TestScene (JRootPane pane, ViewDimensions dimensions) {
		root = pane;
		vd = dimensions;
		this.inputState = new InputState();
		this.image = new BufferedImage(vd.width, vd.height, BufferedImage.TYPE_INT_ARGB);
		this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		this.zBuffer = new float[pixels.length];

		this.aspect = (float)vd.width / (float)vd.height;
		this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-3.0f);
		this.t2 = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,3.0f,0f,0f);
		this.perspective = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.vp = new Mat4();
		this.model1 = new Mat4();
		this.model2 = new Mat4();
		this.mvp1 = new Mat4();
		this.mvp2 = new Mat4();
		this.camera = new Camera();
		this.move = new Vec3(0,0,0);

		this.mouseInfo = new MouseEventDetail();
		KeyBindings.installKeyBindings(root, this.inputState);
		CommandBindings.installCommandBindings(root, this.inputState);
		this.bl = new BresenhamLineUtilities(pixels, vd.width, new CohenSutherlandLineClipper(vd.vpMinX, vd.vpMinY, vd.vpMaxXExclusive-1, vd.vpMaxYExclusive-1));
		this.debug = new DebugUtils(bl,camera,vd, this.inputState);
		this.tr = new TriangleRenderer(pixels, vd.width, vd.vpMinX, vd.vpMinY, vd.vpMaxXExclusive-1, vd.vpMaxYExclusive-1, this.bl, inputState, debug);
	}
	
	// -- 3D models  ------

	Vec4[] v4CubeVertices = {
			// bottom square
			new Vec4 (-0.5f, -0.5f, -0.5f),
			new Vec4 (-0.5f, -0.5f, 0.5f),
			new Vec4 (0.5f, -0.5f, 0.5f),
			new Vec4 (0.5f, -0.5f, -0.5f),
			// top square
			new Vec4 (-0.5f, 0.5f, -0.5f),
			new Vec4 (-0.5f, 0.5f, 0.5f),
			new Vec4 (0.5f, 0.5f, 0.5f),
			new Vec4 (0.5f, 0.5f, -0.5f),
		};

		int[][] cubeEdges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom square edges
				{4, 5}, {5, 6}, {6, 7}, {7, 4}, // top square edges
				{0, 4}, {1, 5}, // left vertical edges
				{2, 6}, {3, 7}, // right vertical edges
		};	
	
		int[] cubeFaceColours = {
			    0xFFFF0000, // bottom - red
			    0xFF00FF00, // top - green
			    0xFF0000FF, // front - blue
			    0xFFFFFF00, // right - yellow
			    0xFFFF00FF, // back - magenta
			    0xFF00FFFF  // left - cyan
		};
		
		// must make sure these are CCW 
		int[][] cubeTriangles = {
		    // bottom (y = -0.5) outward normal -Y
		    {0, 2, 1}, {0, 3, 2},

		    // top (y = +0.5) outward normal +Y
		    {4, 5, 6}, {4, 6, 7},

		    // front (z = +0.5) outward normal +Z
		    {1, 2, 6}, {1, 6, 5},

		    // right (x = +0.5) outward normal +X
		    {2, 3, 7}, {2, 7, 6},

		    // back (z = -0.5) outward normal -Z
		    {0, 4, 7}, {0, 7, 3},

		    // left (x = -0.5) outward normal -X
		    {0, 1, 5}, {0, 5, 4},
		};
		
	private void clear (int argb) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
	}
	
	private void clearZBuffer() {
		this.zBuffer = new float[pixels.length];
		//Arrays.fill(this.zBuffer, Float.POSITIVE_INFINITY);
		Arrays.fill(this.zBuffer, Float.NEGATIVE_INFINITY);
	}
	
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	private void buildMVP(Mat4 out, Mat4 model, ObjectTransformation tx) {
	    model.setModel(tx);
	    out.set(vp);
	    out.mutableMultiply(model);
	}
	
	private void testFilledCubes(double tSeconds) {
	    buildMVP(mvp1, model1, t1);
	    //tr.drawCube(v4CubeVertices, cubeTriangles, mvp1, cubeFaceColours, zBuffer);
	    buildMVP(mvp2, model2, t2);
	    //tr.drawCube(v4CubeVertices, cubeTriangles, mvp2, cubeFaceColours, zBuffer);

		tr.drawCube(camera, v4CubeVertices, cubeTriangles, model1, perspective, cubeFaceColours, zBuffer, inputState.isSet(Mode.SHOW_WIREFRAME));
		tr.drawCube(camera, v4CubeVertices, cubeTriangles, model2, perspective, cubeFaceColours, zBuffer, inputState.isSet(Mode.SHOW_WIREFRAME));

		transformAndRotate(tSeconds);
   
	}
	
	private void transformAndRotate (double tSeconds) {
		float angularSpeedX = 0.6f;   // radians per second
	    float angularSpeedY = 0.3f;

	    t1.angleX += angularSpeedX * tSeconds;
	    t1.angleY += angularSpeedY * tSeconds;
	    t1.zTranslation += t1.zTranslationInc * tSeconds;
		
	    if (t1.zTranslation < -10) {
			t1.zTranslationInc = 3.0f;
		} else if (t1.zTranslation > -3) {
			t1.zTranslationInc = -3.0f;
		}

	    t2.angleX += angularSpeedX * tSeconds;
	    t2.angleY += angularSpeedY * tSeconds;
	    t2.xTranslation += t2.xTranslationInc * tSeconds;
	    
		if (t2.xTranslation > 4) {
			t2.xTranslationInc = -3.0f;
		} else if (t2.xTranslation < -4) {
			t2.xTranslationInc = 3.0f;
		}
		
	}

	private void updateCamera() {
		if (mouseInfo != null) {
			camera.mouseUpdate(mouseInfo);
			mouseInfo.dx = 0;
			mouseInfo.dy = 0;
		}
	}
	
	private void updatePosition(double dt) {
		move.setXYZ(0, 0, 0);
		if (mouseInfo == null)
			camera.updateBasis();

		if (inputState.w()) this.move.mutableAdd(camera.getForwardXZ());
		if (inputState.s()) this.move.mutableSubtract(camera.getForwardXZ());
		if (inputState.d()) this.move.mutableAdd(camera.getRightXZ());
		if (inputState.a()) this.move.mutableSubtract(camera.getRightXZ());
		if (inputState.up()) this.move.mutableAdd(camera.getUp());
		if (inputState.down()) this.move.mutableSubtract(camera.getUp());		

		if (move.lengthSquared() > 0) {
		    move.mutableNormalize();
		    move.mutableScale(speed * (float)dt);
		    camera.position.mutableAdd(move);
		}
	}
	
	public BufferedImage getImage() {return image;}
	
	public void renderFrame(double tSeconds) {
	    updateCamera();
	    updatePosition(tSeconds);
		this.clear(0xFF000000);
		this.clearZBuffer();
	    buildVP();
	
		testFilledCubes(tSeconds);
		if (inputState.isSet(Mode.SHOW_WORLD_AXES)) debug.drawWorldAxesAt(camera.getView(), projection, 0f, 0f, -1f, 20.0f);
		if (inputState.isSet(Mode.SHOW_GRID)) debug.drawWorldGrid(camera.getView(), perspective, 20, 1.0f);
	    if (inputState.isSet(Mode.SHOW_CAMERA_AXES)) debug.drawCameraOverlayAxes(900, 500, 30);
	    if (inputState.isSet(Mode.SHOW_DEBUG_INFO)) debug.drawDebugText(image, tSeconds, perspective);

	}

	@Override
	public void consume(MouseEventDetail detail) {
		mouseInfo.dx += detail.dx;
		mouseInfo.dy += detail.dy;
	}
}
